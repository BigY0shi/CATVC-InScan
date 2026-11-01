# My history: Phase 4 review gate

- **Date:** 2026-10-31
- **Reviewed:** `my-history-ui-spec.md`, `prototypes/my-history.html`, against the locked Reports and Home screens and the server and app code.

## How it was reviewed

- **Three council seats**, each starting fresh:
  - **Skeptic:** attribution, privacy and data correctness
  - **Advocate:** questions staff will ask, wording, accessibility
  - **Artisan:** fit with the locked system
- **Why not five seats:** this is a single screen built from locked Reports parts. Pathfinder's and Craftsman's Reports findings (navigation, the wipe, paging, the classifier pattern) apply here unchanged and are written into spec §7.
- **Measured in Chromium on all 6 states:** contrast, tap targets, accessible names, focus order and console errors.
- **Anti-slop,** run by hand.

## Results after fixes (re-measured)

| Check | Result |
|---|---|
| Contrast | 257 text pairs pass AA; the lowest is 5.70:1 |
| Tap targets | All 68 are at least 48 |
| Accessible names | None missing. Row names lead with the change and time; day headings carry full dates. |
| Focus order | Matches the screen. The script's "up" flags come from the list scrolling as focus moves. |
| Console | No errors |
| Anti-slop | Clean. The banner and notice card reuse Home's components. No decorative pills; the plate is the locked ID plate. |
| Bug found while re-shooting | The offline banner was squeezed under the next heading: its `min-height` let the scrolling column shrink it. Fixed with `.body > * { flex-shrink: 0 }`. |

## What the review changed most

- **Attribution.** Today the server credits whoever *sends* a queued change, not whoever made it, and a legacy upload would put dozens of check-ins and sales under the uploader. Scan spec §5 (send under the original person, or hold) and a separate import action are now hard prerequisites.
- **One classifier.** A tag confirm writes three rows today. History and Work by person now share one server function and one action table, so their counts agree by construction.
- **No other names leak.** The server returns typed field changes from a whitelist, never raw before/after JSON (which holds other people's IDs and notes). It is tested.
- **The phone section tells the truth.**
  - Refused changes get Home's notice card ("didn't go through"), not "will send".
  - A queued tag scan reads "Scanned the tag" until it is accepted.
  - An accepted change moves into Today with no gap.

## Fixed

| Seat | Finding | Fix |
|---|---|---|
| Skeptic | Queued and imported work credited to the sender or uploader | Scan §5 is a prerequisite; imports are their own action, excluded; rows from before the migration are held and shown to nobody |
| Skeptic | Confirm writes 3 rows; non-scan In stock moves; re-confirms; overlapping edits missed | One classifier with the §2 action table, shared with Work by person; tested |
| Skeptic | Raw before/after JSON would leak other people's IDs and notes | Typed `changes[{field, from, to}]` from a whitelist; test that no other ID appears |
| Skeptic, Advocate | Manager view replaces a locked Reports line; Edits figure missing | Raised as D-H3; until answered, the locked text stands. The "Also this week: 3 edits, 1 reprint." line carries all three Work by person figures; tests cover all three |
| Advocate, Skeptic | Refused changes labelled "will send" | Home's rust notice card with the reason and "Review the change"; never under "Not sent yet" |
| Advocate | Changes on another phone are invisible | A line under "Not sent yet": "Used another phone? Changes waiting on it show up once it connects." |
| Advocate, Skeptic | An accepted change briefly disappears | It moves into Today on the phone at once, page 1 is fetched again, and the live region says "Sent"; tested |
| Skeptic | Queued edits have no before value; queued confirm labelled "Moved" too early | The outbox stores before values; a queued scan reads "Scanned the tag" |
| Skeptic | Paging has no tie-breaker; late arrivals; open-ended range | Ordered by `(occurredAt DESC, eventId DESC)`, with `asOf` pinned from page 1; tests for ties and arrivals between pages |
| Skeptic | Tag print times unvalidated and missing from the clamp | `TagPrinted` added to Reports §14's `occurredAt` list (not part of that lock) |
| Advocate, Artisan | The ID plate squeezed the name into about 174 pt, leaving one word on a line | Name on its own full-width line (balanced, 2-line clamp, no clamp at large text); plate and note on line 3 |
| Advocate | Check-in and first print doubled every row | One "Checked in" row with "Tag printed 9:24 am" or "No tag yet"; reprints keep their own rows |
| Artisan, Advocate | An empty "What you did" section; day labels quieter than the rows | Removed. Each day is an h2 in `type-section` with its list, as in Reports. "Yesterday" used; full dates as accessible names |
| Artisan | Banner didn't match Home's status banner | Rebuilt: 20 icon, semibold title and body, amber when offline, ocean when sending |
| Artisan, Advocate | "On this phone" replaced the time on each row | The time stays; the banner and heading carry "not sent"; the accessible name ends "not sent yet" |
| Advocate | Offline copy unclear about redoing; wrong copy when nothing is queued | "They'll send by themselves once it connects. You don't need to do them again." / "Your history needs the shop system." |
| Advocate | Empty state: two zero tiles, copy fixed to "this week", nothing to do on a Monday | One tile (locked rule); copy follows the range; a raised "See last week" button |
| Advocate | Week-on-week comparison reads like a scorecard; "confirm" is jargon | Comparison dropped here. Tile 1 context: "4 still Incoming". Tile 2 context: "By scanning the box tag" |
| Advocate, Skeptic | Footnotes untrue or pointing employees at a place they can't open | Built from the roles holding See Reports: "Managers and admins can see this list too." Manager view: "Dale sees his own list under My history." |
| Skeptic | Someone could be watched without seeing their own record | Guardrail 7 in the roles spec, with a test |
| Skeptic | Deactivated people's history unreachable | `/people/{id}/history` accepts deactivated IDs |
| Skeptic | The claim that Admins can see history views under Shop system had nothing behind it | Removed; the events are `app_events` (Reports slice step 5) |
| Skeptic | The mockup showed 3 of 6 check-ins and Show more too early | The whole week is drawn (14 rows, matching the Reports figures); Show more only after 30 |
| Advocate | Did my price change stick? | "Changed since" on an edit whose value has changed again, without saying who |
| Artisan | Show more was a one-off button | `btn raised`, full width, 12 below the last list |
| Advocate | Screen-reader row names long and backwards | Change and time first; "Opens the unit" dropped |

## Rejected

| Seat | Finding | Why not |
|---|---|---|
| Artisan | Rename tile 2 to "4 confirmed" so the word fits | "Confirmed" is app jargon (Advocate), and "Moved to In stock" is the locked name for this figure in Work by person. The locked tile rule lets the word wrap. |
| Advocate | Search and filtering by action | Not now. 15 to 60 events a week scan fine, and the unit page answers "did it stick". |

## For the owner

- **D-H1:** tell people who can see their list. Recommendation: yes.
- **D-H2:** show their own sign-ins and failed PINs. Recommendation: not now.
- **D-H3:** Work by person opens "Dale's work". This amends the locked Reports §10. Recommendation: yes.
- **D-H4:** take names out of the bulk unit lists for people without See Reports. Recommendation: yes.

Details are in `my-history-ui-spec.md` §8.
