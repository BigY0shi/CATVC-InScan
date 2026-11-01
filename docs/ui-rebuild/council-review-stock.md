# Stock list: Phase 4 review gate

- **Date:** 2026-10-31
- **Reviewed:** `stock-list-ui-spec.md` and `prototypes/stock.html`, against the locked Home, Reports and My history screens, and against the server and app code.
- **How it was reviewed:**
  - **Five `agentic-council` seats:**
    - Skeptic
    - Advocate (rendered in Playwright)
    - Artisan
    - Pathfinder
    - Craftsman, who ran sqlite-jdbc 3.53.4.0 to time the queries and check FTS5 and paging
  - **Measured checks** in Chromium on all 8 states.
  - **Anti-slop,** run by hand.

## Results after fixes (re-measured)

| Check | Result |
|---|---|
| Contrast | 503 of 503 text pairs pass AA. The lowest is 5.70:1. |
| Tap targets | 168 of 168 are at least 48. |
| Accessible names | None missing. Cards use Home's label builder: name first, tag spelled out, full day. |
| Focus order | Matches the screen. Sheets make the screen behind them inert. Focus returns to Sort or Filter. |
| Console | No errors. |
| Anti-slop | Clean. Chips are pills because they're tappable. No blur. Gap words use an icon and plain text, not amber text. |

## What the review changed most

- **The phone's stored copy was wrong in two ways:**
  - It held the 500 newest units of every status and never removed any. Old stock dropped out first, and a unit sold on another phone could stay "In stock" here for good.
  - It held names, or a "mine" flag fetched by whoever last synced. On a shared phone, that leaks or lies.

  The copy now holds every unsold unit plus the newest sold ones, in one shape with no person fields. "Mine" moves to a separate table that the wipe clears.
- **One count source.** Home's count cards and these chips now read the same server `statusCounts`.
- **Search had real bugs:**
  - "ID 3HZW8RTB" failed outright.
  - Eight-letter words like "KAWASAKI" are valid tag IDs, so a search that checks for a tag first would return nothing for them.
  - `%` and `_` weren't escaped.

  One matcher in `core` now serves the server, the phone and Home's Find, with every word required to match.
- **Incoming opens oldest first,** in Home's order. It's the scan queue. Cards say how long each unit has waited.

## Fixed

| Seat | Finding | Fix |
|---|---|---|
| Skeptic, Craftsman, Pathfinder | The copy leaks names; "by you" is wrong on shared phones; `ShopSync` writes display names into `updatedBy` | One person-free shape for everyone; `mine(employeeId, unitId)` cleared by the wipe; managers see names online only; the `ShopSync` write removed |
| Skeptic, Craftsman, Pathfinder, Advocate | 500-newest copy, never pruned; counts drift from Home | The copy holds every unsold unit plus the newest sold ones (a setting, D-S3), pruned unless the outbox holds changes; one `statusCounts` source; the Room migration adds the needed fields |
| Craftsman | Bulk endpoints return `createdBy`, `updatedBy` and `vinFrameVerifiedBy` | A separate `UnitListItem` DTO for every bulk endpoint; names in a `people` map only for See Reports; serialization test |
| Skeptic, Craftsman | Filters the server can't answer yet | Listed in §8 with their prerequisites; shown greyed with "Not tracked yet" until then |
| Skeptic, Craftsman, Pathfinder | Search: the "ID" prefix, 8-letter words, unescaped `LIKE`, no rule for multiple words, O vs 0 in serials, Gboard autocorrect | One `core` matcher (§8); every word must match; punctuation-insensitive makes; VIN from 6 characters or the last 4 to 8; escaping; `matchedBy`; autocorrect off |
| Craftsman | Facet counts undefined; cost unknown | Each group's counts ignore its own choices; counts on page 1 only; measured at about 3 ms for 5,000 units, so no cache and no FTS |
| Skeptic, Craftsman | Paging: empty prices, make case, edits between pages, drifting totals | Empty values last, makes cleaned and compared ignoring case, the `id` tiebreak, `asOf` pinned, the filter hash in the cursor (400 on mismatch), and tests including a sabotage test |
| Craftsman, Skeptic | `status` + `retired` contradict each other; retired units unfindable | `statuses` set; "Show retired" adds a Retired chip |
| Pathfinder | Cancelling a call would flash offline; blocking `execute()` | `enqueue` + `invokeOnCancellation`; cancelled calls never count as offline; `debounce` + `flatMapLatest` |
| Pathfinder | No NavHost, so Home's status choice gets lost | Typed `Stock(status)` route; count cards navigate with `restoreState = false`; one stateless `StockList` for the tab and the drill-down |
| Pathfinder | Paging 3 can't carry counts beside the rows | Hand-written `StateFlow` with items, counts and total; keys are unit IDs |
| Pathfinder | Keyboard covers results; filter sheet gestures lose changes | `adjustResize` with inset padding; only Find stays pinned; the sheet can't be swiped away; Back discards changes, the same as Cancel |
| Pathfinder | Tablet layout | One grid component for 1 or 2 columns; `material3-adaptive` dependency |
| Advocate | Can't confirm "the blue RZR" from the list | Colour on every card; colour searchable |
| Advocate | Incoming in the wrong order and words | Oldest first; "waiting 3 days" on each card |
| Advocate | Offline "Nothing matches" can be wrong | The copy holds every unsold unit; the offline no-match wording says it's the phone's copy and when it's from |
| Advocate, Artisan | "No price" took 4 steps; sort buried; sheet too long | Sort is its own chip and sheet; Missing details comes first; Checked in is chips, not a radio list |
| Advocate | "All" included Sold | All = not sold, with its count (45) |
| Advocate, Skeptic | No-match flow inconsistent ("Show both"); filters vanished | One status: "Show Incoming". Several: "Show 2 matches". Sold mentioned separately. Results line and filters stay visible |
| Advocate | Card names read differently from Home | Home's label builder |
| Advocate | Live region replaced and chatty; "Filter dot 2" | One live region that stays; announced about 1 s after typing; named "Filter, 2 applied" |
| Advocate | Focus after chip removal, Clear and Show not specified | Specified in §5 |
| Advocate, Artisan | Grey meant both "zero" and "unavailable" | Zero shows normally with "0"; grey only for unavailable, with the reason |
| Advocate | "Close" threw changes away silently | Named **Cancel**; swipe-down off |
| Advocate | Wording | "checked in by you on Tue"; parts in lower case mid-line; no breaks inside a part; "Only units checked in by you" |
| Artisan | No signed-in chip in the top bar | Home's `topbar.user` chip; padding 0 12 0 16 |
| Artisan | Cards were the compact Scan card, not Home's | Home's full card: 128 × 96 image, padding 16/8/16/16, balance and pretty wrapping, 12 apart |
| Artisan | Spacing: body gap 16 | 24, built from two 12-gap stacks |
| Artisan | No press states on chips or option rows; no sheet entrance | Pressed layer and 0.985 scale; option rows have a press layer; 200 ms `up` entrance |
| Artisan | Search field not Home's | Home's `.find` exactly: Clear text button, capitals, help line |
| Artisan | Filter chip not built like Find | Sort and Filter both use the Find build |
| Artisan | Sheet chips looked sunk in; weak group labels; two identical header buttons | `raised-fill` chips; `type-section` labels; title in `type-app-title`; Clear all moved to the footer beside the primary |
| Artisan | A one-off switch control | Toggle chips |
| Artisan | Amber gap words next to amber Incoming | Warning icon in `warn-ink`, word in `text` at weight 500 |
| Artisan | Applied chips looked unselected | Selected style, with ✕ |
| Artisan | Sheet covered the title | 748 tall |
| Skeptic | Mockup data disagreed (Polaris 9 against 1; In stock order against Reports and My history) | Search shows 9 Polaris in In stock; In stock sorts newest in stock first, matching the four units Dale moved this week |
| Skeptic | Results reorder under the thumb when the server answers | The server answer replaces the copy only before the first touch; after that, cards update in place; opening a unit fetches it fresh |

## Rejected

| Seat | Finding | Why not |
|---|---|---|
| Skeptic | Audit and rate-limit unit opens to stop someone scraping work counts | Staff need to open units all day. Bulk lists no longer carry names, so rebuilding counts would mean opening every unit with your own PIN, and every open is attributable. The cost to a five-person shop outweighs the risk. Revisit if the shop grows. |
| Craftsman | FTS5 for search | It is available in sqlite-jdbc, but the phone's SQLite has no FTS5, so offline results would differ. Measured `LIKE` is fast enough. |
| Advocate | Change the help line to add colour | It is Home's locked copy. Colour still matches. |

## Deferred

| Item | When |
|---|---|
| The locked Reports mockup draws the compact card in its drill-down. Reports §7 says "Home's record cards" | The build uses Home's card everywhere. The mockup stays as locked. |
| Large-text and 360 dp screenshots | Compose screenshot tests (`roborazzi` is already a dependency) |

## For the owner

- D-S1 to D-S3 in the spec (§10).
- The open D-H4 question about Home's record-card copy.
