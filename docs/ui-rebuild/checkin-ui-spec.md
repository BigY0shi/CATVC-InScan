# InScan: Check in a unit and print its tag

- **Status:** revised after the Phase 4 review gate (`council-review-checkin.md`). The owner said **yes to D-C1 to D-C9** on 2026-10-30, and asked for **D-C10** (reading the label from a photo, with an open model and no Gemini) on 2026-11-02. All decided, **not locked**. D-C11 (which open model) is open. Nothing here is locked until the owner says "lock it".
- **Date:** 2026-10-30
- **Mockup:** `prototypes/checkin.html` (20 states; the state picker on the left is mockup chrome). Contact sheet: `prototypes/checkin-states.png`. Shared tokens: `prototypes/inscan-base.css`.
- **Builds on:**
  - `landing-nav-ui-prompt.md`: its section 0 lock card applies unchanged (palette, emerald for actions only, depth B, and the Home and sign-in locks).
  - `scan-pin-ui-spec.md`: Scan's result card; section 6 below proposes three changes to it.
  - `unit-types-spec.md`: editable unit types, LOCKED.
- **Printer:** not chosen (owner, 2026-10-30). The design works with any driver behind the `LabelPrinter` contract (it reads status back and never assumes a print worked). The recommendation to offer the shop is the **Brother PT-P910BT** (D-C6). Until a driver exists, printing goes through a printer app.

---

## 1. References (opened and looked at this session)

| Reference | What we take | What we leave |
|---|---|---|
| [Turo, VIN step](https://mobbin.com/screens/1b34c1fb-5813-42b5-bdbd-b5d025e5e12c) | One question per step, "1 of 11" with a thin progress bar, a big heading, the field, Next pinned at the bottom | "View all steps" (Review does that job) |
| [My BMW, enter VIN](https://mobbin.com/screens/2df519c0-6571-43d0-93b0-83a716eb6737) | A live "0 / 17" count under the field; a "where is the VIN" link | The car illustration (no drawn vehicles, prompt section 5) |
| [Skyscanner, car type](https://mobbin.com/screens/14f31a5b-01c2-47f5-b43c-69bef06c95b1) | A 2-column grid of picture cards with a check badge on the selected one | Stock car photos (our type images are Yoshi's PNGs) |
| [Grailed, review](https://mobbin.com/screens/7cb1142c-f802-4ab3-bb0b-d09a2450865c), [Vestiaire, review](https://mobbin.com/screens/d6ceb8cd-c4f1-4151-a08e-c14c59ce7913) | Review by section with an Edit on each; one primary at the bottom | ALL-CAPS EDIT buttons; legal small print |
| [Whatnot, step](https://mobbin.com/screens/8841c05f-0458-41e2-b649-73e94e5ed082) | "5 of 8" with Next riding above the keyboard | A pale disabled Next with no reason |
| [iOS print options (Retro)](https://mobbin.com/screens/e3f9e6e1-4aa7-40b7-bec5-89586a0f973f) | Printer row, copies stepper, size row, and a preview of what will print | The system print sheet (thermal tags go through the printer's own driver) |

---

## 2. What the app does today (checked 2026-10-30)

- **The form:** one long form with make, model, year, colour, type chips, "VIN / Serial (17 digits)" with Decode, price and notes. There is **no sub-model field**, even though the QR code carries one (`sm=`).
- **Required fields** (phone and server): make, model, and a price above $0 (`Units.kt` `validate`). The year is optional (1950 to 2100). The type is checked against the old enum (`UnitType.fromLabel`).
- **VIN:**
  - Optional. The check-digit test applies only to 17 characters, and `Vin.normalize` doesn't map O, I or Q.
  - A failing VIN needs "VIN matches the frame exactly" ticked, or the save is refused with `VIN_NOT_CONFIRMED`.
  - A duplicate is refused at sync with `VIN_ALREADY_LOGGED`, and a VIN stays unique forever. The phone doesn't check a typed VIN against existing units.
  - The phone's copy holds only the 500 newest units (`Units.kt` list query).
- **VIN lookup (NHTSA vPIC):**
  - Online only. It fills make, model, year and type, **overwrites typed values and replaces the notes**, and writes the VIN back into the field when it returns.
  - It maps the vehicle type from `VehicleType`, but vPIC calls ATVs and side-by-sides MOTORCYCLE, so they come back as Dirt Bike.
  - For off-road units vPIC often returns the make and year only. The made-up sample VIN in the first draft returned Polaris, no model, and 2024.
- **Photo autofill:** switched off. The button calls `analyzeImageWithGemini`, which only says "Photo autofill is switched off until the shop server is set up" (`InventoryViewModel.kt`). The old loading text still reads "Gemini-3.1-Pro Engine analyzing vehicle..." (`MainScreen.kt`), which was never true. Both go when this flow is built.
- **Save:** a new ULID is made on every save call (a double tap can make two units). The unit is stored as **Incoming** and queued in one transaction.
- **Tag:**
  - Sizes are set per phone: 50 × 30 mm at 203 dpi (the default), 40 × 30 at 203, "36 mm tape, 70 mm long" at **180** dpi (wrong for the PT-P910BT, which prints at 360 dpi with a 32 mm printable height on 36 mm tape), and 62 × 29 at 300.
  - The renderer leaves the VIN line off without a word when it doesn't fit.
  - Sharing to a printer app records the tag as printed when the share menu opens, even if it is cancelled.
- **Confirm:** a scanned tag on an Incoming unit, plus a tap, moves it to In stock. The server checks only that the ID in the QR code matches.
- **Gaps:** the server also accepts a plain Incoming → In stock change (`canMoveTo`). Today's "mark as sold" uses it to go Incoming → In stock → Sold, and the interim rule accepts In stock → Sold with no price check.

---

## 3. The flow

**Entry points:**
- **Home "Check in a unit":** step 1.
- **Scan "Check in this unit":** step 1 with the VIN filled in and already checked.
- **Type it "No tag or VIN?":** step 1 with "It has none" chosen.

**Steps:** VIN → Type → Make & model → Year & colour → Price → Review, then Print. VIN comes first (D-C1).

**The unit ID** is made once, when step 1 opens. Save is idempotent on it, so a double tap or a restored screen can't make two units.

### 3a. The frame every step shares (iPhone 17, 402 × 874)

1. **Top bar** (56, flat `surface`):
   - Left: **Cancel** (steps 1 to 6), **Print later** (on Print before a tag has printed), or **Done** (after a print).
   - Centre: "Check in a unit" (`type-section`). Below 1.5x text the title is hidden.
   - Right: "Step 2 of 6" (`type-meta`, `text-2`).
2. **Progress bar:** 4 dp. A `text-2` fill on a `divider` track, filled up to the current step. It is status, so it is never emerald. It animates with `transform: scaleX()`, 200 ms.
3. **Heading** (`type-screen-title`), then one help line (`type-body`, `text-2`). The help line says what the VIN lookup did on that step: "The VIN found the make. Type the model."
4. **Fields:**
   - Inset wells. Fields are labelled by the step heading when the step asks one question (VIN, Price), and by a visible label otherwise.
   - A field with its marker and chips is a group with 8 dp gaps; groups are 24 dp apart.
   - Quick-pick chips are raised. A selected chip shows a check, an `accent-ink` border and `accent-tint`, so selection isn't shown by colour alone.
5. **Bottom bar**, pinned and riding above the keyboard:
   - **Back** (raised, hidden on step 1, where Cancel does that job) on the left.
   - **Next** (emerald, the screen's one primary) on the right.
   - Next is never greyed out. Tapped with something missing, it marks the field (danger ring), says why under it ("Type the model to go on."), announces it and moves focus there.
   - At 1.5x text and up, the two buttons stack.
6. **Editing from Review:** the step opens with **Back to review** as the primary. Back and system back also return to Review.

**Leaving:** Cancel, or back on step 1, opens a bottom sheet: "Leave without saving?" "Nothing is saved yet. What you've entered for this unit will be lost." It has **Leave** (raised) and **Keep editing** (primary). Nothing is saved until "Save and print tag".

**Shared phones:**
- The draft survives an idle lock and the OS killing the app (section 4).
- If someone else signs in, they see "Dale didn't finish checking in a Polaris. Finish it or throw it away?" with **Throw away** and **Finish it**.

### 3b. Step 1: VIN or serial number

- Heading "VIN or serial number". Help: "Scan the VIN barcode, take a photo of the label, or type it."
- **Scan the VIN barcode**: full-width raised button. It opens Scan in VIN mode and comes back with the read.
- **Photo of the label** (D-C10): full-width raised button under it. It reads the crate label, VIN plate or certificate of origin from one photo (section 3b-2). It is hidden when an admin turns the feature off.
- **VIN | Serial number**: a 2-way switch, built like Scan's mode tiles. Default VIN. This is the explicit choice (D-C3). Nothing is ever treated as a serial just because of its length.
- The field: full width, `type-field-mono` 22/28, capitals, no autocorrect. A live count: "12 of 17".
- **Where's the VIN?** opens a text sheet. The crate label comes first (that's where it is when unloading a truck), then the steering neck, frame rail and under the seat.
- Two raised chips:
  - **Add it later**: the VIN is in the crate (D-C8). The unit is flagged "No VIN yet", like a missing price, and gets its duplicate check when the VIN is added.
  - **It has none**: some go-karts and pit bikes have neither.
- **Cleaning before any check:** spaces and dashes are dropped, and O → 0, I → 1, Q → 0. The field shows the cleaned value. An 18-character read starting with "I" whose last 17 pass the check is taken as those 17 (some Code 39 VIN labels add a leading I).

**Results** (each announced in a polite live region):

| Result | What shows |
|---|---|
| VIN, 17, passes, lookup found something | `good` line "VIN check digit passes." A "From the VIN lookup" box lists **only what came back**, for example "Make Polaris, Year 2026", then "Not found: model and type. You'll fill those in next." |
| VIN, 17, passes, lookup found nothing or offline | `good` line, plus "The VIN lookup didn't find this one." or "The VIN lookup needs the internet. You'll type the details in." |
| VIN, 15, 16 or 18 characters | The field gets a warn ring. "VINs have 17 characters. This has 16. Check it against the frame, or switch to Serial number." Next works only once it is fixed or switched. |
| VIN, check digit fails | Warn ring and banner: "This VIN doesn't add up. A character is probably misread: 0 and O, 1 and I, 8 and B, 5 and S. Some off-road VINs don't follow the check, so if it matches the frame exactly, tick the box." Then "VIN matches the frame exactly" (a 56-tall row). No lookup runs until it's ticked. |
| Already checked in (In stock, Incoming or Sale pending) | "Already checked in. Open it instead of checking it in twice." The unit's record card. The primary becomes **Open that unit**. |
| Already checked in, Sold or Retired | "Sold on Aug 12. If it's back as a trade-in, the owner can bring it back in." For the owner: **Bring it back in** (audited; D-C9). |
| Serial number | No check digit and no lookup. Duplicates are checked on (make, serial) when the make is known, at Save. |

**The lookup:**
- It fills empty fields only and never writes the VIN or the notes.
- It runs once per VIN (cached), and its result is dropped if the VIN changed while it ran.
- Type is set only when `BodyClass` maps clearly to a type on the editable list; otherwise the type is left for the person.
- Every looked-up value carries "From the VIN" on its step **and on Review**. Review is where they're checked against the unit.

### 3b-2. Reading the label from a photo (D-C10)

The point is speed at the truck: one photo of the crate label fills what's printed on it. The rules from the owner's 2026-10-27 decision still hold. It **only reads text that's on the label and never fills in anything it can't read**, every value it fills is **not confirmed until a person confirms it**, and a VIN it reads goes through the same cleaning and check digit as a typed one.

**Taking the photo:**
- Scan's camera opens in **photo mode**: the same plates, no brackets. The prompt says "Get the whole label in the picture. Crate label, VIN plate, or certificate of origin." A raised shutter (72 round) sits in the bottom panel, with **Cancel** on the left.
- One still is taken at full resolution, not a video frame. The torch button works as it does on Scan.
- Camera permission follows Scan 2f.

**Reading:**
- It runs **on the phone** (D-C11), so it works offline, needs no key, and sends no photo anywhere.
- While it reads, the photo stays on screen under a plate: "Reading the label…" with a `text-2` spinner after 300 ms. **Cancel** stops it.
- The reader only returns lines of text with their boxes. Turning text into fields is plain code in `core`, so it can be tested without the model:
  - **VIN:** a 17-character run that passes the check digit after cleaning. One that fails is offered as "Read a VIN that doesn't add up" and goes down the check-digit-fails path.
  - **Make:** matched against this shop's make list.
  - **Model and sub-model:** the text after a "MODEL" label on the same line or the next.
  - **Year:** four digits from 1950 to 2100 after "MODEL YEAR", "MY" or "YEAR".
  - **Colour:** matched against this shop's colour list, or the text after "COLOR".
  - **Never read:** the price (an MSRP on a label isn't the asking price), the type (left to the person or the VIN lookup), and the notes.
  - Anything the parser isn't sure of is left empty. An empty field is better than a wrong one.

**Back on step 1:**
- The VIN fills the field and is checked as if typed. If it passes, the VIN lookup runs as usual.
- A **"From the label"** box (icon `photo_camera`, same build as the lookup box) lists **only what was read**, for example "VIN, Make Polaris, Model RZR XP 1000, Year 2026, Colour Gray", then "Not read: type. Check each one against the unit as you go."
- Nothing was read: "Couldn't read this label. Try again closer, with less glare, or type it in." with **Try again**. No fields change.
- **Fills empty fields only**, like the lookup. Typed values always win. If the label and the VIN lookup disagree, the label wins (it's this unit, the lookup is a catalogue), and the box says "The VIN lookup says RZR XP. The label says RZR XP 1000."
- Taking another photo replaces earlier label values that nobody has confirmed or changed. It never touches the rest.

**Not confirmed until a person confirms it:**
- Every label value carries a marker on its step: icon `photo_camera`, "From the label. Not checked yet." in `warn-ink`.
- **Changing a value confirms it.** The person typed it, so the marker goes.
- **Review** is where the rest are confirmed. Rows from the label keep the warn marker, and above Save sits a check row: "I checked the values from the label against the unit" (56 tall, the same build as "VIN matches the frame exactly").
- Tapping **Save and print tag** with that row unticked marks it (danger ring) and says "Tick this once you've checked the values from the label." Save is never greyed out.
- The tick records who confirmed and when. The values move from "not confirmed" to "confirmed by Dale".

**The photo itself:** kept on the phone only until Save or Cancel, then deleted. It is never uploaded. Keeping photos to tune the reader later is a separate decision (section 7, open).

**The duplicate check** runs on the phone's full copy (paged sync, not just the 500 newest), on the server when online, and again at Save. Offline: "Couldn't check the shop system. If another phone already checked this unit in, it'll be refused when it sends."

### 3c. Step 2: Type

- Heading "What is it?". Help: "The VIN lookup didn't say. Pick the type." (or "From the VIN: Side-by-side. Change it if that's wrong.")
- A 2-column grid of raised picture cards from the editable type list, in the admin's order, all the same height. "Other" is last.
- Selected is latched like Scan's mode tile: the chip shadow, an `accent-tint` name plate with `accent-ink` text, and a filled check badge. It is not an outline, so it never looks like keyboard focus. A radio group; picking a card doesn't advance.

### 3d. Step 3: Make & model

- **Make:** quick-pick chips from this shop's most-checked-in makes (sample: Honda, Yamaha, Polaris, Kawasaki, Can-Am, KTM). Words capitalised, autocorrect off.
- **Model:** required, autocorrect off (it would change "RZR").
- **Sub-model:** optional. Hint: "Trim or version, like EPS or 4x4". It's on the tag (`sm=`).

### 3e. Step 4: Year & colour

- Year: a number field plus chips for the current model year and the two before it. It must be 1950 to 2100, or empty.
- Colour: a field plus chips for this shop's most-used colours.

### 3f. Step 5: Price

- Labelled by the heading "Price". Help: "The asking price. It isn't printed on the tag."
- **Add the price later** (D-C2): the unit is saved with no price, never $0 as a stand-in. It shows "No price yet" and is listed in "Needs a price". It can't go to Sale pending or Sold until it's priced, enforced by the server.

### 3g. Step 6: Review

- Heading "Check it over". Help: "Check it against the unit. Everything except the price prints on the tag." Then "Checking in as Dale".
- **Flat rows, the whole row tappable** (64 min), each with a trailing "Edit":
  - VIN (`type-field-mono`)
  - Type (with a 48 × 36 image)
  - Make and model
  - Year and colour
  - Price
- Values are in `type-card-title`, and "From the VIN" markers stay on the rows they apply to. The screen reader reads each row as one item: "V I N, 4 X A R R…, Edit".
- **Values from the label** (D-C10) keep their "From the label. Not checked yet." marker here, and the check row "I checked the values from the label against the unit" sits under the rows. It only shows when at least one label value is still unconfirmed. The screen reader adds "from the label, not checked yet" to those rows.
- Notes: optional, 3 lines.
- Primary: **Save and print tag**. It is disabled on the first tap. Saving checks duplicates one last time, stores the unit as **Incoming** and queues it in one transaction (works offline), then opens Print.

### 3h. Print the tag

- The top bar keeps "Check in a unit". Left: **Print later**, which asks first (sheet: "Print the tag later? The unit is saved as Incoming. Until it has a tag it shows as 'No tag yet' on Home and on its own page." with **Print later** and **Print now**). After any print, the left button is **Done**.
- **Saved line** at the top, announced: "Saved as Incoming. Tag ID 6MDQ1KXA."
- **Preview:** the real tag from `LabelRenderer`, at true proportions on `--paper`. For the PT-P910BT that's a 70 mm long tag with a 32 mm print area on 36 mm tape, at 360 dpi. When the VIN line doesn't fit the size, the preview says "The VIN is in the QR code only at this size." With copies over 1, the tag text adds "Box 1 of 2" (D-C4).
- **Rows** (flat, 64 tall):
  - **Printer**: name and status.
  - **Label size**: read from the printer's loaded media when the driver can tell (`MediaInfo`); otherwise the phone's setting. Presets are keyed by tape width.
  - **Copies**: a stepper from 1 to 5, "One per box". The count is in `type-big-number`, and the minus button is disabled at 1.
- **Primary:** **Print tag**, or **Send to printer app** when no driver is paired.

| Printer state | Row | Primary |
|---|---|---|
| Ready | "Brother PT-P910BT", "Ready. 36 mm tape loaded." (good) | Print tag |
| No printer paired | "No printer paired", "Pair one, or send the tag to the printer's own app." | Send to printer app |
| Bluetooth off | "Bluetooth is off." with **Turn on** | Send to printer app |
| Bluetooth not allowed | "InScan isn't allowed to use Bluetooth." Same rules as Scan 2f: ask once, then Open settings | Send to printer app |
| Busy | "The printer is busy with another phone." | Print tag (retries) |
| Not ready | the driver's own words: "The cover is open." "Out of tape." (warn) | Print tag, which re-checks first |
| Wrong tape | "24 mm tape loaded. This tag needs 36 mm." | Print tag stays disabled until it matches |
| Printing | "Printing 1 of 2…" | Disabled |
| Printed | "Printed." (good). The heading becomes "Tag printed" with help "Stick it on the box, scan it, then tap Move to In stock." There is a **Print again** text button. Buttons: **Check in another** (raised), **Scan the tag** (primary). | |
| Failed | the reason, then "Nothing was recorded as printed." (danger, assertive) | Try again |
| Didn't hear back | "Didn't hear back from the printer. Check the tag." (warn). Nothing is recorded; the confirm scan settles it. | Print again |

- **Sending to a printer app:**
  - Prefer **Brother's own app via Smooth Print**, which reports success or failure back to InScan through a per-job callback. That gives the same states as a direct print.
  - A generic share is the last resort. It records only when a target app is picked (the chooser's `IntentSender`). On return, the screen shows **"Did the tag print?"** ("The tag went to the printer app. It can't tell InScan whether it printed.") with **Send again** and **Yes, it printed**. This state survives the OS killing InScan while the other app is open.
- **Scan the tag** opens Scan expecting tag 6MDQ1KXA. A different tag reads as "That's tag 3HZW8RTB, not 6MDQ1KXA." The expected tag lands on the "Found, still Incoming" card.
- **Recording:** a direct print is recorded only on `Printed`. `PrintOutcome` gains `Unknown` and a printed count. A confirm scan of a valid tag that has no record is accepted as proof and records the tag then.
- **Back** on Print is the same as Print later or Done. The step screens are gone from the back stack once the unit is saved.

---

## 4. Platform, accessibility and build notes

- **State:**
  - The draft (fields, step, VIN flags, the chosen ID) lives in `SavedStateHandle`, so it survives rotation, process death and the trip to the printer app.
  - Save sets "step = Print" in the same transaction as the unit.
  - Sheets use `rememberSaveable`.
- **Back:** `PredictiveBackHandler` on steps 2 to 6 (back = previous step); step 1 opens the leave sheet; editing from Review returns to Review. Target SDK 36 doesn't call `onBackPressed`.
- **Keyboard:**
  - `windowSoftInputMode="adjustResize"`. The body (weight 1) scrolls above a bar with `navigationBarsPadding().imePadding()`.
  - On a new step, focus goes to the first empty field after the transition. The keyboard is hidden on Type, Review and Print.
  - VIN: `KeyboardCapitalization.Characters`, autocorrect off. Make and model: autocorrect off. Year: `Number`. Price: `Decimal`. The last field's `ImeAction.Next` is Next.
  - iOS `decimalPad` has no Return key, so the bar carries Next.
- **Screen readers:**
  - The heading takes focus on each step: "Step 2 of 6, What is it?"
  - Results, the lookup, duplicates, the save line and printer status are live regions; a failed print is assertive.
  - VINs and IDs are read one character at a time.
  - **Bluetooth scanners** type into the VIN field wherever focus is on step 1 (a fast burst ending in Enter counts as a scan).
- **Text at 1.5x and up:** the centre title hides, buttons stack, the VIN wraps rather than scrolls, and type cards go to one column.
- **Compact height** (landscape phone with the keyboard up): one 48 dp top strip, no help line.
- **Medium and Expanded widths:** the steps sit in a 560 wide column. At Expanded, Review and Print go side by side.
- **Motion:**
  - Forward: the old step fades out over 90 ms; the new one slides 24 → 0 and fades in over 200 ms `ease-out`. Back mirrors it.
  - Jumps from Edit: a 120 ms crossfade.
  - Banners: 150 ms fade from −8 dp.
  - Check badges: scale 0.8 → 1 over 120 ms.
  - Reduce motion: 120 ms crossfades only. Focus moves after the transition.
- **Bluetooth (Android):**
  - `BLUETOOTH`/`BLUETOOTH_ADMIN` with `maxSdkVersion=30`.
  - Ask `BLUETOOTH_CONNECT` when the Printer row is first tapped.
  - Pair through `CompanionDeviceManager`, so no SCAN or location permission is needed.
- **Brother SDK v4:**
  - It needs **Android 8 (minSdk 26; the app is 24 today)** and iOS 14. The AAR is about 26 MB with four ABIs, so ship an app bundle.
  - **iOS needs a Brother-issued MFi PPID**, `UISupportedExternalAccessoryProtocols = com.brother.ptcbp` and `NSBluetoothAlwaysUsageDescription`. The Smooth Print route on iOS may avoid the PPID (inferred).
  - The EULA requires a Licences screen and the notices kept. Its scope names "MPrint" and "PocketJet", so get Brother's written confirmation that PT models are covered.
- **Compose Multiplatform:**
  - `core` is JVM with ZXing, and the renderer uses `android.graphics`.
  - Identical tags on both platforms need a Kotlin Multiplatform QR encoder and a common Skia renderer.
  - `LabelPrinter` moves to commonMain, with a driver per platform.
- **Reading labels (D-C10):**
  - A `LabelReader` contract in commonMain: `read(image) -> List<TextLine(text, box, confidence)>`. It returns text only, never fields, and the field parser lives in `core` with its own tests.
  - One open model behind it on both platforms, run with ONNX Runtime (`onnxruntime-objc` on iOS through CocoaPods; the Android artifact name is to be checked at build time). The model ships inside the app, with no download on first use.
  - ONNX Runtime's native libraries ship per ABI through the app bundle, like the Brother SDK. The model files add to every install, so measure the size before choosing (D-C11).
  - Camera: CameraX `ImageCapture` (Android) and `AVCapturePhotoOutput` (iOS), not the barcode analyser.
  - The photo lives in app-private cache and is deleted on Save, Cancel, or the next launch if the app died.
- **Small fixes:**
  - The FileProvider authority becomes `${applicationId}.fileprovider`.
  - The NHTSA request URL (with the VIN) is logged only in debug builds.

---

## 5. Data, server and app needs

| Need | Why | Where |
|---|---|---|
| Unit types from the editable list, shipped **before** this flow | Type cards like "Two wheels" fail `UnitType.fromLabel` today | `unit-types-spec.md` (locked, not built) |
| Separate serial field, unique on (make, serial) | Serials in the VIN column collide across makes and get read as tag IDs | `units` table, `ensureVinFree`, `findByCode` |
| VIN cleaning (O, I, Q) before the check, duplicate check and lookup | Typed letters never match a scanned VIN later | `Vin.normalize` |
| Nullable price end to end, with Sale pending and Sold blocked while null | "Add the price later" without $0 stand-ins (today's sale paperwork would print $0) | `validate`, Room column, `Units.kt` status rules, a "Needs a price" list |
| Nullable VIN with a "No VIN yet" flag | "Add it later" | `units`, Home flags |
| "No tag yet" on the unit and on Home | A unit saved with no print isn't lost | `UnitDto` gains last-printed and confirmed times |
| Only a tag confirm moves Incoming to In stock (D-C5), shipped **together** with the sale refusing an Incoming unit ("Scan the tag first") and an admin-only, audited path for legacy import | Closing the gap alone would break mark-sold and legacy upload, with phones showing a state the server refused | `Units.kt` status change, `InventoryViewModel` mark-sold, `LegacyPlan` |
| Confirm checks the tag against the unit | A stale tag (details changed after printing) still confirms today | `Tags.confirm` and the phone: compare the QR's fields with the unit. If they differ: "Tag out of date. Reprint it." The unit is flagged when a printed field changes. |
| Confirm accepts a valid tag with no print record | A print the phone never heard back from | `Tags.confirm` records the tag instead of `TAG_NOT_RECORDED` |
| Copies recorded; "Box 1 of 2" on the tag | Two-box units | `TagPrinted` count, `Label.lines` |
| Share records only when a target is picked | A cancelled share is recorded as a print today | `LabelShare`, chooser `IntentSender` |
| PT presets keyed by tape width at 360 dpi (36 mm tape → 32 mm print height) | Today's preset is 180 dpi | `LabelScreens` presets, `LabelSpec` from `status()` |
| Sub-model field; lookup fills empty only; no VIN or notes write-back; result dropped if the VIN changed | Today's form and decode | `InventoryViewModel` |
| Full phone copy (paged) for duplicate checks | The 500-unit cap | `Units.kt` list query, `ShopSync` |
| Label reader on the phone (D-C10, D-C11): `LabelReader`, the field parser, the bundled model | Fill check-in from one photo, offline, with no key | `core` parser, commonMain contract, a driver per platform |
| Per-field source and confirmation: `typed`, `vin_lookup` or `label`, plus who confirmed label values and when | "Not confirmed until a person confirms it", and the label's accuracy report | `units` (or a `unit_field_sources` table), `NewUnit`, Room |
| Admin setting `checkin.labelPhoto` (on by default) | Everything is configurable; a shop can turn the button off | `Setting` enum, `ShopSettings` |
| Remove `analyzeImageWithGemini` and the "Gemini-3.1-Pro Engine" text | No Gemini in production (owner, 2026-11-02) | `InventoryViewModel`, `MainScreen` |
| "Bring it back in" for a Sold unit (owner) | Trade-ins dead-end on the forever-unique VIN | Status change Sold → Incoming, owner-only and audited (D-C9) |

---

## 6. Changes this needs in the Scan spec (Scan is not locked)

1. **"Found, still Incoming" card:** the note becomes "The box tag reads. Check it matches the box: Polaris RZR XP 1000, Gray, VIN ending 104217." The facts row (colour, VIN ending) shows on this card too (D-C7).
2. **New row, "Tag out of date":** the tag's details differ from the unit. The note says "Reprint it before moving it to In stock." The buttons are Scan another and **Reprint tag**. No move.
3. **Opened from Print:** Scan knows the expected tag. Another tag shows "That's tag 3HZW8RTB, not 6MDQ1KXA."

---

## 7. Decisions for the owner

Owner answer, 2026-10-30: **yes to all nine** (decided, not locked). Owner, 2026-11-02: **add D-C10**, with an open model and no Gemini key in production. D-C11 and the photo-keeping question are open.

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D-C1 | VIN first, not the router's Type → Make → … order | **VIN first.** | One scan fills what the lookup knows and catches duplicates before anyone types. |
| D-C2 | Allow "Add the price later" | **Yes, with the server blocking a sale until it's priced.** | Whoever unloads the truck often doesn't know the asking price; a required field gets filled with $1. |
| D-C3 | VIN or serial: an explicit switch, with serials stored separately | **Yes.** | Deciding by length silently turned a mistyped VIN into a "serial" and skipped every check. |
| D-C4 | Copies 1 to 5, with "Box 1 of 2" on the tag | **Yes.** | Two-box units need two tags, and the second box should be told apart. |
| D-C5 | Only a tag confirm moves Incoming to In stock, shipped with the mark-sold and legacy fixes | **Yes.** | It's the handoff's locked rule, and the server doesn't enforce it today. |
| D-C6 | Printer to offer the shop | **Brother PT-P910BT, 36 mm tape**, not confirmed. If chosen: raise the app's minimum to Android 8, apply for Brother's iOS PPID early, and get Brother's written OK on the SDK licence for PT models. | It has an SDK with status read-back for Android and iOS, Smooth Print callbacks for the no-driver path, and 36 mm tape fits a full tag. |
| D-C7 | How hard the box check is when confirming a tag | **Soft:** the card shows model, colour and VIN ending with "Check it matches the box". A strict mode (scan the crate VIN) could come later. | A swapped tag on a pallet is possible, but a strict check adds taps to every box. |
| D-C8 | "Add it later" for a VIN that's inside the crate | **Yes.** | Without it, people tap "It has none" and lose the duplicate check. |
| D-C9 | "Bring it back in" for a sold unit returning (trade-in), owner only | **Yes.** | A VIN is unique forever, so a trade-in can't be checked in at all today. |
| D-C10 | Fill check-in from a photo of the label | **Yes (owner, 2026-11-02).** A "Photo of the label" button on step 1. It reads text only, fills empty fields only, never the price or type, and everything it fills stays "not checked yet" until one tick on Review. On the phone, offline, no key. | The owner kept the feature to speed up entry. Reading on the phone means no Gemini key in the build and no photo leaving the shop. One tick on Review keeps the confirm rule without a tap per field. |
| D-C11 | Which open model reads the label | **Open.** Recommendation: **PaddleOCR (Apache 2.0)**: its text detector finds the lines, then its recogniser reads them. That is the detect-then-read shape of a YOLO setup, built for text. Try PP-OCRv6 small first; PP-OCRv5 mobile has a Latin recogniser if v6's English is weak. Not **Ultralytics YOLO**: it is AGPL-3.0, and this repo has no licence, so shipping it means putting the whole app under AGPL or buying their Enterprise licence. A YOLO-family detector stays parked. If the logs show the reader can't find labels in busy photos, the Apache 2.0 option is YOLOX. **Benchmark before choosing:** run PP-OCRv6 small, PP-OCRv5 mobile and ML Kit Text Recognition v2 on 30 or more real crate labels, VIN plates and certificates from the shop. Compare field accuracy, time on the oldest shop phone, and app size. | A YOLO model finds and boxes objects; it doesn't read characters, so on its own it can't fill a VIN. Printed crate labels and certificates should read well. Stamped or etched VINs on a frame won't read reliably with any of these models, which is why scanning the VIN barcode stays first. ML Kit is free and needs no key, but it isn't an open model, so it's the baseline to beat. |
| Open | Keep label photos to tune the reader later? | **No for now.** Delete them on Save. Revisit with real accuracy numbers from the reporting events. | Photos of certificates of origin are dealer paperwork. Keeping them is a storage and privacy decision for the owner. |

---

## 8. Do not render

- Drawn vehicles or VIN-location drawings (prompt section 5).
- The price on the tag (handoff: never price, status or customer data on the QR code).
- A "Draft sale" button (sales are Reserved).
- "AUTO-POPULATED" banners from the legacy barcode mappings. Maker barcodes can't identify a unit (Scan 2c), so the old mapping prefill isn't carried over.
- A greyed-out Next with no reason.
- "AI", sparkle icons, model or vendor names (no "Gemini", no "engine"), or confidence percentages in the photo flow. It says what it read and that it isn't checked yet.
- A label value shown as checked before the person ticks or changes it.
- An emerald progress bar (status, not an action).

---

## 9. Build and test contract

**Test tags:**

| Element | Tag |
|---|---|
| Frame | `checkin.cancel`, `checkin.back`, `checkin.next`, `checkin.step` |
| VIN | `checkin.vin.photo`, `checkin.vin.scan`, `checkin.vin.kind.vin`, `checkin.vin.kind.serial`, `checkin.vin.field`, `checkin.vin.where`, `checkin.vin.later`, `checkin.vin.none`, `checkin.vin.frameMatch`, `checkin.vin.existing`, `checkin.vin.openExisting` |
| Type | `checkin.type.{typeId}` |
| Make & model | `checkin.make`, `checkin.model`, `checkin.subModel` |
| Year & colour | `checkin.year`, `checkin.year.chip.{year}`, `checkin.colour`, `checkin.colour.chip.{colour}` |
| Price | `checkin.price`, `checkin.price.later` |
| Label photo | `checkin.label.shutter`, `checkin.label.cancel`, `checkin.label.retry`, `checkin.label.box`, `checkin.label.marker.{field}` |
| Review | `checkin.review.edit.{section}`, `checkin.review.labelChecked`, `checkin.notes`, `checkin.save` |
| Print | `checkin.print.later`, `checkin.print.done`, `checkin.print.printer`, `checkin.print.size`, `checkin.print.copies`, `checkin.print.go`, `checkin.print.again`, `checkin.print.sendAgain`, `checkin.print.yes`, `checkin.print.scan`, `checkin.print.another` |
| Sheets | `checkin.leave.keep`, `checkin.leave.go`, `checkin.later.print`, `checkin.later.go` |

**Screenshot tests:** `CheckInScreen(state, …)` is stateless. Roborazzi covers every mockup state, plus these:
- Bluetooth off
- not allowed
- busy
- not ready
- wrong tape
- printing
- failed
- didn't hear back
- too small for the VIN
- 18-character VIN
- Sold match
- serial
- "Add it later"
- price left for later on Review
- Edit with a changed VIN
- label read, partly (VIN, make, model, year)
- label read, VIN fails the check digit
- label unreadable
- label and VIN lookup disagree
- Review with unconfirmed label values, and Save tapped without the tick

Each runs at font scale 1.0, 1.3 and 2.0, at 360 × 640 and 412 × 915, in landscape, and with a fake keyboard inset.

**Behaviour tests:**
- The lookup never overwrites a typed value, the VIN or the notes, and a result for an old VIN is dropped.
- A 16- or 18-character VIN is never saved as a serial without the switch.
- O, I and Q are cleaned before the check and the duplicate check.
- A failing VIN can't be saved without the tick.
- A duplicate can't start or finish a check-in, and is checked again at Save.
- Nothing is saved before Save. Save twice makes one unit (same ID). Process death on Review or Print restores the same draft and never saves twice.
- A direct print is recorded only on `Printed`; failed and unknown record nothing; a cancelled share records nothing.
- Two copies print "Box 1 of 2" and "Box 2 of 2".
- An unpriced unit can't move to Sale pending or Sold.
- Offline: save and print work, and the record is queued once.
- Label photo (parser tests on fixed text lines, no model): it never fills the price, the type or the notes; it never overwrites a typed value; a VIN it reads is cleaned and checked like a typed one; text it isn't sure of leaves the field empty.
- Save is refused while any label value is unconfirmed, and the tick records who and when. Changing a label value confirms it.
- The photo is deleted on Save, on Cancel, and on the next launch after process death. Nothing uploads it.
- With `checkin.labelPhoto` off, the button isn't shown.

**The spec wins** over the mockup where they differ.

---

## 10. Reporting

This follows the owner's rule that every flow is designed with reporting in mind (`reporting-spec.md` §1).

- **Events:**
  - `checkin.started`, `checkin.step_done`, `checkin.abandoned`, `vin.checked`, `vin.lookup` and `checkin.duplicate_blocked`.
  - `print.attempt`, `print.outcome`, `print.asked_did_it_print` and `print.later`.
  - Label photo (D-C10): `label.photo_taken`, `label.read` (fields read, time taken, model version), `label.read_failed` (reason), `label.field_changed` (which field, when a person corrected a value from the label) and `label.confirmed`.
  - The audit log adds `unit.create` and `tag.print`.
- **Reports they feed** (`reporting-spec.md` §4c, §5):
  - check-ins by day, person, type and make
  - drop-off by step
  - how useful the VIN lookup is for powersports
  - how well the label reader does: fields read per photo, how often each field was corrected, and time saved against typed check-ins. These numbers settle D-C11 and decide whether a YOLO-family detector comes back.
  - duplicates caught
  - print failures by reason and printer
  - reprints and their reasons
  - units with no price, no VIN or no tag
  - time from check-in to In stock
- **Data this flow keeps for them:** the unit ID made at step 1 (so an abandoned check-in can be told apart from a finished one), whether the VIN was a VIN, a serial, "later" or "none", and which lookup fields came back. For every field: its source (typed, VIN lookup or label) and, for label values, whether a person changed it before confirming.
