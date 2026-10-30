# Scan and sign-in: Phase 4 review gate

- **Date:** 2026-10-30
- **Reviewed:** `scan-pin-ui-spec.md`, `prototypes/scan.html`, `prototypes/pin.html`, `prototypes/inscan-base.css`

**How it was reviewed:**
- Four `agentic-council` seats, each starting fresh with the files and the code to check claims against: Skeptic (risk and security), Advocate (UX, copy, accessibility), Pathfinder (Android/iOS platform, checked against official docs), Artisan (visual system and motion).
- Measured checks in Chromium on every state (19 states): WCAG 2.x contrast, tap targets, accessible names, focus order, console errors.
- `vectorlab-ux-skills:anti-slop`, matrix S1 to S15, against the code.

**Not run:** Craftsman (the build/test contract follows Home's section 11 pattern and was extended by hand from the seats' findings); `/slopmonster` and the Adversarial UITest/UXTest skills (on the Desktop PC).

Every finding is **fixed**, **rejected with a reason**, or **for the owner** (D1 to D7 in the spec).

## Results after fixes (re-measured)

| Check | Result |
|---|---|
| Contrast | See the verification block in DEV-JOURNAL. Text over the camera is measured against a white scene; text on the gate against a white photo under the scrim. |
| Tap targets | Every control is at least 48 in every state. |
| Accessible names | None missing. |
| Focus order | Top to bottom in every state. |
| Console | No errors. |
| Anti-slop | Clean. No blur, glass or glow; the only gradients are the documented raised fill and the labelled photo placeholder; no pills on static text; no icons in inputs; radii 8 to 16. |

## Fixed

| Seat | Finding | Fix |
|---|---|---|
| Skeptic | "Back to Dale" opened Dale's session with no PIN: fail any sign-in offline, then tap it | Opens Dale's PIN screen, checked on the phone. Test added. |
| Skeptic | D2's "no new exposure" was wrong: a stored hash of a 4-digit PIN cracks in seconds from a copied file; shop phones run debug builds | Keystore HMAC under a non-exportable key; failed count saved before each check; wiped on sign-out, switch and the 5th miss; release builds recommended. D2 reasoning rewritten. |
| Skeptic | Queued changes don't record who made them, so a switch can put Dale's work under Ray's name | `employeeId` on each outbox entry; switch sends the previous person's queue with their token first, or holds it with "3 of Dale's changes waiting". |
| Skeptic | Offline unlock can't tell a session is dead | `expiresAt` at login; offline unlock refused after it; a "Sign in again. Your scans are saved." card. |
| Skeptic | Maker barcodes never match and offered Check in, making duplicates | New row: "This barcode can't identify a unit." No check-in from it. |
| Skeptic | Typed 8-character ID not found had no row; offline VIN made "Check in anyway" the emerald primary | New "No unit with tag ID…" row; Check in anyway is raised and asks to confirm. |
| Skeptic | Move to In stock decided from the phone's copy, even online | Refresh from the server first; confirm returns `changed:false` with who and when ("Already in stock"); offline copy "Moved on this phone". |
| Skeptic | Spec said typing does everything a scan does, but moving needs the label read (handoff s2) | Typed lookups never offer Move to In stock: "Scan the tag to move it to In stock." |
| Skeptic | Lockout copy didn't match the server ("locked for 5 minutes", 3 tries in the mockup) | "Locked until 2:19 pm"; mockup uses 5 tries. Server changes in D3. |
| Skeptic | Scan screen didn't show who's signed in | "Scanning as Dale" in the title strip. |
| Skeptic, Pathfinder | Scan another re-reads the same tag at once; a frame decoding after the pause can still fire | Session number per read, one code per frame, ignore the last value until it's out of view about 1 s. |
| Skeptic | A fast double tap queues Move to In stock twice | Disabled on first tap; outbox skips a second pending confirm. |
| Skeptic | The short-ID candidate list overturns the handoff's "refuses on a clash" and solves a near-impossible case (40 random bits) | List dropped; refusal copy kept with "Scan the QR code". |
| Skeptic | Spec needs data the server doesn't have (ids, initials, Name only) | Listed in spec section 5. |
| Advocate | The result card hid Cancel; leaving after a price check took two taps | Cancel never moves or hides; the card sits on top of it. "Done" dropped. |
| Advocate | Text at 2x overprints the PIN message slot and pushes the keypad under the home bar | Message slot grows from 40; keypad rows shrink first; digit stops growing at 1.3x; card buttons stack. Roborazzi at 1.0/1.3/2.0. |
| Advocate | Type it said "under the QR code" but the tag prints "ID 3HZW8RTB" beside it; Look up greyed out with no reason | Helper "after ID on the tag"; input cleaned (spaces, leading ID, `normalizeShort`); Look up always enabled with a length message; label by length; Enter runs it. |
| Advocate | Bluetooth scanners not covered; the 17-character limit cut a tag payload | Keyboard-wedge bursts count as a read in any mode; no length cap; `CATV1;` strings treated as a tag. |
| Advocate | After a typed lookup, "Scan another" reopened the camera | "Type another" returns to Type it. |
| Advocate | Card headers used system terms | "Found, still Incoming", "Couldn't check the shop system", "Details from the tag". |
| Advocate | PIN screen reader gaps: no digit count, focus lost on lock | Hidden polite "2 of 4 entered"; focus to the new title; keypad returns at `lockedUntil` with an announcement; 3d wording corrected. |
| Advocate | No copy for wrong PINs during an offline unlock | "…before Dale is signed out of this phone. After that, signing in needs the shop system." |
| Advocate | Idle line sat above the name (an eyebrow line, banned in section 9) | Moved under "Enter your PIN". |
| Artisan | A read landing as someone reaches for Cancel put the emerald primary under their thumb | Cancel stays in its exact spot; 250 ms tap guard on new cards. |
| Artisan | Unselected mode tile was flat but pressable | Raised. |
| Artisan, measured | Text over a bright picture: plates at 72% gave 5.9:1; gate scrim at 75% put `text-2` at 4.36:1 (fails AA) | Plates 80% (7.75:1); gate scrim 80% (`text` 7.75:1, `text-2` 5.18:1); gate body copy uses `text`. |
| Artisan | Warnings were coloured body text | Status-banner build (tint fill, `text`, icon in ink). |
| Artisan | The emerald avatar was the loudest thing on the PIN screen but can't be pressed; "Not Dale?" read as part of the keypad | Flat neutral avatar; "Not Dale?" right under the name; keypad right under the message. |
| Artisan | Digits too small for the keys and off the type scale | 44/48 "Keypad digit" (D5); who discs use `type-big-number`. |
| Artisan | Card repeated the outcome (green check header over status tags; four green elements) | Header icon shows what was read in `text-2`; status colour moves to the brackets; record card is tappable, so the header "Open unit" went; Moved card has one button. |
| Artisan | Locked and unreachable left big empty areas with buttons outside the bottom band | Locked notice (prompt section 5 values) sits in the bottom band; unreachable buttons pinned to the bottom (D6). |
| Artisan | Sheet faded instead of sliding; reduce-motion dropped the crossfade | Slides from below, 200 ms ease-out, no fade; reduce-motion 120 ms fade. |
| Artisan | Uneven press scale; disabled keys still pressed in | Raised controls 56+ scale to .97, Light .985; `aria-disabled` excluded. |
| Artisan | Wrong PIN snapped clear; emerald spinner | Dots fade over 120 ms; reject haptic; `text-2` spinner after 300 ms. |
| Artisan | Small drifts from Home (icon optical size, Light build, radii, title size) | Fixed in `inscan-base.css` and the mockups. |
| Pathfinder | PIN screen doesn't fit 360 x 640 | Below about 720 dp tall the avatar hides and keys may go to 56; landscape splits boxes and keypad; tests at 360 x 640. |
| Pathfinder | Type it can't ride above the keyboard as drawn; edge-to-edge enforced at target 35+ | `adjustResize`, `imePadding()` on the panel, scrolling content, `ImeAction.Search`. |
| Pathfinder | Result card had no height limit | Capped under the title strip; body scrolls; buttons pinned and stack when they don't fit. |
| Pathfinder | How the picture freezes was unspecified | `clearAnalyzer()` + `PreviewView.getBitmap()` overlay; preview stays bound; `SavedStateHandle` after process death. |
| Pathfinder | The reader sees more than the person (4:3 analysis vs cropped preview) | Accept only codes in the visible picture; nearest the centre; our QR, then VIN, then others. |
| Pathfinder | "After two refusals" is only true on Android 11+; iOS asks once and has `restricted` | Rationale-plus-asked-flag rule, re-check on resume, iOS states, manifest `required=false`. |
| Pathfinder | Back undefined per state; iOS has no system back | Back table (2d); gate is its own root; shell hidden from screen readers while locked. |
| Pathfinder | Tablet rules named a device, not size classes; Android 16 ignores orientation locks at sw600+ | Window size classes and landscape layouts for both screens. |
| Pathfinder | iPad Air M3 has no rear flash; the hint says "Turn on the light" | Light hidden without a torch; hint "Move closer, or type it in."; torch state from the camera. |
| Pathfinder | Compose Multiplatform has no shared camera | `BarcodeReader` expect/actual; AVFoundation formats listed; zoom by `minimumFocusDistance`. |
| Pathfinder | Haptics API levels; iPads have none | `performHapticFeedback(CONFIRM/REJECT)` with fallback; `UINotificationFeedbackGenerator`; signal never haptic-only. |
| Pathfinder | Secure display unspecified | `FLAG_SECURE` on PIN; `setRecentsScreenshotEnabled(false)`; iOS cover on resign-active. |
| Pathfinder | Hardware keys need focus and NumPad codes; a Bluetooth scan could burn PIN tries | Focus on entry, `onPreviewKeyEvent` for both digit sets, bursts over 4 characters dropped. |

## Rejected, with reason

| Seat | Finding | Why |
|---|---|---|
| Advocate | Add Undo after Move to In stock | The locked status rules don't allow In stock back to Incoming, and online the change is sent at once, so Undo would only work offline. A box tag that reads means the unit is here, so the move is the true state. The real risk (a mis-tap) is handled by Cancel never moving and the 250 ms tap guard. |
| Artisan | Primary full-width on top, Scan another in Cancel's spot | Keeping Cancel fixed (Advocate) solves the same mis-tap with less movement, and keeps one exit in one place. |
| Artisan | Scrim and plates at 85% | At 85% the owner's shop photo all but disappears. 80% keeps `text` above the prompt's 7:1 and `text-2` above 4.5:1 on a white worst case; gate body copy uses `text`. |
| Artisan | Home's 128 dp image in the result card | At 402 wide it cuts the unit name to "2026 Honda FourTrax Rancher…". 96 keeps the name readable; Home's padding and everything else match. |
| Artisan | Move the frozen picture up so it clears the card | The frozen picture is what the camera saw; moving it would misplace the read. The card carries the result. |

## For the owner

D1 to D7 in `scan-pin-ui-spec.md` section 6. **Answered 2026-10-30:** yes to D1–D6 (decided, not locked); D7 open. The owner also renamed "Back to Dale" to "Log back in".

## Deferred

- Point `home-a2b1c2.html` at `inscan-base.css` instead of its inline copy (Home is locked; do it when Home is next touched).
- Font-scale and 360 x 640 frames: HTML can't emulate Android `sp`; these are Roborazzi tests (spec section 8).
- Mockup states not drawn (spec only): camera couldn't start, wrong length in Type it, already in stock, moved offline, maker barcode, typed ID not found, session expired, offline wrong PIN, landscape and tablet.
