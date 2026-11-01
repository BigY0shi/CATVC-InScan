# Reports: Phase 4 review gate

- **Date:** 2026-10-30
- **Reviewed:** `reports-ui-spec.md`, `prototypes/reports.html`, `prototypes/inscan-base.css`, against the server and app code.

**How it was reviewed:**
- Five `agentic-council` seats, each starting fresh with the files and the code to check claims against:
  - **Skeptic:** data integrity, privacy on shared phones, and failure modes.
  - **Advocate:** questions the owner will ask, wording, and accessibility. Checked in Playwright.
  - **Artisan:** the visual system and the dataviz method. Measured in Playwright.
  - **Pathfinder:** Android and iOS platform reality, checked against Apple's documentation and the app code.
  - **Craftsman:** server and API shape, data sources, and testability.
- Measured checks in Chromium on all 14 states: WCAG 2.x contrast, tap targets, accessible names, focus order and console errors.
- Anti-slop: matrix S1 to S15, run by hand.
- The dataviz palette validator, run on the status split.

Every finding is **fixed**, **rejected with a reason**, **deferred**, or **for the owner** (D-RS1 to D-RS3 in the spec).

## Results after fixes (re-measured)

| Check | Result |
|---|---|
| Contrast | 650 text pairs pass AA; the lowest is 5.70:1. The dimmed refetch content is inert and excluded: it's stale content being replaced. |
| Tap targets | All 227 are at least 48 at 402 pt. Chart slots fall to about 44 wide on a 360 dp phone. The full plot height is the hit area, a documented exception (spec §6). |
| Accessible names | None missing. Each chart column is named with its day and value; the readout is hidden from screen readers. |
| Focus order | Follows the screen in every state. The "up" jumps the script flags are the list scrolling as focus moves down it. Sheets make the screen behind them inert. Closing a sheet returns focus to the control that opened it. |
| Console | No errors. |
| Anti-slop | Clean. No blur, glow or gradient washes. Scan's single-hue gradient is the locked Home component. Pills appear only on tappable chips. The empty state is text that points at the controls above it. |
| Palette | Status split: adjacent CVD ΔE 9.1, normal-vision 15.7, contrast ≥ 3:1. The lightness and chroma bands fail by design: the validator scopes them to categorical palettes, and these are status tokens with an icon and a word. |

## What the review changed most

- **The server can't produce honest "by day" numbers yet.**
  - There is no `occurredAt`; the times are the server's own.
  - The clock is a global `Instant.now()`.
  - Timestamps are variable-width text, so comparing them as text misorders them.
  - SQLite has no time zones, and the sample week itself contains the end of daylight saving.

  These are now prerequisites (spec §14) with a first-release order (§17). They are not buried in a needs table.
- **Drill-down counts are equal by construction, not by hope.** The server builds each row's filter (`UnitFilter`), uses the same function for the breakdown and for a paged `/units/query`, and pins `to = asOf`.
- **Shared phones:**
  - Offline copies are dropped from the first release (D-RS3).
  - Exported files and the More navigation stack are wiped whenever the signed-in person changes, by any path.
  - Person filters on any list need "See Reports".
- **The chart now works beyond a week.** It groups by day, week or month by range length, and uses a readout band instead of a floating tooltip.
- **One name per meaning:**
  - "In stock" matches Home (37).
  - "Asking price" replaces "Stock value".
  - "Missing details" replaces "Things to fix", which read as repairs in a shop with a service bay.
  - Sign-in security names the PIN, not the person.

## Fixed

| Seat | Finding | Fix |
|---|---|---|
| Skeptic, Craftsman | No `occurredAt`; global clock; variable-width timestamps; SQLite has no time zones; DST week | §14 prerequisites. Injected clock, `occurredAt` (clamped against phone clock errors), fixed-width timestamps, and a Kotlin `ShopCalendar` that turns ranges into UTC bounds. DST tests for 1 Nov 2026 and 14 Mar 2027. |
| Craftsman, Skeptic, Advocate, Artisan | A report row and its drill-down list can disagree. `/units` caps at 500 with no total, and the mockup showed 4 cards under "6 units". | Server-built `drill` filter; `UnitFilter` in `core`; paged `/units/query`; `to = asOf`; `status = any`. Event-count rows drill to event lists or don't drill. The mockup shows all 6, newest first. |
| Craftsman | Refusal audit rows roll back with the refused transaction | Written from the StatusPages handler in their own transaction (§14). Tested. |
| Craftsman | `unit.status:IN_STOCK` is stored in two JSON shapes | Typed `unit_status_changes` table, written by both paths (§14) |
| Craftsman | `by=` needs one fetch per switch; no `asOf`; no last-period values; summary could disagree with its report | One response with every breakdown key, `asOf`, resolved dates and comparisons. Summary uses each report's own function, tested. `null` means not tracked. |
| Craftsman | Export streaming holds the only database lock; GET with a side effect; CSV ordering | `POST /reports/{id}/export`. Rows built in memory, audit row written with the real count, lock released, then sent. Guard first, then quote; text cells only; one BOM; CRLF. `CsvWriter` unit tests. |
| Craftsman | Indexes, medians, test harness | Indexes listed (§14). Medians computed in Kotlin and tested. `TestShop` fixture in `ReportsTest.kt`. |
| Pathfinder | Saved copies would go to Google backup and device transfer; the phone can't key or wipe by person | No saved copies in the first release (D-RS3). Rules for adding them later are in §9: a separate excluded database, keyed by `employeeId`. |
| Skeptic, Pathfinder | The wipe ran on button presses only; idle lock, a 401, expiry and a deactivated manager were missed | One wipe hung on `employeeId` changing, by any path. `employeeId` added to the sign-in response. The More `NavHost` is keyed by person. |
| Skeptic | Exported CSV on a shared phone | Files live in app cache, are deleted at 24 h and on the wipe, never go to Downloads, and the sheet warns. |
| Pathfinder | `getUriForFile` would throw (no `cache-path` in `file_paths.xml`); `body.string()`; 20 s timeout; share chooser blocked from the background | §8 Android steps: FileProvider path, streamed download, timeout for this call only, `viewModelScope`, chooser only while resumed. |
| Pathfinder | iPad share sheet crashes without an anchor | Anchored to the Export chip; file in `temporaryDirectory`, deleted on completion. |
| Pathfinder | iOS has no native date-range picker | Material 3 `DateRangePicker` from shared code, in a full-screen dialog, with `SelectableDates` and UTC-millis conversion. The locale first-weekday question is marked unverified. |
| Advocate, Pathfinder, Skeptic | The chart breaks for anything longer than a week | Bucket by day (up to 14 days), week (up to 16 weeks) or month; no chart for a single day. The heading and time key follow the bucket. |
| Pathfinder | Fast stepper taps can show the wrong week | Cancellable calls, `mapLatest`, stale replies dropped (§6). |
| Pathfinder | Process death; predictive back double-fetching | Route holds report, preset and by. Fetch and events start from the ViewModel and navigation, never from composition. |
| Pathfinder | Tablet breakpoint misses iPad portrait | Two columns at 600 dp or more of content width (`currentWindowAdaptiveInfo()`) |
| Pathfinder, Advocate | Work by person table doesn't fit a 360 dp phone; stacking keyed to font scale | Rebuilt as a list with fixed columns; table or stacked lines chosen by measured width; screenshot tests at 360 dp and AX5. |
| Pathfinder | Chart columns in one `Canvas` expose nothing to TalkBack or VoiceOver | Row of `selectable` slot `Box`es with merged semantics (§6) |
| Advocate | "How much stock" had three answers (45, 39, 37) | First tile is "37 in stock", matching Home. "Asking price" says it covers 35 of 39 priced units. |
| Advocate, Artisan, Skeptic | "Moved / Now / Already in stock" for one tile; the empty state said "None still Incoming" | Definitions written in §10. Tile reads "8 in stock, of the 14". "Moved to In stock" is used only for the Work by person column. The second tile is hidden when the first is 0. |
| Advocate | "Things to fix" reads as repairs; unclear figures | "Missing details". "Filled everything for 5 of 14". "Usually within 2 hours". "Changes not sent for a day". Full tile words. |
| Advocate | The answer was the smallest, dimmest text; 21 rows mixed business with system health | Figure in `type-body`/`text`. The list is the business view (12 rows); system and security reports sit one level down under Shop system. |
| Advocate | "Sitting too long" was split across three reports; the 90-day line is fixed | Aging gains a Status key; "Incoming over 3 days" added to Time to In stock; "Old stock after" is a Shop setting; Aging counts from "in stock since". |
| Advocate | Selected breakdown segment shown by tint alone (1.09:1) | Check icon, weight 600, `accent-ink` border. Wraps 2 × 2 at 2×. |
| Advocate | Refetch said "Last week" over this week's figures, went silent, and had no failure path | Stale content inert at 50%. One persistent live region: "Updating…", then "Showing last week". On failure, the range reverts with Try again. |
| Advocate | Large text: fixed-height range button; "M T W T F S S" can't tell days apart | `min-height` that grows; steppers wrap above 1.3×; date numbers on the axis at large text. |
| Advocate | Focus went to the title after a sheet; focus rings clipped; range presets as radios that apply on arrow keys | Focus returns to the opener. `outline-offset: -3px` inside rows. Presets are buttons marked `aria-current`. |
| Advocate, Pathfinder | Drill-down titled "Stock" while More stays selected | Own route on the More stack, titled "Dale's check-ins", meta line with range and count. |
| Advocate | Event reports show 0 before recording began | "Not tracked yet" on the list; "Recording started …" footnote; blank chart slots before then. |
| Advocate | Chart slots read "collapsed"; tooltip repeated the name; generic stepper names | No expanded state; readout hidden from screen readers; "Previous week" and "Next week". |
| Advocate | Sheet heading "Show"; "Pacific" on the list; Monday-morning zeros | "Date range"; the time-zone note kept only in the sheet; early-period figures add last period's total. |
| Artisan | Status split and row bars repeated the data on two scales, and the row bars rendered grey | The split is the list's first row (not tappable); status rows have no bars. Type and Make keep one-hue bars. |
| Artisan | Tooltip covered columns and ran over the heading; two values showed at once | 44 pt readout band at the top of the chart, kept inside the plot. Today's cap hides while a column is selected. |
| Artisan | `text-2` bars were the loudest thing on screen; the selected column barely changed | `--data: #8C8F95` (5.1:1); selected column `text` (2.9:1 against the others) |
| Artisan | The report name vanished on scroll; spec and mockup disagreed on the title | Large title in the body; a centred bar title appears once it scrolls under (spec §4.1) |
| Artisan | Tiles didn't follow Home's count cards | Value and word on one baseline ("**14** check-ins"), `space-card` padding, flat `surface` |
| Artisan | Spacing off Home and check-in | Body 24, sections 12, switch 8, one hanging-meta rule (−16), rows 12/12/12/16 |
| Artisan | Breakdown bars on different tracks (261 against 270) | Fixed `1fr 56px 24px` columns |
| Artisan | Export had a drop shadow Find doesn't | Built exactly like Find |
| Artisan | Work by person cells didn't look tappable | Whole row is the target, with a chevron, opening that person's work |
| Skeptic | Sign-in security blames the person whose PIN was tried; lockouts leave no record | "Failed PINs on Dale's name", broken down by phone first. Records for `refused:PIN_LOCKED`, tries during lockout, unknown names and lockouts (§14). |
| Skeptic | "Waiting more than a day: None" is false comfort | A phone check-in (queue length, oldest waiting). Until then the figure is "Not tracked yet". Phones shows "Not heard from since Tue". |
| Skeptic | "Last week: 11" compared part of a week with a whole one; medians hid open units | "Same days last week". Every "usually" figure says how many it covers and how many are still open. |
| Skeptic | Largest-remainder rounding gave equal counts unequal shares | Percentages dropped from rows; the bar shows the proportion. Shares appear only in the "This table" CSV. |
| Skeptic | Sold money figure isn't a sale price; Sale pending is refused by the server; "not priced" impossible under the price CHECK | "Asking price at sale" until M3. Sale pending is noted as filling in with M3. Price nullable (D-C2) listed as a prerequisite, with the `foreign_keys` migration caveat. |
| Skeptic | Aging would read 0 for imported stock | "In stock since", settable by the audited legacy import |
| Skeptic | Person filters on `/units` leak work by person | Person filters need "See Reports" (§2, §12) |
| Skeptic | Role changes and exports were promised but missing; "Admins only" named a role | Role changes and Exports reports added under Shop system. The lock icon refers to the report's permission, not a role. |
| Skeptic | Formula guard missed phone names and VIN-lookup values | The guard covers every text cell (§8) |

## Rejected

| Seat | Finding | Why not |
|---|---|---|
| Skeptic | Have the server email the export to whoever asked | The shop system has no mail setup. Adding one means storing a mail credential and handling bounces. Cache-only files, the wipe and the warning cover the shared-phone risk. Revisit if exports become routine. |
| Pathfinder | Add a "Save to phone" option (Android `CreateDocument`) | On a shared phone a file in Downloads can be read by the next person. Sharing to email or Drive is the intended route. iOS's Save to Files can't be hidden, so the sheet warns instead. |
| Artisan | Show the Work by person cell figures in `accent-ink` as text buttons | Twelve emerald numbers would compete with the one emerald fill. A whole-row target with a chevron matches every other list. |
| Advocate | Keep disabled controls tappable and explain on tap | Online-only removes most disabled states. The one left (Export with nothing to export) has its reason written in the empty notice beside it. |

## Deferred

| Item | When |
|---|---|
| Offline saved copies (rules written in §9) | Only if offline viewing turns out to matter (D-RS3) |
| ETag / caching for reports | After the first slice, if load shows a need |
| Read-only second database connection (WAL) | If exports or reports contend with check-ins in practice. The first release releases the lock before sending. |
| The same tint-only selected state in check-in's VIN/Serial switch | Apply the check-and-weight fix when check-in is built |
| Whether the Material `DateRangePicker` can start weeks on the shop's day | Check against the pinned Compose Multiplatform version when building |

## For the owner

D-RS1 to D-RS3 in `reports-ui-spec.md` §15.
