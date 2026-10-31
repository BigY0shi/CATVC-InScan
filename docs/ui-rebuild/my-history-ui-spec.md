# InScan: My history (spec)

- **Status:** revised after the Phase 4 review gate (`council-review-my-history.md`). Not locked.
- **Owner requirement (2026-10-31):** "Only the manager/admin role can run reports anyway. Regular shop members should just be able to see their own history."
- **Date:** 2026-10-31
- **Mockup:** `prototypes/my-history.html`, 6 states. Contact sheet: `my-history-states.png`.
- **Frame:** iPhone 17, 402 × 874 pt.
- **Builds on the locked screens:**
  - Reports (`reports-ui-spec.md`): top bar and big title, range row and date range sheet, flat tiles, flat bordered lists, section headings
  - Home: ID plate, status banner, notice card

---

## 1. What it is and who sees it

- **More → My history**, for everyone with "See my own history" (by default, every role).
- **It shows only the signed-in person's own work.**
  - The server route takes no person parameter; it uses the caller.
  - The response never contains another employee's ID or name (§7).
- **Managers and Admins open the same screen for one person** from Reports → Work by person:
  - The title is "Dale's work". The bar's Back reads "Back"; its accessible name is "Back to Work by person".
  - The route needs "See Reports". It accepts deactivated people, so their past work can still be looked up.
  - "Not sent yet" and "Didn't go through" never show here: they come from the viewer's own phone.
  - **This replaces the locked Reports §10 line** "opens Dale's work this week, with the three breakdowns as rows". That is owner decision **D-H3**. Until it is answered, the locked text stands.
- **Openness:** the footnote tells people who else can see their list. It is built from the roles that actually hold "See Reports".

## 2. Layout (`more/history`)

From top to bottom, with the body gap 24 and section gap 12, as in Reports:

1. **Top bar** (64): Back "More". The centred bar title appears once the big title scrolls under it. No Export.
2. **Title:** "My history" (`type-screen-title`).
3. **Range row:** the same as Reports. Default **This week**; same date range sheet.
4. **Freshness:** "Updated 9:38 am" (the hanging meta line).
5. **Phone status** (only for the signed-in person, from this phone's outbox):
   - **Status banner** (Home's component: 20 dp icon, a semibold title and a body, minimum 56):
     - Offline, amber: "Shop system offline" / "2 of your changes are saved on this phone. They'll send by themselves once it connects. You don't need to do them again."
     - Online and sending, ocean: "Sending 2 changes…"
   - **Notice card** (Home's component, rust icon) for refused changes:
     - Title: "1 of your changes didn't go through"
     - Body: the reason in words, for example "Already in stock: Ray moved it at 9:30 am."
     - A full-width raised **Review the change** button that opens Needs attention.
     - Refused changes never sit under "Not sent yet". They will not send by themselves.
   - **"Not sent yet"** (h2) and its rows (item 8), each with its time.
     - A queued tag scan reads "**Scanned the tag**", not "Moved to In stock", because the shop system may answer "Already in stock".
     - A queued edit shows its before and after, so the outbox stores the before value of each changed field.
   - One `type-meta` line under the section: "Used another phone? Changes waiting on it show up once it connects."
6. **Tiles** (two, flat, value and word; the word may wrap, per the locked tile rule):
   - **6 check-ins**, with "4 still Incoming". It points at what still needs scanning.
   - **4 moved to In stock**, with "By scanning the box tag". This is the same measure and name as the Work by person column.
   - There is **no week-on-week comparison** on this screen. The owner asked for a history, not a scorecard.
   - With 0 check-ins, only the first tile shows (locked Reports §4).
7. **"Also" line** (`type-meta`, `text-2`): "Also this week: 3 edits, 1 reprint." With this, the screen carries all three Work by person figures.
8. **Days**, newest first. Each day is a `section` with an **h2 in `type-section`** ("Today", "Yesterday", "Wed 28 Oct"; the accessible name is the full date) and one flat bordered list. There is no "What you did" heading: the title already says whose work it is.
9. **Event row:** padded 12/12/12/16, with one text edge. The grid areas are `"ic act time ch" ". nm nm ch" ". meta meta ch"`, row-gap 4.
   - **Line 1:** the action icon (20, `text-2`), the action (`type-label`, `text`) and the time on the right (`type-meta`, `text-2`, tabular).
   - **Line 2:** the unit name, full width (`type-card-title`, `text-wrap: balance`, clamped to 2 lines; no clamp at large text).
   - **Line 3:** the ID plate, then the note (`type-meta`, `text-2`, gap 8). For example "Tag printed 9:24 am", "$9,899 → $9,499" or "Reason: damaged".
   - **A chevron;** the row opens the unit.
   - **When the field has changed since** (the unit's current value no longer matches this person's "after"), the note adds "Changed since". It never says who changed it.
   - **A check-in and its first tag print are one row:** "Checked in", with the note "Tag printed 9:24 am", or "No tag yet" after Print later. Reprints keep their own rows.

   | Action | Icon | Words |
   |---|---|---|
   | Check-in (with first print) | `add_box` | Checked in |
   | Reprint | `print` | Reprinted the tag |
   | Tag confirm that moved the unit | `check_circle` | Moved to In stock |
   | Tag confirm on a unit already In stock (a reprint) | `qr_code_2` | Confirmed the new tag |
   | Queued tag scan (not sent yet) | `qr_code_2` | Scanned the tag |
   | Edit, including an overlapping edit | `edit` | Changed the price / colour / VIN / details |
   | Sale (interim) | `sell` | Marked sold |
   | Sold → Incoming | `undo` | Brought it back in |
   | Imports (legacy upload) | | Not shown, not counted |

10. **Show more:** after 30 rows, a `btn raised` at full width, 12 below the last list. It loads the next page and keeps the scroll position.
11. **Footnote** (`type-meta`, `text-2`), built from the roles that hold See Reports: "Managers and admins can see this list too." In the manager view it reads "Dale sees his own list under My history."

## 3. States

| State | What shows |
|---|---|
| A normal week | As in §2. Online, queued changes send within seconds, so "Not sent yet" shows mainly offline. |
| Offline | The amber banner, "Not sent yet" and its rows, and the another-phone line. Then a notice: "The rest of your history needs the shop system. Connect, then try again." with a raised Try again. With nothing queued, the notice reads "Your history needs the shop system." |
| A change didn't go through | The rust notice card with the reason and "Review the change". The refused change does not appear in "What you did". |
| A queued change is accepted | Its row moves into Today's list on the phone straight away. Page 1 is fetched again, and the live region says "Sent". There is no moment where it looks lost. |
| Nothing in the range | One tile ("0 check-ins") and a notice that follows the range ("Nothing this week yet." / "Nothing last week."). Then a raised **See last week** button, because on a Monday morning the question is usually about Friday. |
| Range change | The Reports rule: the old list stays at 50% and inert, and the live region says "Updating…", then "Showing last week". |
| Manager view | "Dale's work", Back, the range from the report, and the same tiles and "Also" line. No phone sections. |
| No permission | "Your history isn't turned on for your role. Ask an Admin." with Back. |

## 4. Accessibility

- **Row names put the change and time before the ID:** "Changed the price, $9,899 to $9,499, 2026 Polaris Sportsman 570, 2:14 pm, ID 5NVA9HQC." The button role already says it opens something. Rows not sent yet end with ", not sent yet".
- **Headings:** day headings are h2 with full dates. "Not sent yet" is h2.
- **Large text:** the time drops under the action, the name loses its clamp, and the plate and note wrap.
- **Focus order:**
  1. back
  2. range
  3. phone status: the notice button, then the "Not sent yet" rows
  4. tiles
  5. each day's rows
  6. Show more

  After the range sheet closes, focus returns to the range button.

## 5. Permissions and settings

| Action | Permission | Default |
|---|---|---|
| More → My history (own work only) | **See my own history** (new) | Employee, Management, Admin |
| One person's history from Work by person | See Reports | Management, Admin |

**Guardrail** (added to `roles-permissions-spec.md`): "See my own history" can't be removed from a role while another role can see that role's history through Reports. Watching people who can't see their own record is not allowed.

No new settings. The shop time zone and week start come from Reports.

## 6. Reporting

| Event | Props | Feeds |
|---|---|---|
| `history.opened` | `whose` (self, other) | Whether staff use it, and how often managers open a person's history |
| `history.range` | `preset` | Which ranges people need |

These are `app_events`, step 5 of the Reports first-release slice.

## 7. Server and app needs

| Need | Notes |
|---|---|
| **Attribution** | Every row in `audit_log` and `tags` must belong to the person who did the work. **Prerequisite:** Scan spec §5 (an outbox entry is sent under the person who made it, or held), and Reports §14 (`occurredAt`). Outbox rows from before that migration, which have no `employeeId`, are held and shown to nobody. |
| **Imports are their own action** | The legacy upload writes `unit.import` and status changes marked as imports. They are left out of history and of Work by person, and today they would land under whoever ran the upload. |
| **One classifier** | One server function turns `audit_log` (or `unit_status_changes`, Reports §14) into events, using the §2 action table. It folds the duplicates: a confirm writes `tag.confirm` and `unit.status:IN_STOCK`, which is one event. "Moved to In stock" counts only when a tag confirm moved the status. **Work by person uses the same function.** |
| **Typed changes, never raw JSON** | `changes: [{field, from, to}]` from a whitelist: price, colour, VIN, year, make, model, type. `before_json` holds other people's IDs (`printedBy`, `createdBy`, `updatedBy`, `vinFrameVerifiedBy`) and notes; it never leaves the server. |
| `GET /me/history?preset=&asOf=&cursor=` | The caller only. Ordered by `(occurredAt DESC, eventId DESC)`. Page 1 returns `asOf`, and later pages send it back, so late arrivals don't land behind the cursor. Includes `changedSince` per edit. |
| `GET /people/{employeeId}/history` | The same response. Needs "See Reports". Accepts deactivated people. |
| **Tag print times** | `TagPrinted` gets `occurredAt` and the same clamp as other writes. Today `printed_at` is phone text, unvalidated. Added to Reports §14. |
| **App** | The outbox stores `employeeId` and the before values of edited fields. On drain, the accepted row moves locally and page 1 is fetched again. My history lives on the More stack, which the Reports wipe resets. |

## 8. Decisions for the owner

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D-H1 | Tell people who else can see their list | **Yes,** in the footnote | It's true, and staff who find out later trust the app less. |
| D-H2 | Show their own sign-ins and failed PINs | **Not now.** Work only. | Sign-in events are security data (Admin, D-R2). A bare "your PIN was tried 5 times" alarms people without telling them what to do. |
| D-H3 | The Work by person row opens "Dale's work" (this screen) instead of three breakdown rows | **Yes. This amends the locked Reports §10 line.** | The manager sees the same figures (check-ins, moved to In stock, and the "Also" line with edits) plus what was actually done. That is the answer to "what did Dale do this week?". |
| D-H4 | Employees can still see who checked in or edited each unit, through the unit lists the app already loads (`/units` returns `createdBy`/`updatedBy` to anyone) | **Keep names on a single unit's page; drop them from bulk lists and the phone's stored copy for people without "See Reports".** | You need to know who to ask about one unit. You don't need to let anyone rebuild everyone's work counts. Today any signed-in phone can. |

## 9. Test contract

- **Own work only:**
  - `/me/history` ignores any person parameter.
  - `/people/{id}/history` refuses an Employee.
  - **No other employee's ID or name appears anywhere** in a `/me/history` response, including inside changes.
- **The manager view agrees with Work by person:** check-ins, moved to In stock and edits match the Dale row for the same range, all three from the same classifier.
- **One confirm is one event,** not three. A confirm on a unit already In stock reads "Confirmed the new tag" and doesn't count as a move.
- **Imports** appear in no one's history and in no count.
- **Paging:** two events with equal `occurredAt` both appear exactly once across pages. An event arriving between page 1 and page 2 doesn't shift page 2. 31 events give 30 rows and Show more.
- **Phone status:**
  - "Not sent yet" lists only the signed-in person's outbox entries. Pre-migration rows show to nobody.
  - Refused entries show in the notice card, never under "Not sent yet".
  - When an entry is accepted, it appears in Today exactly once, with no gap.
- **The guardrail:** removing "See my own history" from Employee is refused while Management holds "See Reports".
