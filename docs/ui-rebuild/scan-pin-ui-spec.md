# InScan: Scan screen and sign-in (PIN) screens

- **Status:** revised after the Phase 4 review gate (`council-review-scan-pin.md`).
  - **Section 3, sign-in screens: LOCKED by the owner on 2026-10-30.** That covers the who screen, PIN entry and its states, the locked state, the idle lock, and the unreachable gate: layout, states and copy as drawn in `prototypes/pin.html`.
  - **Section 2, Scan: LOCKED by the owner on 2026-10-30.** That covers the layout, states, result card and copy as written here and drawn in `prototypes/scan.html`. It includes the three result-card rows added with check-in:
    - "Found, still Incoming", which shows the facts row
    - "Tag out of date"
    - "That's a different tag"
  - The reader behaviour and permission rules (2e, 2f) are build requirements, not part of the lock.
  - The engineering items (D2 offline unlock, D3 server lockout, sections 3e, 5 and 5b) are decided requirements, not part of the lock.
- **Date:** 2026-10-30
- **Mockups:** `prototypes/scan.html`, `prototypes/pin.html` (state picker on the left; the phone frame is the design). Shared tokens: `prototypes/inscan-base.css`.
- **Builds on:** `landing-nav-ui-prompt.md`. Everything in its section 0 locked card applies here unchanged: palette v4 true charcoal, emerald for actions only, depth B (only pressable things get depth), 4-digit PINs, the type families and the role tokens. This doc adds two screens and proposes changes to that prompt (section 4).
- **Colour rule used here:** at most one emerald fill per screen. On screens where the Scan button shows, that is Scan. The Scan screen and the gate have no nav bar, so each gets one emerald primary.

---

## 1. References (opened and looked at this session)

| Reference | What we take | What we leave |
|---|---|---|
| [Cal AI, scanner](https://mobbin.com/screens/2d75d0b8-cdd0-45a0-924a-768e742e4816) | Full-bleed camera, four rounded corner brackets, big icon-plus-word mode tiles at the bottom, the light toggle bottom-left | The X in the top corner. Owner's pick is a big Cancel bar at the bottom instead |
| [Cal AI, result over the photo](https://mobbin.com/screens/0fc4d5ed-a420-4fa7-b1a6-ede4432a378f) | The result slides up as a sheet over the frozen picture, with buttons at the bottom (secondary left, primary right) | Its long scrolling form |
| [ZARA, scanner](https://mobbin.com/screens/cadc9478-2e7a-45df-9413-f4c88c974dfc) | Camera / Keyboard switch, one plain sentence explaining both | ALL-CAPS labels |
| [HBO Max, PIN](https://mobbin.com/screens/6dbf491d-b808-4672-9bfc-ea537e728fad), [HBO Max, wrong code](https://mobbin.com/screens/0225cfd1-2048-402b-9eba-955e02884e5a) | 4 boxes with the current one ringed; full-width keypad; the mismatch message near the boxes | Its keypad is too small (owner: "definitely a larger keyboard"); dialer letters under digits |
| [Disney+, who's watching](https://mobbin.com/screens/fbcb9fd4-1d7c-4aaf-9680-a1f7b671b47e), [Disney+, profile PIN](https://mobbin.com/screens/f7c3d456-e5e6-48e8-9bf6-b288fbefd870) | Who-picker as big round avatars over a background photo under a dark scrim; PIN screen with avatar, name, then 4 tall boxes | Character avatars (people are initials here); "Forgot your PIN?" (the owner resets PINs, so the link reads "Not Dale? Switch user") |
| [Qantas, not connected](https://mobbin.com/screens/18f77caf-19d5-4199-98b4-a36f34096d23) | Centered icon in a circle, a short title, one line, Try again. Owner: "minus the bottom nav", plus a background image | The snackbar over the tab bar; Try again as a small text link (gloves need a full button) |

---

## 2. Scan screen

Opened from the raised Scan button (or from "Scan the box tag" on Home). Full-screen over the shell; the bottom bar is hidden.

### 2a. Layout, iPhone 17 (402 x 874 pt)

From top to bottom:
1. **Camera picture**, full-bleed behind the status bar.
2. **Title strip** (56, `bg` at 80% opacity, no blur): "Scan a tag or VIN" in `type-section` on the left, **"Scanning as Dale"** in `type-meta` / `text-2` on the right (this screen makes a status change, so it says whose name goes on it).
3. **Frame:** four corner brackets, 280 x 220, centered in the picture. `text` colour, 4 dp stroke, 28 dp arms, 14 dp outer radius. Guidance only (see 2e for what the reader accepts).
4. **Instruction** under the frame on a solid 80% `bg` plate (8 dp radius, not a pill): "Point at the QR tag or the VIN barcode." The 80% plate keeps `text` at 7.75:1 even over a white scene.
5. **Light**, bottom-left of the picture: built like Home's signed-in chip (48 tall, `surface-raised`, `border-strong`, chip shadow), icon plus the word "Light". On: accent-tint fill and accent-ink, like the selected nav pill. Its pressed state follows the camera's real torch state, not a local toggle. **Hidden when the camera has no torch** (the iPad Air M3 has no rear flash).
6. **Bottom panel** (solid `surface`, 1 dp top divider):
   - **Mode switch:** two raised tiles, "Camera" and "Type it", 64 tall, icon plus word. Selected: accent-tint fill, accent-ink, chip shadow.
   - **Cancel bar:** full width, 64 tall, raised, "Cancel". Owner's pick. **It never moves and never hides.** When the result card is up, the mode switch hides and the card sits on top of the Cancel bar.

### 2b. States

| State | What changes |
|---|---|
| **Camera** (default) | As 2a. Reader on. |
| **No read after 8 s** | The instruction becomes "Not reading? Turn on the light, or type it in." Without a torch: "Not reading? Move closer, or type it in." Resets on the next read. |
| **Camera not allowed** | Picture replaced by: icon badge, "Camera isn't allowed" (`type-section`), "InScan needs the camera to read tags. You can still type the tag ID." Full-width primary **Allow camera**, which becomes **Open settings** when the system won't ask again (rules in 2f). On iOS with a `restricted` camera, no button: Type it only. Mode switch and Cancel stay. |
| **Camera couldn't start** | Same frame: "The camera couldn't start", "Type the tag ID instead, or try again.", primary **Try again**. |
| **Type it** | Picture hidden. Heading in the strip: "Type a tag ID or VIN". A well field (mono 22/28, capitals, no autocorrect, 56 tall) with a live count ("6 of 8", "12 of 17") at its right edge. Helper: "The 8 characters after ID on the tag, or the 17-character VIN." Primary **Look up**, always enabled; it reads "Look up tag ID" at 8 characters and "Look up VIN" at 17. The keyboard's Search key runs it. Text button "No tag or VIN? Check it in without one". |
| **Type it, wrong length** | On Look up with any other length, the helper becomes: "Tag IDs have 8 characters, VINs 17. You typed 10." (error icon, `text`). |
| **Result card** | See 2c. The camera picture freezes behind it; the brackets take the outcome's status colour. |

**Typing is cleaned before it counts:** spaces dropped, a leading "ID" dropped (the tag prints "ID 3HZW8RTB"), and `ItemId.normalizeShort` applied (O to 0, I and L to 1). A string that starts with `CATV1;` is a whole tag typed by a Bluetooth scanner and is treated as a read.

### 2c. Result card

A good read pauses the reader, plays a confirm haptic, and slides the card up from below (200 ms, `ease-out`, no fade; reduce-motion: 120 ms fade). It sits on top of the Cancel bar. **The layout is the same every time:**

- **Header:** an icon for *what was read* (`qr_code_2` for our tag, `barcode` for a VIN, `keyboard` for typed) in `text-2`, then the outcome in `type-section`, `text` colour. The status colour lives in the brackets and the status tag, not the header.
- **Summary:** for a unit, the flat record card from Home (16/8/16/16 padding, ID plate, status tag, name clamped to 2 lines, one meta line, chevron). **Tapping it opens the unit**, as on Home. In the card the type image is 96 wide, not Home's 128, so the name fits 2 lines at 402 wide. A facts row (Colour, VIN ending in mono, Price) when useful.
- **Note** (only when needed): plain notes are `text` with a coloured icon. Warnings use the status-banner build from prompt section 5 (`warn-tint` fill, `text`, 20 dp icon in `warn-ink`).
- **Buttons:** left is the way back to reading ("Scan another", or "Type another" after a typed lookup), right is the primary when there is one. One button spans the full width.
- **Tap guard:** taps on the card in its first 250 ms are ignored, so a tap meant for what was under the finger can't land on the primary.
- **Height:** the card never grows above the title strip. Its body scrolls; the buttons stay pinned. When the two buttons don't fit side by side (narrow screens, text at 1.5x and up), they stack: the scan-again button on top, the primary underneath.

| Read | Header | Note | Buttons |
|---|---|---|---|
| Our tag or ID; unit In stock, Sale pending or Sold | Found | none | Scan another (full width) |
| Our tag on an **Incoming** unit | Found, still Incoming | "The box tag reads. Check it matches the box: Polaris RZR XP 1000, Gray, VIN ending 104217." Facts row (colour, VIN ending) shown (check-in spec D-C7) | Scan another, **Move to In stock** |
| Our tag, but its details differ from the unit (edited after printing) | Tag out of date | "The details on this tag don't match the unit any more. Reprint it before moving it to In stock." | Scan another, **Reprint tag**. No move. |
| Opened from check-in's Print, and a different tag is read | That's a different tag | "That's tag 3HZW8RTB, not 6MDQ1KXA." | Scan another |
| After Move to In stock, online | Moved to In stock | "5 still incoming." (from the phone's copy) | **Scan next box** (full width) |
| After Move to In stock, offline | Moved on this phone | "It sends when the phone connects. 5 still incoming." | **Scan next box** |
| Move to In stock, but the shop system says it already happened | Already in stock | "Ray moved it at 2:05 pm." | Scan another |
| Typed ID of an Incoming unit | Found, still Incoming | "Scan the tag to move it to In stock." (moving needs the label read, handoff s2) | Type another |
| VIN, not found | Not in the shop system | Check-digit line: "VIN check digit passes." (good icon) or "VIN check digit fails. Check it against the frame." (warn banner) | Scan another, **Check in this unit** (VIN prefilled) |
| VIN, shop system unreachable | Couldn't check the shop system | warn: "If another phone already checked this unit in, don't check it in again." | Scan another, Check in anyway (raised, not emerald; asks to confirm first) |
| Any other barcode (maker barcode, not a VIN or our tag) | This barcode can't identify a unit | "Scan the VIN or the tag instead." | Scan another (full width). **No check-in from it**, so it can't make duplicates. |
| Our tag, unit not on this phone yet and not found in the shop system | Details from the tag | warn: "Another phone checked this unit in and hasn't sent it yet. Don't check it in again." Summary marked "From the tag, may be out of date." | Scan another (full width) |
| Typed 8-character ID, not found | No unit with tag ID 7KD2QX4M | Online: "Check the characters, or scan the tag." Offline: warn "Not on this phone. Another phone may not have sent it yet. Don't check it in again." | Type another (full width) |
| Typed 8-character ID matches more than one unit | More than one unit has tag ID 7KD2QX4M | "Scan the QR code or type the VIN instead." (today's `SHORT_ID_AMBIGUOUS` text; the handoff says a clash is refused) | Type another, **Scan the QR code** |
| Session expired or revoked mid-scan | Sign in again | "Your scans are saved." | **Sign in** (goes to the gate; returns here after) |

Rules:
- **Move to In stock is the only status change on this screen.** It needs its own tap, is disabled on the first tap, and is queued once (skip a second pending confirm for the same unit in the outbox).
- **Online, refresh first:** before showing "Found, still Incoming", ask the shop system for the unit, so a unit that's already in stock or sold shows that instead. The confirm call returns `changed: false` plus who and when if it was already done.
- Cancel and Scan another never change a status. Cancel returns to where Scan was opened.

### 2d. Back, per state

| State | System back (Android), edge swipe (iOS) |
|---|---|
| Camera | Same as Cancel |
| Type it | Closes the keyboard first; next back returns to Camera |
| Result card | Same as Scan another (dismiss the card, resume the reader) |
| Camera not allowed | Same as Cancel |

Overlays use `PredictiveBackHandler`. Target SDK 36 doesn't call `onBackPressed`, so no code relies on it.

### 2e. Reader behaviour (build notes)

- **Freeze:** on a read, clear the analyzer (`ImageAnalysis.clearAnalyzer()`), grab `PreviewView.getBitmap()` on the main thread and lay it over the preview. Keep the preview bound so resuming is instant. Unbind on `ON_STOP`. Keep the read value in `SavedStateHandle`; if the OS killed the app, show the card over a plain `bg` background.
- **One read at a time:** each read carries a session number checked in the main-thread callback (a frame already decoding can finish after the pause). Take one code per frame.
- **No instant re-read:** after Scan another, ignore the value just read until it has been out of view for about 1 s.
- **Only what the person can see:** accept codes inside the visible picture only (`MlKitAnalyzer` with view coordinates, or a `ViewPort`), not the parts of the 4:3 analysis frame cropped off or under the bottom panel. With several codes in view, take the one nearest the frame centre, preferring our QR tag, then a valid VIN, then anything else.
- **Formats** stay as today: QR, Data Matrix, Code 128, Code 39.
- **Bluetooth scanners** (keyboard wedge): a burst of keys ending in Enter, faster than a person types, counts as a read in any mode. The whole string goes to the same lookup (`matchOnPhone`).
- **Torch:** CameraX `CameraControl.enableTorch`, state from `CameraInfo.getTorchState()`. It goes off whenever the camera closes. iOS: `AVCaptureDevice.torchMode` inside `lockForConfiguration`.
- **Haptics:** read = `View.performHapticFeedback(CONFIRM)`; fallback below API 30. iOS: `UINotificationFeedbackGenerator` `.success`. iPads have no haptics; the brackets and card carry the signal.

### 2f. Camera permission

- Ask with the system prompt. Show **Open settings** when the answer is a denial and `shouldShowRequestPermissionRationale` is false **after we have asked at least once** (keep a saved "asked" flag). This covers Android 11+ (two refusals) and Android 7 to 10 ("Don't ask again"). Re-check on every `ON_RESUME`; an "Only this time" grant runs out.
- iOS: `notDetermined` → ask once; `denied` → Open settings; `restricted` → no button, Type it only. Needs `NSCameraUsageDescription`.
- Manifest: `<uses-feature android:name="android.hardware.camera" android:required="false">`, so tablets without a rear camera can still install.

### 2g. Accessibility

- The camera picture is decorative. On open: "Scanning. Point the camera at a tag."
- When the card opens, focus moves to its header. The record card is one element and reads like Home's ("2026 Honda FourTrax Rancher 4x4. Incoming. Tag 3 H Z W 8 R T B…"). IDs are read character by character (`VerbatimTtsAnnotation`; iOS: spelled-out accessibility label).
- The card is not modal: Cancel stays reachable under it.
- Type it is the full alternative to the camera for finding a unit. Moving a unit to In stock still needs its label read (camera or Bluetooth scanner), by the handoff's locked rule.

### 2h. Other screens and sizes

- **Type it with the keyboard up:** `windowSoftInputMode="adjustResize"`; the panel (mode switch + Cancel) takes `imePadding()`; the content between the title strip and the panel scrolls, so it fits on a 360 x 640 phone. `ImeAction.Search` runs Look up; `KeyboardCapitalization.Characters`, autocorrect off. Edge-to-edge is enforced from target SDK 35, so insets are handled explicitly.
- **Window size classes** (prompt 4b), not device names. Compact: as drawn. Medium and Expanded: the card, mode switch and Cancel are 480 wide and centred at the bottom. Android 16 ignores orientation locks at sw600 and up, so landscape is real: picture full-bleed, card and controls in a 400-wide column on the trailing side. Update the analysis rotation on rotate.
- **iOS via Compose Multiplatform:** no shared camera. Define a `BarcodeReader` expect/actual: CameraX + ML Kit on Android; `AVCaptureMetadataOutput` (QR, Data Matrix, Code 128, Code 39) inside a `UIKitView` on iOS. Pro iPhones focus poorly up close, so zoom based on `minimumFocusDistance`. Compose on iOS may fight `imePadding()` with its own focus scrolling (inferred; check on device).

---

## 3. Sign-in screens (the "who" gate)

The gate is its own root: back on the who screen leaves the app; back on the PIN screen goes to who. While the gate shows, the shell under it is hidden from screen readers, and the predictive-back preview never reveals it.

### 3a. Who's using this phone

- Background: the shop photo (Yoshi supplies it) under an **80%** `bg` scrim. (Worst case, a white photo: `text` 7.75:1, `text-2` 5.18:1. At the brief's 75%, `text-2` fell to 4.36:1.) Same treatment as the unreachable screen, so all gate screens share one look and shell screens stay flat charcoal. Body copy on the photo uses `text`; only meta lines use `text-2`.
- Heading "Who's using this phone?" in `type-screen-title`, centred.
- **People as big round buttons:** 88 dp raised initials disc (`type-big-number`), name under it in `type-tile-label`. Three per row, alphabetical. Scrolls past 9.
- Footer meta: "This phone: Counter phone" (the name given when it was connected).
- **"Name and PIN" mode:** tapping a person opens 3b. **"Name only" mode:** tapping a person signs them in (needs server work: section 5).

### 3b. PIN entry (owner's pick: Disney boxes plus a bigger keypad than HBO Max)

From top to bottom:
1. Initials avatar, 72 dp, **flat and neutral** (`surface-raised`, 1 dp `divider`, initials in `text`, `type-app-title`). It can't be pressed here, so no depth and no emerald.
2. Name, `type-screen-title`.
3. **"Not Dale? Switch user"** text button, 48 tall, right under the name. Always visible; the keypad is in-app, so no system keyboard can cover it.
4. 16 gap, then "Enter your PIN" in `type-body`, `text-2`.
5. **Four boxes**, 60 x 68, gap 12. Inset wells, like the Find field. The current box has a 2 dp `accent-ink` ring. A filled box shows a dot, never the digit.
6. **Message line:** at least 40 dp, so nothing jumps; it grows with large text instead of overprinting.
7. **Keypad**, starting right under the message line and filling to the bottom (owner: "almost all the way to the input boxes"). 3 x 4 raised keys, 8 dp gaps, rows share the space (about 90 dp each on iPhone 17). Digits in the new **Keypad digit** role, Barlow Semi Condensed 44/48 Bold (needs the owner's OK: D5). Bottom row: empty slot, 0, Delete (icon plus the word). No letters, no Sign in key: **the 4th digit signs in**, once.

States:

| State | What shows |
|---|---|
| Empty | First box ringed |
| Typing | Dots fill left to right; the ring moves. A hidden polite live region says "2 of 4 entered". |
| Checking | All 4 dots; keypad disabled. "Checking…" with a small `text-2` spinner, shown only if it takes over 300 ms. |
| Wrong PIN | Boxes get `danger-ink` rings, a short shake (6 dp, 3 times, 240 ms; reduce-motion: none), a reject haptic, and "That PIN didn't match. Try again." in `danger-ink`. When the server says 2 or fewer tries are left: a second line in `text`, "2 tries left before a 5-minute lock." The dots fade out over 120 ms and the boxes clear at 600 ms. |
| Wrong PIN, offline unlock | Same, but the second line reads "2 tries left before Dale is signed out of this phone. After that, signing in needs the shop system." |
| Locked | The keypad's place goes to a notice card (prompt section 5 build: 16 radius, 20 padding, 24 dp lock icon in `danger-ink` beside a `type-section` title, body in `text`, full-width raised 56 dp button). Title "Dale is locked until 2:19 pm"; body "Too many wrong PINs. Try again then, or ask the owner to reset the PIN."; button **Switch user**. The top "Not Dale?" link hides (the button replaces it). Focus moves to the title. At 2:19 pm the keypad comes back with the announcement "You can try your PIN again." |
| Idle lock (same person) | Under "Enter your PIN": "Locked after 30 minutes idle. You'll pick up where you left off." (meta, with a lock-clock icon). Not above the name: section 9 bans eyebrow lines. |

**Sizes:**
- Below about 720 dp of height (a 360 x 640 phone): the avatar hides, the name line stays, and keys may shrink to 56 dp. Otherwise keys never go under 64.
- Font scale: the message line grows; keypad rows shrink first; the key digit stops growing at 1.3x.
- Landscape (and Medium/Expanded widths): boxes and message on the left half, keypad on the right, max 400 wide each.

### 3c. Can't reach the shop system (gate `unreachable`)

Qantas message on the shop photo, no bottom nav:
- Centred in the space above the buttons: 72 dp `warn-tint` circle with the cloud-off icon in `warn-ink`; title "Can't reach the shop system" (`type-screen-title`); body in `text`: "Signing in as Ray needs the shop system. Check the shop Wi-Fi, then try again."; meta "Last tried 2:21 pm. Dale is still signed in on this phone." (shown only with the second button).
- **Buttons in the bottom band**, like every other screen's main action: primary **Try again** (emerald, the screen's one primary) and, only if someone still has a valid session on this phone, raised **Log back in** (owner's wording; the meta line above says whose session it is).
- **"Log back in" opens Dale's PIN screen** (checked on the phone, as in 3e). It never opens his session directly, or anyone could get in by failing a sign-in offline.

### 3d. Accessibility and input

- Keypad keys are buttons named "1" to "0" and "Delete last digit". Like the system lock screen, a screen reader speaks a key as the finger explores it; **the entered PIN is never read back**, and the boxes only say how many digits are in.
- The boxes are one element: "PIN, 2 of 4 digits entered".
- The message line is a polite live region.
- Focus moves to the new title when the screen changes (who → PIN → locked).
- **Hardware keys:** the PIN screen requests focus on entry and reads `Key.Zero`–`Key.Nine` and `NumPad0`–`NumPad9` in `onPreviewKeyEvent`; Backspace deletes. A burst of more than 4 characters in under 100 ms (a Bluetooth scanner) is dropped, so a scan can't burn PIN tries.
- **Secure display:** `FLAG_SECURE` while the PIN screen shows (no screenshots, recordings or casts of key presses). `setRecentsScreenshotEnabled(false)` (API 33+) so the recent-apps thumbnail doesn't show the shell behind an idle lock. iOS: cover the screen on `willResignActive`.
- Under "Name only", only 3a and 3c apply.

### 3e. Offline unlock (same person)

Recommended in D2. Only for the person whose session is on this phone; switching to someone else needs the shop system.
- At sign-in, the app computes an **HMAC of the PIN under an Android Keystore key that can't be exported** (iOS: Keychain/Secure Enclave equivalent) and stores only that. A copy of the app's files is useless without the device's hardware key.
- The failed-try count is saved **before** each check. 5 wrong local tries sign the person out of this phone.
- The stored value is wiped on sign-out, switch user and the 5th wrong try.
- Offline unlock is refused after the session's `expiresAt` (returned at login: section 5).
- **Shop phones should run release builds.** Debug builds let anyone with a cable read app data.

---

## 4. Changes made to `landing-nav-ui-prompt.md`

These changed unlocked parts of the prompt. Applied on 2026-10-30 after the owner said yes to D1–D6.

1. **Section 8, "Good scan":** was "a 200 ms emerald frame flash and a strong haptic, then the app goes straight to the record". Becomes: "the brackets take the outcome's status colour, a confirm haptic, and the result card (Scan spec 2c) slides up over the frozen picture".
2. **Section 4d, scan route:** was "closes on a good read". Becomes: "a good read shows the result card; tapping the unit opens `stock/{unitId}`; Check in this unit opens check-in with the VIN; Scan another resumes; Cancel returns to where Scan was opened".
3. **Section 4d, idle lock:** add "the same person can unlock offline (Scan spec 3e); switching to someone else needs the shop system".
4. **Section 5, unreachable screen:** scrim 80% (was 75%); Try again is the screen's one emerald primary (was raised) and sits in the bottom band with "Log back in"; title at `type-screen-title` 28 (the prompt's 24 isn't on the type scale).
5. **Section 6, type scale:** add "Keypad digit, Barlow Semi Condensed 44/48 Bold" (D5).

---

## 5. Data, server and app needs

| Need | Why | Where |
|---|---|---|
| PIN exactly 4 digits (decided) | Four boxes, sign in on the 4th digit | `server/.../Auth.kt` line 32 still allows 4 to 8 |
| `attemptsLeft` on `WRONG_PIN` | "2 tries left before a 5-minute lock." Without it the line is left out | `Auth.login` counts `failed_attempts` but doesn't return it |
| `lockedUntil` on `PIN_LOCKED` | "Locked until 2:19 pm", and bringing the keypad back on time. Without it, today's copy: "Wait 5 minutes, then try again." | `Auth.login` sets `locked_until`; not returned |
| `expiresAt` on login | Refuse offline unlock after the session ends | `LoginResponse` has no expiry; the session lasts 12 h (`Auth.kt`) |
| `employeeId` on every queued change | Today a queued change is sent under whoever is signed in when it syncs, so the audit log and `confirmed_by` can name the wrong person | `OutboxEntry` has no employee field (`ShopStore.kt`). Switch user sends the previous person's queue with their token first (switching needs the shop system anyway). If their token is dead, hold those changes and show "3 of Dale's changes waiting". |
| `changed: false` plus who and when on tag confirm | "Already in stock (Ray, 2:05 pm)" | `Tags.confirm` returns the unit unchanged and silently today |
| Unit refresh before "Found, still Incoming" (online) | The phone's copy can be stale | `onBarcodeScanned` uses the phone's copy first and doesn't ask the server on a phone match |
| `id` and initials from `/auth/employees` | `who.person.{employeeId}` and the "DL" discs | Returns display names only |
| "Name only" sign-in | Shop setting from the router | `login` requires a PIN: server work |
| Keystore HMAC for offline unlock | 3e | App only |
| Outbox: skip a second pending confirm for the same unit | Double tap on Move to In stock | `changeLocally` always appends (`ShopStore.kt`) |
| "N still incoming" | After Move to In stock | Count from the phone's copy; no server change |
| Device name | "This phone: Counter phone" | Already stored at connect (`devices.label`) |

### 5b. Queued check-ins must survive resets (owner requirement, 2026-10-30)

**Already true in the code (checked 2026-10-30):**
- The queue is the `outbox` table in the phone's Room database (`ShopStore.kt`). It survives app restarts, crashes, the OS killing the app, reboots and a dead battery.
- An entry leaves the queue only in two ways:
  - when the shop system accepts it. `recordSent` removes it and stores the server's copy in one transaction.
  - when an employee taps discard on a refused change (`ShopSync.discard`). A refused change otherwise stays in Needs attention and is never dropped silently.
- **App updates can't wipe it.** The database has no destructive fallback: a schema change without a migration fails loudly (`AppDatabase.kt`). `AppDatabaseMigrationTest` checks the migrated schema.
- **Sign out and reconnecting keep it.** They clear only the sign-in file (`shop_connection.xml`), not the database.

**Add:**

| Gap | Rule |
|---|---|
| Discarding a refused check-in deletes the unit from the phone, even if its tag is already on a box. This is the "tag that points at nothing" case from D7. | Discard on a refused check-in whose tag was printed asks to confirm and names the tag: "Tag 3HZW8RTB is already printed. Discarding leaves a tag that finds nothing. Fix it and send instead?" The buttons are **Fix it** (primary) and **Discard anyway**. |
| Sign out or switch user with changes waiting | Switching sends the previous person's queue first (section 5). Sign out says "3 changes still waiting. They send under your name when the phone reconnects." That needs `employeeId` on each entry (section 5). |
| Connecting the phone to a different shop system with changes waiting | The waiting changes would go to the wrong shop system. So refuse with "3 changes haven't been sent to this shop system yet. Send them first." |
| Nobody can see that changes are waiting | The ocean "Sending 3 changes…" banner (prompt section 5) shows whenever the queue isn't empty. More → "Send changes now" shows the count. |
| Tests | Kill the process with entries queued, and they are still there on restart. An upgrade migration keeps outbox rows (extend `AppDatabaseMigrationTest` to insert rows before migrating and assert them after). Sign out keeps rows. Discarding a printed check-in asks first. |

**Can't be protected inside the app:** uninstalling, Android's "Clear storage", a factory reset, or a lost or broken phone. With the phones normally online, a check-in leaves the queue within seconds, so the window is small.

Android's daily cloud backup already includes the database (only the sign-in file is excluded in `data_extraction_rules.xml` and `backup_rules.xml`). A restored phone gets back what was queued at its last backup. That is a bonus, not something to rely on.

---

## 6. Decisions for the owner

Owner answer, 2026-10-30: **yes to D1–D6** (decided, not locked). **D7: not now.** Instead, queued check-ins must survive resets (section 5b).

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D1 | After a good read, show the result card, or jump straight to the record (today's spec)? | **Result card, always.** | Confirming a pallet of incoming boxes becomes two taps per box instead of four, and every read looks the same. Cost: one extra tap to open an in-stock unit (tap the card). |
| D2 | Can the same person unlock after idle while the phone is offline? | **Yes, same person only, with the Keystore HMAC in 3e,** and release builds on shop phones. | Nobody gets stuck in the yard. My first draft said a stored PIN check adds no risk; the review showed a plain stored hash of a 4-digit PIN can be cracked in seconds from a copied file, so it must be bound to the phone's hardware key. |
| D3 | Server lockout changes | **Yes to `attemptsLeft` and `lockedUntil`.** Also recommended: forget failed tries after a quiet period, and lengthen repeat locks (5, then 15, then 60 minutes, then an owner reset) with an alert to the owner. | Today the failed count never expires, so "2 tries left" can appear on someone's first try of the day, and 5 guesses every 5 minutes covers all 10,000 PINs in days. Anyone can also lock a coworker out on purpose. |
| D4 | Who screen background: the shop photo, or flat charcoal? | **Shop photo**, 80% scrim, same as the unreachable screen. | Your Disney and Qantas + CHOPT picks both use it, and it marks gate screens as different from the working app. |
| D5 | Add a "Keypad digit" size (44/48) to the type scale? | **Yes.** | 34 px digits were a quarter of the key's height; 44 reads at arm's length with gloves. |
| D6 | Unreachable screen: Try again and Log back in in the bottom band (drawn), or under the text like Qantas? | **Bottom band.** | Same place as every other main button in the app, in thumb reach. The message keeps the Qantas look. |
| D7 | Owner-only "Log from the tag" for a unit whose check-in was lost? | **Not now.** | New scope. The tag's own ID would be reused, so it can't make a duplicate; worth adding if lost check-ins actually happen. |

---

## 7. Do not render (additions to the prompt's section 9)

- A scanning laser line or animated grid (the current app has both).
- "DECODING…" progress or percentages: reads are instant.
- Fake camera content beyond a labelled placeholder and our own tag or a VIN sticker (both generated from real data, not drawn).
- Biometric unlock (fingerprint and face can't tell which employee is holding a shared phone).
- "Forgot your PIN?" (the owner resets PINs).
- Letters under keypad digits.
- A list of units to pick from on a short-ID clash (the handoff says a clash is refused).

---

## 8. Build and test contract

- **Test tags:**

  | Element | Tag |
  |---|---|
  | Scan: title, Light, mode tiles, Cancel | `scan.title`, `scan.light`, `scan.mode.camera`, `scan.mode.type`, `scan.cancel` |
  | Scan: type field, Look up, no-tag link | `scan.type.field`, `scan.type.lookup`, `scan.type.noTag` |
  | Scan: permission | `scan.permission.allow`, `scan.permission.settings` |
  | Result card | `scan.result`, `scan.result.open` (the record card), `scan.result.primary`, `scan.result.again`, `scan.result.checkInAnyway` |
  | Who list | `who.person.{employeeId}` |
  | PIN | `pin.boxes`, `pin.key.0` to `pin.key.9`, `pin.key.delete`, `pin.switchUser`, `pin.message`, `pin.locked.switch` |
  | Unreachable | `gate.unreachable.retry`, `gate.unreachable.back` |

  The mockups carry the same values as `data-testid`.
- **Stateless and previewable:** `ScanScreen(state, …)` and `PinScreen(state, …)` take a state object, so every row of the state tables is a screenshot test (Roborazzi) at font scale 1.0, 1.3 and 2.0, **at 360 x 640 and 412 x 915, and in landscape**.
- **Behaviour tests:**
  - The 4th digit submits exactly once (a double tap on the last key doesn't send twice).
  - A read while the result card is up is ignored; a frame finishing after the pause is dropped by its session number.
  - The value just read isn't read again after Scan another until it has left the picture.
  - Move to In stock is queued once, even with two taps.
  - A typed lookup never offers Move to In stock.
  - A maker barcode never offers Check in.
  - Wrong PIN never shows or announces the digits.
  - A burst of more than 4 keys in under 100 ms on the PIN screen is dropped.
  - "Log back in" opens Dale's PIN screen, never his session.
  - Offline unlock refuses a different person, refuses after `expiresAt`, and signs out after 5 wrong local tries (counter saved before each check).
  - A queued change keeps the employee who made it through a user switch.
- **Clock injected** for "Locked until 2:19 pm", "Last tried", the keypad's return and the 8-second hint.
- **The spec wins** over the mockup where they differ.

---

## 9. Reporting

This follows the owner's rule that every flow is designed with reporting in mind (`reporting-spec.md` §1).

- **Scan records these events:**
  - `scan.opened`, `scan.read` (with how it was read, the format, the time to read and the outcome), `scan.hint_shown`, `scan.light`, `scan.camera_problem` and `scan.cancelled`.
  - The confirm writes `tag.confirm` and `unit.status:IN_STOCK` to the audit log.
- **Sign-in records these events:**
  - `login` and `login.failed` (audit log).
  - Lockouts, written by the server as audit `refused:PIN_LOCKED`.
  - `signin.idle_lock`, `signin.offline_unlock`, `signin.switch_user` and `gate.unreachable`.
- **Reports they feed** (`reporting-spec.md` §4a, §4b, §5):
  - scans by day and person, the outcome mix, typed vs camera, phones with camera trouble
  - time in Incoming
  - sign-ins, failed PINs and lockouts
  - shared-phone hand-offs
  - how often the 30-minute idle lock fires (to tune the default)
