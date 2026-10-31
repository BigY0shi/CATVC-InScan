# InScan: Reports screens (spec)

- **Status: LOCKED by the owner on 2026-10-31** ("Yes that looks great! Lock it in.").
  - **The lock covers** the Reports list and Shop system list, the report template, the date range sheet, the charts, drill-down, the export sheet, and the states. That means their layout, states and copy as written here and drawn in `prototypes/reports.html`.
  - **Not part of the lock:** the server prerequisites (§14), the first-release slice (§17), and D-RS1 to D-RS3 (§15). Those are requirements and decisions.
  - **Owner requirement added on 2026-10-31:** "Regular shop members should just be able to see their own history." That is a separate screen, `my-history-ui-spec.md`; Reports stays Management and Admin.
- **Date:** 2026-10-30
- **Mockup:** `prototypes/reports.html`, 14 states. Each state can be shot alone with `?shot&state=<key>`. The contact sheet is `prototypes/reports-states.png`.
- **Frame:** iPhone 17, 402 × 874 pt. 1 CSS px = 1 pt or dp.
- **Locked and applied:**
  - Palette v4, with emerald for actions and selected states only. The raised Scan button in the nav is the one emerald fill.
  - Depth B.
  - The 5-slot nav. Reports is reached from More, so the nav stays visible with More selected.
  - Home's count-card type pattern, used for the headline figures.
- **Server work comes first.** Most figures here need it; see §14 (prerequisites) and §17 (first-release slice).

---

## 1. References (opened and looked at this session)

| App | Screen | Taken | Left |
|---|---|---|---|
| [Jobber](https://mobbin.com/screens/aaaa68ff-32f2-46f0-ba47-fe3ff66a4108) | Business health | Grouped figures, each saying its period ("Past 30 days") | Coloured change chips that judge every number good or bad |
| [Starling](https://mobbin.com/screens/9ea374e1-6270-494e-8d5f-c0232eca40de) | Date range sheet | Presets as rows, each with its real dates; "My week starts: Sunday" said out loud | One long sheet of week, month and year |
| [YNAB](https://mobbin.com/screens/6cd1ca53-4492-4e45-881b-46eee7dd35a1) | Spending breakdown | Row: name, amount, thin proportion bar; the row opens the detail | Emoji; a different colour per row |
| [Commons](https://mobbin.com/screens/f2edd29b-b33d-4759-8a2e-89f92cc5daa5) | Emissions by category | Period control with previous and next arrows; "View all" drill-down | Cartoon icons |
| [GoPay](https://mobbin.com/screens/f150e527-b352-4442-93a9-3b0556696288) | Spending | Period stepper beside the total; rows with a drill arrow | Ruler-dial period picker |
| [Revolut Business](https://mobbin.com/screens/4cbc7fb1-eaaa-4067-9e4b-d42b9cb4a7bf) | Merchant revenue, dark | Filter chips in one row above every card | Area charts; a chart in every card |
| [Cash App](https://mobbin.com/screens/dc7db548-1b0b-4980-b7f0-779609a93904) | Earnings | Chart, then "Key stats" rows, then "Top customers" rows | A single neon bar |
| [Insight Timer](https://mobbin.com/screens/53d30bee-4247-413a-ab28-013ea9f3b466) | My Stats | "Updated: Just now" freshness line | Tabs for the period |
| [Buddy](https://mobbin.com/screens/de540aa0-34bc-4776-8ccf-1ab03f849786) | Export data | Row count shown before the export button | Giant file icon |
| [Zing](https://mobbin.com/screens/7d64d799-b93c-4e5d-8b34-61a08c4a293a) | Export statement | Choice of file kind, each with one line on what it's for | PDF option |
| [Deel](https://mobbin.com/screens/2e121437-b071-4232-a81b-559089e2f7dd) | Analytics, Reports | (Seen, rejected) | A "View" button card per report: 30 cards nobody scans |

---

## 2. Where Reports lives and who sees it

- **Reports is at More → Reports** (D-R5). It isn't a tab, because the nav is locked.
- **The row shows only to people with "See Reports"** (default: Management and Admin).
- **Sign-in security** shows only with "See sign-in security" (default: Admin).
- **Export** needs "Export report data", a new permission (D-RS2; default: Management and Admin).
- **Opened without the permission** (an old link, or the permission was removed while the screen was open):
  - The screen says "Reports are for managers and admins. Ask an Admin if you need them.", with Back.
  - No figures show.
  - The server refuses the data anyway.
- **Filters that name a person** need "See Reports" on every endpoint that accepts them, including Stock's list (`checked_in_by`, `confirmed_by`). Otherwise an Employee could rebuild "work by person" from the Stock list.

---

## 3. Reports list (`more/reports`)

**The list is a business view.** Owners and managers read it to run the shop; the shop-system reports sit one level down (§3b).

From top to bottom:

1. **Top bar** (64, `surface`, 1 dp divider). Back: a text button with a back icon and "More". No title in the bar until the big title scrolls under it (§4).
2. **Title:** "Reports" (`type-screen-title`).
3. **Freshness:** "Updated 9:38 am" (`type-meta`, `text-2`), 8 below the title.
4. **Groups:**
   - Inventory
   - Intake
   - Sales
   - People
   - Shop system

   Each has a heading (`type-section`) and one flat bordered list (`bg` fill, `border-strong`, 12 radius, 1 dp dividers).
5. **Rows**, 64 tall at least, padded 12/12/12/16:
   - the report name (`type-card-title`)
   - under it, the figure (`type-body`, **`text`**: the answer is the point, so it isn't dimmed)
   - a chevron

   The whole row opens the report. The focus ring is drawn inside the row (`outline-offset: -3px`), so the list's rounded clip doesn't cut it.

**Why rows are flat:** they are information that opens more information, like Home's record cards (locked Home: flat and tappable). Twenty raised cards would read as twenty buttons. Raised stays for the controls: range, steppers, breakdown switch and Export.

**Each figure names its own range**, for example "14 this week" or "3 this week". When a period has barely started (Monday morning), the figure adds the last period: "1 so far this week. Last week 11."

**A figure the shop system doesn't record yet reads "Not tracked yet"**, never 0. After launch, the report footnote says "Recording started Mon 2 Nov".

| Group | Report | Figure on the list | Kind |
|---|---|---|---|
| Inventory | Units by status | 37 in stock | Snapshot |
| Inventory | Asking price total | $318,400 | Snapshot |
| Inventory | Aging | 3 over 90 days | Snapshot |
| Inventory | Missing details | 8 units | Snapshot |
| Intake | Check-ins | 14 this week | Period |
| Intake | Time to In stock | Usually within 2 hours | Period |
| Intake | Tags printed | 17 this week | Period |
| Intake | Tags not confirmed | 5 waiting | Snapshot |
| Sales | Sold | 3 this week | Period |
| Sales | Days to sell | Usually 38 days | Period |
| People | Work by person | 4 people this week | Period |
| Shop system | Phones, problems and security | Scans, printing, sign-ins, refused changes | Opens §3b |

### 3b. Shop system (`more/reports/system`)

The same list build, titled "Shop system", with these rows:

| Report | Figure |
|---|---|
| Phones | 1 not heard from since Tue |
| Scans | 86 this week |
| Camera trouble | 1 phone this week |
| VIN lookups | Filled everything for 5 of 14 |
| Check-ins not finished | 2 this week |
| Print problems | 1 this week |
| Sign-in security 🔒 | 2 lockouts this week |
| Refused changes | 3 this week |
| Edited at the same time | 1 this week |
| Changes not sent for a day | Not tracked yet |
| Role changes | None this month |
| Exports | 2 this week |

A lock icon marks a row that people without that report's permission don't see. The footnote reads: "A lock means only people allowed to see it get this row."

**Sales today:**
- "Sold" counts the interim mark-sold (`Units.kt`), so its money figure is the **asking price at the time**, labelled that way, until M3 records sale prices.
- Sale pending fills in when M3 selling ships. Until then the server refuses that status.

**Tickets** gets a group when tickets ship.

---

## 4. A report screen: one template

Every report uses the same frame.

1. **Top bar** (64):
   - **Left:** Back, labelled "Reports" (or "Shop system").
   - **Middle:** a **centred title** (`type-app-title`, one line, ellipsis, about 180 pt). It appears only once the big title has scrolled under the bar, so the report name never leaves the screen.
   - **Right:** **Export**, built exactly like Home's Find chip:
     - 48 tall, `surface-raised`, `border-strong`, `chip-shadow`, no outer shadow
     - padding 0/14/0/10
     - icon in `accent-ink`
     - the word "Export"

     It is hidden without "Export report data".
2. **Title:** the report name (`type-screen-title`).
3. **Filter row** (period reports), one row above everything it scopes:
   - **Previous:** `[‹]`, 48 wide, raised, at least 56 tall.
   - **Range button:** raised, fills the row, at least 56 tall and grows with large text. Two lines: "This week" (`type-label`) over "Mon 26 Oct to today" (`type-meta`, `text-2`), and an expand icon.
   - **Next:** `[›]`, same size as Previous.

   The steppers are named for the unit they step: "Previous week", "Next week". Next is disabled when the range reaches today. Above text 1.3×, the steppers sit on their own row under the range button.
   - **Snapshot reports** have no range. They get one line instead: "Right now. Updated 9:38 am."
4. **Freshness:** "Updated 9:38 am" (`type-meta`, `text-2`), 8 below the row above.
5. **Headline figures:** one or two flat tiles (`surface`, 12 radius, `space-card` padding, no border, no shadow). They're built like Home's count cards:
   - **Value and word** on one baseline: "**14** check-ins", "**$318,400** asking price". The value is `type-big-number`; the word is `type-tile-label` in `text`. They may wrap.
   - **Context line** under them (`type-meta`, `text-2`).
   - **No coloured arrows.** More check-ins is good and more print failures is bad, so the owner judges.
   - **In-progress periods compare like with like:**
     - "Same days last week: 10" compares Monday to Friday this week with Monday to Friday last week.
     - A finished period compares with the whole period before: "Week before: 11".
   - **"Usually" figures** (medians) always say how many units they cover and how many are still open: "Usually 2 hours. 8 moved; 6 still Incoming."
   - When the first figure is 0, only one tile shows.
6. **Chart** (§6), only when the trend over time is the point.
7. **Breakdown:** heading "Breakdown", then:
   - **Switch:** raised segments, 48 tall, 8 apart. The selected one has a check icon, weight 600, `accent-tint` and `accent-ink`. The check and weight carry the state where glare washes out the tint. At text 2× the switch wraps into a 2 × 2 grid.
   - **Rows:** largest first, or in the natural order for days and age bands. Each row has:
     - the name (`type-card-title`)
     - a thin 8 dp bar under the name, one hue (`--data`), square at the left with a 4 dp rounded end. The longest row fills the track.
     - the count, right-aligned in a fixed 56 column so every bar has the same track (`grid-template-columns: 1fr 56px 24px`)
     - a chevron
   - **No percentages.** Rounding made equal counts look unequal (22% against 21%), and the bar already shows the proportion. The CSV "This table" export includes shares.
   - **Rows that count units drill to a list** (§7). Rows that can't drill have no chevron and aren't buttons.
   - More than 8 rows shows the first 7, then "Show all 12" (done on the phone; the response has every row).
8. **Footnote** (`type-meta`, `text-2`): what is counted and from when.

**One emerald fill only:** the Scan button in the nav. Report screens have no emerald buttons. Inside a modal sheet the Scan button sits under the scrim and is inert, so the sheet may have one primary.

**Focus after a sheet closes** goes back to the control that opened it: the range button (now reading the new range) or Export.

---

## 5. Date range sheet

- **Opening:** from the range button. A bottom sheet over `scrim-modal`, on `surface-raised`.
- **Header:** "Date range" with a **Close** text button. Back and a tap on the scrim also close it.
- **Presets** are rows of **buttons**, 64 tall, each with its real dates (Starling):

  | Preset | Dates |
  |---|---|
  | Today | "Fri 30 Oct" |
  | This week | "Mon 26 Oct to today" |
  | Last week | "Mon 19 Oct to Sun 25 Oct" |
  | This month | "1 Oct to today" |
  | Last 30 days | "1 Oct to 30 Oct" (30 days including today) |

  The current one shows a check and is marked current (`aria-current`; in Compose, `selected` semantics). Tapping a row applies it and closes the sheet. These are not radio buttons, so an arrow key never applies a range by accident.
- **Below a hairline: "Custom dates…"** opens the **Material 3 `DateRangePicker` from shared code**, in a full-screen dialog after this sheet closes.
  - iOS has no native range picker (`UIDatePicker` picks one date; `UICalendarView` does single, multiple or week-of-year).
  - Future dates, and dates older than the 13 months that event-based reports keep, are greyed out with `SelectableDates`, rather than refused after the pick.
  - The picker returns UTC-midnight millis: convert with `ZoneOffset.UTC`, then treat the date as a shop-calendar date.
  - **Unverified:** whether the picker's first weekday can be set to the shop's week start, or always follows the phone's locale.
- **Footer** (`type-meta`): "Weeks start Monday. Times are shop time (Pacific)." Both are Shop settings (§12).
- **The app sends the preset, not the dates.** The server resolves "this_week" and sends back `from`, `to` and `asOf`, so the phone never works out where a week starts. The route stores the preset, so "This week" restored on a Monday means the new week.

---

## 6. Charts

Built to the dataviz method (the skill's procedure, run this session):

- **Form:**
  - A trend uses **columns**.
  - The current-stock split uses a **thin segmented bar** beside rows that give each part's name and count.
  - Comparing parts uses **one-hue ranked bars**.
  - A single number is a **figure, not a chart**.
  - No pies, donuts, dual axes or stacked areas.
- **Bucket by range length:**

  | Range | Columns | Example |
  |---|---|---|
  | A single day | None; the chart is hidden | |
  | Up to 14 days | One per day | Mon … Sun |
  | Up to 16 weeks | One per week | "Week of 5 Oct" |
  | Longer | One per month | |

  The heading and the time breakdown key follow the bucket: "By day" or "Day", "By week" or "Week", "By month" or "Month".
- **Colour:**
  - **Single-series columns and bars** use `--data` = `#8C8F95` (5.1:1 on `bg`). A tapped column turns `--data-hi` = `text` (2.9:1 against the others), so the selection is plain to see.
    - Mid-grey keeps the headline figures leading.
    - One series means no legend; the heading names it.
    - Emerald is never a data colour.
  - **Status split** (Units by status) uses the status inks the app already uses, each part with its icon and word: Incoming `warn-ink`, In stock `good-ink`, Sale pending `info-ink`.
    - Validator (`validate_palette.js --mode dark --surface #1E1F21`):
      - adjacent CVD ΔE 9.1 (protan): pass
      - normal-vision floor 15.7: pass
      - contrast ≥ 3:1: pass
      - the lightness and chroma bands: fail. The script scopes those to categorical palettes; these are status tokens that always carry an icon, a word and a 2 dp gap.
    - The split sits **inside the list as its first, non-tappable row**, and the status rows have **no bars of their own**, so the same data isn't drawn twice on two scales. The Type and Make keys use one-hue bars.
- **Marks:**
  - Columns: at most 24 wide, a 4 dp rounded top, a square base, one hairline baseline.
  - Gridlines: solid hairlines in `divider` at clean ticks (0, 2, 4, 6).
  - Tick labels in text tokens.
  - Today's column carries its value on its cap, and its day label reads "**Today**".
  - Days not reached yet show only their label, with no column.
  - **Before recording started:** blank slots, not zero.
- **Readout, not a floating tooltip:**
  - A 44 pt band at the top of the chart holds the readout for the tapped column: the date in `type-meta`, and the value strong ("1 check-in").
  - It is centred on the column and kept inside the plot.
  - While a column is selected, today's cap value hides, so only one value shows at a time.
  - The readout is left out of the accessibility tree; the column's own name already carries the value.
- **Each column is its own accessibility node** (in Compose: a `Row` of slot `Box`es, each `selectable` with `semantics(mergeDescendants = true) { contentDescription = "Thursday 29 October, 1 check-in" }`, drawing its column with `drawBehind`). One `Canvas` would expose nothing.
  - Days that haven't happened are left out of focus.
  - The chart's group name lists every value.
- **Slot width:** the plot width divided by the slot count. That is 49 on a 402 pt iPhone and about 44 on a 360 dp Android phone, where the tick labels move inside the plot.
  - The hit area is the full plot height, about 44 × 184 at the smallest. That clears WCAG 2.5.8's 24 px.
  - It is a deliberate exception to the app's own 48 rule, recorded here.
- **Refetch keeps the frame:**
  - When the range changes, the old figures stay at **50%** opacity and **inert** (hidden from screen readers).
  - One live region, always in the page, says "Updating to last week…" and then "Showing last week".
  - If the fetch fails, the range button goes back to the previous range and the region says "Couldn't load last week. Try again." with a Try again button.
  - Only the newest request counts. The app cancels older calls (`suspendCancellableCoroutine` + `call.cancel()`, `mapLatest` on the range) and drops any reply whose `(report, preset, from, to)` isn't current.
  - Reduce motion: no fade.

---

## 7. Drill-down

- **A row that counts units opens those units:**
  - It opens on **its own route on the More stack** (`more/reports/{report}/units?…`), titled with what it shows: "Dale's check-ins".
  - The meta line restates the range and count: "This week, Mon 26 Oct to today. 6 units, newest first."
  - It reuses the Stock list composable and Home's record cards.
  - The Stock tab is never filtered behind the person's back.
  - Back returns to the report at the same breakdown and scroll.
- **The count always matches, by construction.** Each breakdown row in the response carries a **`drill` filter built by the server**, and the app sends it back unchanged:
  - The filter is a `UnitFilter` in `core`, with one server function that turns it into SQL.
  - That same function is used for the breakdown `GROUP BY` and for `GET /units/query → { total, items, next }`, which is paged and has no 500 cap.
  - The filter includes `to = asOf` and `status = any`, so a unit that sold since the report loaded still appears.
- **Rows that count events,** such as edits or reprints, drill to a list of those events, filtered by an `EventFilter` built the same way, or don't drill.
- **Offline,** rows still read but don't open: "Opening the list needs the shop system."

---

## 8. Export CSV

- **Sheet:**
  - Heading "Export check-ins".
  - Line: "This week, Mon 26 Oct to Fri 30 Oct."
  - Choice: two rows (D-RS1). "Each check-in" (14 rows, the default) and "This table" (4 rows, the breakdown on screen, with shares).
  - Note: "The file includes names and prices. Send it to yourself; don't save it on a shared phone."
  - Buttons: Cancel (raised) and **Share file** (primary).
- **Share file:**
  1. `POST /reports/{report}/export` with `{preset or from/to, rows}`. A POST, because the export has a side effect: an audit row that a retried GET would write twice.
  2. The button reads "Preparing…" and is disabled.
  3. **Android:**
     - The download runs in `viewModelScope`. It streams the body to `cacheDir/exports/` (`body.source()` → `sink().buffer().writeAll`) after checking the status, because errors come back as JSON. A longer read timeout applies to this call only.
     - The file is shared through FileProvider. That needs `<cache-path name="exports" path="exports/"/>` in `file_paths.xml`, which today has only `documents/` and `labels/`, so `getUriForFile` would throw. The intent is `type text/csv`, with `EXTRA_STREAM`, `ClipData` and `FLAG_GRANT_READ_URI_PERMISSION`.
     - The chooser opens only while the screen is resumed. If the file finishes while the app is in the background, the sheet shows a "Share file" button instead.
  4. **iOS:**
     - The file goes to `temporaryDirectory` under its final name.
     - `UIActivityViewController` is anchored to the Export chip (`popoverPresentationController.sourceView` and `sourceRect`), because iPad throws without an anchor.
     - The file is deleted in `completionWithItemsHandler`.
  5. **On failure,** the sheet says "Couldn't make the file. Check the connection and try again." and keeps the choice.
- **Export files never outlive their owner:**
  - Android deletes files older than 24 hours at app start, and all of them on the wipe in §9. Not on the share sheet's return: Gmail and Drive read the file after it closes.
  - **No "Save to phone" option.** A file in Downloads on a shared phone is readable by the next person. iOS's sheet still offers Save to Files and can't hide it, so the note says not to.
- **File rules (server):**
  - **Name:** `inscan-check-ins-2026-10-26-to-2026-10-30.csv`.
  - **Format:** UTF-8 with one byte-order mark, CRLF line ends, RFC 4180 quoting.
  - **Header row:** column names in words ("Checked in (shop time)", "Tag ID", "Status now", "Type", "Make", "Model", "Year", "Colour", "VIN", "Serial number", "Asking price", "Checked in by", "Phone").
  - **Formula guard, then quote:** a text cell that starts with `=`, `+`, `-`, `@`, a tab or a carriage return gets a leading `'`, and only then is it quoted (`=a,b` becomes `"'=a,b"`). It applies to every text cell: typed fields, phone names and values from the VIN lookup. Number cells are written as numbers and not guarded.
  - **Build the rows in memory** (shop-sized), write the `report.export` audit row with the real row count, release the database lock, then send. Streaming while holding the single connection would stall every check-in during a slow download.

---

## 9. States and shared phones

| State | What shows |
|---|---|
| First load (list or report) | **List:** the rows, with "Loading…" in place of each figure. **Report:** the frame and headings, then a small progress ring with "Loading…". No skeleton blocks. |
| Refetch | §6 |
| Offline | "Reports need the shop system. Connect, then try again." with a raised Try again. **Nothing is saved for offline viewing in the first release** (D-RS3). |
| Nothing in the range | One tile at 0 with its comparison. The chart and breakdown are replaced by "No check-ins today, so there's nothing to export." Export is disabled. |
| Couldn't load | "Couldn't load this report.", the reason in words from `Problem`, and a raised Try again. |
| No permission | §2 |

**The wipe:**
- **When:** whenever the signed-in `employeeId` changes, for any reason: Sign out, Switch user, idle lock followed by a different person, a 401 or 403, or the session expiring.
- **What happens:**
  - Exported files are deleted.
  - The More navigation graph is reset (`key(employeeId) { NavHost(…) }`), so the last person's report screens and their ViewModels are gone.
  - Any saved report copies are dropped, if they're added later.
- **Needs:** the sign-in response to include `employeeId` (today it returns only `displayName` and `role`), and one wipe hung on `employeeId` changing in the connection state.

**If saved copies are added later,** they must:
- live in their own `report_cache.db`, excluded from cloud backup and device transfer in both `backup_rules.xml` and `data_extraction_rules.xml` (iOS: `isExcludedFromBackup`)
- be keyed by `employeeId` and have a maximum age
- never include Sign-in security
- show "As of 8:52 am" instead of "Right now"

---

## 10. What each report shows

| Report | Headline | Chart | Breakdown keys | Drills to | "Each" CSV rows |
|---|---|---|---|---|---|
| Units by status | **37** in stock (plus 6 Incoming, 2 Sale pending); **$318,400** asking price | Status split in the list | Status · Type · Make | Units with that status | Each unit not sold |
| Asking price total | **$318,400** asking price (35 of 39 priced); **4** missing a price | None | Status · Type · Make | Units in that group | Each unit In stock or Sale pending, with price |
| Aging | **3** over 90 days (of 39 in stock or Sale pending); **27 days** typical | None | Age · Status · Type · Make, then "Oldest in stock" (3 record cards) | Units in that band, oldest first | Each unit with its "in stock since" date |
| Missing details | **8** units | None | No price · No VIN · No tag · Tag out of date | Units with that gap | Each unit, with its gaps |
| Check-ins | **14** check-ins (same days last week: 10); **8** in stock (of the 14; 6 still Incoming) | By day, week or month | Person · Type · Make · Day | Units checked in | Each check-in |
| Time to In stock | **2 hours** usually (8 moved; 6 still Incoming); **2** Incoming over 3 days | None | Person · Type | Incoming units, oldest first | Each confirm, with hours waited |
| Tags printed | **17** printed; **3** reprints | By day, week or month | Reason · Person · Printer route | Tag-print events | Each print |
| Tags not confirmed | **5** waiting | None | Days waiting · Person who printed | Incoming units with a tag | Each unit |
| Sold | **3** sold; **$27,400** asking price at sale | By day, week or month | Type · Make · Person | Units sold in range | Each sale |
| Days to sell | **38 days** usually (9 sold; 39 not yet) | None | Type · Make | Units sold, by days | Each sale, with days |
| Work by person | **4** people; **14** check-ins | None | One person list (below) | That person's work | One row per person |
| Phones | **3** phones; **1** not heard from since Tue | None | Phone: last heard from, changes waiting, app version | Phone detail | One row per phone |
| Scans | **86** scans; **9** typed instead | By day, week or month | Result · Person · Camera or typed | No list | Each scan |
| Camera trouble | **1** phone | None | Phone · Kind | Phone detail | Each problem |
| VIN lookups | **14** lookups; **5** filled everything | None | Result (full, partial, none, offline) · Type | The check-ins with that result | Each lookup |
| Check-ins not finished | **2** | None | Step left at · Person | No list | Each, with the step |
| Print problems | **1** failed; **0** unknown | None | Reason · Printer route · Phone | Failed-print events | Each failure |
| Sign-in security | **5** failed PINs; **2** lockouts | None | **Phone first**, then "Name the PIN was tried on" | Event list | Each event |
| Refused changes | **3** | None | Reason · Person | Refusal events | Each refusal |
| Edited at the same time | **1** | None | Person · Field | Unit history | Each overlap |
| Changes not sent for a day | Not tracked yet (needs the phone check-in, §14) | None | Phone · Person | Phone detail | Each waiting change |
| Role changes | **0** this month | None | Person changed · Changed by | Event list | Each change |
| Exports | **2** | None | Person · Report | Event list | Each export |

**Definitions (one name per meaning):**
- **In stock** means status In stock, the same as Home.
- **Asking price** is the unit's price field. The app has no cost or sale price until M3.
- **Moved to In stock** means a confirm done in the range, including units checked in earlier. It is used only as the Work by person column.
- **Of the 14, in stock** means units checked in during the range that are In stock now.
- **"In stock since"** is the first In stock change. The audited legacy import may set it, so imported stock doesn't all read 0 days at launch. Aging, its sort and its bands all use it.
- **Age bands** come from the "Old stock after" setting (default 90 days): 0–30, 31–60, 61–90, Over 90. Snow units sit through summer; the owner can move the line.
- **Tag out of date** means the unit's details have changed since its newest tag was printed. Stored as a flag column, set on update and cleared on print, so it can be filtered.
- **Sign-in security** names the PIN's *name* ("Failed PINs on Dale's name"), never "Dale failed". Whoever typed it may not be Dale.

**Work by person** is a list built like a table:
- **Header line** (`type-meta`): Person / Check-ins / Moved to In stock / Edits.
- **Rows:** each person is one row: name, three right-aligned figures in fixed columns, and a chevron.
- **Tapping a row** opens "Dale's work this week", with the three breakdowns as rows.
- **Narrow screens:** whether it fits is decided by **measuring** the width (`BoxWithConstraints`), not by font scale. Below the needed width, each row stacks its three figures as "6 check-ins", "4 moved to In stock", "3 edits".

---

## 11. Accessibility and sizes

- **Targets:** at least 48, except chart slots (§6).
- **Focus order:**
  1. back, (bar title), Export
  2. range controls
  3. tiles, read as text
  4. chart slots
  5. breakdown switch
  6. rows
- **Focus return:** after a sheet closes, focus goes back to the control that opened it.
- **Headings:** every section heading is a real heading.
- **Not colour alone:**
  - Status rows have an icon and a word.
  - The selected switch has a check and weight.
  - Single-series bars are one hue.
- **Large text:**
  - The range button grows.
  - The steppers wrap to their own row above 1.3×.
  - The switch goes 2 × 2 at 2×.
  - Breakdown names wrap to 2 lines.
  - Chart day labels become **date numbers** ("26 27 28 29 30 31 1"), because "M T W T F S S" can't tell Tuesday from Thursday. Each slot keeps its full name.
  - Screenshot tests run at 360 dp, 1.5×, 2× and iOS AX5 (about 3.1×).
- **Tablets:** when the content width is 600 dp or more after any rail (`currentWindowAdaptiveInfo()`, not "Expanded", which misses an iPad in portrait), the report goes two columns: tiles and chart on the left, breakdown on the right. The list stays one column, at most 640 wide.
- **Reduce motion:** the refetch fade and the sheet slide become instant.

---

## 12. Permissions and settings

| Action | Permission | Default |
|---|---|---|
| See More → Reports and every report except Sign-in security | See Reports | Management, Admin |
| Use person filters on any list | See Reports | Management, Admin |
| See Sign-in security | See sign-in security | Admin |
| Export CSV | **Export report data** (new, D-RS2) | Management, Admin |

| Shop setting | Default | Changed by |
|---|---|---|
| Shop time zone | America/Los_Angeles | Admin |
| Week starts on | Monday | Admin |
| Old stock after | 90 days | Admin |
| Keep detailed events for | 13 months (D-R3) | Admin |

---

## 13. Reporting (what the Reports screens record)

| Event | Props | Feeds |
|---|---|---|
| `report.opened` | `report`, `from` (list, link) | Which reports are used, so unused ones can go |
| `report.range` | `report`, `preset`, `days` | Which ranges people need |
| `report.breakdown` | `report`, `by` | Which breakdowns matter |
| `report.drilldown` | `report`, `by` | Whether drill-down is used |
| (audit) `report.export` | `report`, `from`, `to`, `rows`, `count` | The Exports report: who took data out of the app |
| (audit) `refused:NOT_ALLOWED` | `permission` | Refused changes |

Opening, range and breakdown events start from the ViewModel and navigation events, never from composition. A cancelled predictive-back gesture redraws the report underneath and must not fetch again or count twice.

---

## 14. Server and app needs

**Prerequisites. These come before any report:**

| Need | Why |
|---|---|
| **Injected clock**: `inscanServer(db, clock)`, passed to the stores and to `audit()`. Today `now()` is a global `Instant.now()` (`Database.kt`). | Every time-based test in §16 needs it |
| **`occurredAt` on every write** (NewUnit, StatusChange, TagConfirm, outbox entries). Stored as `audit_log.occurred_at` and `units.checked_in_at`. **Clamped** to between (receivedAt minus the longest an outbox may wait) and (receivedAt plus 5 minutes). Clamped rows are counted under Refused changes / Data quality. | Counts by when the work happened; a wrong phone clock can't rewrite closed weeks |
| **One timestamp format**: fixed-width UTC (`yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`) or epoch ms everywhere. `tags.printed_at` parsed on arrival. A migration rewrites existing rows. | `Instant.toString()` varies in width, so text comparison misorders times at second boundaries |
| **`ShopCalendar(zone, weekStart, clock)`** in Kotlin turns a preset or dates into `[startUtc, endUtc)` plus bucket bounds. SQL filters by UTC range; Kotlin buckets. | SQLite has no time-zone database. The sample week includes the end of DST (Sun 1 Nov 2026 is 25 hours) |
| **Refusals audited outside the rolled-back transaction**: `refused:<CODE>` written from the StatusPages handler in its own transaction | Today a refusal thrown inside `db.transaction` rolls its own audit row back |
| **A typed `unit_status_changes` table** (unit, from, to, occurred_at, received_at, actor, price_cents), written by both `/status` and tag confirm | `unit.status:IN_STOCK` is stored in two JSON shapes today |
| **Roles and permissions** (`roles-permissions-spec.md` §6) and a **settings table** | Every permission and setting above |
| **`employeeId` in the sign-in response** | The wipe (§9) |
| **Indexes:** `audit_log(action, occurred_at)`, `audit_log(entity_id, action)`, `units(status)`, `units(checked_in_at)`, `tags(printed_at)`. `refused:` rows are found by range, not `LIKE`. | `audit_log` has no indexes |
| **Price can be empty** (D-C2) | "Missing a price" is impossible while `price_cents > 0`. Rebuilding that CHECK needs `foreign_keys=OFF`, which has no effect inside `migrate()`'s transaction, so it needs its own step |
| **Phone check-in:** each phone reports its queue length and oldest waiting `occurredAt`; `devices.last_seen` | Phones and "Changes not sent for a day". Without it, "None" would be false comfort |
| **Sign-in security records:** `refused:PIN_LOCKED`, tries during a lockout, unknown-name tries, and a lockout row | Today only `login.failed` exists, and a lockout leaves no record |

**Report endpoints:**

| Endpoint | Notes |
|---|---|
| `GET /reports/summary` | All list figures in one call, filtered by permission. Each figure is computed by **the report's own function**: the test checks that `summary[r] == report(r).tiles[0]`. `null` means not tracked. |
| `GET /reports/{report}?preset=` or `?from=&to=` | Returns `ReportDto` (in `core`): `asOf`, the resolved `from`/`to`, tiles with their comparisons, series, and **every breakdown key's rows in one response**, each row with its `drill` filter. |
| `POST /reports/{report}/export` | §8 |
| `GET /units/query` | Paged, `{total, items, next}`, takes a `UnitFilter` (§7) |
| Medians | Computed in Kotlin (SQLite's percentile needs a compile flag; not checked for sqlite-jdbc 3.53.4.0). Tested empty, odd and even. |
| Caching / ETag | Later |

**App:**
- The route holds report, preset, from/to and by (`more/reports/{report}?preset=&by=`), so they survive process death.
- The data lives in the back-stack entry's ViewModel, so Back returns to the same scroll.
- The scroll container isn't composed until data is there.
- Calls are cancellable (§6).

---

## 15. Decisions for the owner

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D-RS1 | What Export gives | **A choice: "Each check-in" (default) or "This table".** | "Each" is what you sort and filter in a spreadsheet; "This table" is what you paste into a message. |
| D-RS2 | Export as its own permission | **Yes, "Export report data", Management and Admin by default.** | Seeing a number and carrying every unit's price out of the app are different levels of trust. |
| D-RS3 | Offline copies of reports | **Not in the first release. Reports are online only.** | You expect the phones to be online. Saved copies on shared phones need per-person storage, wiping and backup exclusion (§9) for little gain. Add them only if offline viewing turns out to matter. |

---

## 16. Test contract

The fixture is a `TestShop` (a new `ReportsTest.kt`) with a movable clock and helpers to seed units and events.

- **Drill-down agrees with the report:** for every report × breakdown key × row, the `drill` filter's `GET /units/query` total equals the row's count.
- **Summary agrees:** `summary[r] == report(r).tiles[0]`, for every report.
- **Counts follow `occurredAt` in shop time:** a check-in made 11:50 pm Sunday and received 8 am Monday counts last week.
- **Clamping:** a phone time 3 days in the future is clamped and counted under data quality.
- **DST:** the week of Sun 1 Nov 2026 (a 25-hour day) and the week of Sun 14 Mar 2027 (23 hours) bucket every event into the right shop day. Sabotage: forcing UTC turns these red.
- **Week start:** switching "Week starts on" to Sunday moves Sunday's work into the next week.
- **Timestamps:** rows in the first second of a shop day land in that day.
- **Permissions:**
  - An Employee is refused by all report endpoints and by person filters on `/units/query`.
  - A Manager doesn't see Sign-in security and is refused by it.
  - Removing "Export report data" refuses the next export.
  - Every refusal leaves its audit row.
- **CSV:** `CsvWriter` unit tests (no Ktor):
  - guard then quote (`=a,b` → `"'=a,b"`)
  - numbers unguarded
  - one BOM
  - CRLF
  - a phone name of `@SUM(A1)` guarded
- **Export audit:** one export writes one `report.export` row, with the real count.
- **Medians:** empty, odd and even.
- **The wipe (app):** after a different `employeeId` signs in by any path (button, idle lock, 401, expiry), no export file and no report screen of the previous person remains.
- **Sabotage:** make `/units/query` ignore `to` and confirm the drill-down agreement test goes red.

---

## 17. First-release slice

| Order | What |
|---|---|
| 1 | Prerequisites in §14: clock, `occurredAt`, timestamps, ShopCalendar, roles, settings, refusals, `employeeId`, indexes |
| 2 | `UnitFilter` and `/units/query` |
| 3 | Reports that need nothing new: Units by status, Asking price total, Check-ins (with the per-check-in CSV), Time to In stock, Tags printed, Tags not confirmed, Sold, Work by person |
| 4 | After D-C2 (price can be empty): Missing details |
| 5 | After `app_events` and the phone check-in: Scans, Camera trouble, VIN lookups, Check-ins not finished, Print problems, Phones, Changes not sent for a day |
| 6 | After the sign-in records: Sign-in security |

Online only. No saved copies (D-RS3).
