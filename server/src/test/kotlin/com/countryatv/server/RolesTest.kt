package com.countryatv.server

import com.countryatv.core.Problem
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Roles are rows, code checks permissions, and the guardrails of roles-permissions-spec.md §2 hold.
class RolesTest {
    private val shop = TestShop()
    private val admin = RoleAdmin(shop.db, shop.auth, shop.clock)

    private fun person(name: String, role: String): Caller {
        val id = shop.auth.createEmployee(name, role, "1234")
        return shop.db.read { c -> loadRole(c, findRoleId(c, role)!!)!! }.let { Caller(id, name, it.id, it.name, it.permissions, "phone") }
    }

    private fun refusedFor(rule: String, block: () -> Unit) {
        val e = assertFailsWith<ApiProblem> { block() }
        assertEquals(Problem.ROLE_RULE, e.problem, e.details.toString())
        assertTrue(e.details.single().contains(rule), e.details.toString())
    }

    @Test
    fun `the migration seeds three roles and every person keeps a role equal to their old one`() {
        val old = OldShop()
        Database(old.file.path).use { db ->
            val roles = db.read { c -> listOf("employee", "management", "admin").map { loadRole(c, it)!! } }
            assertEquals(listOf("Employee", "Management", "Admin"), roles.map { it.name })
            assertEquals(setOf(Permission.SCAN_AND_TAG, Permission.EDIT_UNITS, Permission.MARK_SOLD, Permission.SEE_OWN_HISTORY), roles[0].permissions)
            assertEquals(roles[0].permissions + setOf(Permission.SEE_REPORTS, Permission.EXPORT_REPORTS, Permission.MANAGE_PEOPLE, Permission.CONNECT_PHONE), roles[1].permissions)
            assertEquals(Permission.entries.toSet(), roles[2].permissions)
            fun roleOf(id: String) = db.read { c -> c.prepareStatement("SELECT role_id || '/' || role FROM employees WHERE id = ?").use { it.setString(1, id); it.executeQuery().use { rs -> rs.next(); rs.getString(1) } } }
            assertEquals("employee/employee", roleOf(old.dale))
            assertEquals("admin/admin", roleOf(old.ray))
        }
    }

    @Test
    fun `sign-in returns the person's id, role and permissions, and keeps the old role field`() = testApplication {
        val id = shop.auth.createEmployee("Mia", "Management", "1234")
        val client = shop.client(this)
        val device = shop.auth.enrollDevice(shop.auth.newSetupCode("test"), "Phone")
        val r = client.post("/auth/login") { header(DEVICE_HEADER, device); contentType(ContentType.Application.Json); setBody(LoginRequest("Mia", "1234")) }.body<LoginResponse>()
        assertEquals(id, r.employeeId)
        assertEquals("Management", r.roleName)
        assertEquals("employee", r.role, "phones released before roles see Management as a non-admin")
        assertTrue("SEE_REPORTS" in r.permissions && "CONNECT_PHONE" in r.permissions && "EDIT_ROLES" !in r.permissions)
    }

    @Test
    fun `connecting a phone needs Connect a phone, which Management has and Employee doesn't`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        shop.auth.createEmployee("Mia", "management", "1234")
        val client = shop.client(this)
        val sam = client.signIn(shop.auth, "Sam", "1234")
        val mia = client.signIn(shop.auth, "Mia", "1234")
        val refused = client.post("/admin/setup-codes") { header("Authorization", "Bearer $sam") }
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals(listOf("Connect a phone"), refused.body<ProblemBody>().details)
        assertEquals(HttpStatusCode.OK, client.post("/admin/setup-codes") { header("Authorization", "Bearer $mia") }.status)
    }

    @Test
    fun `guardrail 1, Admin is fixed`() {
        val owner = person("Owner", "admin")
        refusedFor("can't be changed") { admin.setRolePermissions("admin", setOf(Permission.SCAN_AND_TAG), owner) }
        refusedFor("can't be deleted") { admin.deleteRole("admin", owner) }
    }

    @Test
    fun `guardrail 2, there is always an active Admin`() {
        val owner = person("Owner", "admin")
        val yoshi = person("Yoshi", "admin")
        admin.setEmployeeRole(owner.employeeId, "management", yoshi)
        refusedFor("always have an active Admin") { admin.deactivateEmployee(yoshi.employeeId, person("Nephew", "employee").copy(permissions = Permission.entries.toSet())) }
        val manager = person("Mia", "management")
        refusedFor("can do more than yours") { admin.deactivateEmployee(yoshi.employeeId, manager) }
    }

    @Test
    fun `guardrail 3, only Admin changes roles, can't grant that, and nobody changes their own`() {
        val owner = person("Owner", "admin")
        val manager = person("Mia", "management")
        val sam = person("Sam", "employee")
        assertEquals(Problem.NOT_ALLOWED, assertFailsWith<ApiProblem> { admin.setEmployeeRole(sam.employeeId, "management", manager) }.problem)
        refusedFor("their own role") { admin.setEmployeeRole(owner.employeeId, "employee", owner) }
        refusedFor("Only Admin can edit roles") { admin.setRolePermissions("management", Permission.entries.toSet() - Permission.EDIT_ROLES + Permission.EDIT_ROLES, owner) }
        refusedFor("Only Admin can edit roles") { admin.createRole("Supervisor", setOf(Permission.SEE_OWN_HISTORY, Permission.EDIT_ROLES), owner) }
    }

    @Test
    fun `guardrail 4, only an Admin resets an Admin's PIN`() {
        val owner = person("Owner", "admin")
        val manager = person("Mia", "management")
        val sam = person("Sam", "employee")
        admin.resetPin(sam.employeeId, "4321", manager)
        refusedFor("may reset this PIN") { admin.resetPin(owner.employeeId, "4321", manager) }
        admin.resetPin(owner.employeeId, "4321", person("Yoshi", "admin"))
        assertEquals(2, shop.count("SELECT COUNT(*) FROM audit_log WHERE action = 'employee.pin_reset'"))
    }

    @Test
    fun `guardrail 5, role changes are audited with before and after`() {
        val owner = person("Owner", "admin")
        val sam = person("Sam", "employee")
        admin.setRolePermissions("employee", setOf(Permission.SCAN_AND_TAG, Permission.SEE_OWN_HISTORY), owner)
        admin.setEmployeeRole(sam.employeeId, "management", owner)
        assertEquals("""{"name":"Employee","permissions":["SCAN_AND_TAG","EDIT_UNITS","MARK_SOLD","SEE_OWN_HISTORY"]}""",
            shop.single("SELECT before_json FROM audit_log WHERE action = 'role.update'"))
        assertEquals("""{"name":"Employee","permissions":["SCAN_AND_TAG","SEE_OWN_HISTORY"]}""",
            shop.single("SELECT after_json FROM audit_log WHERE action = 'role.update'"))
        assertEquals("""{"role":"management"}""", shop.single("SELECT after_json FROM audit_log WHERE action = 'employee.role'"))
        assertEquals("employee", shop.single("SELECT role FROM employees WHERE id = ?", sam.employeeId), "legacy column: not an admin")
    }

    @Test
    fun `guardrail 6, a removed permission refuses the very next request`() = testApplication {
        val owner = person("Owner", "admin")
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val sam = client.signIn(shop.auth, "Sam", "1234")
        assertEquals(HttpStatusCode.Created, client.createUnit(sam, unit()).status)
        admin.setRolePermissions("employee", setOf(Permission.EDIT_UNITS, Permission.MARK_SOLD, Permission.SEE_OWN_HISTORY), owner)
        val refused = client.createUnit(sam, unit())
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals(listOf(Permission.SCAN_AND_TAG.label), refused.body<ProblemBody>().details)
        assertEquals(1, shop.count("SELECT COUNT(*) FROM audit_log WHERE action = 'refused:NOT_ALLOWED'"))
    }

    @Test
    fun `guardrail 7, nobody loses their own history while anyone can see Reports`() {
        val owner = person("Owner", "admin")
        refusedFor("See my own history") { admin.setRolePermissions("employee", setOf(Permission.SCAN_AND_TAG), owner) }
        refusedFor("See my own history") { admin.createRole("Lot porter", setOf(Permission.SCAN_AND_TAG), owner) }
        val porter = admin.createRole("Lot porter", setOf(Permission.SCAN_AND_TAG, Permission.SEE_OWN_HISTORY), owner)
        assertEquals(setOf(Permission.SCAN_AND_TAG, Permission.SEE_OWN_HISTORY), admin.roles().single { it.id == porter }.permissions)
    }

    @Test
    fun `Management adds people only into roles that can't do more than Management`() {
        val manager = person("Mia", "management")
        admin.addEmployee("Sam", "employee", "1234", manager)
        refusedFor("can do more than you can") { admin.addEmployee("Sneaky", "admin", "1234", manager) }
        assertEquals(manager.employeeId, shop.single("SELECT actor FROM audit_log WHERE action = 'employee.create' AND after_json LIKE '%Sam%'"))
    }

    @Test
    fun `retiring and undoing a sale need Retire, which only Admin has at launch`() = testApplication {
        shop.auth.createEmployee("Mia", "management", "1234")
        val client = shop.client(this)
        val mia = client.signIn(shop.auth, "Mia", "1234")
        val u = unit().also { client.createUnit(mia, it) }
        val r = client.authedPost(mia, "/units/${u.id}/status", StatusChange("RETIRED", "flood"))
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertEquals(listOf(Permission.RETIRE_AND_UNSELL.label), r.body<ProblemBody>().details)
    }
}
