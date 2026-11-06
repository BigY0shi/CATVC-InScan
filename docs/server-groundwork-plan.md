# Server groundwork: plan

- **Status:** approved ("it's a go on server work", owner, 2026-11-01) and built on branch `server-groundwork`, one commit per step. Not deployed: deploying is the owner's job (`DEPLOY-PROXMOX.md`, Updating).
- **Date:** 2026-10-31; built 2026-11-01
- **Asked for:** "Stock list, and then server groundwork" (owner, 2026-10-31).

## Objective

Put in the server foundations that Reports, My history and Stock all depend on, so that their numbers are honest and their privacy rules hold. **This adds no new screens.** Every change keeps today's phones working: new fields are optional, and old requests still succeed.

The sources are the "prerequisites" in `reports-ui-spec.md` §14, `my-history-ui-spec.md` §7 and `stock-list-ui-spec.md` §8, all checked against the current code (`server/`, about 1,350 lines).

## Steps (each one lands with its tests, and each is a separate commit)

| # | Step | What changes | Proven by |
|---|---|---|---|
| 0 | **Pin today's behaviour** | Tests for `GET /units` list and search, the status changes, and the tag print and confirm as they behave now. Nothing in the server changes. | The new tests pass on today's code |
| 1 | **Injected clock** | `inscanServer(db, clock)`; `Auth`, `Units` and `Tags` take a `Clock`; `audit()` uses it. The global `now()` goes. A `TestShop` fixture holds a clock tests can move. | Every existing test still passes. A time-based test can control the clock. |
| 2 | **Migration runner can run Kotlin steps** | `MIGRATIONS` today splits SQL on `;`, which can't rewrite data or rebuild a table. Add Kotlin migration steps, run once, in order, never edited. | A test migrates a database copied from the current schema, with sample rows, and checks every row survives |
| 3 | **One timestamp format** | Every stored time is written as fixed-width UTC (`yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`). A Kotlin migration rewrites existing rows in `units`, `tags`, `audit_log`, `sessions`, `devices` and `employees`. `printedAt` from phones is parsed and normalised on arrival; garbage is refused. | Sorting by text equals sorting by instant, including the first second of a day. A bad `printedAt` is refused. |
| 4 | **`occurredAt`** | It is accepted (optional) on new unit, status change, tag print and tag confirm. Stored as `audit_log.occurred_at` and `units.checked_in_at`; `received_at` is the server time. It is clamped to between `received − max outbox age` and `received + 5 min`, and clamped rows are flagged. Without it, it equals `received_at`. | A check-in made at 11:50 pm Sunday and received at 8 am Monday keeps its Sunday time. A phone time in the future is clamped and flagged. Old phones still work. |
| 5 | **Settings and ShopCalendar** | A `settings` table with defaults: time zone America/Los_Angeles, week starts Monday, old stock after 90 days, events kept 13 months, sold units kept on phones 200, Stock opens on In stock. `ShopCalendar(zone, weekStart, clock)` turns presets into `[startUtc, endUtc)` and day buckets. | Tests for the DST weeks (Sun 1 Nov 2026 is 25 h; Sun 14 Mar 2027 is 23 h), Sunday-start weeks and presets. Sabotage: forcing UTC turns them red. |
| 6 | **Typed status changes** | A `unit_status_changes` table (unit, from, to, occurred_at, received_at, actor, price_cents, `source`: scan, manual, import), written by both `/status` and tag confirm. `units.in_stock_since` is set on the first move to In stock. A one-time backfill comes from the existing `audit_log` (both JSON shapes). | Each path writes exactly one row. The backfill matches the audit log. |
| 7 | **Refusals and sign-in records** | `refused:<CODE>` is written from the StatusPages handler in its own transaction, so it survives the rollback. New records: `refused:PIN_LOCKED`, tries during a lockout, unknown-name tries, and a `login.lockout` row. | A 403 or 409 leaves its audit row. Five wrong PINs leave a lockout row. |
| 8 | **Roles and permissions** | `roles` and `role_permissions` tables, and `employees.role_id`. The migration maps employee to Employee and admin to Admin, and adds Management. Permission checks replace the two `isAdmin` checks. Sign-in returns `employeeId` and the person's permissions. The CLI takes a role name. Guardrails 1 to 7. | Every guardrail is a test: the last Admin can't be removed; Admin can't be edited; the history-visibility guardrail. Every existing employee keeps an equivalent role. |
| 9 | **The list DTO (D-H4)** | `UnitListItem` for `GET /units` (and later `/units/query`), with no `createdBy`, `updatedBy` or `vinFrameVerifiedBy`. A `people` map comes only with See Reports. `UnitDto` stays for single units. | A serialization test: no other employee's ID appears in any bulk response for an Employee. |
| 10 | **Imports are their own action** | The legacy upload path writes `unit.import` and status changes with `source = import`, and needs Admin. Imports are left out of history and work counts. | An imported unit appears in nobody's history |
| 11 | **Indexes** | `audit_log(action, occurred_at)`, `audit_log(entity_id, action)`, `units(status)`, `units(checked_in_at)`, `tags(printed_at)`, `unit_status_changes(unit_id, occurred_at)` | An `EXPLAIN QUERY PLAN` test for the main report queries |

**Not in this batch,** each needing its own decision or a design that is already locked:
- **Price can be empty (D-C2):** a table rebuild with `foreign_keys=OFF` outside the migration transaction. It's the riskiest migration, so it gets its own PR.
- **`/units/query`, the search matcher and the report endpoints:** they build on this foundation; next PR. *Built 2026-11-03 on `server-reports`, except the search matcher: see "Reports and My history endpoints" at the end.*
- **4-digit PINs in `Auth.kt`.**
- **App changes:** outbox `employeeId` and `occurredAt`, the Room migration, the wipe.

## Risks and how they're handled

| Risk | Handling |
|---|---|
| **Migrations rewrite the shop's live data** (steps 3, 6, 8) | Nothing is dropped. Each migration is tested on a copy of the current schema with sample rows. The deploy note says: **stop the server, copy `inscan.db`, then start the new version.** Rolling back means restoring that copy. |
| Old phones send requests without the new fields | Every new field is optional with today's behaviour as the default, and tested |
| The roles migration could lock someone out | Admins stay Admin; guardrail 2 keeps at least one Admin; tested |
| Timestamp rewrite misses a column | The migration lists every time column, and a test scans the schema for any column ending in `_at` that isn't covered |
| CI can't run on this container right now (Maven Central 429) | I'll retry locally. CI on the PR is the gate either way. |

## How it ships

- **One PR,** `server-groundwork`, from `main` (after #14 and #15), with one commit per step and CI green.
- **Deploying is yours:** back up `inscan.db`, then roll out the new server. `docs/DEPLOY-PROXMOX.md` gets the backup line.
- **Nothing reaches the shop until you deploy.**

## As built (2026-11-01)

Everything above landed as planned, with these differences, each for a reason found while building:

| Step | Difference | Why |
|---|---|---|
| 3 | A stored value that isn't a time (a phone once could send anything as `printedAt`) is left as it was and logged, not refused. | A migration can't refuse a row that's already there, and nothing is ever dropped. New ones are refused. |
| 4 | Clamping: a phone time more than 5 minutes ahead becomes the received time; one older than the queue limit becomes the oldest allowed. Both are flagged in `audit_log.occurred_clamped`. The queue limit is the Shop setting `sync.maxQueueAgeDays` (default 30). `tags.confirmed_at` is the confirm's phone time too. | "Clamp" needed a target on each side, and the limit is a policy, so it's a setting. |
| 7 | Refusals from phones that aren't enrolled or signed in aren't recorded. | They can't be named, and an expired session retrying on every sync would flood the log. |
| 8 | Roles and people changes live in a `RoleAdmin` service with every guardrail tested, but have **no HTTP routes yet**. Beyond the spec: nobody resets a PIN of, deactivates, or adds someone into a role that can do more than their own. | The Roles and People screens aren't designed. The extra rule is guardrail 4 stated generally, and it stops Management adding an Admin. |
| 8 | `NOT_ALLOWED` now reads "Your role doesn't allow that. Ask an admin if you need it." and names the missing permission in `details`. | "Only the owner can do that" stopped being true. |
| 9 | `GET /units` keeps `createdBy` and `updatedBy` as **empty strings** for people without See Reports, and has no `vinFrameVerifiedBy`. Managers and admins get the ids. The `people` map moves to `/units/query`. | Released phones parse the list with Moshi, which refuses a list missing a required field. `/units` returns a bare array, so it has nowhere to put a map. |
| 10 | Imports are marked by an optional `source: "import"` on new unit and status change. | The released app's legacy upload uses the normal routes, so the server can't tell imports apart until the app says so (app work). |
| — | `DEPLOY-PROXMOX.md` Updating now says to back up with `sqlite3 .backup` while running, never by copying `inscan.db` alone. | In a test, a plain copy taken after stopping the server held 2 of 13 audit rows; the rest were in `inscan.db-wal`. |

**Verified:**
- 63 server tests (15 before), including sabotage checks on the time format, the calendar's zone, a permission check and an index.
- End to end: the released server (main at `0c3fae5`) wrote a real database through its API; the new server started on a backup of it, migrated it to schema 8, and was used like phones would: an old phone token signed in, an Employee's list carried no ids, Management made a setup code and an Employee couldn't, an offline check-in kept its phone time, and only Admin could take a unit out of Sold.

## Reports and My history endpoints (built 2026-11-03, branch `server-reports`)

Reports first-release slice items 2 and 3 (`reports-ui-spec.md` §17), the Check-ins export (§8) and My history (`my-history-ui-spec.md` §7), on top of the groundwork above. Not deployed.

### Endpoints

All need a signed-in session. Refusals are the usual `ProblemBody` and leave a `refused:<CODE>` audit row.

| Method and path | Params | Permission | Returns |
|---|---|---|---|
| `GET /units/query` | `filter` (a `UnitFilter` as JSON, URL-encoded: the one query-string form), `cursor`, `limit` (default 30, 1 to 200) | Signed in. Person filters (`checkedInBy`, `confirmedBy`, `soldBy`, `tagPrintedBy`) need See Reports, except `checkedInBy` = yourself (See my own history) | `UnitQueryPage {total, items: [UnitListItem], next, asOf, people?}` |
| `GET /reports/summary` | none (period figures use This week) | See Reports | `ReportSummary {asOf, preset, from, to, figures: [{report, kind, tile}], notBuilt}` |
| `GET /reports/{report}` | `preset` (`today`, `this_week`, `last_week`, `this_month`, `last_30_days`) or `from` + `to` (shop dates, inclusive). Neither: This week. Snapshots ignore them. | See Reports | `ReportDto` |
| `POST /reports/{report}/export` | body `ExportRequest {preset?, from?, to?, rows: "each" \| "table", by?}` | See Reports and Export report data | CSV, `Content-Disposition: attachment; filename=inscan-check-ins-<from>-to-<to>.csv` |
| `GET /me/history` | `preset` or `from` + `to`; `cursor` + `asOf` on later pages | See my own history | `HistoryPage` |
| `GET /people/{employeeId}/history` | the same | See Reports; deactivated people allowed | `HistoryPage` with `person` |

**Report keys:** `units_by_status`, `asking_price_total`, `check_ins`, `time_to_in_stock`, `tags_printed`, `tags_not_confirmed`, `sold`, `work_by_person`. Others return 404 `REPORT_NOT_FOUND` (new `Problem`). Only `check_ins` exports; the rest return 404 `EXPORT_NOT_AVAILABLE` (new).

**Range errors** (400 `BAD_REQUEST`): an unknown preset, a preset and dates together, only one date, `to` before `from`, or `to` after today. `details` says which.

### Shapes (all in `core`, package `com.countryatv.core.reports`, `@Serializable`)

- **`UnitFilter`**
  - **Fields, all optional:** `asOf`, `statuses`, `unitTypes`, `makes` (ignoring case), `imported`, `checkedInFrom`/`checkedInTo`, `checkedInBy`, `movedToInStock`, `confirmedBy`, `soldFrom`/`soldTo`, `soldBy`, `hasTag`, `newestTagPrintedFrom`/`newestTagPrintedTo`, `tagPrintedBy`, and `sort` (`newest_checked_in` by default, or `oldest_checked_in`).
  - **Times** are fixed-width UTC; ranges are `[from, to)`.
  - **`asOf`** limits the list to units the server had by then, with their status **as of then** (rebuilt from `unit_status_changes`).
  - **One server function, `UnitQuery.where`,** is used both by `/units/query` and by every breakdown `GROUP BY`. Each dimension has one SQL expression, so a row's count and its list's total agree by construction.
  - **Unknown fields are refused.**
- **`ReportDto`:** `{report, kind: "snapshot" | "period", asOf, preset?, from?, to?, startUtc?, endUtc?, tiles, series?, breakdowns}`.
  - `Tile {key, value: Long?, unit: count | cents | minutes | people, tracked, parts: [Figure], comparison?, drill?}`. `value: null` with `tracked: false` means **Not tracked yet**; `value: null` with `tracked: true` means nothing to measure. Neither is ever 0.
  - `Comparison {basis, from, to, value}`. The basis is one of `same_days_last_week`, `week_before`, `same_day_last_week`, `same_days_last_month`, `previous_30_days` or `previous_days`.
  - `Series {bucket: day | week | month, points: [{from, to, value}]}`.
  - `Breakdown {key, rows: [{key, label, count?, figures, drill?, personId?}]}`. Every breakdown key's rows come in one response. A row with `drill: null` doesn't open a list.
- **`HistoryPage`:** `{asOf, preset?, from, to, person?, totals?, events, next?}`.
  - `HistoryEvent {eventId, kind, occurredAt, unit: {id, shortId, year, make, model, subModel, color}, tagPrintedAt?, changes: [{field, from, to, changedSince}], changedSince, overlapping, fromStatus?, toStatus?, reason?, priceCents?}`.
  - `kind` is one of `check_in`, `reprint`, `printed_tag`, `moved_to_in_stock`, `confirmed_new_tag`, `edit`, `marked_sold`, `brought_back` or `status_change`.
  - Ordered by `occurredAt` DESC, `eventId` DESC, 30 per page. A cursor needs the page-1 `asOf`. The range is resolved at `asOf`, and only rows received by `asOf` count.

### What each report returns

| Report | `tiles[0]` (the list figure) | `tiles[1]` | Breakdowns |
|---|---|---|---|
| `units_by_status` (snapshot) | `in_stock`, parts `incoming`, `salePending` | `asking_price` (cents), part `units` | `status`, `type`, `make` (all drill) |
| `asking_price_total` (snapshot) | `asking_price` (cents), parts `priced`, `units` | `missing_price`: **tracked false** (needs D-C2) | `status`, `type`, `make`, each with `askingPriceCents` (all drill) |
| `check_ins` | `check_ins` + comparison | `in_stock_now`, parts `of`, `stillIncoming` | `person`, `type`, `make`, then `day`/`week`/`month` (all drill); series |
| `time_to_in_stock` | `usual_minutes` (median), parts `moved`, `stillIncoming` | `incoming_over_3_days` | `person` (who confirmed), `type`, over the moved units, with `medianMinutes` (all drill) |
| `tags_printed` | `printed` + comparison | `reprints` | `reason`, `person`, `printer`, time key (no drill: events); series |
| `tags_not_confirmed` (snapshot) | `waiting` | none | `days_waiting` bands, `person` who printed (all drill) |
| `sold` | `sold` + comparison | `asking_price_at_sale` (cents) | `type`, `make`, `person`, time key (all drill); series |
| `work_by_person` | `people` | `check_ins` (drills) | `person`: `figures` `checkIns`, `movedToInStock`, `edits`, plus `personId` |

Work by person, `/me/history` and `/people/{id}/history` all count with one classifier (`HistoryClassifier`).

### Example (real output, trimmed)

`GET /reports/check_ins?preset=this_week`:

```json
{"report":"check_ins","kind":"period","asOf":"2026-11-04T20:00:00.000Z","preset":"this_week","from":"2026-11-02","to":"2026-11-04",
 "startUtc":"2026-11-02T08:00:00.000Z","endUtc":"2026-11-05T08:00:00.000Z",
 "tiles":[{"key":"check_ins","value":3,"unit":"count","tracked":true,"parts":[],
   "comparison":{"basis":"same_days_last_week","from":"2026-10-26","to":"2026-10-28","value":1},
   "drill":{"asOf":"2026-11-04T20:00:00.000Z","imported":false,"checkedInFrom":"2026-11-02T08:00:00.000Z","checkedInTo":"2026-11-05T08:00:00.000Z"}}, "…"],
 "series":{"bucket":"day","points":[{"from":"2026-11-02","to":"2026-11-02","value":1}, "…"]},
 "breakdowns":[{"key":"make","rows":[{"key":"polaris","label":"Polaris","count":2,"drill":{"…":"…","makes":["polaris"]}}]}, "…"]}
```

The full shapes, every endpoint's real JSON, the CSV and the history page are in the handoff report for the app builder.

### Schema and build

- **Migration 9** (appended):
  - `units.imported` (backfilled from `unit.import` rows; set by imports).
  - Indexes `units_created_by` and `audit_occurred` (`IndexTest`).
- **"Export report data"** was already seeded for Management and Admin in migration 7.
- **`core` now applies the kotlinx.serialization plugin** and exports `kotlinx-serialization-core` 1.11.0, the version Ktor already uses.
- **`PATCH /units/{id}`** accepts an optional `occurredAt`, so an offline edit keeps its phone time in history.

### Not tracked, not built, gaps

- **Not tracked** (`tracked: false`): Asking price total's "missing a price" (D-C2).
- **Not built** (listed in the summary's `notBuilt`): Aging, Missing details, Days to sell, and the Shop system group, including Sign-in security.
- **`/units/query`:** no text search, facet counts or other Stock sorts yet.
- **Tags printed rows** don't drill (no `EventFilter` yet).
- **The list's "Last week 11" line** needs a second call. `tiles[0].comparison` is same-days.
- **A snapshot's `asOf`** pins status, not later edits to make, type or price.
- **No recording-start footnote.** Clamped rows aren't counted yet.
- **Report JSON is verbose:** every `drill` writes all of its fields, nulls included.

### Verified

- **Tests:** 81 server tests (63 before) and 21 core tests pass. `scripts/check-honesty.sh` is clean.
- **Sabotage, each turning its test red, then restored:**
  - `/units/query` ignoring `checkedInTo`: drill-down agreement.
  - Status ignoring `asOf`: a list opened after the shop moved on.
  - History ignoring `asOf`: paging.
