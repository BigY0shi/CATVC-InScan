# InScan UI rebuild: design brief (Phase 0 + 1)

**Scope:** the landing screen (Home) and the navigation router.
**Status:** draft for Yoshi's review. Nothing in this brief is locked unless it is marked LOCKED.
**Date:** 2026-10-29
**Pairs with:** `landing-nav-ui-prompt.md` (the build prompt) and `design-skill-pipeline.md` (which skill runs at which stage).

## Skills used

| Skill | What it was used for |
|---|---|
| `mobbin:search` | 7 searches through the Mobbin connector. Mobbin has no Android screens, so **all references are iOS** and are used for structure only. |
| `frontend-design` | Design plan, a check against generic AI-design defaults, and copy rules |
| aura `design-first-ui-prompting` | Tightened the prompt: goal, a constraints card, a negative prompt, and one-variable variants |

| `vectorlab-ux-skills:anti-slop` | Pre-check of the prompt spec (section 3b). It runs again on the mockups in Phase 4. |

**Not used:**
- SkillDB router (required by handoff §5.1): not available in this session. It still needs running on the Desktop PC.
- UXKIN: **replaced** by `vectorlab-ux-skills:anti-slop` and `/slopmonster`, on Yoshi's call (2026-10-29).
- `/slopmonster`: **not found.** It isn't a skill in this session, and a search of the linked PC's Claude Code folder found nothing by that name. The fallback is aura `audit-ai-design-slop`.

---

## 1. References: what was looked at and what was taken

All of these are iOS screens on Mobbin, used for reference only and never copied.

### Home and records

| Screen | What it shows | What we take, or why we reject it |
|---|---|---|
| [Jobber, search/home](https://mobbin.com/screens/46b551e5-d5c6-4efc-8207-c57159c28652) | Field-service app. Search on top, record rows with the status word on the right, 5 labelled tabs ending in More. | **Take:** the record row (name, then meta, with status on the right) and the More tab. **Reject:** status shown only as a coloured square. InScan pairs icon, word and colour. |
| [alias, Inventory (dark)](https://mobbin.com/screens/98106f05-accf-462b-a309-4e6f65ac1aeb) | Status rows with counts and chevrons | **Take:** count rows. This became the large-text fallback for Stock at a glance. **Reject:** small-caps section labels and low-contrast tab labels. |
| [Shopify, Inventory](https://mobbin.com/screens/e817f82a-6ffa-4818-b9b3-d63d9fddf8f7) | "Incoming" as a filter chip, scan icon in the header | **Take:** Incoming as a first-class filter on the Stock tab (a later screen). **Reject:** a "Changes saved" toast sitting over the nav. |
| [Rivian, vehicles](https://mobbin.com/screens/a3632e93-89ce-4a83-8c09-a8da53cad691) | Vehicle cards with a VIN line | **Take:** the vehicle name is the card's main line, and identifiers come second. |
| [Jira Cloud, summary](https://mobbin.com/screens/733f90a7-6308-467f-b24e-f0b1a38dd205) | Count cards: icon, number and word in the status colour | **Take:** every count carries its icon and word, matching the chip rule. **Reject:** the donut chart. |
| [Posh, analytics](https://mobbin.com/screens/08c0cea7-d68a-446e-9f13-352f8e78ca05) | A count with one explanatory line | **Take:** the one-line hint ("Scan box tags to confirm"). |

### Center action in the nav

| Screen | What it shows | What we take, or why we reject it |
|---|---|---|
| [Shell Recharge, home](https://mobbin.com/screens/ae77d76c-c4a6-4d6f-813c-de02992c5ecc) | Raised center circle with its label underneath, 5 slots ending in More | **Closest match to the locked nav.** It confirms the labelled, raised center action. |
| [MacroFactor](https://mobbin.com/screens/215233aa-292a-42c2-aa5a-d8740ade221c) | Center "+" with no label | **Reject** the unlabelled center button. Our Scan keeps its label. |
| [WHOOP, home](https://mobbin.com/screens/e01e8d3e-4ea6-4e54-ac99-04e325b6dd92) | The action is a separate circle floating right of the tab bar | **Reject:** it favours the right thumb and floats over content. |
| [Lovi, today](https://mobbin.com/screens/3ed8cfcc-59ab-4569-b805-e210634a7449) | "New Scan" as an ordinary center tab, not raised | Shows the alternative. A raised button wins for "one obvious button". |

### Scan overlay

Not in this prompt. Collected now for the Scan screen prompt.

| Screen | What we take |
|---|---|
| [PayPal](https://mobbin.com/screens/72955d2a-aa8d-45b1-938b-b9bbc623f05a), [Solflare](https://mobbin.com/screens/fcb01559-a002-4b6d-863d-74ab70035da3) | Corner brackets, one plain instruction, and a big Cancel at the bottom within thumb reach |
| [ZARA scanner](https://mobbin.com/screens/bdc50966-0bac-486a-ab63-9ef090e30e7e) | A Camera / Keyboard switch, so you can type the code when the camera can't read it. This matches InScan's existing type-it fallback. |
| [Cal AI scanner](https://mobbin.com/screens/eadf867f-ca9d-426b-97bd-7aaaaf48528f) | A torch toggle, for dim shop bays |
| [Thrive Market](https://mobbin.com/screens/acc1a07e-59d4-466f-b103-738371fad1c2) | A plain guidance line ("Hold phone steady…") |

### Offline state

| Screen | What we take, or why we reject it |
|---|---|
| [Alta, help chat](https://mobbin.com/screens/2b00c4b9-d2a6-4a6a-9d95-2aba49348be1) | **Take:** the inline banner, with the state as a title and the consequence underneath. This now drives the amber banner copy. |
| [Qantas, trips](https://mobbin.com/screens/18f77caf-19d5-4199-98b4-a36f34096d23) | **Reject:** a network error shown as a snackbar over the tab bar. The prompt bans snackbars for problems. |

### Shared phone and PIN

These are for the "who's using this phone" gate.

| Screen | What we take |
|---|---|
| [Kit, PIN](https://mobbin.com/screens/f2f0a7d0-581a-4305-826d-5e6835eba979) | Greeting by name, PIN dots, a big round keypad, and a "Not Alex? Switch profile" link. For InScan that link reads **"Not Dale? Switch user"**. |
| [HBO Max, profile PIN](https://mobbin.com/screens/dda54356-a3d5-4f07-9d6e-6491339c0b32) | Avatar plus 4 boxes plus a full-width keypad |

---

## 1b. Yoshi's picks (2026-10-29), and how each one is applied

| Pick | Where it goes | How it's applied |
|---|---|---|
| **Rivian:** pictures of the units | Home record cards (in the prompt) | A **unit-type image** on each card: one image per type (ATV, Side-by-side, Dirt bike, Snowmobile, Jet ski, Other), from the `UnitType` list in `core/.../Inventory.kt`. Per-unit photos would need new data: there is no photo or image field in `core` or `server` (checked). Images are PNGs supplied by Yoshi. No manufacturer photos or logos. Mockups use labelled placeholders, never drawn vehicles. |
| **Oura:** important notices | Home status zone (in the prompt) | A **notice card**: title, one or two lines, and a full-width button. A refused change shows as this card with "Review the change", and it can't be closed until it's fixed. The offline banner stays a slim banner, because the app keeps working offline. Shop notices from the owner (closeable, optional image) are Reserved: no such feature exists yet. |
| **Cal AI:** scanner with a big cancel bar | Scan screen (its own prompt, later) | A full-width, prominent Cancel bar at the bottom, bigger than ZARA's; a torch toggle; a Camera / Type it switch. |
| **Qantas + CHOPT:** offline screen with a background image | Gate (`unreachable` in the router) | **Conflict, resolved like this:** the handoff locks InScan as offline-first (§2: phones create records offline and sync later). A full-screen offline page on Home would stop check-ins out in the yard. So the full-screen version is used **only where the shop system is required to continue**: connecting the phone, signing in, and later finishing a sale (online only). Everywhere else, the amber banner shows and work continues. Yoshi's background image goes under a 75% scrim. It's a photo or texture, not a mascot like CHOPT's tomato (anti-slop rule S10). |
| **Disney + a big keypad:** PIN screen | Gate, `who` (its own prompt, later) | Initials avatar, then the PIN field, then **"Not Dale? Switch user"**, then an in-app keypad that fills from the bottom up to just under that link. Because it's an in-app keypad, not the system keyboard, the link is never covered. **Decided (Yoshi, 2026-10-29): PINs are exactly 4 digits.** That gives four Disney-style boxes, and sign-in fires on the 4th digit with no Sign in key. This **needs a server change**: `Auth.kt` line 32 currently allows `pin.length in 4..8`. Any employee already set up with a longer PIN has to be reset. The lockout copy comes from the server rule: "2 tries left before a 5-minute lock". |
| **Jira Cloud + Binance:** density target for Home | Home (in the prompt) | **Stock at a glance** becomes a 2 × 2 grid of count cards. Each has a tinted icon badge, the count in the status colour, a label, and one context line. Posh and alias are rejected as under-designed. Jira's donut chart is left out because the counts already say it. **Data note:** "Sold this week" has no server query yet. It's countable from the audit log, but not built. If it's dropped, the grid becomes 3 cards. |
| **Nav: icons instead of words?** | Bottom nav (in the prompt) | **Kept the words.** The prompt already bans icon-only buttons, and the people using this aren't tech people. Icons alone make them guess at four of the five tabs. The overflow problem only shows up at the very largest text sizes, so instead nav labels **grow up to 18 sp and then stop**. At 18 sp, "Tickets" fits its fifth of the bar. |

---

## 2. Design plan

Written to the `frontend-design` format: color, type, layout, principles.

**Subject:** a parts-and-units counter at a rural powersports shop. **Audience:** mechanics who also sell; gloves, reading glasses, one hand. **Primary job of Home:** get to Scan, or to what's waiting, in one tap.

### Color (LOCKED)

| Name | Hex | Use |
|---|---|---|
| Charcoal | `#1E1F21` | background |
| Bench | `#28292C` | bars and cards |
| Raised | `#323337` | panels |
| Bone | `#F2F3F4` | text |
| Emerald | `#10B981` | actions only |
| Amber | `#F4BC50` | Incoming and offline |

### Type (not locked)

- **Barlow Semi Condensed:** headings and counts. It has the feel of painted shop signage.
- **Atkinson Hyperlegible Next:** everything people read.
- **Atkinson Hyperlegible Mono:** only for codes that get matched character by character against a printed tag: IDs, VINs, ticket numbers.

### Layout (compact Home)

Left-aligned throughout, single column, thumb jobs in the bottom third.

```text
┌──────────────────────────────┐
│ Country ATV        [DL Dale▾]│  who's signed in, always visible
│ ✓ All changes sent 2 min ago │  or amber banner / rust notice card
│ ┌──────────────────────────┐ │
│ │ ⌕ Find a unit: ID, VIN…  │ │  inset well
│ └──────────────────────────┘ │
│ ┌───────────┐ ┌────────────┐ │
│ │ ⊞ Check in│ │ ⎙ Reprint  │ │  raised, tactile
│ │   a unit  │ │   a tag    │ │
│ └───────────┘ └────────────┘ │
│ Incoming to confirm   See all│
│ ┌──────────────────────────┐ │
│ │ ID 3HZW8RTB   [Incoming] │ │  flat record + chevron
│ │ 2026 Honda FourTrax…   › │ │
│ │ ATV, checked in by Dale  │ │
│ └──────────────────────────┘ │
│ [In stock 37][Incoming 6][Sale pending 2]  raised, filter
├──────────────────────────────┤
│ Home  Stock  (SCAN) Tickets More│
└──────────────────────────────┘
```

### Principles

1. **Depth means "press this".** Actions are raised and records are flat.
2. **The screen says what something means for your work, not just its state.** "3 changes are saved on this phone," not "Sync pending."
3. **Every status is icon, word and colour together.**
4. **The ID looks like the tag on the box.** See section 3.

---

## 3. Check against generic AI-design defaults

`frontend-design` lists the traits that generated designs cluster around. Here is where this brief stands on each.

| Trait | Where we stand | What we did |
|---|---|---|
| **Near-black background with a single bright accent** (trait 2) | **We are close to it.** The palette and emerald are LOCKED, so they stay. | Distinctiveness has to come from the subject instead: the tag plate, signage type and tactile controls. **Proposed:** the ID plate (variant axis (c) in the prompt). It is a near-white plate with the ID in bold mono, so the screen ID looks like the thermal tag on the box and a mechanic can match screen to box at a glance. This is the one bold element; everything else stays quiet. |
| Middle-dot meta strings and spaced em dashes (trait 5) | They were all over the UI copy. | **Fixed.** Cards now use plain phrases ("ATV, checked in by Dale on Tue"), and the offline banner has a title plus a consequence line. |
| Monospace for small data labels (trait 5) | **Kept on purpose.** | Mono is only used for codes matched character by character to a printed tag. Nothing else is mono. |
| One radius on everything (trait 4) | **Partly.** Cards, tiles and fields are all 12 dp. | **Proposed, not applied:** records at 8 dp and controls at 14 dp, so shape also separates records from actions. Cheap to try in Phase 2. |
| ALL-CAPS labels, eyebrows, "→" | Already banned. | Now also listed in the prompt's "Do not render" section. |

## 3b. Anti-slop pre-check of the prompt spec (`vectorlab-ux-skills:anti-slop`)

This pass ran against the written spec, because there are no mockups yet. The real run is against the mockups in Phase 4.

| Check | What it found | What was done |
|---|---|---|
| **S1:** gradient on the main button | The Scan gradient spanned about 20 lightness points (50.6 → 31.0). The rule allows 10 or less, single hue. | **Fixed:** `#12D091` → `#10B981` → `#0EA171` (44.3 → 34.3). Glyph contrast 5.7–9.4:1, edge vs bar 4.4:1. |
| **S8:** pills around static information | Status chips were pills, and they had tactile depth even though they can't be tapped. That broke our own "depth means press this" rule. The ticket kind was also a pill. | **Fixed:** status is now a **status tag**, an icon plus the word in the status colour, with no pill and no depth (6.4–10.7:1 on cards). Ticket kind is plain text. Pills now appear only on things you can tap: the signed-in chip, filter chips and the selected nav pill. |
| **S10:** cartoon art in empty or error states | The CHOPT reference has a cartoon mascot. | **Rule added:** the unreachable background is a photo or texture, never a mascot. |
| **S7:** hard drop shadows | The outer shadows use 45–50% alpha. | **Documented exception:** on `#1E1F21`, a light-theme shadow of under 10% is invisible. Offsets stay at 3 dp or less, so they read as an edge. |
| **S9:** icons in inputs | Find has a search icon and a clear button. | Allowed. Both do something. |
| S3, S4, S6, S12–S15 | Glass, blur, glow, gradient borders, over-rounding | Not present. The largest radius is 16 dp, on notice cards and sheets. |
| ID plate (variant c) | It's a container around static information, which is the shape S8 flags. | **Kept as a variant on purpose.** Its job is matching the printed box tag. Phase 4 judges it on the mockup. |

---

## 4. Prompt changes made in Phase 1

None of these touch a locked decision.

1. **Section 0 added:** the one-glance goal, success criteria, and a LOCKED constraints card (palette, accent, nav, depth, mode). This follows `design-first-ui-prompting`'s "constraints card".
2. **Copy:**
   - Offline banner: "Shop system offline" / "3 changes are saved on this phone. They'll send when the shop system is back." (pattern from Alta)
   - Normal state: "All changes sent 2 min ago".
   - "Needs the shop system. You're offline right now."
   - All middle-dot strings in the UI copy are removed. Section 7's sample data is now line-by-line tables, so builders render it exactly.
3. **Large-text fix:** at font scale 1.3 or more, the three count tiles become full-width rows (pattern from alias). By my arithmetic, at 200% text each tile has about 86 dp of content width, which can't fit "pending" at about 28 sp. The prompt's own rule says nothing breaks mid-word, so this was a defect against the prompt itself.
4. **Section 9 "Do not render" added:** the negative prompt.
5. **Section 10 "Variants" added:** one variable per variant. The axes are (a) section order, (b) quick-job tile layout, and (c) ID as plain mono or as the tag plate.

---

## 5. Open risks for later phases

- **Nav labels at large text: resolved in the prompt.** Labels grow up to 18 sp and then stop; they are never icon-only (section 1b). This still needs a real check on the Pixel Fold.
- **PIN length: decided, 4 digits.** The server change to `Auth.kt` line 32 is still to do.
- **"Sold this week" needs a server query** that doesn't exist yet.
- **Images needed from Yoshi:** six unit-type images, plus the unreachable-screen background.
- **The Scan screen is unspecified.** The router covers it, but its layout doesn't exist yet. References are collected in section 1.
- **Typefaces aren't locked.** They are part of the direction, but you haven't confirmed them.

## 6. Next

Phase 2: `prototype` builds the Home variants from the prompt along axes (a), (b) and (c). You pick, then Phase 3 (polish), then the Phase 4 review gate.
