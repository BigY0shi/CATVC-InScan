# InScan: Scan screen and sign-in (PIN) screens

- **Status:** draft for the owner's review. Nothing here is locked until the owner says "lock it".
- **Date:** 2026-10-30
- **Mockups:** `prototypes/scan.html`, `prototypes/pin.html` (state picker on the left; the phone frame is the design).
- **Builds on:** `landing-nav-ui-prompt.md`. Everything in its section 0 locked card applies here unchanged: palette v4 true charcoal, emerald for actions only, depth B (only pressable things get depth), 4-digit PINs, the type scale and the role tokens. This doc adds two screens and proposes three changes to that prompt (section 4 below).

---

## 1. References (opened and looked at this session)

| Reference | What we take | What we leave |
|---|---|---|
| [Cal AI, scanner](https://mobbin.com/screens/2d75d0b8-cdd0-45a0-924a-768e742e4816) | Full-bleed camera, four rounded corner brackets, big icon-plus-word mode tiles at the bottom, the light toggle bottom-left | The X in the top corner. Owner's pick is a big Cancel bar at the bottom instead |
| [Cal AI, result over the photo](https://mobbin.com/screens/0fc4d5ed-a420-4fa7-b1a6-ede4432a378f) | The result slides up as a sheet over the frozen picture, with two buttons at the bottom (secondary left, primary right) | Its long scrolling form |
| [ZARA, scanner](https://mobbin.com/screens/cadc9478-2e7a-45df-9413-f4c88c974dfc) | Camera / Keyboard switch, one plain sentence explaining both | ALL-CAPS labels |
| [HBO Max, PIN](https://mobbin.com/screens/6dbf491d-b808-4672-9bfc-ea537e728fad), [HBO Max, wrong code](https://mobbin.com/screens/0225cfd1-2048-402b-9eba-955e02884e5a) | 4 boxes with the current one ringed; full-width keypad; the mismatch message sits above the boxes | Its keypad is too small (owner: "definitely a larger keyboard"); dialer letters (ABC) under digits |
| [Disney+, who's watching](https://mobbin.com/screens/fbcb9fd4-1d7c-4aaf-9680-a1f7b671b47e), [Disney+, profile PIN](https://mobbin.com/screens/f7c3d456-e5e6-48e8-9bf6-b288fbefd870) | Who-picker as big round avatars over a background photo under a dark scrim; PIN screen with avatar, name, then 4 tall boxes | Character avatars (people are initials here); "Forgot your PIN?" (PINs are reset by the owner, so the link reads "Not Dale? Switch user") |
| [Qantas, not connected](https://mobbin.com/screens/18f77caf-19d5-4199-98b4-a36f34096d23) | Centered icon in a circle, a short title, one line, Try again. Owner: "minus the bottom nav", plus a background image | The snackbar over the tab bar; Try again as a small text link (gloves need a full button) |

---

## 2. Scan screen

Opened from the raised Scan button (or from "Scan the box tag" on Home). Full-screen over the shell; the bottom bar is hidden.

### 2a. Layout, iPhone 17 (402 x 874 pt)

From top to bottom:
1. **Camera picture**, full-bleed behind the status bar.
2. **Title strip** (56, `bg` at 72% opacity, no blur): "Scan a tag or VIN" in `type-section`.
3. **Frame:** four corner brackets, 280 x 220, centered in the picture. White (`text`), 4 dp stroke, 28 dp arms, 14 dp outer radius. The brackets are guidance only: the reader reads anywhere in the picture.
4. **Instruction** under the frame on a solid `bg` 72% plate (8 dp radius, not a pill): "Point at the QR tag or the VIN barcode."
5. **Light** button, bottom-left of the picture: raised, icon plus the word "Light", `aria-pressed`. On: accent-tint fill and accent-ink, like the selected nav pill.
6. **Bottom panel** (solid `surface`, 1 dp top divider):
   - **Mode switch:** two tiles, "Camera" and "Type it", 64 tall, icon plus word. Selected: accent-tint fill, accent-ink, chip shadow. Unselected: flat on `surface` with a `border-strong` outline.
   - **Cancel bar:** full width, 64 tall, raised, the word "Cancel" in `type-tile-label`. Owner's pick from Cal AI. It sits in the same spot in every state of this screen.

### 2b. States

| State | What changes |
|---|---|
| **Camera** (default) | As 2a. Reader on. |
| **No read after 8 s** | The instruction changes to "Not reading? Turn on the light, or type it in." Nothing else moves. Resets on the next read. |
| **Camera not allowed** | Picture replaced by: icon badge, "Camera isn't allowed", "InScan needs the camera to read tags. You can still type the tag ID." Primary button "Allow camera". After two refusals Android stops asking, so the button becomes "Open settings". Mode switch and Cancel stay. Never a dead screen. |
| **Camera couldn't start** | Same frame as above with "The camera couldn't start", "Type the tag ID instead, or try again.", primary "Try again". (The current app already has this failure path.) |
| **Type it** | Picture hidden. Heading "Type the tag ID or VIN"; a well field (mono, capitals, no autocorrect, 56 tall); helper "The 8 characters under the QR code, or the 17‑character VIN."; a live count ("6 of 8"); primary "Look up", enabled only at exactly 8 or 17 characters; text button "No tag or VIN? Check it in without one". The system keyboard opens; mode switch and Cancel ride above it. |
| **Result card** | See 2c. The camera picture freezes behind it; the brackets turn `good-ink` around the read. |

### 2c. Result card

A good read pauses the reader, plays a confirm haptic, and slides up a card over the frozen picture (200 ms). The card replaces the bottom panel. **Layout is the same in every case**, so people learn it once:

- **Header line:** icon plus word saying what happened ("Found", "Tag reads", "Not in the shop system", "From the label").
- **Summary:** for a unit, the same flat record card as Home (type image, ID plate, status, name, one meta line), plus a facts line (colour, VIN ending, price when set).
- **Note** (only when needed): one or two lines in the status ink with its icon.
- **Two buttons at the bottom:** left is always **"Scan another"** (raised, neutral; resumes the camera), right is the **primary** (emerald). Same positions every time.

| Read | Header | Note | Primary | Also |
|---|---|---|---|---|
| Our tag or ID, unit In stock / Sale pending / Sold | Found | none | **Open unit** | |
| Our tag on an **Incoming** unit | Tag reads | "The tag on the box reads. Moving it puts the unit in stock." | **Move to In stock** | "Open unit" text button in the header |
| After Move to In stock | Moved to In stock | "5 still incoming." (count from the phone) | **Scan next box** | "Open unit" text button |
| VIN or maker barcode, not found | Not in the shop system | VIN check: "VIN check digit passes" (good) or "VIN check digit fails. Check it against the frame." (warn) | **Check in this unit** (VIN prefilled) | |
| Same, but the shop system couldn't be reached | Not on this phone | warn: "Couldn't check the shop system. If another phone already logged this unit, don't check it in again." | **Check in anyway** | |
| Our tag, unit not on this phone yet (logged on a phone that hasn't sent it) | From the label | warn: "Logged on another phone that hasn't sent it yet. Don't check it in again." Label details shown, marked "from the label, may be out of date". | **Done** (closes Scan) | |
| Typed 8-character ID matches more than one unit | 2 units end in 7KD2QX4M | none | none | The matching units as tappable record cards. Needs the phone match to return the candidates (section 5). |

Rules:
- **Move to In stock is the only status change on this screen**, and it needs its own tap (the current app's rule: "Only from the explicit button after scanning the label"). It goes through the outbox, so it works offline.
- While the card is up the reader is paused, so one box can't be confirmed twice.
- Cancel and Scan another never change a status.
- Cancel returns to the screen Scan was opened from. "Done" does the same.

### 2d. Accessibility and platform

- The camera picture is decorative. The reader announces "Scanning. Point the camera at a tag." on open.
- When the card opens, focus moves to its header; the screen reader reads header, unit name, status, note. IDs are read character by character (`VerbatimTtsAnnotation`; iOS: spelled-out accessibility label).
- "Type it" is the full accessible alternative: everything a scan does, typing does.
- Light uses CameraX `CameraControl.enableTorch` (iOS: `AVCaptureDevice.torchMode`). Hidden when the camera has no flash.
- Reader formats stay as today: QR, Data Matrix, Code 128, Code 39.
- **Tablet (iPad 820 x 1180):** same screen; the result card is 480 wide, centered at the bottom; mode switch and Cancel are 480 wide, centered.

---

## 3. Sign-in screens (the "who" gate)

### 3a. Who's using this phone

- Background: the shop photo (Yoshi supplies it) under an **80%** `bg` scrim. Same treatment as the unreachable screen, so all gate screens share one look and shell screens stay flat charcoal. (80%, not the 75% in the design brief: measured worst case, a white photo, puts `text-2` at 4.36:1 under 75% and 5.18:1 under 80%.)
- Heading "Who's using this phone?" in `type-screen-title`, centered.
- **People as big round buttons:** 88 dp initials avatar (raised, because it's pressable), name under it in `type-tile-label`. Three per row, alphabetical (the server's order). Scrolls if there are more than 9.
- Footer meta: "This phone: Counter phone" (the name given when it was connected).
- **"Name and PIN" mode:** tapping a person opens 3b. **"Name only" mode:** tapping a person signs them in.

### 3b. PIN entry (owner's pick: Disney boxes plus a bigger keypad than HBO Max)

From top to bottom:
1. Initials avatar, 72 dp (not pressable here, so flat: accent-tint fill, accent-ink initials).
2. Name, `type-screen-title`.
3. "Enter your PIN" in `type-body`, `text-2`.
4. **Four boxes**, 60 x 68, gap 12. Inset wells (same as the Find field). The current box has the 3 dp `accent-ink` ring. A filled box shows a dot, never the digit.
5. **Message line** (fixed 40 dp slot so nothing jumps): empty, or the checking / wrong-PIN text.
6. **"Not Dale? Switch user"** text button, 48 tall. Always visible: the keypad never covers it, because it's an in-app keypad, not the system keyboard.
7. **Keypad** fills from the bottom up to just under that link (owner: "almost all the way to the input boxes"). 3 x 4 raised keys, 8 dp gaps, rows share the space (about 92 dp tall each on iPhone 17, never under 64). Digits in Barlow Semi Condensed 34/40 Bold. Bottom row: empty slot, 0, Delete (icon plus the word). No letters under digits, no Sign in key: **the 4th digit signs in**.

States:

| State | What shows |
|---|---|
| Empty | First box ringed |
| Typing | Dots fill left to right; the ring moves |
| Checking | All 4 dots; message "Checking…" with a small spinner; keypad disabled for that moment |
| Wrong PIN | Boxes get `danger-ink` outlines, a short shake (6 dp, 3 times, 240 ms; reduce-motion: no shake), message "That PIN didn't match. Try again." in `danger-ink`. When 2 or fewer tries are left, a second line: "2 tries left before a 5-minute lock." Boxes clear after 600 ms. |
| Locked | Keypad replaced by a notice card (Oura-style, the owner's pick for important notices): title "Dale is locked for 5 minutes", body "Too many wrong PINs. Try again at 2:19 pm, or ask the owner to reset the PIN.", and a full-width raised button "Switch user". |
| Idle lock (same person) | Above the avatar: "Locked after 30 minutes idle". Under "Enter your PIN": "You'll pick up where you left off." |

### 3c. Can't reach the shop system (gate `unreachable`)

Qantas layout on the shop photo, no bottom nav:
- Centered: 72 dp circle (`warn-tint`) with the cloud-off icon in `warn-ink`.
- Title "Can't reach the shop system" (`type-screen-title`).
- One line: "Signing in as Ray needs the shop system. Check the shop Wi-Fi, then try again."
- Primary, full width, 56: **Try again**.
- Secondary (only if someone still has a valid session on this phone): raised "Back to Dale". Dale is still signed in, so going back to Dale works offline.
- Meta: "Last tried 2:21 pm".

### 3d. Accessibility and platform

- The keypad keys are buttons with plain names ("1" to "0", "Delete last digit"). Hardware keyboards and Bluetooth scanners can type digits directly; Backspace deletes.
- The boxes are one control for the screen reader: "PIN, 2 of 4 digits entered". Digits are never spoken.
- The message line is a polite live region, so "That PIN didn't match" is announced without moving focus.
- Under "Name only", nothing in this section shows except 3a and 3c.
- **Tablet:** the content is a centered 400 dp column; the keypad stays 400 wide.

---

## 4. Proposed changes to `landing-nav-ui-prompt.md`

These change unlocked parts of the prompt. Applied only if the owner agrees (D1 to D3 in section 6).

1. **Section 8, "Good scan":** was "a 200 ms emerald frame flash and a strong haptic, then the app goes straight to the record". Becomes: "the brackets turn `good-ink`, a confirm haptic, and the result card slides up over the frozen picture (section 2c of the Scan spec)".
2. **Section 4d, scan route:** was "closes on a good read". Becomes: "a good read shows the result card; Scan another resumes; Open unit goes to `stock/{unitId}`; Check in this unit opens check-in with the VIN; Cancel returns to where Scan was opened".
3. **Section 4d, idle lock:** add "the same person can unlock offline; switching to someone else needs the shop system".

---

## 5. Data and server needs

| Need | Why | Where |
|---|---|---|
| PIN exactly 4 digits (already decided) | Four boxes, sign in on the 4th digit | `server/.../Auth.kt` line 32 still allows 4 to 8 |
| `attemptsLeft` on `WRONG_PIN` | "2 tries left before a 5-minute lock." Without it the line is left out | `Auth.login` already counts `failed_attempts`; the response doesn't carry it |
| `lockedUntil` on `PIN_LOCKED` | "Try again at 2:19 pm". Without it the copy falls back to "Wait 5 minutes, then try again." (today's `Problem.PIN_LOCKED` text) | `Auth.login` sets `locked_until`; not returned |
| Offline unlock for the same person | Idle lock shouldn't strand someone in the yard | App only: at sign-in, keep a salted slow hash of the PIN with the session; check it locally on unlock. 5 wrong local tries drop the session. No new server data |
| Short-ID clash candidates | The "2 units end in…" list | `ShopSync.PhoneMatch.ShortIdClash` has no payload today |
| "N still incoming" | After Move to In stock | Count from the phone's copy; no server change |
| Device name | "This phone: Counter phone" | Already stored at connect (`devices.label`) |

---

## 6. Decisions for the owner

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D1 | After a good read, show the result card, or jump straight to the record (today's spec)? | **Result card, always.** | Confirming a pallet of incoming boxes becomes two taps per box instead of four, and every read looks the same, so nobody has to learn which codes jump and which don't. Cost: one extra tap when you just want to open an in-stock unit. |
| D2 | Can the same person unlock after idle while the phone is offline? | **Yes, same person only.** Switching user still needs the shop system. | A 4-digit PIN hash on the phone is weak on its own, but the phone already holds that person's session token, which is stronger than the PIN. So this adds no new exposure, and nobody gets locked out in the yard. |
| D3 | Ask the server for tries left and the lock end time? | **Yes** (two small fields). | "2 tries left" warns before the lock; "Try again at 2:19 pm" beats "wait 5 minutes" when you don't know when the 5 minutes started. |
| D4 | Who screen background: the shop photo, or flat charcoal? | **Shop photo**, same as the unreachable screen. | Your Disney and Qantas+CHOPT picks both use it, and it marks gate screens as different from the working app. |

---

## 7. Do not render (additions to the prompt's section 9)

- A scanning laser line or animated grid (the current app has both).
- "DECODING…" progress or percentages: reads are instant.
- Fake camera content beyond a labelled placeholder and our own tag.
- Biometric unlock (fingerprint and face can't tell which employee is holding a shared phone).
- "Forgot your PIN?" (the owner resets PINs).
- Letters under keypad digits.

---

## 8. Build and test contract

- **Test tags:**

  | Element | Tag |
  |---|---|
  | Scan: title, Light, mode tiles, Cancel | `scan.title`, `scan.light`, `scan.mode.camera`, `scan.mode.type`, `scan.cancel` |
  | Scan: type field, Look up, no-tag link | `scan.type.field`, `scan.type.lookup`, `scan.type.noTag` |
  | Scan: permission | `scan.permission.allow`, `scan.permission.settings` |
  | Result card | `scan.result`, `scan.result.primary`, `scan.result.again`, `scan.result.open`, `scan.result.match.{unitId}` (the full ID: matches share the short one) |
  | Who list | `who.person.{employeeId}` |
  | PIN | `pin.boxes`, `pin.key.0` to `pin.key.9`, `pin.key.delete`, `pin.switchUser`, `pin.message`, `pin.locked.switch` |
  | Unreachable | `gate.unreachable.retry`, `gate.unreachable.back` |

  The mockups carry the same values as `data-testid`.
- **Stateless and previewable:** `ScanScreen(state, …)` and `PinScreen(state, …)` take a state object, so every row of the state tables above is a screenshot test (Roborazzi), at font scale 1.0, 1.3 and 2.0.
- **Behaviour tests:**
  - The 4th digit submits exactly once (double-tap on the last key doesn't send twice).
  - A read while the result card is up is ignored.
  - Move to In stock is queued once, even with two taps.
  - Look up is disabled at any length other than 8 or 17.
  - Wrong PIN never shows or announces the digits.
  - Offline unlock refuses a different person and drops the session after 5 wrong local tries.
- **Clock injected** for "Try again at 2:19 pm", "Last tried", and the 8-second hint.
- **The spec wins** over the mockup where they differ.
