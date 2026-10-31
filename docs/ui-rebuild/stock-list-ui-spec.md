# InScan: Stock list (spec)

- **Status:** Phase 1 draft, not locked.
- **Owner request (2026-10-31):** "Stock list".
- **Date:** 2026-10-31
- **Mockup:** `prototypes/stock.html`, with contact sheet `stock-states.png`.
- **Frame:** iPhone 17, 402 × 874 pt.
- **Built from the locked screens:**
  - Home: top bar, record cards, status tags, status banner
  - Reports: big title, flat bordered lists, sheet build, one-emerald rule
  - the drill-down contract in Reports §7
- **D-H4 applies:** people without "See Reports" never get other people's names in this list.

---

## 1. References (opened and looked at this session)

| App | Screen | Taken | Left |
|---|---|---|---|
| [Shopify](https://mobbin.com/screens/b933ed27-9092-4c6e-9499-82f8b2a2bef0) | Products | Search field first, then status choices (All, Active, Draft, Archived) above the list | Icon-only sort and filter buttons |
| [eBay](https://mobbin.com/screens/42c9beda-6621-42c7-bde1-a57911a99138) | My Collection | "2 items" count; "Status (1)" and "Sort" chips showing how many are applied | Grid of photos |
| [alias](https://mobbin.com/screens/208ea8a9-bf35-4d6e-a91d-bfd6dc06dada) | For sale | Count line above the list; filter chips that show their value ("Sizes: 10") | Neon accent |
| [Turo](https://mobbin.com/screens/a65e4a8d-ed2f-46a2-a2bb-3e67284fc592) | Filters | Sort as the first row of the filter sheet; vehicle type cards; "Show 200+ results" button | Price sliders |
| [Skyscanner](https://mobbin.com/screens/d9426e97-514c-465d-8a83-54d07993b950) | Filter | "Clear all filters"; the button states the live count ("See 217 cars") | Photo cards for every type |
| [BlaBlaCar](https://mobbin.com/screens/12b2fc80-a308-4945-b869-53b4f06d6fd7) | Filter | A count beside every option; options with 0 greyed out | Blue checkboxes |
| [Navan](https://mobbin.com/screens/0af01228-50ed-4b50-97bd-b3d37a1382b7) | Results | "1 Filters ×" chip that clears in one tap | Purple chips |
| [Trip.com](https://mobbin.com/screens/1515a437-ef10-411a-b777-6d25513c2ab1) | Filter panel | "81 cars in total" stated beside Clear and Apply | Drop-down panels over the list |

## 2. What it is

- **The Stock tab** (`stock`). It answers "what do we have, and where is it in the process".
- **How it opens:**
  - From the nav it opens on **In stock**.
  - From Home's count cards it opens on that status, so "6 Incoming" opens Incoming.
- **The same list composable serves Reports drill-downs** on the More stack (Reports §7, locked): titled "Dale's check-ins", with a fixed server filter and no search or status chips.
- **Offline,** it works from the phone's stored copy (§6).

## 3. Layout (`stock`)

From top to bottom:

1. **Top bar** (64, `surface`): "Stock" in `type-app-title` on the left, the same as Home's brand position. No back button: it's a tab root.
2. **Search well** (56; Home's search field build: `well-fill`, `border-strong`, `well-shadow`):
   - A magnifier icon (functional) and the placeholder "Tag ID, VIN, make or model".
   - A clear ✕ (48 target) once there is text.
   - The keyboard's Search key closes it. Matching is as you type, after 250 ms.
   - **Matches:**
     - the tag ID (the 8-character short ID, cleaned like Scan: O→0, I/L→1)
     - the VIN or serial (any 4 or more characters, anywhere in it)
     - make, model, sub-model, colour and year
3. **Status chips** (Home's chip build, 48 tall, raised; they wrap to a second line, never scroll sideways, so no status is hidden off screen):
   - **All · In stock 37 · Incoming 6 · Sale pending 2 · Sold**
   - Choose one. The selected chip shows a check, weight 600 and `accent-tint` (the Reports switch rule).
   - Counts come from the same source as Home's count cards, so the two always agree.
   - **While a search is typed, each count shows how many matches that status has.** So "In stock 0 · Incoming 1" shows where the match is.
   - Sold has no count; it grows forever.
   - Retired units appear only with the "Show retired" filter.
4. **Results line:**
   - On the left, `type-meta`, `text-2`: "37 units, newest first". The sort is said in words.
   - On the right, a raised **Filter** chip (`tune` icon). With filters applied it reads "Filter · 2".
5. **Applied filters** (only when there are any): removable chips (raised, ✕, named "Remove filter: Two wheels"), then a **Clear all** text button.
6. **List:** Home's record cards (locked), 10 apart. The meta line changes by status, and the price shows wherever a sale is possible:

   | Status | Meta line |
   |---|---|
   | In stock | "Four wheels · $9,499 · 12 days in stock" |
   | Incoming | "Two wheels · checked in by you, today · no tag yet" |
   | Sale pending | "Side-by-side · $18,999 · sale started Tue" |
   | Sold | "Water · sold 14 Oct" |

   - **Gaps show in `warn-ink`:** "No price", "No VIN", "no tag yet", "Tag out of date".
   - **Names (D-H4):**
     - People without "See Reports" see "checked in by you" for their own units and "checked in Tue" for everyone else's.
     - Managers and admins see "checked in by Ray, Tue".
7. **Paging:** 30 at a time. A "Loading more…" row appears as the end comes near. At the end: "That's all 37."

**One emerald fill:** the Scan button. Nothing on this screen is emerald except the filter sheet's "Show 12 units", which sits under a scrim (§4).

## 4. Filter and sort sheet

The sheet is near full height (`surface-raised`, `scrim-modal`). It scrolls, and its footer stays pinned.

- **Header:** "Filter and sort", with a **Clear all** text button and **Close**.
- **Sort,** as radio rows, 56 tall each:
  - Newest checked in (default)
  - Oldest in stock first
  - Price, high to low
  - Price, low to high
  - Make, A to Z
- **Type:** chips (multi-select) from the editable type list, in the admin's order, each with its count ("Two wheels 14"). Options with 0 in the current results are greyed out but still choosable, so you can see they exist.
- **Make:** chips with counts, top 6 by count, then "All makes (11)" opens a full list.
- **Model year:** chips: this year, last year, older.
- **Missing details:** checkbox rows with counts, matching the Reports "Missing details" report:
  - No price
  - No VIN
  - No tag yet
  - Tag out of date
- **Checked in:** radio rows: Any time, Today, This week, This month, Custom dates (the Reports picker).
- **Who checked it in:**
  - **With "See Reports":** person chips.
  - **Without it:** one switch, "Only units I checked in".
- **Show retired:** a switch, off by default.
- **Footer:** **Show 12 units**, the primary button with a live count. With 0 it reads "No units match" and is disabled. Changes apply only when it is tapped. Close throws them away.

## 5. States

| State | What shows |
|---|---|
| Default | In stock, newest first |
| From a Home count card | That status selected |
| Search typed | The results, with the chip counts showing matches per status |
| No match in the selected status | "No match in In stock. 1 in Incoming." with a raised **Show Incoming** button, plus Clear search |
| No match anywhere | "Nothing matches "rzr 2019"." and "Check the spelling, or scan the tag instead." with Clear search |
| Filters applied | Removable chips and Clear all; the count says "12 units" |
| Filters match nothing | "Nothing matches these filters." with a raised **Clear filters** |
| Offline | Home's amber status banner: "Shop system offline" / "Showing this phone's copy. Units checked in long ago may be missing." Search, status and type work. Filters the copy can't answer (who checked it in, for people without See Reports; dates older than the copy) are greyed out, with "Needs the shop system". |
| First load | The phone's copy appears at once. The freshness line under the results ("Updated 9:38 am") changes when the server answers. No skeleton. |
| Report drill-down | Title from the report, a fixed meta line, no search, chips or Filter (Reports §7) |

## 6. Accessibility

- **Each card has one name:** "In stock, 2026 Polaris Sportsman 570, ID 5NVA9HQC, Four wheels, $9,499, 12 days in stock."
- **Chips:** status chips are a single-select group; filter chips are buttons whose names start with "Remove filter".
- **Live region** for the count: "12 units", announced after typing stops.
- **Focus:** the search well, chips, Filter, the applied chips, then the cards. After the sheet closes, focus returns to Filter.
- **Large text:**
  - The chips wrap.
  - The results line stacks the Filter chip under the count.
  - Cards keep Home's large-text rules.
- **Tablets** (600 dp or more of content width): two columns of cards.

## 7. Permissions and settings

| Action | Permission | Default |
|---|---|---|
| See the Stock list and search it | (signed in) | All roles |
| Filter by person | See Reports | Management, Admin |
| "Only units I checked in" | See my own history | All roles |
| See other people's names on cards | See Reports (D-H4) | Management, Admin |
| Show retired | (signed in) | All roles |

| Shop setting | Default | Changed by |
|---|---|---|
| Stock opens on | In stock | Admin |

## 8. Reporting

| Event | Props | Feeds |
|---|---|---|
| `stock.opened` | `from` (tab, home_count, report) | How people get to stock |
| `stock.search` | `chars`, `results`, `status`, `matched` (id, vin, text, none) | The "Find hit rate" (reporting spec §4d) |
| `stock.filter` | `keys` (type, make, year, missing, checked_in, person, retired) | Which filters matter |
| `stock.sort` | `by` | Which sorts matter |
| `stock.empty` | `reason` (search, filters) | Dead ends |

Opening a unit from the list is the existing `unit.opened`, with no new event.

## 9. Server and app needs

| Need | Notes |
|---|---|
| `GET /units/query` | Takes a `UnitFilter` (status, q, types, makes, years, missing, checked_in from/to, person, retired) and returns `{ total, counts: {byStatus, byType, byMake, byYear, missing}, items, next }`. This is the Reports §7 endpoint, with counts added for the chips and the sheet. Sorts: newest, oldest in stock (`in_stock_since`), price up and down, make. Paged by `(sortKey, id)`. |
| Search | The short ID cleaned and matched exactly. VIN and serial matched by substring (4 or more characters). Text fields matched by prefix per word. It replaces today's `LIKE` across five columns with `LIMIT 500`. |
| Person filters | `checked_in_by` and `mine` refuse people without "See Reports", except `mine=true`, which uses the caller (Reports §2). |
| Names on cards (D-H4) | For people without "See Reports", items carry `checkedInByMe: true/false` instead of `createdBy`/`updatedBy`. The phone's stored copy keeps the same shape for them. |
| `in_stock_since` | Reports §10. Used by the oldest sort and "days in stock". |
| Phone copy | Holds the 500 newest units today. Offline it serves search, status and type. The banner says older units may be missing. |
| Status counts | One source for Home's count cards and these chips, so they always agree |

## 10. Decisions for the owner

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D-S1 | What Stock opens on from the nav | **In stock** (a Shop setting) | The usual question is "what do we have to sell". Incoming is one tap away and Home shows it already. |
| D-S2 | Where a search looks | **The chosen status, but the chips show where else it matched** | Searching everything mixes sold units in with stock. The counts on the chips make sure nothing is missed. |

## 11. Test contract

- **Chip counts equal Home's count cards** at the same moment.
- **Drill-down:** the report's `drill` filter gives the same `total` as its row (Reports §16).
- **Search:**
  - "3hzw8rtb", "ID 3HZW8RTB" and "3HZW8RTB" find the same unit.
  - Typing an O instead of a zero still matches.
  - 4 characters of a VIN match.
- **D-H4:** an Employee's `/units/query` response and stored copy contain no `createdBy` or `updatedBy` for others' units, and `checked_in_by` is refused for them.
- **Paging:** sorting by price with equal prices never repeats or skips a unit across pages.
- **Offline:** the list, search and status work from the copy; person filters are disabled; the banner shows.
