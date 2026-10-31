# InScan: My history (spec)

- **Status:** Phase 1 draft, not locked.
- **Owner requirement (2026-10-31):** "Only the manager/admin role can run reports anyway. Regular shop members should just be able to see their own history."
- **Date:** 2026-10-31
- **Mockup:** `prototypes/my-history.html`, with contact sheet `my-history-states.png`.
- **Frame:** iPhone 17, 402 × 874 pt.
- **Builds on the locked Reports screens** (`reports-ui-spec.md`):
  - the same top bar and large title
  - the same range row and date range sheet
  - the same flat tiles and flat bordered lists
  - Home's record cards and status tags
  - nothing new to learn

---

## 1. What it is and who sees it

- **More → My history**, for everyone with "See my own history". By default that is every role.
- **It shows only the signed-in person's own work.** Other people's names never appear. The server takes no person parameter on this route; it uses the caller.
- **Managers and Admins open the same screen for one person** from Reports → Work by person (the row's drill target, locked in `reports-ui-spec.md` §10):
  - The title is "Dale's work". Back reads "**Back**", because "Work by person" is too long for the bar; its accessible name is "Back to Work by person".
  - That route needs "See Reports".
  - The "Not sent yet" section never shows here, because it comes from the viewer's own phone.
- **Openness:** the footnote tells employees who else can see their history. Hidden monitoring breeds distrust; a plain line doesn't.

## 2. Layout (`more/history`)

From top to bottom:

1. **Top bar** (64):
   - Back: "More".
   - The centred bar title appears once the big title scrolls under it (Reports §4).
   - No Export. An employee's own list isn't data to take out of the app, and managers already export Work by person.
2. **Title:** "My history" (`type-screen-title`).
3. **Range row:** steppers and the range button, the same as Reports. Default: **This week**. Same date range sheet.
4. **Freshness:** "Updated 9:38 am".
5. **Not sent yet** (only when this phone holds queued changes by this person):
   - A `warn-tint` banner: "2 of your changes are still on this phone. They send when it reaches the shop system."
   - Under it, those changes as event rows (item 7), each marked "On this phone".
   - It comes from the phone's outbox (entries carry `employeeId`, Scan spec §5), so it works offline.
   - It disappears as entries are accepted.
6. **Tiles** (two, flat, value + word):
   - **6 check-ins**, with "Same days last week: 5"
   - **4 moved to In stock**, with "Tags you scanned to confirm"
7. **"What you did",** grouped by shop day, newest first. Each day has a heading ("Today", "Thu 29 Oct" in `type-label`, `text-2`) and one flat bordered list. Each **event row** is 72 tall at least, padded 12/12/12/16:
   - **Line 1:** an action icon (20, `text-2`) and the action (`type-label`, `text`), with the time right-aligned (`type-meta`, `text-2`).
   - **Line 2:** the unit's ID plate (`uid`) and the unit name (`type-card-title`). The name wraps to 2 lines rather than being cut off, because the model is the part people read.
   - **Line 3, only for edits and reprints:** what changed or why, in `type-meta`, `text-2`. For example "Price $9,899 → $9,499" or "Reason: damaged".
   - **A chevron.** The row opens the unit.

   | Action | Icon | Words |
   |---|---|---|
   | Check-in | `add_box` | Checked in |
   | Tag printed | `print` | Printed the tag / Reprinted the tag |
   | Confirm | `check_circle` | Moved to In stock |
   | Edit | `edit` | Changed the price / Changed details |
   | Sale (interim) | `sell` | Marked sold |
   | Trade-in | `undo` | Brought it back in |

   - After 30 events: **Show more** (a raised button, full width, 56). It loads the next page and keeps the scroll position.
8. **Footnote** (`type-meta`, `text-2`): "You can see this list, and so can the shop's managers and admins (Reports → Work by person)."

## 3. States

| State | What shows |
|---|---|
| A normal week | As in §2. Online, queued changes send within seconds, so "Not sent yet" normally shows only when the phone is offline. |
| Nothing in the range | Tiles at 0, with "Same days last week: 5". Then "Nothing this week yet. Check-ins, tags and moves you make show up here." |
| Offline | The "Not sent yet" section, if any, stays: it comes from the phone. Below it: "The rest of your history needs the shop system. Connect, then try again." with a raised Try again. |
| First load, or a range change | The Reports rules: no skeleton. The old list stays at 50% and inert, and the live region says "Updating…" and then "Showing last week". |
| Manager view | "Dale's work": title, Back (to Work by person), the range that came from the report, no "Not sent yet". Footnote: "Dale sees this same list under My history." |
| No permission | "Your history isn't turned on for your role. Ask an Admin." with Back. |

## 4. Accessibility

- **Each event row has one name:** action, unit, time and change. For example "Changed the price, 2026 Polaris Sportsman 570, ID 5NVA9HQC, 2:14 pm, $9,899 to $9,499. Opens the unit."
- **Day headings** are real headings, so screen-reader users can jump by day.
- **Large text:** the time drops under the action line, and the unit name wraps to 2 lines.
- **Focus order:**
  1. back
  2. range
  3. the Not sent banner and its rows
  4. tiles
  5. each day's rows
  6. Show more

  Focus goes back to the range button after the sheet closes.

## 5. Permissions and settings

| Action | Permission | Default |
|---|---|---|
| See More → My history (own work only) | **See my own history** (new) | Employee, Management, Admin |
| See one person's history from Work by person | See Reports | Management, Admin |

No new settings. It uses the shop time zone and the week start from the Reports settings.

## 6. Reporting

| Event | Props | Feeds |
|---|---|---|
| `history.opened` | `whose` (self, other) | Whether staff use it; how often managers open a person's history |
| `history.range` | `preset` | Which ranges people need |

A manager opening a named person's history is recorded as `history.opened` with `whose: other` and the `entity_id` of the person. Admins can see that under Shop system if it ever matters.

## 7. Server and app needs

| Need | Notes |
|---|---|
| `GET /me/history?preset=&cursor=` | The caller's own events only. It never accepts a person. Sources: `audit_log` where `actor` is the caller (create, update, status changes), and `tags` where `printed_by` or `confirmed_by` is the caller. Paged by `occurredAt`, newest first. It uses the Reports prerequisites (`occurredAt`, ShopCalendar, fixed-width timestamps). |
| `GET /people/{employeeId}/history?preset=&cursor=` | The same response, for managers. Needs "See Reports". |
| Edit rows say what changed | Built from `before_json`/`after_json` for the fields people know (price, colour, VIN, year, make, model). Other fields read "Changed details". |
| Outbox entries carry `employeeId` | Already required (Scan spec §5). Used for "Not sent yet". |
| The wipe | My history lives on the More stack, which is reset when the signed-in person changes (Reports §9). |

## 8. Decisions for the owner

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D-H1 | Should the footnote say managers can see your work? | **Yes.** | It's true, and staff who find out later trust the app less. |
| D-H2 | Include your own sign-ins and failed PINs on your name? | **Not now.** Work only. | Sign-in events are security data (Admin only, D-R2). Showing "your PIN was tried wrong 5 times" would alarm people without telling them what to do. |

## 9. Test contract

- **An Employee sees only their own events.** `/me/history` ignores any person parameter, and `/people/{id}/history` refuses an Employee.
- **A manager's view of Dale matches** the Dale row in Work by person: the counts agree for the same range.
- **Not sent yet** lists exactly the signed-in person's outbox entries. Another person's queued entries on the same phone never show.
- **Paging:** 31 events give 30 rows and Show more. After loading the next page, no row appears twice.
- **A check-in made offline** shows under "Not sent yet" until it is accepted. Then it shows once in "What you did", on the day it was made.
