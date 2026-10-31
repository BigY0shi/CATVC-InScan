# InScan: Reports screens (spec)

- **Status:** Phase 1 draft. Built from `reporting-spec.md` §5 (the outline) and `roles-permissions-spec.md` (who sees what). Nothing here is locked.
- **Date:** 2026-10-30
- **Mockup:** `prototypes/reports.html`. Each state can be shot alone with `?shot&state=<key>`.
- **Frame:** iPhone 17, 402 × 874 pt. 1 CSS px = 1 pt or dp.
- **Locked and applied:**
  - Palette v4. Emerald is only for actions; the raised Scan button in the nav is the one emerald fill.
  - Depth B.
  - The 5-slot nav. Reports is reached from More, so the nav stays visible with More selected.

---

## 1. References (opened and looked at this session)

| App | Screen | Taken | Left |
|---|---|---|---|
| [Jobber](https://mobbin.com/screens/aaaa68ff-32f2-46f0-ba47-fe3ff66a4108) | Business health | Grouped list of figures, each section saying its period ("Past 30 days") | Coloured change chips that judge every number as good or bad |
| [Starling](https://mobbin.com/screens/9ea374e1-6270-494e-8d5f-c0232eca40de) | Date range sheet | Presets as rows, each spelling out its real dates; "My week starts: Sunday" said out loud | Week, month and year all in one long sheet |
| [YNAB](https://mobbin.com/screens/6cd1ca53-4492-4e45-881b-46eee7dd35a1) | Spending breakdown | Each row: name, amount and a thin proportion bar; row opens the detail | Emoji, and a different colour per row |
| [Commons](https://mobbin.com/screens/f2edd29b-b33d-4759-8a2e-89f92cc5daa5) | Emissions by category | Period control with previous/next arrows; "View all" drill-down | Cartoon icons |
| [GoPay](https://mobbin.com/screens/f150e527-b352-4442-93a9-3b0556696288) | Spending | Period stepper beside the total; rows with an arrow to drill down | Ruler-dial period picker |
| [Revolut Business](https://mobbin.com/screens/4cbc7fb1-eaaa-4067-9e4b-d42b9cb4a7bf) | Merchant revenue, dark | Filter chips in one row above every card | Area charts, and a chart in every card |
| [Cash App](https://mobbin.com/screens/dc7db548-1b0b-4980-b7f0-779609a93904) | Earnings | Chart, then "Key stats" as label and value rows, then "Top customers" rows | A single neon bar |
| [Insight Timer](https://mobbin.com/screens/53d30bee-4247-413a-ab28-013ea9f3b466) | My Stats | "Updated: Just now" freshness line at the foot | Tabs for the period |
| [Buddy](https://mobbin.com/screens/de540aa0-34bc-4776-8ccf-1ab03f849786) | Export data | Shows the row count ("Found 13 transactions") before the export button | Giant file icon |
| [Zing](https://mobbin.com/screens/7d64d799-b93c-4e5d-8b34-61a08c4a293a) | Export statement | A choice of what kind of file, each choice with one line saying what it's for | PDF option |
| [Deel](https://mobbin.com/screens/2e121437-b071-4232-a81b-559089e2f7dd) | Analytics, Reports | (Seen, rejected) | A "View" button card per report: 30 cards nobody scans |

---

## 2. Where Reports lives and who sees it

- **More → Reports** (D-R5). It isn't a tab, because the nav is locked.
- **The row shows only to people with "See Reports".** By default that is Management and Admin.
- **Sign-in security** is one report inside Reports. It shows only with "See sign-in security", which is Admin by default.
- **Export** needs "Export report data", a new permission (D-RS2). By default that is Management and Admin.
- **Opened without the permission** (an old link, or the permission was removed while the screen was open): the screen says "Reports are for managers and admins. Ask an Admin if you need them." It offers a Back button and shows no figures. The server refuses the data anyway (`refused:NOT_ALLOWED`).

---

## 3. Reports list (`more/reports`)

From top to bottom:
1. **Top bar** (64, `surface`, 1 dp divider). It has three parts:
   - **Back:** a text button with a back icon and the word "More".
   - **Title:** "Reports" (`type-app-title`).
   - **Right side:** empty.
2. **Freshness line** (`type-meta`, `text-2`): "Updated 9:38 am. Times are shop time (Pacific)."
3. **Groups**, in this order:
   - Inventory
   - Intake
   - Tags
   - Sales
   - Scanning
   - People and phones
   - Data quality

   Each group has a section heading (`type-section`) and one flat bordered list (`bg` fill, `border-strong`, 12 radius, 1 dp dividers).
4. **Rows:** 64 tall minimum, padding 12/16.
   - Left: the report name (`type-card-title`), with **one live figure** under it (`type-meta`, `text-2`), for example "14 this week", "45 on hand" or "3 over 90 days".
   - Right: a chevron.
   - The whole row opens the report.

**Why rows are flat:** they are information that opens more information, like Home's record cards (locked Home, flat and tappable). Twenty-two raised cards would read as twenty-two buttons. Raised stays for controls: the date range, the steppers, Export and the breakdown switch.

**Figures on the list** come from one server call (`GET /reports/summary`).
- Each figure uses the report's default range: "this week" for period reports, "now" for snapshots.
- The list never shows charts. A list that tries to be a dashboard shows everything at once and nothing well.

**Rows in the first set** (`reporting-spec.md` §5; the figures match the mockup's sample shop):

| Group | Report | Figure on the list | Kind |
|---|---|---|---|
| Inventory | Units by status | 45 on hand | Snapshot |
| Inventory | Stock value | $318,400 | Snapshot |
| Inventory | Aging | 3 over 90 days | Snapshot |
| Inventory | Things to fix | 8 units | Snapshot |
| Intake | Check-ins | 14 this week | Period |
| Intake | Check-in to In stock | Typically 2 h | Period |
| Intake | VIN lookups | 9 of 14 partly filled | Period |
| Intake | Check-ins not finished | 2 this week | Period |
| Tags | Tags printed | 17 this week | Period |
| Tags | Print problems | 1 this week | Period |
| Tags | Tags not confirmed | 5 waiting | Snapshot |
| Sales | Sold | 9 this month | Period |
| Sales | Days to sell | Typically 38 days | Period |
| Scanning | Scans | 86 this week | Period |
| Scanning | Camera trouble | 1 phone | Period |
| People and phones | Work by person | 4 people this week | Period |
| People and phones | Phones | 3 phones, 1 waiting to send | Snapshot |
| People and phones | Sign-in security (Admin) | 2 lockouts this week | Period |
| Data quality | Refused changes | 3 this week | Period |
| Data quality | Edited at the same time | 1 this week | Period |
| Data quality | Waiting more than a day | None | Snapshot |

- **"Typically"** means the median. The screens say "typically" and the CSV header says "median".
- **Sales today:** the "Sold" report counts the interim mark-sold (`Units.kt`). Sale pending and its aging fill in when M3 selling ships.
- **Tickets** gets a group when tickets ship, not before. An empty group is clutter.

---

## 4. A report screen: one template

Every report uses the same frame, so learning one teaches them all.

1. **Top bar** (64):
   - Back: "Reports".
   - Title: the report name.
   - Right: **Export**, a raised chip with the download icon and the word "Export", built like Home's Find chip. It is hidden without "Export report data". Online only: offline it stays but is disabled, with "Export needs the shop system" in its description.
2. **Filter row**, one row above everything it scopes (dataviz rule). This is for **period reports** only:
   - `[‹]` previous period (48 wide, 56 tall to match the range button, raised)
   - **range button** (raised, fills the row, 56 tall): two lines, "This week" (`type-label`) over "Mon 26 Oct to today" (`type-meta`, `text-2`), with an expand icon
   - `[›]` next period (same size)

   The stepper moves by the preset's own unit (day, week or month). Next is disabled when the range reaches today.
   - **Snapshot reports** have no range. In its place is one line: "Right now. Updated 9:38 am."
   - **Why snapshots get no range:** "Units by status on 3 October" would mean replaying the audit log. That's possible later; it isn't needed to run the shop now.
3. **Freshness line**, under the filter row: "Updated 9:38 am" (`type-meta`, `text-2`).
4. **Headline figures:** one or two flat stat tiles side by side.
   - Each tile: a label (`type-label`, `text-2`), the value (`type-big-number`, proportional figures) and one line of context (`type-meta`), for example "Last week: 11".
   - **No coloured up or down arrows.** More check-ins is good; more print failures is bad. A neutral "Last week: 11" lets the owner judge.
   - Tiles stack at text 1.5× and above.
5. **Chart**, only when the trend over time is the point. For example: Check-ins by day, or Sold by week. Section heading "By day". Specs in §6.
6. **Breakdown**, heading "Breakdown":
   - **Switch:** raised segments (48 tall) for the report's breakdown keys, for example **Person · Type · Make · Day**. "Day" is the chart's table view (dataviz: every chart has a table twin).
   - **Ranked rows** (one hue, largest first; day order for Day):
     - the name (`type-card-title`)
     - the count, right-aligned, with tabular figures
     - the share ("43%", `type-meta`). Shares use largest-remainder rounding, so a breakdown always adds up to 100%.
     - a thin 8 dp bar under the name, as wide as the share of the largest row. The bar is square at the left and has a 4 dp rounded end.
     - a chevron
   - **Each row drills down** (§7).
   - More than 8 rows: the first 7, then "Show all 12".
7. **Footnote** (`type-meta`, `text-2`), different per report. For check-ins: "Counted on the day the work was done, in shop time. A check-in made offline counts on the day it was made."

**One emerald fill only:** the Scan button in the nav. Report screens use no emerald buttons. Inside a modal sheet the Scan button sits under the scrim and is inert, so the sheet may have one primary.

---

## 5. Date range sheet

Opens from the range button: a bottom sheet with `scrim-modal`, the `surface-raised` sheet, the heading "Show", and a **Close** text button beside it. Back and a tap on the scrim also close it.

- **Presets as rows,** each with its real dates (Starling):

  | Preset | Dates shown |
  |---|---|
  | Today | "Fri 30 Oct" |
  | This week | "Mon 26 Oct to today" |
  | Last week | "Mon 19 Oct to Sun 25 Oct" |
  | This month | "1 Oct to today" |
  | Last 30 days | "1 Oct to 30 Oct" (30 days including today) |

  - Rows are 64 tall.
  - The selected row shows a 20 dp check and `aria-checked`.
  - Picking a row applies it and closes the sheet. There is no Apply button.
- **Below a hairline:** "Custom dates…". It opens the platform's own date-range picker (Material `DateRangePicker`, iOS `UIDatePicker` pair). It's accessible and familiar, and there's nothing to design.
  - Ranges longer than 13 months are refused: "Reports keep 13 months of detail." That is D-R3.
  - Audit-based reports have no limit.
- **Footer** (`type-meta`): "Weeks start Monday. Times are shop time (Pacific)." Both are Shop settings (§12).
- **Back or a tap on the scrim** closes the sheet and changes nothing.

---

## 6. Charts

Built to the dataviz method (the skill's procedure, run this session):

- **Form first:**
  - A trend over days or weeks uses **columns**.
  - A part-to-whole of current stock uses a **thin segmented bar** with the figures beside it.
  - Comparing parts uses **ranked horizontal bars** (the breakdown rows).
  - A single number is a **stat tile**, not a chart.
  - No pies, donuts, dual axes or stacked areas.
- **Colour:**
  - **Single-series columns and bars:** `text-2` (#C6C9CD). One series means one colour and no legend; the heading names it. Emerald is never used: it means "press me".
  - **Status split** (Units by status): the status inks the app already uses, with the same icon and word on every row:

    | Status | Ink |
    |---|---|
    | Incoming | `warn-ink` |
    | In stock | `good-ink` |
    | Sale pending | `info-ink` |

    **Validator** (`validate_palette.js --mode dark --surface #1E1F21`):
    - Adjacent CVD separation passes: ΔE 9.1, protan.
    - The normal-vision floor passes: 15.7.
    - Contrast passes: each ≥ 3:1 on `bg`.
    - The lightness and chroma bands fail. The script scopes those two bands to categorical palettes, and these are status tokens that always carry an icon and a word, and a 2 dp gap between parts.
- **Marks:**
  - Columns are at most 24 wide, with a 4 dp rounded top and a square base, on one hairline baseline.
  - Gridlines are solid hairlines in `divider`, at clean ticks (0, 2, 4).
  - Tick labels and values use text tokens, never the bar colour.
  - Only **today's** column carries its value on the cap. The axis, the tooltip and the Day breakdown carry the rest.
- **Days with no data yet** (later this week): the slot shows the day label in `text-2` and no column. Nothing is drawn at zero height for a day that hasn't happened.
- **Tap or focus a column:**
  - A tooltip shows the date and the value, for example "Thu 29 Oct" and "1 check-in". The value is the strong text.
  - The column lightens to `text`.
  - The hit area is the whole day slot (48 wide or more, the full plot height).
  - A second tap or a tap elsewhere closes it.
  - Each slot is a focusable element named "Thursday 29 October, 1 check-in".
- **Accessible name of the chart:** "Check-ins by day, this week: Monday 4, Tuesday 2, Wednesday 3, Thursday 1, Friday 4."
- **Refetch keeps the frame:**
  - When the range changes, the old figures stay at 40% opacity under a one-line "Updating…" status. They are not replaced by a skeleton.
  - When the reply arrives, everything swaps at once.
  - Reduce-motion: no fades.

---

## 7. Drill-down

- **A breakdown row opens the matching list,** filtered the same way.
  - Example: "Dale" in Check-ins, this week, opens **Stock** filtered to "Checked in Mon 26 Oct to today" and "Checked in by Dale".
  - The list's count must equal the row's count (reporting rule 6). The test contract checks it.
- **The filtered list is pushed on the More stack.** The Stock tab isn't switched, so More stays selected and Back returns to the report.
- **The Stock screen itself is designed separately.** This spec fixes only how the filtered list opens:
  - the Stock top bar
  - a row of **removable filter chips** (raised, each with a ✕, named "Remove filter: Checked in by Dale")
  - "6 units" (`type-meta`)
  - Home's record cards
  - **Back** returns to the report at the same scroll position and breakdown.
- **Drill targets per report:** see §10. Reports whose rows aren't units open their own detail lists instead:
  - Print problems → the failed prints, with time, phone and reason.
  - Refused changes → each refusal, with who, when and why.
  - Phones → one phone's last sync and waiting changes.

---

## 8. Export CSV

Export opens a sheet (Buddy, Zing):

- **Heading:** "Export check-ins".
- **Line:** "This week, Mon 26 Oct to Fri 30 Oct."
- **Choice:** two rows, 64 tall, as radio rows (D-RS1):
  - **Each check-in:** "14 rows. One line per unit, for sorting and filtering." (Default.)
  - **This table:** "4 rows. The breakdown on screen, by person."
- **Note** (`type-meta`): "The file includes names and prices. Your phone's share options open next."
- **Buttons:** Cancel (raised), and **Export** (primary; see §4 on the one emerald fill).
- **Export:**
  1. The server builds the file. The button shows "Preparing…" and is disabled.
  2. The system share sheet opens: Save to Files, Mail, AirDrop, Nearby Share.
  3. Closing the share sheet returns to the report.
  4. Failure: the sheet says "Couldn't make the file. Check the connection and try again." It keeps the choice.

**File rules (server):**
- **File name:** `inscan-check-ins-2026-10-26-to-2026-10-30.csv`.
- **Encoding:** UTF-8 with a byte-order mark, so Excel opens it cleanly. Comma-separated, with RFC 4180 quoting.
- **First row:** the column names in words ("Checked in (shop time)", "Tag ID", "Status now", "Type", "Make", "Model", "Year", "Colour", "VIN", "Serial number", "Price", "Checked in by", "Phone").
- **Spreadsheet-formula guard:** a cell that starts with `=`, `+`, `-`, `@`, a tab or a carriage return gets a leading `'`. Names and notes are typed by people; a cell like `=HYPERLINK(...)` must not run when the owner opens the file.
- **Range:** the report's own range, in shop time.
- **Every export is written to `audit_log`** as `report.export`, recording the report, the range, the choice and the row count. Data leaving the app is a change in who holds it.

---

## 9. States

| State | What shows |
|---|---|
| First load (list or report) | **List:** the rows, each with "Loading…" in place of the figure. **Report:** the frame with the headings, then "Loading…" with a small progress ring where the figures go. No skeleton blocks. |
| Refetch after a range change | The old figures at 40% opacity under "Updating…" (§6). |
| Offline, seen before | **Banner:** "Offline. Showing what this phone loaded at 8:52 am." Built as a `warn-tint` banner. **Behaviour:** the figures stay, the range and breakdowns that were loaded still work, other ranges are disabled, and Export is disabled. |
| Offline, never loaded | "Reports need the shop system. Connect, then try again." with a raised Try again. |
| Nothing in the range | Tiles show 0. The chart and breakdown are replaced by one line: "No check-ins today." Export is disabled ("Nothing to export"). |
| Couldn't load | "Couldn't load this report." The reason comes in words from `Problem`. A raised Try again. |
| No permission | §2. |
| Permission removed while the screen is open | The next call is refused. The screen shows the no-permission message, and the phone deletes its saved copies for that person. |

**Saved copies on a shared phone** (D-RS3):
- The phone keeps the last loaded copy of the list and of each report opened, so offline viewing works.
- Copies are **keyed by the signed-in person** and shown only to them.
- They are deleted on sign out, on Switch user, and when the permission goes.
- Saved report data never shows to an Employee who picks up a manager's phone.

---

## 10. The first set: what each report shows

| Report | Headline tiles | Chart | Breakdown keys | Row drills to | "Each" CSV rows |
|---|---|---|---|---|---|
| Units by status | On hand; Stock value | Thin status split | Status · Type · Make | Stock filtered by status | Each unit on hand |
| Stock value | Total of priced units; Not priced | None | Status · Type · Make | Stock filtered | Each unit on hand with price |
| Aging | Over 90 days; Typical days in stock | None | Age band (0–30, 31–60, 61–90, Over 90) · Type · Make. Then "Oldest in stock": 3 record cards | Stock sorted oldest first, filtered by band | Each unit with days in stock |
| Things to fix | Units with something to fix | None | No price · No VIN · No tag · Tag out of date | Stock with that flag | Each flagged unit |
| Check-ins | Check-ins; Now in stock (of those checked in) | By day | Person · Type · Make · Day | Stock filtered by check-in date and key | Each check-in |
| Check-in to In stock | Typical time; Still Incoming | None | Person · Type | Stock, Incoming, oldest first | Each confirm with hours waited |
| VIN lookups | Lookups; Partly filled | None | Result (full, partial, none, offline) · Type | Check-ins with that result | Each lookup |
| Check-ins not finished | Not finished | None | Step left at · Person | (no list: count only) | Each abandon with step |
| Tags printed | Printed; Reprints | By day | Reason · Person · Printer route | Units with a tag printed in range | Each print |
| Print problems | Failed; Unknown outcome | None | Reason · Printer route · Phone | Failed-print list | Each failure |
| Tags not confirmed | Waiting | None | Days waiting · Person who printed | Stock, Incoming with tag | Each unit |
| Sold | Sold; Total of sale prices | By week | Type · Make · Person | Stock, Sold in range | Each sale |
| Days to sell | Typical days | None | Type · Make | Sold units sorted by days | Each sale with days |
| Scans | Scans; Typed instead | By day | Result · Person · Camera or typed | (no list) | Each scan |
| Camera trouble | Phones with trouble | None | Phone · Kind | Phone detail | Each problem event |
| Work by person | People active | None | One **table**: Person / Check-ins / Moved to In stock / Edits, sorted by check-ins | Stock filtered by that person and column | One row per person |
| Phones | Phones; Waiting to send | None | Phone (last seen, changes waiting, app version) | Phone detail | One row per phone |
| Sign-in security (Admin) | Failed PINs; Lockouts | None | Person · Phone | Event list | Each event |
| Refused changes | Refused | None | Reason · Person | Refusal list | Each refusal |
| Edited at the same time | Overlaps | None | Person · Field | Unit history | Each overlap |
| Waiting more than a day | Changes waiting | None | Phone · Person | Phone detail | Each waiting change |

**Work by person** is the one report with a table instead of one ranked breakdown, because its point is comparing several measures for the same people.
- Columns: Person / Check-ins / Moved to In stock / Edits.
- Numbers are tabular and right-aligned; rows are 56 tall.
- Each cell is a drill target, named, for example, "Dale, 6 check-ins".
- The table fits 370 pt at text 1×. From 1.3× each person becomes a stacked card with label and value lines.

---

## 11. Accessibility and sizes

- **Targets:** every target is ≥ 48. Chart slots are 48 or wider.
- **Focus order:**
  1. back, title, Export
  2. the filter row
  3. the tiles, read as text
  4. the chart slots, left to right
  5. the breakdown switch
  6. the rows
- **Headings:** each section heading is a real heading, so screen-reader users can jump between sections.
- **Colour is never alone:**
  - Status rows carry an icon and a word.
  - The single-series bars are single hue.
  - Shares are printed as numbers.
- **Text 1.5× and up:**
  - Tiles stack.
  - Breakdown rows wrap the name to 2 lines, with the count staying right.
  - The chart keeps its height and its day labels shorten to "M T W T F S S", with full names kept as accessible names.
- **iPad and Android tablets** (size class expanded): the report body goes to two columns, with tiles and the chart on the left and the breakdown on the right. The list stays one column, max 640 wide, centred.
- **Reduce motion:** the refetch fade and sheet slides become instant swaps.

---

## 12. Permissions and settings

| Action | Permission | Default holders |
|---|---|---|
| See More → Reports and every report except Sign-in security | See Reports | Management, Admin |
| See Sign-in security | See sign-in security | Admin |
| Export CSV (either kind) | **Export report data** (new, D-RS2) | Management, Admin |
| Drill into Stock | See Reports, plus whatever Stock already allows | Management, Admin |

| Shop setting | Default | Who changes it |
|---|---|---|
| Shop time zone | America/Los_Angeles | Admin |
| Week starts on | Monday | Admin |
| Keep detailed events for | 13 months (D-R3) | Admin |

Changes needed in `roles-permissions-spec.md` §3:
- split "See Reports … and export CSV" into **See Reports** and **Export report data**
- add the three settings above

---

## 13. Reporting (what the Reports screens themselves record)

| Event | Props | Feeds |
|---|---|---|
| `report.opened` | `report`, `from` (list, link) | Which reports get used, so unused ones can go |
| `report.range` | `report`, `preset` (today, this_week, last_week, this_month, last_30, custom), `days` | Which ranges people need |
| `report.breakdown` | `report`, `by` | Which breakdowns matter |
| `report.drilldown` | `report`, `by` | Whether drill-down is used |
| (audit) `report.export` | `report`, `from`, `to`, `rows` (each, table), `count` | Who took data out of the app, and when |
| (audit) `refused:NOT_ALLOWED` | `permission` | Attempts without permission |

---

## 14. Server and app needs

| Need | Notes |
|---|---|
| `GET /reports/summary` | All list figures in one call, filtered to what the caller may see |
| `GET /reports/{report}?from=&to=&by=` | Returns the tiles, series and breakdown rows, computed in SQL in shop time. `from`/`to` are shop-local dates; the server converts to UTC once. |
| `GET /reports/{report}.csv?from=&to=&rows=each\|table` | Streams the file with the formula guard; writes `report.export` to `audit_log` |
| Permission checks on all three | "See Reports", "See sign-in security", "Export report data" (`roles-permissions-spec.md`) |
| Stock list accepts the report filters | `checked_in_from/to`, `checked_in_by`, `type`, `make`, `status`, `flag`, `age_band`, `sort=oldest`. The same filter model is used by reports (reporting rule 6). |
| Phone-side saved copies | Room tables keyed by `employee_id`. Deleted on sign out, Switch user and a refused permission. |
| `occurredAt` on every outbox entry | Already required by `reporting-spec.md` §6. Without it, "by day" is wrong for anything sent late. |

---

## 15. Decisions for the owner

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D-RS1 | What Export gives | **Both, as a choice: "Each check-in" (default) or "This table".** | "Each" is what a spreadsheet user sorts and pivots. "This table" is what you'd paste into a message. |
| D-RS2 | Export as its own permission | **Yes: "Export report data", Management and Admin by default.** | Seeing a number and carrying every unit's price out of the app are different trust levels. Being separate means you can let a manager see Reports without exporting. |
| D-RS3 | Saved copies for offline viewing | **Yes, per person, deleted on sign out and Switch user.** | Phones are shared. A manager's copy must never show to the next person. |

---

## 16. Test contract

- **Drill-down agrees with the report:** for each breakdown row, the Stock list opened with its filters returns the same count. This is a server test over a seeded shop.
- **Counts use `occurredAt` in shop time.** Example: a check-in made at 11:50 pm Sunday and sent at 8 am Monday counts on Sunday, in last week. The clock is injected.
- **Week boundaries follow the setting.** Changing "Week starts on" to Sunday moves Sunday's work into the next week.
- **Permissions:**
  - An Employee gets `NOT_ALLOWED` from all three endpoints.
  - A Manager gets the list without Sign-in security, and `NOT_ALLOWED` from it.
  - Removing "Export report data" refuses the next export.
- **The CSV formula guard:** a unit whose colour is typed as `=1+1` exports as `'=1+1`. The file opens in a spreadsheet as text.
- **Saved copies:** after Switch user, the next person sees neither the list nor any report, offline or online.
- **Sabotage check:** make the server ignore `to` and confirm the drill-down agreement test goes red.
