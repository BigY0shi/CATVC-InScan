# InScan: roles and permissions (draft)

- **Status:** owner direction (2026-10-30): *"Everything in the whole app should be configurable by admin/owners really....but at launch there needs to be 3 roles. Management, Employees, Admin/Tech Support (me)."* The owner reviewed it on 2026-10-30 ("Everything looks good"), so the model, the guardrails and the default permissions in §3 are **decided, not locked**. D-P1 is answered (§5). The screens for editing roles aren't designed yet.
- **Checked against:** repo `BigY0shi/CATVC-InScan`, branch `ui-checkin`, read-only.

---

## 1. The model

- **The app and server check permissions, never role names.** `isAdmin` goes away.
- **A role is a named set of permissions, stored in the database.** Adding a fourth role later is a data change, not a code change.
- **Each person has exactly one role.**
- **Launch seeds three roles:** Employee, Management and Admin. Admin is tech support.
- **Every shop policy is a Shop setting, not a constant in code.** Each setting has a default and a permission that decides who changes it. Examples:
  - sign-in mode and idle lock
  - label size and copies
  - the unit type list
  - how long events are kept

  Every spec lists the settings it adds (`design-skill-pipeline.md`, Phase 4 gate).
- **The server enforces; the app only hides.**
  - The app hides what a person can't do.
  - The server refuses it anyway if asked, and audits the refusal as `refused:NOT_ALLOWED` (reporting rule 4).

## 2. Guardrails

1. **Admin is fixed.** Admin holds every permission. Its permissions can't be edited, and the role can't be deleted.
2. **There is always at least one active Admin.** The last Admin can't be demoted, deactivated or removed.
3. **Only Admin changes roles.**
   - "Edit roles and permissions" belongs to Admin and can't be granted to anyone else.
   - Nobody can change their own role.
4. **Only an Admin resets an Admin's PIN.**
5. **Every role change is audited.** That covers a role's permissions and a person's role (section 6), and the changes show in Reports under People and phones.
6. **A removed permission takes effect on the next request.** The server checks permissions on every call, so a session that is already open loses the permission at once. The app refreshes the list at its next sync.

## 3. Default permissions at launch

✓ = has it by default. Admin can move any row except the last one to a different set of roles.

| Permission | Employee | Management | Admin | Today in code |
|---|---|---|---|---|
| Scan, check in, print and reprint tags, confirm a tag | ✓ | ✓ | ✓ | Any signed-in person |
| Edit unit details and price | ✓ | ✓ | ✓ | Any signed-in person |
| Mark Sold (interim, until M3 sales) | ✓ | ✓ | ✓ | Any signed-in person |
| See Reports, including work by person, and export CSV | | ✓ | ✓ | Not built |
| Add or deactivate employees; reset an Employee's or Manager's PIN | | ✓ | ✓ | Server CLI only (`add-employee`) |
| Connect a phone (make a setup code) | | ✓ | ✓ | Admin (`App.kt`, `/admin/setup-codes`) |
| Retire a unit; change a Sold unit, including "Bring it back in" (D-C9) † | | | ✓ | Admin (`Units.kt`, retire and from-Sold check) |
| Edit unit types † | | | ✓ | Admin (unit-types spec, locked decision 4) |
| Shop settings: sign-in mode, idle lock, label defaults † | | | ✓ | Not built |
| Legacy import (the audited path from D-C5) | | | ✓ | Not built |
| See sign-in security: failed PINs, lockouts, offline unlocks (D-R2) | | | ✓ | Not built |
| Edit roles and permissions | | | ✓ (fixed) | Not built |

**†** Earlier specs say "owner only" for these rows. The shop owner holds Admin (D-P1), so those decisions stay true as written.

**Why Management connects phones and resets PINs:** a dead phone or a forgotten PIN shouldn't wait for tech support. Each one is audited, and an Employee PIN alone can't reach Admin-only work.

## 4. Reports (reporting-spec D-R1 and D-R2)

- **D-R1, who sees Reports:** the "See Reports" permission. By default that is Management and Admin.
- **D-R2, per-person numbers.** These are two separate permissions:
  - **Work by person:** check-ins, confirms, edits and sales by name. Part of "See Reports", so Management and Admin have it.
  - **Sign-in security:** failed PINs, lockouts and offline unlocks by name. Admin only by default; Admin can grant it to Management.

## 5. Decision for the owner

**Owner answer (2026-10-30):** *"Shop owner will get admin, they need complete control. As well as my Nephew, and me."* At launch the Admins are the shop owner, Yoshi and Yoshi's nephew. Management is for the managers. Locked unit-types decision 4 holds as written.

| # | Decision | Answer | Why |
|---|---|---|---|
| D-P1 | Which role the shop owner holds | **Answered: Admin.** Three Admins at launch: the owner, Yoshi and Yoshi's nephew. | (1) Every "owner only" decision so far stays true as written, including **unit-types locked decision 4** ("only the owner changes the list"), D-C9 and Shop settings. (2) The owner never waits on tech support. (3) Managers get Reports and day-to-day staff work without the shop's policy settings. The cost: the owner can change anything tech support can. The audit log shows who did what, and guardrail 2 always keeps an Admin. |

**The alternative the owner didn't pick: the owner in Management.** Every † row would then have had to do one of two things, and both would have broken **locked** unit-types decision 4:
- **Move to Management.** The managers then also edit unit types and Shop settings, and bring sold units back.
- **Stay Admin.** The owner then has to ask tech support every time.

A fourth "Owner" role would fix it, but you asked for three roles at launch.

## 6. Server and app needs

| Need | Where |
|---|---|
| `roles` (id, name, built_in) and `role_permissions` (role_id, permission) tables; `employees.role` becomes `role_id` | `Database.kt` (today `role TEXT NOT NULL CHECK (role IN ('employee','admin'))`) |
| A migration that maps `employee` to Employee and `admin` to Admin, keeping every row | `Database.kt` |
| Replace the two `isAdmin` checks with permission checks | `App.kt` (setup codes), `Units.kt` (retire and from-Sold) |
| Sign-in returns the person's permissions; the app hides by permission, not role | `App.kt` `LoginResponse`, `ShopClient.kt` (stores `KEY_ROLE` today) |
| The `add-employee NAME [admin]` CLI takes a role name | `Main.kt` |
| Screens for More → Shop settings → **Roles** (a permission grid per role) and **People** (each person's role, add, deactivate, reset PIN) | Not designed yet |

## 7. Reporting

| Event | Props | Feeds |
|---|---|---|
| (audit) `role.create`, `role.update` | Permission set before and after | Who changed what anyone can do |
| (audit) `employee.role` | Old and new role | Role changes by person |
| (audit) `refused:NOT_ALLOWED` | The permission that was missing | Refused actions by permission and person (Data quality) |

## 8. Test contract

- **Each guardrail in section 2 is a server test.** For example: demoting the last Admin is refused, and editing Admin's permissions is refused.
- **Taking a permission away refuses that person's very next request,** with no new sign-in needed.
- **Sabotage check:** replace one permission check with `true` and confirm a test goes red.
- **The migration keeps every employee:** each one keeps a role equal to their old one.
