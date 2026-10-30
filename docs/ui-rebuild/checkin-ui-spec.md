# InScan: Check in a unit and print its tag

- **Status:** draft for the owner's review. Nothing here is locked until the owner says "lock it".
- **Date:** 2026-10-30
- **Mockup:** `prototypes/checkin.html` (the state picker on the left is mockup chrome; the phone frame is the design). It uses the shared tokens in `prototypes/inscan-base.css`.
- **Builds on:**
  - `landing-nav-ui-prompt.md`: its section 0 locked card applies unchanged (palette, emerald for actions only, depth B, the Home and sign-in locks).
  - `scan-pin-ui-spec.md`: Scan's result card and its rules.
  - `unit-types-spec.md`: editable unit types, LOCKED.
- **Printer:** not chosen yet (owner, 2026-10-30). The design works with any printer driver that meets the `LabelPrinter` contract (it reads status back and never assumes a print worked). The recommendation to offer the shop is the **Brother PT-P910BT** with 36 mm tape (handoff, printer section). Until a driver exists, printing goes through the share-to-printer-app path that is in the app today.

---

## 1. References (opened and looked at this session)

| Reference | What we take | What we leave |
|---|---|---|
| [Turo, VIN step](https://mobbin.com/screens/1b34c1fb-5813-42b5-bdbd-b5d025e5e12c) | One question per step, "1 of 11" with a thin progress bar, a big heading, the field with a camera button beside it, Next pinned at the bottom | "View all steps" (the Review step does that job here) |
| [My BMW, enter VIN](https://mobbin.com/screens/2df519c0-6571-43d0-93b0-83a716eb6737) | A live "0 / 17" count under the field; a "where is the VIN" help link | The large car illustration (no drawn vehicles, prompt section 5) |
| [Skyscanner, car type](https://mobbin.com/screens/14f31a5b-01c2-47f5-b43c-69bef06c95b1) | A 2-column grid of picture cards; the selected card gets an outline and a check badge | Stock car photos (our type images are Yoshi's PNGs) |
| [Grailed, review](https://mobbin.com/screens/7cb1142c-f802-4ab3-bb0b-d09a2450865c), [Vestiaire, review](https://mobbin.com/screens/d6ceb8cd-c4f1-4151-a08e-c14c59ce7913) | Review grouped by section with an Edit per section; a "double check everything" banner at the top; one primary at the bottom | ALL-CAPS EDIT buttons; legal small print |
| [Whatnot, step](https://mobbin.com/screens/8841c05f-0458-41e2-b649-73e94e5ed082) | "5 of 8" with Next riding above the keyboard | A pale disabled Next with no reason given |
| [iOS print options (Retro)](https://mobbin.com/screens/e3f9e6e1-4aa7-40b7-bec5-89586a0f973f) | Printer row, copies stepper, size row, and a preview of what will print | The system print sheet itself: thermal tags go through the printer's own driver |

---

## 2. What the app does today (checked 2026-10-30)

- **The form:** one long form on the main screen with make, model, year, colour, type chips, "VIN / Serial (17 digits)" with Decode, price and notes. There is **no sub-model field**, but the QR code carries one (`sm=`). Errors show as a single line above the buttons.
- **Required fields** (phone and server): make, model, and a price above $0 (`Units.kt` `validate`). The year is optional but must be 1950–2100. The type must be on the list.
- **VIN:**
  - Optional. The check-digit test applies only to 17 characters (`Vin.kt`).
  - A failing VIN needs the "VIN matches the frame exactly" tick, or the server refuses it with `VIN_NOT_CONFIRMED`.
  - A duplicate VIN is refused at sync with `VIN_ALREADY_LOGGED`. The phone doesn't check a typed VIN first.
- **VIN lookup (NHTSA vPIC):** online only. It fills make, model, year and type, **overwrites anything already typed, and replaces the notes** with decode details.
- **Save:**
  - The phone picks the ID (a ULID; the tag shows the last 8).
  - The unit is stored as **Incoming** and queued (`logNew`), then the label sheet opens.
- **Tag:**
  - The renderer draws the QR code beside `Label.lines()`. A label that is too small fails with a message.
  - Sizes are set per phone: 50×30 mm @203 (the default), 40×30 @203, 70×36 mm tape @180, 62×29 @300, or custom.
  - Sharing to a printer app **records the tag as printed the moment the share menu opens** (`printedVia = "shared"`), whether or not it prints.
- **Confirm:** scanning the tag on an Incoming unit, then tapping the button, moves it to In stock (`Tags.confirm`).
- **Gap:** the server also accepts a plain status change from Incoming to In stock (`Units.kt`, `canMoveTo`). That bypasses the handoff's rule that only a scanned label moves a unit to In stock (section 6, D-C5).

---

## 3. The flow

**Entry points:**
- Home "Check in a unit" opens at step 1.
- Scan "Check in this unit" opens at step 1 with the VIN filled in and already checked.
- Type it "No tag or VIN? Check it in without one" opens at step 1 with "No VIN or serial" already chosen.

**Steps:** VIN → Type → Make & model → Year & colour → Price → Review → Print tag.

This is VIN first rather than the router's order (D-C1): one scan or one typed VIN fills type, make, model and year, so those steps become a quick confirm.

### 3a. The frame every step shares (iPhone 17, 402 × 874)

1. **Top bar** (56, flat `surface`, 1 dp divider):
   - left: **Cancel** (text button)
   - centre: "Check in a unit" (`type-section`)
   - right: "Step 2 of 6" (`type-meta`, `text-2`)
2. **Progress bar** under the top bar: 4 dp tall, `accent` fill for completed steps on a `divider` track. The Print step is outside the count.
3. **Heading** for the step (`type-screen-title`), then one plain line of help (`type-body`, `text-2`).
4. **The step's fields.** Fields are inset wells, like the Find field. Chips for quick picks are raised (they are pressable).
5. **Bottom bar**, pinned and riding above the keyboard (`imePadding`): **Back** (raised) on the left, **Next** (emerald, the screen's one primary) on the right. Next is never greyed out without a reason: tapping it with something missing puts the reason under the field and moves focus there.
6. **"Checking in as Dale"** shows on Review, as the Home review recommended.

**Leaving:** Cancel, or back on step 1, asks once: "Leave without saving?" with **Keep editing** and **Leave**. Back on any later step goes to the previous step. Nothing is saved until Review's "Save and print tag" (router rule: back, close and cancel never change a status).

### 3b. Step 1: VIN

- Heading "VIN or serial number". Help: "Scan the VIN barcode, or type it. Go-karts and some pit bikes have a serial number instead."
- A well field (mono 22/28, capitals, no autocorrect) with a **Scan** button beside it (icon plus word). Scan opens the Scan screen in "VIN" mode and comes back here with the read.
- A live count under the field: "12 of 17".
- A text button "Where's the VIN?" opens a short sheet with the places to look on each type: steering neck, frame rail, under the seat. Text only, no drawings.
- A raised toggle chip: **"No VIN or serial"**. It skips the lookup and the VIN on the tag.
- **At 17 characters:**

| Result | What shows |
|---|---|
| Check digit passes, online | `good` line "VIN check digit passes." Then the lookup runs: "Looking up the VIN…", then "Filled in from the VIN: 2026, Polaris, RZR XP 1000, Side-by-side. Check them on the next steps." |
| Check digit passes, offline | `good` line, plus "The VIN lookup needs the internet. You'll type the details in." |
| Check digit fails | warn banner with the check's own message ("This VIN doesn't add up…"), plus a checkbox "VIN matches the frame exactly". Next works only once it's ticked, and says so if tapped before. |
| Already in the shop system (or on this phone) | info card: "Already checked in: 2026 Polaris RZR XP 1000, tag 6MDQ1KXA, In stock." **Open that unit** (primary). Next is replaced; a duplicate can't be started. |
| Not 17 characters | Treated as a serial number: no check digit, no lookup. Line "Saved as a serial number." |

- **The lookup never overwrites.** It fills only empty fields, and each filled field shows a small "From the VIN" marker until the person moves past that step. If someone had already typed a value, the lookup shows "The VIN says Polaris. Use it?" with **Use it** and **Keep mine**. Notes are never touched. Every looked-up value is confirmed by a person on its step (handoff rule on autofill).
- **Duplicate check:** the phone checks its own copy at once. Online, it also asks the shop system. Offline, the line reads "Couldn't check the shop system. If another phone already checked this unit in, it will be refused when it sends."

### 3c. Step 2: Type

- Heading "What is it?".
- A 2-column grid of picture cards from the editable unit-type list, in the admin's order. Each card is 160 × 128, raised (pressable), with the type image and its name in `type-tile-label`. Selected: 2 dp `accent-ink` outline, `accent-tint` name plate and a check badge (Skyscanner pattern). "Other" is always last.
- If the VIN lookup picked a type, it starts selected with the "From the VIN" marker.

### 3d. Step 3: Make & model

- Heading "Make and model".
- **Make:** a well field with raised quick-pick chips under it, built from the makes this shop has checked in most (sample: Honda, Yamaha, Polaris, Kawasaki, Can-Am, KTM). Tapping a chip fills the field.
- **Model:** a well field, required.
- **Sub-model:** a well field, optional, with the hint "Trim or version, like EPS or 4x4". It's on the tag (`sm=`). The unit-types lock sends MX, street and electric through model and sub-model.

### 3e. Step 4: Year & colour

- Heading "Year and colour".
- **Year:** a number field, plus raised chips for the current model year and the two before it (2027, 2026, 2025). It must be 1950–2100, or left empty.
- **Colour:** a well field, plus chips for this shop's most-used colours.

### 3f. Step 5: Price

- Heading "Price". Help: "The asking price. It isn't printed on the tag."
- A well field with a "$" prefix and the number keypad.
- **D-C2, recommended:** a raised chip **"Add the price later"**. The unit is checked in with no price, shows "No price yet" in its record, and can't move to Sale pending until it's priced. This needs a server change, since `validate` refuses a price of 0 or less today.

### 3g. Step 6: Review

- Heading "Check it over".
- A banner (status-banner build, info tone): "These details print on the tag. Check them against the unit."
- Sections in a flat list (`bg` fill, 1 dp `border-strong`), each with an **Edit** text button that jumps to that step and comes back here:
  - VIN or serial (mono)
  - Type, with its image
  - Make, model and sub-model
  - Year and colour
  - Price ("No price yet" when skipped)
- A **Notes** field (optional, a 3-line well): "Condition, upgrades, anything the next person should know."
- The line "Checking in as Dale".
- Primary, full width: **Save and print tag**. Saving stores the unit as **Incoming** and queues it (works offline), then opens step 7. A saved unit is never lost by leaving step 7.

### 3h. Step 7: Print the tag

- The top bar changes: the title becomes "Print the tag", the step count goes, and **Cancel becomes Done**. The unit is already saved, so there is nothing to lose.
- **Preview:** the real tag, drawn by `LabelRenderer` at the chosen size and shown at its true proportions on a white plate. It has the QR code on the left and `Label.lines()` on the right, ending "ID 3HZW8RTB".
- **Rows** (flat list, 56 tall each, every row tappable):
  - **Printer:** the name plus its status, in words and colour (see the table). Tapping it pairs or changes the printer.
  - **Label size:** for example "70 × 36 mm tape". It is read from the printer when the driver can tell; otherwise it uses the phone's setting.
  - **Copies:** a stepper, 1 to 5. Two copies are for a unit that ships in two boxes.
- **Primary:** **Print tag** (emerald). With no direct printer, it becomes **Send to printer app** (the share path).

| Printer state | Row shows | Primary |
|---|---|---|
| No printer paired | "No printer paired. Pair one, or send the tag to the printer's own app." | **Send to printer app** |
| Paired, ready | "Brother PT-P910BT. Ready. 36 mm tape loaded." | **Print tag** |
| Not ready | the driver's own words, for example "The printer's cover is open." or "Out of tape." (warn) | Print tag, which re-checks first |
| Wrong tape | "36 mm tape loaded, the label size is 50 × 30 mm. Change the size to match?" | **Use 36 mm tape** |
| Printing | "Printing 1 of 1…" | disabled while printing |
| Printed | "Printed." (good) | **Scan the tag** |
| Failed | the driver's reason, then "Nothing was recorded as printed." (danger) | **Try again** |

- **Recording a print:**
  - A direct print is recorded only on the driver's `Printed` outcome.
  - The share path still records when it is sent (no app reports back), as `printedVia = "shared"`.
  - The step then shows "Sent to the printer app. Did it print?" with **Yes, scan it** and **Send again**.
  - Scanning the tag is the real check either way.
- **After printing:** "Stick the tag on the box, then scan it to move the unit to In stock."
  - **Scan the tag** (primary) opens Scan.
  - The read lands on Scan's "Found, still Incoming" card, which has Move to In stock.
  - **Done** (top bar) opens the new unit as Incoming.
- **Too small:** if the tag doesn't fit the label size, the preview area shows the renderer's own message, with **Change label size**.
- **Offline:** printing works (it's local Bluetooth). The tag record is queued with the unit.
- **Reprint** (from a unit, later) reuses this step with the field "Why a new tag?" (optional; today's reprint reason).

### 3i. States the mockup shows

1. VIN, empty
2. VIN, passed and looked up
3. VIN, check digit fails
4. VIN, already checked in
5. Type, from the VIN
6. Make & model
7. Year & colour
8. Price
9. Review
10. Print, no printer
11. Print, ready
12. Printed
13. Leave without saving

---

## 4. Accessibility and platform

- **Reading order** is screen order. Each step's heading takes focus when the step opens, and the screen reader reads "Step 2 of 6, What is it?"
- **The "From the VIN" marker** is part of each field's description: "Make, Polaris, from the VIN."
- **Type cards** are radio buttons in one group: "Side-by-side, selected, 3 of 6".
- **Quick-pick chips** fill the field and announce it: "Make set to Polaris."
- **Next with something missing:** the reason is announced and focus moves to the field. Colour is never the only cue.
- **Keyboards:**
  - VIN: capitals, no autocorrect, `ImeAction.Next`.
  - Price: `KeyboardType.Decimal`.
  - Year: `KeyboardType.Number`.
  - The bottom bar takes `imePadding()`; the step body scrolls.
- **Font scale 2.0:** chips wrap, and type cards go to one column at 1.5x and up.
- **Window size classes:** Medium and Expanded show the steps in a 560-wide centred column. At Expanded, Review and Print sit side by side (details left, tag preview right).
- **Printer driver** (when one exists): it runs behind `LabelPrinter`, so the screen only knows `status()` and `print()`.
  - Android: the Brother Mobile SDK lists the PT-P910BT with status classes (handoff).
  - iOS: the same SDK has an iOS build (checked earlier this session). Compose Multiplatform needs it as an expect/actual.

---

## 5. Data, server and app needs

| Need | Why | Where |
|---|---|---|
| Sub-model field on check-in | It's on the tag and in `NewUnit`, but the form has no field | App form; `NewUnit.subModel` already exists |
| VIN lookup fills empty fields only and never touches notes | Today it overwrites typed values and notes | `InventoryViewModel` decode handling |
| Duplicate-VIN check before saving | Today a typed duplicate is only refused at sync | Phone copy now; server lookup when online (`/units/by-code`) |
| Price can be empty (D-C2) | "Add the price later" | `validate` refuses `priceCents <= 0`. Allow null. Block Sale pending until priced |
| Only a tag confirm moves Incoming to In stock (D-C5) | Today a plain status change also does, which skips the label check | `Units.kt` status change; `canMoveTo` allows it |
| Record a direct print only on the driver's `Printed` | "Never assume a print worked" (`LabelPrinter` contract) | Print step |
| Unit types from the editable list | Type cards | `unit-types-spec.md` (locked, not built). `validate` checks the old enum until then |
| Quick-pick makes and colours from this shop's history | Faster with gloves | Count from the phone's copy; no server change |

---

## 6. Decisions for the owner

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D-C1 | VIN first, not the router's Type → Make → … order | **VIN first.** | One scan fills four steps; without a VIN, the "No VIN or serial" chip goes straight on. |
| D-C2 | Allow "Add the price later" | **Yes.** The unit can't go to Sale pending until priced. | Whoever unloads the truck often doesn't know the asking price. A required field gets filled with $1, which is worse than an honest blank. |
| D-C3 | "VIN or serial number" in one field | **Yes.** | Go-karts (a confirmed type) often have a serial, not a VIN. The field already takes any length; only 17 characters get the check and lookup. |
| D-C4 | Copies stepper, 1 to 5 | **Yes.** | A unit that ships in two boxes needs two tags. The same tag on both is fine: both read as the same unit. |
| D-C5 | Close the server gap: only a tag confirm moves Incoming to In stock (owner-only override) | **Yes.** | It's the handoff's locked rule; today the server doesn't enforce it. |
| D-C6 | Printer to offer the shop | **Brother PT-P910BT with 36 mm tape** (not confirmed; owner to raise with the shop). | It has a mobile SDK with status read-back for Android and iOS, and 36 mm tape fits a full tag. The design works with any compliant driver, with the share path as the fallback. |

---

## 7. Do not render

- Drawn vehicles or VIN-location illustrations (prompt section 5).
- The price on the tag (handoff: never price, status or customer data on the QR code).
- A "Draft sale" button (sales are Reserved).
- "AUTO-POPULATED" banners from the legacy barcode mappings. Maker barcodes can't identify a unit (Scan spec 2c), so the old mapping prefill isn't carried into this flow.
- A greyed-out Next with no reason.

---

## 8. Build and test contract

- **Test tags:**

  | Element | Tag |
  |---|---|
  | Frame | `checkin.cancel`, `checkin.back`, `checkin.next`, `checkin.step` |
  | VIN | `checkin.vin.field`, `checkin.vin.scan`, `checkin.vin.none`, `checkin.vin.where`, `checkin.vin.frameMatch`, `checkin.vin.openExisting` |
  | Type | `checkin.type.{typeId}` |
  | Make & model | `checkin.make`, `checkin.make.chip.{make}`, `checkin.model`, `checkin.subModel` |
  | Year & colour | `checkin.year`, `checkin.year.chip.{year}`, `checkin.colour` |
  | Price | `checkin.price`, `checkin.price.later` |
  | Review | `checkin.review.edit.{section}`, `checkin.notes`, `checkin.save` |
  | Print | `checkin.print.printer`, `checkin.print.size`, `checkin.print.copies`, `checkin.print.go`, `checkin.print.scan`, `checkin.print.done` |
  | Leave dialog | `checkin.leave.keep`, `checkin.leave.go` |

- **Stateless and previewable:** `CheckInScreen(state, …)` takes a state object. Roborazzi covers every state in 3i at font scale 1.0, 1.3 and 2.0, at 360 × 640 and 412 × 915, and in landscape.
- **Behaviour tests:**
  - The lookup never overwrites a typed value or the notes.
  - A failing VIN can't be saved without the tick.
  - A duplicate VIN can't start a check-in.
  - Nothing is saved before "Save and print tag".
  - Leaving after saving keeps the unit.
  - A direct print is recorded only on `Printed`.
  - A failed print records nothing.
  - Two copies print the same tag.
  - Offline: save and print work, and the record is queued once.
- **The spec wins** over the mockup where they differ.
