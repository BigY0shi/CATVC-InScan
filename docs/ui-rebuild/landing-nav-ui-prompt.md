# InScan — Landing Screen & Navigation Router · UI Design Prompt

> Paste everything below this line into the builder (Aura, Gemini, Claude, Stitch, etc.).

---

## 0. Goal and locked constraints

**What this screen has to say in one glance:** "Here's what needs you, and Scan is right here."

**It succeeds when:**
- Someone who has never used the app finds Scan and both quick jobs at a glance.
- Anyone can read who is signed in without hunting for it.
- An offline or needs-attention state is impossible to miss, and it says what that means for their work.
- It reads at arm's length, both in a dim shop bay and with sun glare on the screen.

```text
LOCKED: do not change or reinterpret
PALETTE  true charcoal: bg #1E1F21, surface #28292C, raised #323337 (full table in section 6)
ACCENT   emerald #10B981 for actions only; ocean blue is a status colour only
NAV      Home · Stock · [Scan] · Tickets (TBA) · More
DEPTH    tactile controls only; information surfaces stay flat (spec in section 6)
MODE     dark charcoal, not light mode and not pure black
```

## 1. Product Context

Design the **landing screen** and the **navigation router** for **InScan**, the Android app used by **Country ATV**, a small powersports dealership and repair shop in Southern Oregon. About four mechanics share a few phones. They also sell bikes, so the same people do the intake, the repairs and the sales.

The app does five jobs:

1. **Check in** new units (ATVs, side-by-sides, dirt bikes, snowmobiles, jet skis) and **bag-and-tag** them: enter the details and print a QR tag for the box.
2. **Scan** a tag to open its record. Scanning a box tag is also how an *Incoming* unit gets confirmed *In stock*.
3. **Stock**: look up any unit by tag, VIN, ID or make/model.
4. **Work tickets (TBA)**: repair, prep/PDI and warranty tickets with **parts requests** attached. These are not in the first release, but the navigation and landing layout must already have a place for them. See the TBA convention in section 4.
5. **Paperwork** for sales. This comes later, so it is reserved in the router and not drawn here.

**Who uses it:** older mechanics and biker types. Many wear reading glasses, have greasy hands or gloves on, and use the phone one-handed while standing at a bench or out in the yard. Some of that is in bright sun and some is in a dim shop bay. They aren't tech people. The app has to feel like a well-labelled tool wall, not a fintech dashboard.

**Platform:** Android phones, including a **Pixel Fold**, so it has to work folded and unfolded. The UI will be built in Jetpack Compose with Material 3. Use Android/M3 anatomy, not iOS patterns: no blurred iOS tab bar, no SF Symbols, no swipe-back-only navigation.

**Phones are shared.** The person signed in has to be visible on every screen, because every change is recorded under their name.

---

## 2. Design Direction

**Feel:** a workshop that's in good order. Solid and plain-spoken, like a steel workbench, a well-kept tool wall and good shop signage. True charcoal (`#1E1F21`) with a slight cool, graphite cast. Not black and not brown. **Very high contrast**, **big type**, **big targets**.

**Qualities:** utilitarian, legible, calm, sturdy, honest. Each screen has one obvious action.

**Rules from the owner (hard requirements):**
- **One obvious button per job.** The emerald Scan button is the only filled accent button visible on the landing screen.
- **Shop language everywhere:** "tag", "ticket", "check in", "paperwork", "waiting on this phone". Never "sync", "entity", "payload", "SKU", "outbox" or "dashboard".
- **No fake or teaser controls.** Don't draw buttons for features that don't exist yet, don't use "coming soon" tiles, and don't draw a simulate-scan button. **One exception is allowed: the Tickets (TBA) slot in the bottom navigation.** It stays where it is so the nav never has to be rearranged later. In the first release it opens an honest "not built yet" screen with no fake data.
- **The camera only runs on the Scan screen.** It starts when Scan is tapped and stops after a good read or Cancel. There is no continuous scan mode and no timers.
- **Navigation never changes a status.** Only an explicit, labelled button does.

**Avoid:**
- Neumorphism, glassmorphism, neon glow, grain or noise textures.
- Gradients and shadows anywhere other than the tactile-control spec in section 6.
- A hacker or terminal look. Emerald should read as "go", not "Matrix". No monospace for UI text, no scanlines, no HUD brackets.
- The previous build's look: orange accents on near-black, and soft, shadowed (neumorphic) cards. The charcoal background stays, but nothing else from that look comes back.
- Generic SaaS: KPI cards with sparklines, charts, donut graphs or "Welcome back! 👋" heroes.
- Icon-only buttons. Every icon has a visible text label.
- Gray-on-gray text, any text smaller than 14 sp, and ALL-CAPS paragraphs.
- Hamburger drawers, swipe-only or long-press-only actions, and toasts for anything important.

---

## 3. Layout Structure

**Frames to design**
- **Compact (folded or regular phone):** 412 × 915 dp portrait.
- **Unfolded (Pixel Fold inner screen):** a medium or expanded width class, about 840 dp wide. Draw it in landscape.

**Grid and spacing**
- 4 dp base unit.
- Screen gutters 16 dp on compact and 24 dp unfolded.
- 24 dp between sections, 12 dp between items in a section, and 16 dp padding inside cards.
- Touch targets are at least 48 dp. Primary targets are **56 dp or more** because of gloves.
- The thumb zone matters: the most-used actions (Scan and the nav) live in the bottom third of the screen.

**Vertical hierarchy on the compact landing screen, top to bottom**
1. Top app bar (64 dp)
2. Shop status line or banner (changes with state)
3. Find field
4. Quick jobs (2-up)
5. Incoming to confirm (later, the same slot holds My tickets (TBA))
6. Stock at a glance
7. Bottom navigation with the raised Scan button

**Unfolded:** a navigation rail on the left with Scan at the top of the rail, then two content columns. Each column is at least 360 dp wide, with a 24 dp gap between them.

**Text scaling:** layouts must reflow cleanly at 200% system font size. Rows grow taller, nothing truncates mid-word, and status tags wrap below the title instead of being cut off.

---

## 4. Screen Structure & Navigation Router

**The TBA convention.** Anything marked **(TBA)** is part of work tickets and parts requests. It gets designed now and built later.
- Lay it out so it slots in later without moving anything else on screen.
- Each TBA item names what fills its place in the **first release**.
- Draw the first-release version unless a deliverable asks for the future state.

### 4a. Landing screen ("Home"), compact

1. **Top app bar**
   - Left: "Country ATV" in the heading face, 22 sp bold.
   - Right: a **signed-in chip** made of an initials circle, the first name and a chevron. Example: `DL Dale ▾`. It's 48 dp tall and opens a sheet with *Switch user* and *Sign out*.
2. **Shop status zone.** It shows the normal strip, or the offline banner, or the notice card, or both the banner and the card. The app keeps working offline, so none of these block the screen.
   - *Normal:* a quiet one-line strip that reads "All changes sent 2 min ago", with a leading emerald-ink check icon. The text is `text-2` at 14 sp.
   - *Offline:* a full-width **amber banner** with a leading cloud-off icon. It stays until the shop system is reachable again. It has two lines:
     - Title, semibold: "Shop system offline"
     - Body: "3 changes are saved on this phone. They'll send when the shop system is back."
   - *Refused changes:* a **notice card** (section 5) with a rust icon.
     - Title: "1 change wasn't accepted"
     - Body: "The shop system turned down a change made on this phone. Review it to fix or throw it away."
     - A full-width "Review the change" button that opens Needs attention.
     - It has no close button. It goes away only once the change is fixed or thrown away.
3. **Find field.** A 56 dp tall field drawn as an inset well (section 6), with a strong border and a search icon. Placeholder in the first release: "Find a unit: ID, VIN, make or model". Typing a complete ID or VIN goes straight to that unit. *Later (TBA), the placeholder becomes "Find a unit or ticket: ID, VIN, ticket # or make" and ticket numbers match too.*
4. **Quick jobs.** Two equal tiles side by side, each 96 dp tall, drawn as raised tactile controls (section 6):
   - "Check in a unit", with a box-with-plus icon
   - "New ticket" (TBA), with a wrench-with-plus icon. **First release:** "Reprint a tag", with a printer icon. It opens Find, then that unit's tag screen, because sun-faded tags get reprinted rather than re-entered.

   Icons are 28 dp in emerald ink. Labels are 17 sp semibold. These tiles are tonal, not filled; the only filled emerald element is the Scan button.
5. **My tickets (TBA).** A section header with "My tickets" on the left and an "All tickets" text button on the right. Below it are up to 3 ticket cards. Each card shows:
   - Line 1: the ticket number in mono (`WO-26-0042`), the kind as plain text (Repair, Prep or Warranty), and the status tag aligned right.
   - Line 2: the unit name, 17 sp semibold.
   - Line 3: one line of meta text, for example "Waiting on PR-26-0017, due Thu".

   The whole card is tappable and opens the ticket.

   *Empty state:* "No tickets assigned to you." with an "All tickets" button.

   **First release: "Incoming to confirm".** This takes the same slot and uses the same card component. The header reads "Incoming to confirm", with a "See all" text button that opens Stock filtered to Incoming. Below it are up to 3 unit cards.

   Each card has a **unit-type image** on the left, so a mechanic can tell at a glance what they're looking for (section 5, record card). To its right:
   - Line 1: the 8-character ID exactly as printed on the box tag, in mono (`ID 3HZW8RTB`), with the Incoming status tag aligned right.
   - Line 2: the unit name, 17 sp semibold.
   - Line 3: the type and who checked it in, as a plain phrase, for example "ATV, checked in by Dale on Tue".

   Tapping a card opens the unit.

   *Empty state:* "Nothing waiting to be confirmed."
6. **Stock at a glance.** A 2 × 2 grid of count cards: **Incoming 6**, **In stock 37**, **Sale pending 2**, **Sold this week 4**. Aim for a polished, information-rich card: not a bare number, and not a busy dashboard. Each card is a raised tactile control (section 6) and contains, top to bottom:
   - a 40 dp rounded-square **icon badge**: the status tint as the fill, the status icon in its ink colour
   - the **count** in the heading face, 28 sp, in the status ink colour
   - the **label** in `text`, 15 sp medium
   - one **context line** in `text-2`, 14 sp:
     - Incoming: "Scan box tags to confirm"
     - In stock: "Ready to sell"
     - Sale pending: "Waiting on paperwork"
     - Sold this week: "Since Monday"

   Tapping a card opens Stock filtered to that status. No charts.

   **Large text:** at a system font scale of 1.3 or more, the grid becomes four full-width rows. Each row has the badge and label on the left and the count on the right, so no label breaks mid-word.
7. **Bottom navigation.** Specified in 4c below.

### 4b. Landing screen, unfolded
- **Rail on the left:** the Scan button at the top of the rail, then Home, Stock, Tickets (TBA) and More.
- **Left column:** Incoming to confirm, with up to 6 cards. Later this becomes My tickets (TBA).
- **Right column:** Find field, Quick jobs, Stock at a glance.
- **Status banner:** spans both columns, directly under the top bar.

### 4c. Bottom navigation (compact)

The bar has five slots: **Home · Stock · [ SCAN ] · Tickets (TBA) · More**. This order is locked.

- **Tickets (TBA) in the first release:**
  - Draw it exactly like the other items. Don't grey it out, because a greyed item reads as broken.
  - Tapping it opens a plain screen. The title is "Work tickets" and the body reads: "Work tickets aren't built yet. Repairs, prep and parts requests will live here."
  - No buttons, no badge, no sample data.

- **Bar:** 80 dp tall on the surface colour, with a 1 dp top divider. Every item has an icon (24 dp) and a label (14 sp) that is always visible.
- **Large text:** nav labels grow with the system font size up to **18 sp, then stop**. Icons and the bar keep their size. At 18 sp, "Tickets" takes about 70–76 dp of its 82 dp slot. That's an estimate; check it on the device. Labels are never hidden, truncated or replaced by icons alone.
- **Selected item:** an emerald-tint pill (64 × 32 dp) behind the icon, the icon in emerald ink, and the label in primary text, semibold. Selection is shown by the pill and the weight, not by colour alone.
- **Unselected items:** icon and label in secondary text.
- **Scan button:**
  - A 64 dp emerald circle, raised 16 dp above the bar edge.
  - A 4 dp ring of background colour around it so it reads as a cutout.
  - It gets the Scan button's tactile treatment from section 6.
  - A dark QR-scan glyph inside it.
  - The label "Scan" below it, in primary text, 14 sp semibold.

  It's an **action, not a tab**: it opens the Scan screen full-screen over whatever screen is current.
- **Badges:**
  - Tickets (TBA) shows the count of open tickets assigned to the signed-in user, as an emerald pill with dark text. The first release has no badge.
  - More shows a **rust dot** when anything needs attention.

### 4d. Router map (deliverable: draw this as a diagram frame)

```
GATE  (shown only when needed; nothing else is reachable until both pass)
  connect ............ enrol this phone: shop system address, setup code, phone name
  who ................ "Who's using this phone?" → employee list → PIN
                       (session expiry returns here, then back to the exact screen they were on)
  unreachable ........ full-screen "Can't reach the shop system" with a background image
                       and a big "Try again" button. Shown ONLY when connect or sign-in
                       can't continue without the shop system (later also: finishing a sale)

SHELL  (bottom bar on compact · rail when unfolded)
  home ............... landing screen
  stock .............. search + status filter chips + list
    stock/{unitId} ............ unit detail (status, tag; later tickets + service history (TBA))
      stock/{unitId}/edit
      stock/{unitId}/label .... preview tag, label size, share to printer
      stock/{unitId}/paperwork  RESERVED (sales, online only). Do not draw.
  tickets (TBA) ...... first release: "not built yet" screen
                       later: segmented control Tickets | Parts
    tickets/{woNo} (TBA) ...... ticket detail
    tickets/new?unit={unitId} (TBA)
    parts/{prNo} (TBA) ........ parts request detail
  more ............... Needs attention · Upload from this phone · Label size ·
                       Dealer details · Send changes now · Switch user · Sign out

OVER THE SHELL  (full-screen, each has its own Close; bottom bar hidden)
  scan ............... camera on while open; closes on a good read or Cancel
     unit tag ..........→ stock/{unitId}
     ticket tag (TBA) ..→ tickets/{woNo}
     unknown code / VIN →  scan/not-found → "Check in this unit" (VIN prefilled)
  check-in ........... short stepper: Type → Make → Model → Year & colour → VIN →
                       Review → Print tag → done (opens the new unit)
  needs-attention
  upload
```

**Router rules (show them as notes on the diagram):**
- Each tab keeps its own back stack. Tapping the active tab again pops to its root.
- System Back on any tab root goes to Home. Back on Home leaves the app, with no "are you sure" dialog.
- Back, Close and Cancel **never change a status**. If someone leaves Check in before Review, the app asks once: "Leave without saving?" with the choices *Keep editing* and *Leave*.
- The Scan screen always returns to where it was opened from on Cancel, and goes forward to the record on a good read.
- Anything that needs the shop system while it's offline stays visible, with a plain reason ("Needs the shop system. You're offline right now."). It is never hidden.
- **Offline never blocks work.** Check-in, scanning, tags and lookups keep working on the phone, with the amber banner showing. The full-screen `unreachable` state is only for steps that cannot happen without the shop system.
- Reserved routes (paperwork and the in-app shop assistant) exist in the map only. **Don't render any entry point for them.**
- TBA and Reserved are different things:
  - **TBA routes** (tickets and parts) are designed now. Draw them on the diagram with a solid outline and a "TBA" tag.
  - **Reserved routes** are not designed yet. Draw them dashed.

---

## 5. Component System

- **Top app bar:** title on the left, signed-in chip on the right. Flat surface colour, no shadow, 1 dp bottom divider.
- **Signed-in chip:** 32 dp initials circle (emerald tint background, emerald-ink letters), first name, chevron. 48 dp tall.
- **Status banner:** full width, a tinted background with `text`-colour message, a leading 20 dp icon in the ink colour, an optional trailing text button, and 56 dp minimum height. Two variants: amber (offline) and ocean (sending: "Sending 3 changes to the shop system…").
- **Notice card:** for things someone has to act on.
  - Layout: full width, 16 dp radius, flat `surface`, 1 dp `border-strong`, 20 dp padding.
  - Top row: a 24 dp icon in the notice's ink colour, then the title in the heading face, 20 sp.
  - Body: one or two lines, 17 sp, in `text`.
  - Button: full width, a raised tactile control, 56 dp tall. Neutral, not emerald, because Scan stays the only emerald fill.
  - **Closing:** system notices (refused changes) have no close button and clear themselves when the condition clears. *Reserved:* later shop notices from the owner get a close button and an optional image header. Don't render those.
- **Unreachable screen** (gate state, not part of Home):
  - Full-bleed background image supplied by Yoshi, under a `bg` scrim at 75% opacity. Raise the scrim if the brightest part of the image drops text below 7:1.
  - The image is a photo or texture from the shop's world, such as a trail, a shop floor or tyre tread. **No cartoon mascots or illustrated people.**
  - Centred content:
    - a 64 dp cloud-off icon
    - title "Can't reach the shop system", heading face, 24 sp
    - body "Signing in needs the shop system. Check the shop Wi-Fi, then try again.", 17 sp
    - a full-width raised "Try again" button, 56 dp tall
  - No bottom nav and no snackbar.
- **Find field:** 56 dp tall, 12 dp radius, an inset well (section 6), a leading search icon, and a clear button once text is entered.
- **Quick-job tile:** 96 dp tall, 12 dp radius, a raised tactile control (section 6). A 28 dp icon sits above a 17 sp semibold label. Pressing it presses in.
- **Record card:** used for Incoming units now and for tickets (TBA) later. The whole card is the target.
  - **Flat:** surface background, 12 dp radius, 1 dp strong border, three lines (as in 4a), a right-aligned status tag, and a trailing 24 dp chevron in `text-2`.
  - **Unit-type image** on the left: 96 × 72 dp (4:3), 8 dp radius, on `surface-raised`. It shows the unit's *type*, not that exact unit. **Unit types are an editable list** that the owner manages. For the mockups, use: Two wheels, Four wheels, Side-by-side, Snow, Water, Other. Each type has one image. A type with no image shows the generic "Other" image.
  - Images are photographic or rendered PNGs supplied by Yoshi. **In mockups, draw a labelled placeholder frame** (for example, "ATV image" centred in `text-2`). **Never draw vehicles with SVG, CSS or icons.** Never use manufacturer photos or logos.
  - At large text sizes the image keeps its size and the text wraps beside it.
- **Count card:** as in 4a item 6. Badge, count, label and context line. A raised tactile control (section 6).
- **Status tag:** a 16 dp icon plus the status word, both in the status ink colour, 14 sp medium.
  - **No pill, no container, no depth.** Status is information, not a control.
  - It always pairs icon and word, never colour alone.
  - Ink on `surface` is 6.4–10.7:1.
  - Pill-shaped chips are only for things you can tap: the signed-in chip and the filter chips on Stock.
- **Nav bar, nav rail, raised Scan button:** as in 4c.
- **Section header:** 18 sp semibold title with an optional trailing text button.
- **Bottom sheet:** used for the signed-in chip menu. Flat `surface-raised` colour, 16 dp top radius, 56 dp rows.

---

## 6. Visual Design Rules

**Palette: true charcoal (locked).** The background is `#1E1F21`, a charcoal with a slight cool, graphite cast. There is no brown or warm tint anywhere: surfaces, borders and text all stay neutral-to-cool.

It's a touch lighter than a typical near-black dark theme, which helps a little in sun, but it is not a light theme. All contrast ratios below were checked against WCAG 2.x.

| Token | Hex | Use | Contrast |
|---|---|---|---|
| `bg` | `#1E1F21` | app background | — |
| `surface` | `#28292C` | bars, cards | — |
| `surface-raised` | `#323337` | tiles, fields, sheets, pressed states | — |
| `border-strong` | `#8C8F95` | edges of anything tappable or typeable | 4.5:1 on surface, 3.9:1 on raised (meets non-text 3:1) |
| `divider` | `#3C3D41` | decorative separators only | — |
| `text` | `#F2F3F4` | primary text | 14.9:1 on bg, 11.4:1 on raised |
| `text-2` | `#C6C9CD` | secondary text, meta, placeholders, unselected nav | 9.9:1 on bg, 7.6:1 on raised |
| `emerald` | `#10B981` | **primary action fill (Scan)** | 5.7:1 vs surface |
| `on-emerald` | `#06140E` | text and icons on emerald | 7.4:1. **Never white on emerald (2.5:1).** |
| `emerald-pressed` | `#0C9A6C` | pressed Scan | 5.3:1 with on-emerald |
| `emerald-ink` | `#45D9A1` | emerald text and icons on charcoal | 8.1:1 on surface, 7.0:1 on raised |
| `emerald-tint` | `#16493A` | selected-nav pill, count-card badges | ink 5.7:1 |
| `ocean-ink` / `ocean-tint` | `#79BEEC` / `#203C50` | Sale pending, In progress, "sending" info | 5.7:1 |
| `amber-ink` / `amber-tint` | `#F4BC50` / `#4D3B18` | Incoming, Waiting on parts, offline | 6.2:1 |
| `rust-ink` / `rust-tint` | `#F9907A` / `#4E2721` | Needs attention, errors | 5.7:1 |
| `stone-ink` / `stone-tint` | `#DADDE1` / `#1E1F21` | Open, Sold, Retired, Closed | 12.1:1 |

**Palette rules:**
- There are only **two text colours**, `text` and `text-2`. Nothing dimmer. Hierarchy comes from size and weight, not from fainter grays.
- **Nothing that carries text is lighter than `surface-raised`.** Don't add lighter grays.
- Status tints sit *darker* than the surfaces, so badges and banners read as solid and their text keeps its contrast.
- Banners use `text` for the message and the ink colour for the icon. They don't use ink-coloured body text.

**Accent rule:** emerald is the single action accent. Ocean blue is a **status colour only** and is never used for buttons.

**Status mapping (icon + text + colour):**
- Units:
  - Incoming: amber, box icon
  - In stock: emerald, check-circle icon
  - Sale pending: ocean, hourglass icon
  - Sold: stone, tag icon
  - Retired: stone (dimmed), archive icon
- Tickets (TBA):
  - Open: stone, circle icon
  - In progress: ocean, wrench icon
  - Waiting on parts: amber, package icon
  - Ready: emerald, check icon
  - Closed: stone (dimmed), lock icon

**Typography (free fonts, bundled in the app):**
- Headings and big numbers: **Barlow Semi Condensed**, SemiBold and Bold. It's a signage-style grotesk, and the condensed width fits long model names.
- UI and body text: **Atkinson Hyperlegible Next**. It was designed by the Braille Institute for low-vision reading and clearly separates I/l/1 and O/0.
- IDs, VINs, ticket and parts numbers: **Atkinson Hyperlegible Mono**.

**Type scale (sp, size/line height):**

| Role | Size | Weight |
|---|---|---|
| Screen title | 28/34 | Bold |
| Section | 18/24 | SemiBold |
| Card title | 17/24 | SemiBold |
| Body | 17/24 | Regular |
| Label | 15/20 | Medium |
| Meta | 14/20 | Regular |
| Nav label | 14/16 | Medium (selected: SemiBold) |
| Big number | 28/32 | Bold |

Nothing is smaller than 14 sp. Use sentence case throughout.

**IDs** display exactly as printed on the tag: `ID 7KQ2M9XF` (8 characters, no hyphen).

**Shape and depth:**
- Radius: 12 dp on cards, tiles and fields; 16 dp on sheets; pills only for interactive chips: the signed-in chip, filter chips and the selected nav pill.
- **Depth: tactile controls only (locked).** Depth means "you can press this", and nothing else gets it. Everything else is flat, and its depth comes only from the surface steps (`bg` → `surface` → `surface-raised`) plus `border-strong`.

**What gets tactile treatment:**
- the Scan button
- quick-job tiles
- count cards
- notice-card buttons
- the signed-in chip
- the selected nav pill
- the Find field, as an inset well

**What stays flat:**
- top bar, nav bar, banners, sheets, section headers
- record cards: they open a detail screen, so they get a trailing chevron instead of depth

**Values** (all checked for contrast):

| Element | Fill | Edges and shadow |
|---|---|---|
| **Raised control** (tiles) | Vertical gradient `#3A3B40` → `#2E2F33`. Text 10.1–12.0:1 on it. | 1 dp top inner highlight, white 10%. 1 dp bottom inner shade, black 35%. Outer shadow y 2 dp, blur 3 dp, black 45%. 1 dp `border-strong`. |
| **Pressed raised control** | Flat `#2A2B2F` | Highlight and outer shadow removed. Inner shadow y 2 dp, blur 4 dp, black 45%. Reads as pushed in. |
| **Inset well** (Find field) | `#18191B`. Placeholder `text-2` 10.6:1. | Inner shadow y 2 dp, blur 4 dp, black 55%. 1 dp bottom inner highlight, white 6%. 1 dp `border-strong`. |
| **Scan button** | Radial gradient, emerald only, within 10 lightness points: `#12D091` (center-top) → `#10B981` → `#0EA171` (edge). Glyph `on-emerald`, 5.7–9.4:1. Edge vs bar 4.4:1. | 2 dp top inner highlight, white 35%. Bottom inner shade, y −3 dp, blur 4 dp, black 25%. Outer shadow y 3 dp, blur 6 dp, black 50%. Keeps the 4 dp `bg` ring. **Pressed:** flat `emerald-pressed`, highlight removed, scale 0.96. |
| **Interactive chips and pills** (signed-in chip, filter chips, selected nav pill) | Their tint | 1 dp top inner highlight, white 8%. 1 dp bottom inner shade, black 30%. No outer shadow. |

**Never:** glow, shimmer, particles, reflective chrome, or textures. No depth on information surfaces.

**Gradients:** one hue only, within 10 lightness points, and only on tactile controls.

**Shadow strength:** the outer-shadow alphas (45–50%) are deliberate. On `#1E1F21`, a light-theme shadow (under 10% alpha) is invisible. Offsets stay at 3 dp or less, so these read as an edge, not a floating card.

**Icons:** Material Symbols Rounded, weight 500, outlined when unselected and filled when selected.

---

## 7. UI Content (mockup data; realistic and plainly sample)

- **Signed in:** Dale (initials DL). Phone name: "Front counter".
Render this copy exactly. Each card is given line by line.

- **Incoming to confirm (first release):**

  | Line 1 (ID, status) | Line 2 (name) | Line 3 |
  |---|---|---|
  | `ID 3HZW8RTB`, Incoming | 2026 Honda FourTrax Rancher 4x4 | ATV, checked in by Dale on Tue |
  | `ID 6MDQ1KXA`, Incoming | 2026 Polaris RZR XP 1000 | Side-by-side, checked in by Ray on Tue |
  | `ID B4TN7GWS`, Incoming | 2026 Yamaha YZ250F | Dirt bike, checked in by Dale on Mon |

- **My tickets (TBA; future-state frame only):**

  | Line 1 (ticket, kind, status) | Line 2 (name) | Line 3 |
  |---|---|---|
  | `WO-26-0042`, Prep, In progress | 2026 Polaris RZR XP 1000 | PDI checklist 6 of 14 |
  | `WO-26-0039`, Repair, Waiting on parts | Customer's 2019 Honda Foreman 500 | Waiting on PR-26-0017, due Thu |
  | `WO-26-0045`, Warranty, Ready | 2025 Yamaha YZ250F | Call the customer for pickup |

- **Stock at a glance:** Incoming 6, In stock 37, Sale pending 2, Sold this week 4.
- **Unit examples, for the router diagram thumbnails:**
  - 2026 Honda FourTrax Rancher 4x4, ATV, `ID 3HZW8RTB`, Incoming
  - 2026 Can-Am Outlander 700, ATV, `ID 9PCN4VDE`, In stock
- **Offline variant:** the amber banner reads "Shop system offline" / "3 changes are saved on this phone. They'll send when the shop system is back." The More tab shows the rust dot.
- **Badges:** Tickets 3, in the future-state frame only.

---

## 8. Interaction and Motion

Motion is functional and quick. Nothing bounces.
- **Tab switch:** 150 ms crossfade, no horizontal slide.
- **Push to a detail screen:** 200 ms slide-in from the right. Back reverses it.
- **Overlays (Scan, Check in):** 200 ms slide up from the bottom; Close slides back down.
- **Press feedback:**
  - Tactile controls press in using the pressed values in section 6, over 80 ms.
  - Flat record cards step their fill up one surface level.
  - The Scan button also scales to 0.96 and gives a light haptic tick.
- **Good scan:** a 200 ms emerald frame flash and a strong haptic, then the app goes straight to the record. There is no confirmation dialog.
- **Banners:** slide down 150 ms when they appear and stay until the condition clears. They never auto-dismiss.
- **Loading:** static skeleton blocks in `surface-raised`, with no shimmer. Under 300 ms, show nothing.
- **Snackbars:** only for confirmations of an action just taken ("Tag sent to printer app"), never for problems.
- **Reduce-motion setting:** all slides become instant crossfades.
- **Focus:** a 3 dp `emerald-ink` outline on every focusable element, for hardware keyboards and Bluetooth scanners.

---

## 9. Do not render

- Any logo or brand mark. "Country ATV" appears as text in the heading face only. No watermarks.
- Any text beyond what this prompt gives. No lorem ipsum, no extra people, no extra units.
- Features this prompt doesn't name:
  - a notification bell
  - profile photos (people are shown as initials)
  - a paperwork button
  - a chat or assistant entry
  - charts
- These UI copy patterns:
  - meta strings joined with middle dots ("A · B · C")
  - spaced em dashes
  - ALL-CAPS labels
  - eyebrow labels above headings
  - "→" on buttons or links

  Write plain phrases and let the layout separate things.
- Decorative numbering (01 / 02 / 03), gradient washes, or one identical card style for everything. Records and actions must look different (section 6).

## 10. Variants (only if asked for more than one)

Keep every token and every locked constraint fixed. Change **one** variable per variant:
- **(a) Home section order:** "Incoming to confirm" above or below "Stock at a glance".
- **(b) Quick jobs:** two equal tiles, or one wide "Check in a unit" tile with "Reprint a tag" as a smaller tile beside or below it.
- **(c) ID treatment:** the ID shown as plain mono text, or as an **ID plate** that mimics the printed box tag. The plate has a near-white fill (`text`, `#F2F3F4`), the ID in `bg` (`#1E1F21`) Atkinson Hyperlegible Mono Bold at 15 sp, and a 4 dp radius, with no "ID" prefix. Plate contrast is 14.9:1.

---

## Deliverables

1. **Home, compact, first release, normal state.**
2. **Home, compact, first release, offline with 3 changes waiting and 1 needing attention.** Show the amber banner, the rust notice card below it, and the rust dot on More.
2b. **Gate: unreachable.** The full-screen "Can't reach the shop system" state, with a placeholder background image labelled "Background image (Yoshi)".
3. **Home, compact, future state with tickets (TBA).** This frame shows:
   - the My tickets section
   - the "New ticket" tile
   - the Tickets badge

   It proves tickets slot in later without moving anything else on the screen.
4. **Home, unfolded, first release:** rail plus two columns.
5. **Router diagram:** the map from 4d, drawn as connected screen thumbnails (gate → shell tabs → detail routes → overlays), with the router rules as side notes.
   - TBA routes: solid outlines with a "TBA" tag.
   - Reserved routes: dashed outlines labelled "Reserved", not drawn as UI.
6. **Component sheet:**
   - nav bar states: default, selected, pressed, badge, rust dot
   - nav rail
   - the raised Scan button, default and pressed
   - each tactile control, default and pressed: quick-job tile, count card, Find field (inset well)
   - the notice card
   - a flat record card next to a raised tile, to show the difference between a record and an action
   - all status tags
   - the three banner variants
   - the first-release "Work tickets" screen

Define every colour and type style above as **named tokens** (CSS variables if you output HTML), using the exact names in section 6, so they map one-to-one onto the Compose theme later.
