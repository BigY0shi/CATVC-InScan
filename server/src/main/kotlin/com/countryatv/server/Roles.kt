package com.countryatv.server

import com.countryatv.core.Problem
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.sql.Connection
import java.time.Clock

// What a person can do. Code checks permissions, never role names (roles-permissions-spec.md §1).
// The label is shop language: refusals name the permission that was missing.
enum class Permission(val label: String) {
    SCAN_AND_TAG("Scan, check in, print and confirm tags"),
    EDIT_UNITS("Edit unit details and price"),
    MARK_SOLD("Mark a unit Sold"),
    SEE_OWN_HISTORY("See my own history"),
    SEE_REPORTS("See Reports"),
    EXPORT_REPORTS("Export report data"),
    MANAGE_PEOPLE("Add or deactivate people and reset PINs"),
    CONNECT_PHONE("Connect a phone"),
    RETIRE_AND_UNSELL("Retire a unit or change a sold one"),
    EDIT_UNIT_TYPES("Edit unit types"),
    SHOP_SETTINGS("Change Shop settings"),
    LEGACY_IMPORT("Import old records"),
    SEE_SIGNIN_SECURITY("See sign-in security"),
    EDIT_ROLES("Edit roles and permissions");

    companion object {
        fun parse(name: String): Permission? = entries.firstOrNull { it.name == name }
    }
}

// The three roles seeded at launch. Ids are stable; names are what people see and can't collide.
object BuiltInRoles {
    const val EMPLOYEE = "employee"
    const val MANAGEMENT = "management"
    const val ADMIN = "admin"
}

data class RoleInfo(val id: String, val name: String, val builtIn: Boolean, val fixed: Boolean, val permissions: Set<Permission>)

private fun ruleBroken(rule: String) = ApiProblem(HttpStatusCode.Conflict, Problem.ROLE_RULE, listOf(rule))

fun Caller.require(permission: Permission) {
    if (permission !in permissions) throw ApiProblem(HttpStatusCode.Forbidden, Problem.NOT_ALLOWED, listOf(permission.label))
}

// Reads roles. Admin is fixed: it holds every permission, including ones added after launch.
fun loadRole(c: Connection, roleId: String): RoleInfo? {
    val (name, builtIn, fixed) = c.prepareStatement("SELECT name, built_in, fixed FROM roles WHERE id = ?").use {
        it.setString(1, roleId)
        it.executeQuery().use { rs -> if (rs.next()) Triple(rs.getString(1), rs.getInt(2) == 1, rs.getInt(3) == 1) else return null }
    }
    val permissions = if (fixed) Permission.entries.toSet() else c.prepareStatement("SELECT permission FROM role_permissions WHERE role_id = ?").use {
        it.setString(1, roleId)
        it.executeQuery().use { rs -> buildSet { while (rs.next()) Permission.parse(rs.getString(1))?.let(::add) } }
    }
    return RoleInfo(roleId, name, builtIn, fixed, permissions)
}

// Accepts a role id or name in any case, so "admin", "Admin" and "Management" all work from the CLI.
fun findRoleId(c: Connection, idOrName: String): String? =
    c.prepareStatement("SELECT id FROM roles WHERE id = ? OR lower(name) = lower(?)").use {
        it.setString(1, idOrName.trim()); it.setString(2, idOrName.trim())
        it.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
    }

// Changes to roles and people, with the guardrails of roles-permissions-spec.md §2 enforced here,
// on the server, whatever a phone asks for. Every change is audited.
class RoleAdmin(private val db: Database, private val auth: Auth, private val clock: Clock = Clock.systemUTC()) {

    fun roles(): List<RoleInfo> = db.read { c ->
        val ids = c.createStatement().use { st -> st.executeQuery("SELECT id FROM roles ORDER BY name").use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } } }
        ids.mapNotNull { loadRole(c, it) }
    }

    fun createRole(name: String, permissions: Set<Permission>, by: Caller): String {
        by.require(Permission.EDIT_ROLES)
        val clean = name.trim()
        if (clean.isEmpty()) throw ApiProblem(HttpStatusCode.UnprocessableEntity, Problem.MISSING_DETAILS, listOf("a name for the role"))
        return db.transaction { c ->
            checkGrantable(c, permissions)
            if (findRoleId(c, clean) != null) throw ruleBroken("Another role already has that name.")
            val id = com.countryatv.core.ItemId.new()
            c.prepareStatement("INSERT INTO roles (id, name, built_in, fixed) VALUES (?,?,0,0)").use {
                it.setString(1, id); it.setString(2, clean); it.executeUpdate()
            }
            writePermissions(c, id, permissions)
            audit(c, clock, by.employeeId, by.deviceId, "role.create", "role", id, null, roleJson(clean, permissions))
            id
        }
    }

    fun setRolePermissions(roleId: String, permissions: Set<Permission>, by: Caller) {
        by.require(Permission.EDIT_ROLES)
        db.transaction { c ->
            val role = loadRole(c, roleId) ?: throw ApiProblem(HttpStatusCode.NotFound, Problem.ROLE_NOT_FOUND)
            if (role.fixed) throw ruleBroken("Admin always holds every permission. Its permissions can't be changed.")
            checkGrantable(c, permissions, changing = roleId)
            writePermissions(c, roleId, permissions)
            audit(c, clock, by.employeeId, by.deviceId, "role.update", "role", roleId, roleJson(role.name, role.permissions), roleJson(role.name, permissions))
        }
    }

    fun deleteRole(roleId: String, by: Caller) {
        by.require(Permission.EDIT_ROLES)
        db.transaction { c ->
            val role = loadRole(c, roleId) ?: throw ApiProblem(HttpStatusCode.NotFound, Problem.ROLE_NOT_FOUND)
            if (role.fixed) throw ruleBroken("The Admin role can't be deleted.")
            val members = c.prepareStatement("SELECT COUNT(*) FROM employees WHERE role_id = ?").use {
                it.setString(1, roleId); it.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
            }
            if (members > 0) throw ruleBroken("People still hold this role. Move them to another role first.")
            c.prepareStatement("DELETE FROM role_permissions WHERE role_id = ?").use { it.setString(1, roleId); it.executeUpdate() }
            c.prepareStatement("DELETE FROM roles WHERE id = ?").use { it.setString(1, roleId); it.executeUpdate() }
            audit(c, clock, by.employeeId, by.deviceId, "role.delete", "role", roleId, roleJson(role.name, role.permissions), null)
        }
    }

    fun setEmployeeRole(employeeId: String, roleId: String, by: Caller) {
        by.require(Permission.EDIT_ROLES)
        if (employeeId == by.employeeId) throw ruleBroken("Nobody can change their own role.")
        db.transaction { c ->
            val current = currentRoleId(c, employeeId)
            loadRole(c, roleId) ?: throw ApiProblem(HttpStatusCode.NotFound, Problem.ROLE_NOT_FOUND)
            if (current == roleId) return@transaction
            if (current == BuiltInRoles.ADMIN) ensureAnotherActiveAdmin(c, employeeId)
            c.prepareStatement("UPDATE employees SET role_id = ?, role = ? WHERE id = ?").use {
                it.setString(1, roleId); it.setString(2, legacyRole(roleId)); it.setString(3, employeeId); it.executeUpdate()
            }
            audit(c, clock, by.employeeId, by.deviceId, "employee.role", "employee", employeeId,
                buildJsonObject { put("role", current) }.toString(), buildJsonObject { put("role", roleId) }.toString())
        }
    }

    // Management adds people without reaching above itself: the new person's role must hold no
    // permission the adder lacks. Admins hold everything, so only they can add an Admin.
    fun addEmployee(displayName: String, roleId: String, pin: String, by: Caller): String {
        by.require(Permission.MANAGE_PEOPLE)
        val role = db.read { c -> loadRole(c, roleId) } ?: throw ApiProblem(HttpStatusCode.NotFound, Problem.ROLE_NOT_FOUND)
        ensureCovers(by, role.permissions, "You can't add someone to a role that can do more than you can.")
        return auth.createEmployee(displayName, roleId, pin, actor = by.employeeId, deviceId = by.deviceId)
    }

    fun deactivateEmployee(employeeId: String, by: Caller) {
        by.require(Permission.MANAGE_PEOPLE)
        if (employeeId == by.employeeId) throw ruleBroken("You can't deactivate yourself.")
        db.transaction { c ->
            val role = loadRole(c, currentRoleId(c, employeeId))!!
            ensureCovers(by, role.permissions, "You can't deactivate someone whose role can do more than yours.")
            if (role.id == BuiltInRoles.ADMIN) ensureAnotherActiveAdmin(c, employeeId)
            c.prepareStatement("UPDATE employees SET active = 0 WHERE id = ?").use { it.setString(1, employeeId); it.executeUpdate() }
            c.prepareStatement("DELETE FROM sessions WHERE employee_id = ?").use { it.setString(1, employeeId); it.executeUpdate() }
            audit(c, clock, by.employeeId, by.deviceId, "employee.deactivate", "employee", employeeId, null, null)
        }
    }

    // Guardrail 4: only an Admin resets an Admin's PIN. In general, nobody resets the PIN of a role
    // that can do more than theirs.
    fun resetPin(employeeId: String, newPin: String, by: Caller) {
        by.require(Permission.MANAGE_PEOPLE)
        val role = db.read { c -> loadRole(c, currentRoleId(c, employeeId))!! }
        ensureCovers(by, role.permissions, "Only someone whose role can do everything theirs can may reset this PIN.")
        auth.setPin(employeeId, newPin, actor = by.employeeId, deviceId = by.deviceId)
    }

    private fun currentRoleId(c: Connection, employeeId: String): String =
        c.prepareStatement("SELECT role_id FROM employees WHERE id = ?").use {
            it.setString(1, employeeId)
            it.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        } ?: throw ApiProblem(HttpStatusCode.NotFound, Problem.PERSON_NOT_FOUND)

    private fun ensureCovers(by: Caller, needed: Set<Permission>, rule: String) {
        if (!by.permissions.containsAll(needed)) throw ruleBroken(rule)
    }

    private fun ensureAnotherActiveAdmin(c: Connection, leaving: String) {
        val others = c.prepareStatement("SELECT COUNT(*) FROM employees WHERE role_id = ? AND active = 1 AND id <> ?").use {
            it.setString(1, BuiltInRoles.ADMIN); it.setString(2, leaving)
            it.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        if (others == 0) throw ruleBroken("The shop must always have an active Admin.")
    }

    // Guardrail 3: "Edit roles and permissions" stays with Admin. Guardrail 7: nobody is watched
    // without seeing their own record, so while any role can see Reports (Admin always can), every
    // role keeps "See my own history".
    private fun checkGrantable(c: Connection, permissions: Set<Permission>, changing: String? = null) {
        if (Permission.EDIT_ROLES in permissions) throw ruleBroken("Only Admin can edit roles and permissions.")
        if (Permission.SEE_OWN_HISTORY !in permissions) {
            val anyoneWatches = roles(c).any { it.id != changing && Permission.SEE_REPORTS in it.permissions } || Permission.SEE_REPORTS in permissions
            if (anyoneWatches) throw ruleBroken("Every role keeps \"See my own history\" while anyone can see Reports.")
        }
    }

    private fun roles(c: Connection): List<RoleInfo> {
        val ids = c.createStatement().use { st -> st.executeQuery("SELECT id FROM roles").use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } } }
        return ids.mapNotNull { loadRole(c, it) }
    }

    private fun writePermissions(c: Connection, roleId: String, permissions: Set<Permission>) {
        c.prepareStatement("DELETE FROM role_permissions WHERE role_id = ?").use { it.setString(1, roleId); it.executeUpdate() }
        c.prepareStatement("INSERT INTO role_permissions (role_id, permission) VALUES (?,?)").use { st ->
            for (p in permissions.sortedBy { it.ordinal }) { st.setString(1, roleId); st.setString(2, p.name); st.executeUpdate() }
        }
    }

    private fun roleJson(name: String, permissions: Set<Permission>) = buildJsonObject {
        put("name", name)
        put("permissions", JsonArray(permissions.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) }))
    }.toString()
}

// The employees.role column predates roles; it stays filled for phones released before them.
fun legacyRole(roleId: String) = if (roleId == BuiltInRoles.ADMIN) "admin" else "employee"
