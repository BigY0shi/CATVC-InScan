# InScan: reporting foundation and the Reports area

- **Status:** owner requirement (2026-10-30): *"Design every flow with analytics/reporting in mind. The owner/managers should have a 'reports' type area that gives them breakdowns of everything that we keep track of in the app."* The rules in section 1 apply to every flow from now on. The Reports screens themselves aren't designed yet; section 5 is the outline they'll be built from. Nothing here is locked.
- **Date:** 2026-10-30
- **Applies to:** `landing-nav-ui-prompt.md` (Home), `scan-pin-ui-spec.md` (Scan, sign-in), `checkin-ui-spec.md` (check-in and print), and every spec after them.

---

## 1. Rules every flow follows

1. **Every spec has a "Reporting" section.** It lists:
   - the events the flow records (section 4 holds the full tables)
   - the reports those events feed
   - any data the flow needs to keep for them

   A spec without one doesn't pass the Phase 4 gate (`design-skill-pipeline.md`).
2. **Who and when, on every change.**
   - A queued change carries the `employeeId` of the person who made it. Scan spec §5 already requires this; it is what makes "by person" breakdowns true.
   - It also carries `occurredAt`, the phone's time when the person did it. The server keeps `occurredAt` and its own `receivedAt`.
   - Reports count by `occurredAt`, so a check-in done offline at 9 am and sent at noon counts at 9 am.
3. **Writes go through the audit log; everything else goes in events.**
   - The server already writes `audit_log` (who, which phone, when, before and after) for every create, update, status change, tag print, tag confirm, sign-in and failed PIN. That stays the record of what changed.
   - Things that change no data (scans, lookups, a failed print, an abandoned check-in) go into a new `app_events` table (section 3).
4. **Refusals are recorded.** When the server refuses a change (`VIN_ALREADY_LOGGED`, `VIN_NOT_CONFIRMED`, `STATUS_CHANGE_NOT_ALLOWED`…), it writes `audit_log` with action `refused:<CODE>`. Today refusals leave no trace on the server, so "what goes wrong" reports would be blind.
5. **Status only moves through audited paths.** Check-in D-C5 keeps "time in Incoming" and "days to sell" honest.
6. **Every list filters the way reports break down.** Stock and the other lists accept the same filters reports use: status, type, make, person, date range and flags (no price, no VIN, no tag). Tapping a row in a report opens the matching list ("drill down"). A report and the list it opens always agree.
7. **One clock.** Times are stored in UTC and shown in the shop's time zone. Days and weeks follow the shop's calendar (the week starts Monday 00:00 shop time, as in prompt §11). Tests inject the clock.
8. **The data stays in the shop.**
   - No third-party analytics SDKs.
   - Events go to the shop system through the same outbox as everything else, so they work offline.
   - No free text, customer data or location in events.

---

## 2. What the data already supports (checked 2026-10-30)

| Already recorded | Where | Reports it can feed |
|---|---|---|
| Unit create, update, overlapping update, every status change | `audit_log` (`unit.create`, `unit.update`, `unit.update.overlapping`, `unit.status:*`) | Check-ins by day, person, type and make; sold per period; days to sell; edits after check-in; overlapping edits |
| Tag print (via, reason, who) and tag confirm | `tags` table plus `audit_log` | Tags printed, reprints with reasons, time from print to confirm, tags never confirmed |
| Sign-in, failed PIN | `audit_log` (`login`, `login.failed`) | Sign-ins by person and phone; failed PINs and lockouts |
| Phones connected | `devices`, `audit_log` (`device.enroll`) | Phones, when last seen (needs `last_seen`; section 6) |
| Current units with price, status and type | `units` | Inventory by status, type and make; stock value; aging; flags |

**Not recorded today:**
- when a change actually happened (only when the server got it)
- scans and their outcomes
- VIN lookups
- print failures and cancelled shares
- abandoned check-ins
- camera and Bluetooth problems
- refusals
- idle locks and offline unlocks

---

## 3. The `app_events` table

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT, primary key | A ULID made on the phone, so a resend can't double-count |
| `occurred_at` | TEXT | Phone time, UTC |
| `received_at` | TEXT | Server time |
| `employee_id` | TEXT, nullable | Null before sign-in (camera or gate events) |
| `device_id` | TEXT | |
| `event` | TEXT | `noun.verb`, from the tables in section 4 only |
| `entity`, `entity_id` | TEXT, nullable | For example `unit`, `6MDQ1KXA…` |
| `props_json` | TEXT | A small set of named, typed fields per event. No free text. |

- Sent in batches through the outbox, at low priority behind real changes.
- Events the phone records but can't send yet count toward nothing until they arrive.
- Events are kept for **13 months** by default (D-R3, yes). The period is a Shop setting.
- `audit_log` is never pruned.

---

## 4. Events per flow

### 4a. Scan (`scan-pin-ui-spec.md` §2)

| Event | Props | Feeds |
|---|---|---|
| `scan.opened` | `from` (home, nav, checkin, incoming) | Scan use by entry point |
| `scan.read` | `via` (camera, typed, wedge), `format` (qr, code39, code128, datamatrix), `ms_to_read`, `outcome` (found, incoming, moved, not_found, offline, from_tag, clash, maker_barcode, stale_tag, wrong_tag) | Scans per day and person, outcome mix, typed-vs-camera share, read speed |
| `scan.hint_shown` | (none) | Camera trouble rate |
| `scan.light` | `on` | How often the light is needed |
| `scan.camera_problem` | `kind` (denied, restricted, failed) | Phones with camera problems |
| `scan.cancelled` | `had_read`, `ms_open` | Scans abandoned |
| (audit) `tag.confirm`, `unit.status:IN_STOCK` | | Confirms per day and person; time in Incoming |

### 4b. Sign-in (`scan-pin-ui-spec.md` §3)

| Event | Props | Feeds |
|---|---|---|
| (audit) `login`, `login.failed` | | Sign-ins and failed PINs by person and phone |
| `signin.locked` (server writes it as audit `refused:PIN_LOCKED`) | `until` | Lockouts |
| `signin.idle_lock` | `idle_min` | How often phones idle-lock (tunes the 30-minute default) |
| `signin.offline_unlock` | `ok` | Offline unlocks and failures |
| `signin.switch_user` | `pending_changes` | Shared-phone hand-offs |
| `gate.unreachable` | `step` (connect, signin) | Shop system reachability by phone |

### 4c. Check in and print (`checkin-ui-spec.md`)

| Event | Props | Feeds |
|---|---|---|
| `checkin.started` | `from` (home, scan, typeit) | Check-ins by entry point |
| `checkin.step_done` | `step`, `ms` | Where time goes in check-in |
| `checkin.abandoned` | `at_step` | Drop-off by step |
| `vin.checked` | `kind` (vin, serial, later, none), `result` (pass, fail, frame_ticked, short, long) | VIN quality, serial share, "VIN later" backlog |
| `vin.lookup` | `result` (full, partial, none, offline), `fields` (make, model, year, type) | How useful the lookup is for powersports |
| `checkin.duplicate_blocked` | `match_status` | Duplicates caught |
| (audit) `unit.create` | | Check-ins by day, person, type and make |
| `print.attempt` | `via` (direct, smooth_print, share), `copies` | Print volume by route |
| `print.outcome` | `via`, `outcome` (printed, failed, unknown, cancelled), `reason` (code, not text) | Print failure rate and reasons, by printer |
| `print.asked_did_it_print` | `answer` (yes, send_again) | Share-path reliability |
| `print.later` | (none) | Units left without a tag |
| (audit) `tag.print` | | Tags printed, reprints and reasons |

### 4d. Home (`landing-nav-ui-prompt.md`)

| Event | Props | Feeds |
|---|---|---|
| `home.count_opened` | `status` | Which counts people use |
| `home.job` | `job` (checkin, reprint) | Quick-job use |
| `search.used` | `matched` (id, vin, text, none) | Find hit rate |

Home's "Sold this week" card (TBA) comes from `unit.status:SOLD` in `audit_log`, counted by `occurredAt` in the shop's week. The Reports work closes that TBA.

---

## 5. The Reports area (outline; screens to design next)

- **Where:** More → **Reports** (D-R5, yes). The 5-slot nav is locked, so Reports doesn't get a tab.
- **Who sees it:** people with the "See Reports" permission. By default that is Management and Admin (`roles-permissions-spec.md` §4).
- **Online only**, like Shop settings. Offline it shows the last loaded copy with "As of 2:14 pm".
- **Every report has:**
  - the same date range control: Today, This week, This month, Last 30 days, Custom
  - headline numbers
  - one breakdown table (by day, person, type, make or phone)
  - a bar chart only where a trend over time is the point. Charts are allowed here, not on Home (prompt §9 bans them on Home).
  - rows that drill down into the matching list
  - **Export CSV**
- **Reports, first set:**

| Group | Reports |
|---|---|
| Inventory | Units by status; stock value (sum of prices, In stock and Sale pending); aging (days in stock, in buckets); flags: no price, no VIN, no tag, tag out of date |
| Intake | Check-ins by day, person, type and make; time from check-in to In stock; VIN lookup usefulness; check-ins abandoned, by step; duplicates caught |
| Tags | Tags printed; reprints and reasons; print failures by reason and printer; tags printed but never confirmed |
| Sales | Sold per period, by type and make; days to sell; Sale pending aging. Sales are Reserved (M3), so these fill in when selling ships. |
| Scanning | Scans by day and person; outcome mix; typed vs camera; phones with camera trouble |
| People and phones | Work done by person (check-ins, confirms, edits); sign-ins; failed PINs and lockouts (D-R2); phones, last seen, changes waiting |
| Data quality | Refused changes by reason; overlapping edits; changes waiting more than a day |
| Tickets (TBA) | When work tickets ship |

- **Design rules:**
  - The locked palette and depth B apply.
  - Report cards in the Reports list are raised, because they're tappable.
  - Report bodies are flat information.
  - Charts follow the dataviz rules: the status colours for series that are statuses, and `text-2` for single-series bars. Emerald stays for actions.

---

## 6. Server and app needs

| Need | Why |
|---|---|
| `occurredAt` and `employeeId` on every outbox entry and every API write; the server stores both times | Reports count when work happened and who did it |
| `app_events` table, a batch endpoint, and an outbox kind `event` | Section 3 |
| Audit refusals as `refused:<CODE>` | Data-quality reports |
| `devices.last_seen` updated on each sync | Phones report |
| A read-only reports API, gated by the "See Reports" and "See sign-in security" permissions, with server-side SQL and CSV export | Section 5. Phones hold only the 500 newest units, so reports can't be computed on the phone. |
| Shared filter model for lists and reports | Rule 6 |
| Roles and permissions in place of `isAdmin` | `roles-permissions-spec.md` §6 |

---

## 7. Decisions for the owner

| # | Decision | Recommendation | Why |
|---|---|---|---|
**Owner answers (2026-10-30), decided, not locked:**
- **D-R3, D-R4 and D-R5: yes.**
- **D-R1:** replaced by the owner's direction: three roles at launch (Management, Employees, Admin/Tech Support), with every permission configurable. D-R1 and D-R2 now live in `roles-permissions-spec.md` §4. Which role the shop owner holds is D-P1 in that spec.

| # | Decision | Answer |
|---|---|---|
| D-R1 | Who sees Reports | The "See Reports" permission. Management and Admin by default. |
| D-R2 | Per-person reports | **Work by person:** with "See Reports". **Sign-in security** (failed PINs, lockouts, offline unlocks): a separate permission, Admin by default and grantable to Management. *Recommended; waiting for D-P1.* |
| D-R3 | How long `app_events` are kept | **Yes: 13 months by default**, as a Shop setting. `audit_log` is kept forever. |
| D-R4 | CSV export on every report | **Yes.** |
| D-R5 | Where Reports lives | **Yes: More → Reports.** |
