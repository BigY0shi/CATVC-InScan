# InScan: Stock list (spec)

- **Status:** revised after the Phase 4 review gate (`council-review-stock.md`). D-S1 to D-S3 yes and D-H4 on cards decided (owner, 2026-11-01). Not locked.
- **Owner request (2026-10-31):** "Stock list".
- **Mockup:** `prototypes/stock.html`, 8 states; contact sheet `stock-states.png`.
- **Frame:** iPhone 17, 402 × 874 pt.
- **Built from the locked screens:**
  - **Home:** top bar with the signed-in chip, the Find field, chips, the full record card, the status banner.
  - **Reports:** sheets, the selected-state rule, the 24/12 spacing, the one-emerald rule, and the drill-down contract (§7).
- **D-H4 applies:** people without "See Reports" never get other people's names.

---

## 1. References (opened and looked at this session)

| App | Screen | Taken | Left |
|---|---|---|---|
| [Shopify](https://mobbin.com/screens/b933ed27-9092-4c6e-9499-82f8b2a2bef0) | Products | Search first, then status choices, then the list | Icon-only sort and filter |
| [eBay](https://mobbin.com/screens/42c9beda-6621-42c7-bde1-a57911a99138) | My Collection | Item count; "Status (1)" and "Sort" chips side by side | Photo grid |
| [alias](https://mobbin.com/screens/208ea8a9-bf35-4d6e-a91d-bfd6dc06dada) | For sale | Count line; chips that show their value | Neon accent |
| [Turo](https://mobbin.com/screens/a65e4a8d-ed2f-46a2-a2bb-3e67284fc592) | Filters | Type cards; "Show 200+ results" | Price sliders; sort buried in the sheet |
| [Skyscanner](https://mobbin.com/screens/d9426e97-514c-465d-8a83-54d07993b950) | Filter | "Clear all"; live count on the button | Photo cards for every type |
| [BlaBlaCar](https://mobbin.com/screens/12b2fc80-a308-4945-b869-53b4f06d6fd7) | Filter | A count beside every option | Greying out zero options (reads as "can't" to older staff) |
| [Navan](https://mobbin.com/screens/0af01228-50ed-4b50-97bd-b3d37a1382b7) | Results | Applied filters as chips you clear in one tap | Purple |
| [Trip.com](https://mobbin.com/screens/1515a437-ef10-411a-b777-6d25513c2ab1) | Filter panel | "81 cars in total" beside Clear and Apply | Drop-down panels |

## 2. What it is

- **The Stock tab** (`stock`) answers "what do we have, and where is it in the process".
- **Where it opens:**
  - From the nav, on **In stock** (a Shop setting).
  - From Home's count cards and "See all", on that status. The route carries it (`Stock(status)`), so the tab's remembered state never overrides it.
- **The same list composable serves Reports drill-downs** on the More stack (Reports §7, locked): a fixed server filter and a title from the report, with no search, chips or Filter.

## 3. Layout (`stock`)

From top to bottom. The body gap is 24, made of two 12-gap stacks:

1. **Top bar** (64, `surface`, padding 0 12 0 16), as on Home:
   - "Stock" in `type-app-title` on the left.
   - The signed-in chip (Home's `topbar.user`) on the right.
   - No Find chip, because the field below is Find.
2. **Stack 1:**
   - **The Find field**, exactly Home's build:
     - `.find` well, with a functional magnifier.
     - Placeholder "Find a unit"; typed text in capitals.
     - Autocorrect and autocapitalise suggestions off.
     - The accent-ink **Clear** text button.
     - The help line "Tag ID, VIN, make or model" stays visible under it.
   - **Status chips:** Home's chip build, raised, 48 tall, with press feedback (pressed layer, 0.985 scale). They wrap and never scroll sideways.
     - **All 45 · In stock 37 · Incoming 6 · Sale pending 2 · Sold**
     - **All** means everything not sold. Its count matches Reports' "45 not sold".
     - **Sold** has no count.
     - "Show retired" adds a **Retired** chip.
     - One chip is selected, shown by a check, weight 600 and `accent-tint`.
     - While a search is typed, each count shows that status's matches.
3. **Stack 2:**
   - **Results line:** on the left, the count, which is the screen's one live region (`type-meta`, `text-2`), for example "37 units". On the right, two chips built like Find (accent-ink icon, padding 0 14 0 10, gap 6):
     - **Sort:** "Newest in stock ▾". It opens a short sort sheet: radio rows that apply on tap.
     - **Filter**, or "Filter · 2". The accessible name is "Filter, 2 applied".
   - **Applied filters:** chips in the selected style, each with an accent-ink ✕ and named "Remove filter: Two wheels", then a **Clear all** text button.
   - **Cards:** Home's full record card (128 × 96 image, padding 16/8/16/16, `balance`/`pretty`), 12 apart.
4. **Freshness:** after Home's rule, only when it matters: "On this phone, as of 9:02 am" under the results line once the copy is more than 15 minutes old, or offline.

**Default sort follows the status,** because each status is a different question:

| Status | Default sort | Meta line on the card |
|---|---|---|
| In stock | Newest in stock first | "Four wheels · Grey · $9,499 · 1 day in stock" |
| Incoming | **Oldest first** (the scan queue, same order as Home) | "Side-by-side · Olive · checked in Tue · waiting 3 days" |
| Sale pending | Newest first | "Side-by-side · Black · $18,999 · sale started Tue" |
| Sold | Newest sold first | "Water · White · sold 14 Oct" |
| All | Newest checked in | the status's own line |

**Card rules:**
- **Colour is always shown,** because staff ask for "the blue RZR".
- **Gaps:** a 16 pt `warning` icon in `warn-ink`, then the word in `text`, weight 500. Words are lower case mid-line: "no price", "no VIN", "no tag yet", "tag out of date". Amber text would read as the Incoming status.
- **Line breaks:** no part of the meta line breaks inside itself; non-breaking spaces join its words.
- **Names (D-H4; owner, 2026-11-01: "employees see no name"):**
  - People without "See Reports" see no name on any card, their own included: "checked in Tue".
  - Managers and admins see "checked in by Ray on Tue", **online only**.
  - The "Only units checked in by you" filter stays. It filters; it doesn't put a name on a card.
- **Screen-reader names** use Home's label builder: name, tag ID spelled out, colour, price, status, full day. For example: "2026 Polaris Sportsman 570, tag 5 N V A 9 H Q C, grey, $9,499, in stock 1 day."

**Paging:** 30 at a time. "Loading more…" near the end; "That's all 37." at the end.

**The server answer never reorders the list under a finger:**
- Before the first scroll or tap, the server's answer replaces the phone copy's.
- After that, cards update in place, and new order waits for the next refresh (pull down, or reopening the tab).
- Opening a unit always fetches it fresh.

## 4. Sort sheet and filter sheet

**Sort sheet:** short, with "Sort by" as the heading and six radio rows that apply on tap and close:
- Newest in stock
- Oldest in stock
- Newest checked in
- Oldest checked in
- Price, high to low
- Price, low to high
- Make, A to Z

**Filter sheet:** 748 tall (so the "Stock" title stays visible above it), `surface-raised`, entering with the 200 ms `up`.
- **Dismissing:**
  - Swipe-down is off.
  - **Cancel** in the header throws changes away. So do Back and a tap on the scrim. The word says what happens.
- **Header:** "Filter" in `type-app-title`, and **Cancel**.
- **Groups**, in this order. Each has a label in `type-section`/`text` and sits 12 from its content. All options are raised chips (`raised-fill`, `chip-shadow`), multi-select, each with its count:
  1. **Missing details:** No price · No VIN · No tag yet · Tag out of date. It comes first because "what has no price" is the common question.
  2. **Type:** from the editable list, in the admin's order.
  3. **Make:** the top 6 by count. When there are more makes, "All makes (9)" opens a page inside the sheet.
  4. **Model year:** the years present, newest first, plus "No year".
  5. **Checked in:** single-select chips: Any time · Today · This week · This month · Custom dates (a page inside the sheet, using the Reports picker).
  6. **Who checked it in:**
     - With "See Reports": person chips.
     - Otherwise: one toggle chip, "Only units checked in by you".
  7. **Show retired:** a toggle chip.
- **Counts:** each group's counts ignore that group's own choices and apply the others. So picking "Two wheels" doesn't zero the other types.
- **Zero:** an option with 0 is shown normally with its "0". Grey means one thing only: unavailable (offline, or the server doesn't track it yet). Unavailable options say why ("Needs the shop system", "Not tracked yet").
- **Footer** (pinned, padding 12 16 50, Reports' `.acts` layout):
  - Raised **Clear all** (min 112).
  - The primary **Show 9 units**.
  - When the choices match nothing, the primary reads "No units match", is disabled, and a line above it names the cause: "Snow + No price match nothing."

## 5. States

| State | What shows |
|---|---|
| Default | In stock, newest in stock first |
| From Home's Incoming count | Incoming, oldest first; "6 units"; cards say how long each has waited |
| Search typed | Matches in the selected status; the chip counts show matches elsewhere |
| No match in this status, matches elsewhere | The results line and filters stay. **One other status:** "No match in In stock. 1 in Incoming." with **Show Incoming**. **Several:** "Show 2 matches" (switches to All, which is not sold). **Sold** is mentioned separately: "1 sold unit matches", with **Show sold**. With filters on: "with 2 filters on" and **Clear filters**. |
| No match anywhere | "Nothing matches "RZR 2019"." and "Check the spelling, or scan the tag." with **Clear search** |
| Filters applied | Applied chips with Clear all; "2 units" |
| Offline | Home's amber status banner: "Shop system offline" / "Showing this phone's copy, as of 9:02 am."<br>**Work:** search, status, type, make and year (the copy holds every unsold unit, §8).<br>**Grey with "Needs the shop system":** sold before the copy's window, dates outside it, and person filters for everyone.<br>**No match offline:** "Nothing on this phone's copy matches. Scan the tag, or check again when the shop system is back." |
| Report drill-down | Title from the report, a fixed meta line, no search, chips or Filter (Reports §7) |

**Focus:**
- After closing a sheet: Sort or Filter.
- After removing an applied chip: the next chip, or Filter if none are left.
- After Clear search: the Find field.
- After Show Incoming, Clear all or a status change: the results line.

**The live region:** one, always present (the count). It announces about 1 s after typing stops, never on every keystroke.

## 6. Accessibility and sizes

- **Status chips** are a single-select group (`selectableGroup`, `Role.RadioButton`). Filter options are checkboxes. Toggle chips are switches.
- **Large text:**
  - Only the Find field stays pinned while scrolling; chips and the results line scroll with the list.
  - The results line stacks Sort and Filter under the count.
  - Cards follow Home's large-text rules.
- **Keyboard:**
  - `adjustResize`, with the list padded by the keyboard's inset.
  - The Search key closes the keyboard.
  - Dragging the list hides it.
- **Tablets** (600 dp or more of content): two columns of cards, in the same grid component as one column, so scroll position survives rotation.

## 7. Permissions and settings

| Action | Permission | Default |
|---|---|---|
| See and search the Stock list | (signed in) | All roles |
| Person filters, and names on cards | See Reports (online only) | Management, Admin |
| "Only units checked in by you" | See my own history | All roles |

| Shop setting | Default | Changed by |
|---|---|---|
| Stock opens on | In stock | Admin |
| Sold units kept on each phone | the newest 200 | Admin |

## 8. Server and app needs

| Need | Notes |
|---|---|
| **Search: one matcher in `core`** | Used by the server, the phone copy and Home's Find. In order:<br>1. Strip a leading "ID", then clean the short ID (`normalizeShort`). An exact tag match ranks first, but never stops other fields matching: "KAWASAKI" and "MAVERICK" are valid 8-character IDs.<br>2. Each word must match some field, after lower-casing and removing punctuation and spaces. That makes "can am", "canam" and "Can-Am" equal, and "seadoo" equal to "Sea-Doo".<br>3. Grey and gray are the same.<br>4. VIN and serial: 6 or more characters anywhere, or the last 4 to 8. Matched raw and cleaned (O→0).<br>5. `%` and `_` are escaped.<br>The response says `matchedBy` (id, vin, text). |
| **`GET /units/query`** | Reports §7, with:<br>- `statuses` as a set (Show retired adds Retired), replacing status plus retired.<br>- `include=counts` on page 1 only. Each facet group ignores its own choices; one `statusCounts(filter)` function also serves Home.<br>- Keyset paging per sort, with empty values last, make compared ignoring case after cleaning, and the `id` tiebreak.<br>- `asOf` pinned on page 1, with every later page filtered `<= asOf`.<br>- The cursor carries a hash of the filter; a mismatch gets a 400.<br>- One documented way to put a filter in a query string, with a round-trip test. |
| **Cost** | Measured on sqlite-jdbc 3.53.4.0: all 7 queries per keystroke take about 3 ms at 5,000 units and about 53 ms at 50,000. No cache. No FTS: the phone's SQLite has no FTS5, and both sides must match the same way. |
| **List DTO (D-H4)** | A separate `UnitListItem` for every bulk endpoint (`/units`, `/units/query`). It has no `createdBy`, `updatedBy` or `vinFrameVerifiedBy`. Names come in a separate `people` map only for callers with "See Reports". `UnitDto` stays for single-unit endpoints. Tested by serializing. |
| **The phone's copy, one shape for everyone** | **Holds:** every unit that isn't Sold or Retired, plus the newest sold units (the setting). Rows outside that are pruned unless the outbox has changes for them.<br>**No person fields at all.** "Mine" lives in a separate `mine(employeeId, unitId)` table, cleared by the wipe.<br>**Room migration** adds `checked_in_at`, `in_stock_since`, tag state, retired and colour.<br>**Fix:** `ShopSync` stops writing a display name into `updatedBy`. |
| **One count source** | Home's count cards read the server's `statusCounts`, stored with its `asOf`. Offline, they and the chips count the copy, which now holds every unsold unit, so they still agree. |
| **Not tracked yet, so shown greyed with "Not tracked yet":** | **No price:** needs D-C2.<br>**Tag out of date:** needs the flag column.<br>**Checked-in dates:** need `occurredAt`.<br>**"Sale started" and "sold" dates, and the in-stock sorts:** need `unit_status_changes` and `in_stock_since`. Never fall back to `created_at`, which counts time spent as Incoming.<br>**Person chips:** need roles. |
| **App** | **Calls:** cancellable (`enqueue` + `invokeOnCancellation`); a cancelled call is never shown as offline.<br>**Search pipeline:** `debounce(250)` + `flatMapLatest`; the Search key and Clear skip the debounce.<br>**Navigation:** a typed route `Stock(status)` in a real NavHost, with count cards navigating with `restoreState = false`. One stateless `StockList(state, onAction)` serves the tab and the drill-down.<br>**Paging:** hand-written, one `StateFlow` holding items, counts and total, because Paging 3 can't carry counts beside rows. Keys are unit IDs; scroll is restored by the top unit's key.<br>**Filter sheet:** `ModalBottomSheet` with `skipPartiallyExpanded`, drag gestures off, a pinned footer, and pages inside the sheet.<br>**Dependency:** `material3-adaptive` for the tablet breakpoint. |

## 9. Reporting

| Event | Props | Feeds |
|---|---|---|
| `stock.opened` | `from` (tab, home_count, report) | How people reach stock |
| `stock.search` | `chars`, `results`, `status`, `matchedBy` | Find hit rate (reporting spec §4d) |
| `stock.filter` | `keys` | Which filters matter |
| `stock.sort` | `by` | Which sorts matter |
| `stock.empty` | `reason` (search, filters, offline) | Dead ends |

## 10. Decisions (owner: "yes to all", 2026-11-01)

| # | Decision | Decided | Why |
|---|---|---|---|
| D-S1 | What Stock opens on | **In stock** (a Shop setting) | "What do we have to sell" is the common question. |
| D-S2 | Where a search looks | **The chosen status; the chips show matches elsewhere, with one-tap Show buttons** | Nothing is missed, and sold units don't mix in with stock. |
| D-S3 | The phone keeps every unsold unit, plus the newest 200 sold | **Yes** (a Shop setting) | Today's copy drops the oldest stock first, so a unit sold elsewhere can show In stock on this phone indefinitely. |

**D-H4 on record cards, decided (owner, 2026-11-01): employees see no name.** This applies to Home's record cards and to every card drawn from the phone's copy, Stock included. People without See Reports see "checked in Tue", never "by you" and never another person's name.

## 11. Test contract

- **Search:**
  - Before replacing `GET /units`, write tests that pin its current behaviour.
  - One shared fixture of searches and their expected IDs runs against the server and the phone's Room query. "ID 3HZW8RTB", "3hzw8rtb" and "3HZWBRTB" all find the unit.
  - "KAWASAKI" finds Kawasakis. "can am" finds Can-Am.
  - `%` and `_` are taken literally.
- **Counts:**
  - Home and the chips read the same `statusCounts`.
  - Choosing a type doesn't zero the other types.
  - The total equals the rows paged.
- **Paging:**
  - Ties and empty prices never skip or repeat.
  - A unit added between pages doesn't shift them.
  - A reused cursor with a different filter gets a 400.
  - Sabotage: drop the `id` tiebreak and the paging test fails.
- **D-H4:**
  - No `createdBy`, `updatedBy` or `vinFrameVerifiedBy` in any bulk response.
  - After a manager syncs and then an Employee signs in on the same phone, the Employee sees no names on any card, their own included.
- **Phone copy:** after a clean sync it equals the server for every unsold unit, and a unit sold elsewhere leaves In stock.
- **Offline:** search, status and type work; person filters are grey; the banner shows the copy's time.
