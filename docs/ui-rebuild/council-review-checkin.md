# Check in and print tag: Phase 4 review gate

- **Date:** 2026-10-30
- **Reviewed:** `checkin-ui-spec.md`, `prototypes/checkin.html`, `prototypes/inscan-base.css`

**How it was reviewed:**
- Four `agentic-council` seats, each starting fresh with the files and the code to check claims against:
  - **Skeptic:** data integrity and failure modes.
  - **Advocate:** task flow, copy and accessibility.
  - **Pathfinder:** platform, checked against Brother, Android, Apple and NHTSA documentation, with live vPIC calls.
  - **Artisan:** the visual system and motion.
- Measured checks in Chromium on all 18 states: WCAG 2.x contrast, tap targets, accessible names, focus order and console errors.
- Anti-slop, run by hand against matrix S1 to S15.

Every finding is **fixed**, **rejected with a reason**, **deferred**, or **for the owner** (D-C1 to D-C9 in the spec).

## Results after fixes (re-measured)

| Check | Result |
|---|---|
| Contrast | 353 of 353 text pairs pass AA; the lowest is 5.71:1. |
| Tap targets | 137 of 137 are at least 48. The VIN-check box is 28, but its label row (56 tall) is its target. |
| Accessible names | None missing. The VIN and price fields are named by their step heading (`aria-labelledby`). |
| Focus order | Matches the screen in every state. Sheets make the screen behind them inert. |
| Console | No errors. |
| Anti-slop | Clean. No blur, glow or gradient washes. Chips are pills only because they're tappable. The scrim is a token (`--scrim-modal`). |

## What the review changed most

- **The sample data was wrong.** Pathfinder ran the mockup's made-up VIN through NHTSA vPIC. It came back as Polaris with no model, and year 2024; the mockup showed "2026 RZR XP 1000". vPIC also calls ATVs and side-by-sides MOTORCYCLE, which today's code maps to Dirt Bike. The mockup now shows an honest partial lookup (make and year only), and the type is never guessed from `VehicleType`.
- **The printer recommendation needed homework:**
  - The PT-P910BT prints at 360 dpi with a 32 mm print height on 36 mm tape; the app's preset says 180 dpi.
  - Brother's SDK v4 needs Android 8; the app's minimum is Android 7.
  - On iOS, it needs a Brother-issued MFi PPID.
  - Brother's Smooth Print gives a no-driver path that reports success or failure.
- **The data model has holes the UI can't paper over.** Each needs server or app work, listed in spec §5:
  - serials sharing the VIN column
  - price that can't be empty
  - stale tags still confirming
  - the Incoming → In stock shortcut that mark-sold relies on

## Fixed

| Seat | Finding | Fix |
|---|---|---|
| Skeptic | Deciding "serial" by length turned a mistyped 16- or 18-character VIN into a serial, skipping the check and duplicate check; O, I and Q never matched later | Explicit VIN / Serial number switch; length warnings at 15, 16 and 18; O→0, I→1, Q→0 before any check; leading-I 18-character reads handled |
| Skeptic | Closing the Incoming → In stock gap breaks mark-sold and legacy upload | Shipped together: sale refuses an Incoming unit; admin-only audited legacy path (§5, D-C5) |
| Skeptic | "Price later" plus the interim mark-sold rule sells at $0; the null price needs migrations | Nullable end to end, never 0; server blocks Sale pending and Sold; "Needs a price" list |
| Skeptic | A stale tag (details changed after printing) still confirms | Confirm compares the tag's fields with the unit; "Tag out of date" row in Scan (§6); the unit is flagged when a printed field changes |
| Skeptic | `PrintOutcome` can't say "unknown" or "printed 1 of 2"; a lost link gives a tag with no record | `Unknown` and a count added; a valid confirm scan records the tag instead of `TAG_NOT_RECORDED` |
| Skeptic, Advocate | Between save and print, a unit can end up with no tag and nobody knows | "Print later" asks first; "No tag yet" on the unit and on Home |
| Skeptic | A double tap on Save makes two units (a new ID on each call) | ID made once at step 1; idempotent save; button disabled on the first tap |
| Skeptic | Lookup race writes the old VIN back; it looks up failing VINs | Result dropped if the VIN changed; never writes the VIN; no lookup until the frame tick |
| Skeptic | Duplicate check has holes (500-unit cap, only at step 1, sold units dead-end) | Paged full copy; re-check at Save; owner-only "Bring it back in" (D-C9) |
| Skeptic | Serials collide across makes in the VIN column and read as tag IDs | Separate serial field, unique on (make, serial) (§5) |
| Skeptic | New type cards fail today's enum check | Unit-types work must ship first (§5) |
| Skeptic | One record for any number of copies | Count recorded; "Box 1 of 2" printed |
| Skeptic | A cancelled share is recorded as printed | Record only when a target is picked (`IntentSender`) |
| Skeptic, Pathfinder | The renderer drops the VIN line silently; wrong dpi for PT tape | Preview says "The VIN is in the QR code only at this size"; PT presets at 360 dpi keyed by tape width |
| Advocate | "Saved" was a grey word in the corner | "Saved as Incoming. Tag ID 6MDQ1KXA." at the top of Print, announced |
| Advocate | The share path, the only path on day one, wasn't designed | "Did the tag print?" state drawn; Smooth Print preferred; survives process death |
| Advocate | 2x text spills the top bar and hides most of the VIN | Full-width VIN field; title hidden and buttons stacked from 1.5x; the VIN wraps |
| Advocate | The only skip was "No VIN", but the VIN is usually in the crate | "Add it later" (D-C8) beside "It has none"; crate label first in "Where's the VIN?" |
| Advocate | Unsaved drafts on a shared phone | Draft survives idle lock and process death; "Dale didn't finish…" with Finish it / Throw away |
| Advocate | VIN copy invited changing the check digit | New copy names the usual misreads; softened for off-road VINs (NHTSA says their check digits may not hold) |
| Advocate, Pathfinder | Editing from Review lost the way back | "Back to review" primary; back returns to Review |
| Advocate | Print copy said scanning moves the unit to In stock (it takes a tap); no next check-in | "Stick it on the box, scan it, then tap Move to In stock."; Scan expects this tag; "Check in another" |
| Advocate | Nothing was announced | Live regions for results, lookup, duplicates, save and printer; assertive on failure; Bluetooth scanner input on step 1 |
| Advocate | Small copy and behaviour issues | Autocorrect off for make and model; "Everything except the price prints"; "V I N" read by letter; Print again; no Back on step 1 |
| Artisan | Emerald progress bar (status in the action colour) | `text-2` fill, `scaleX` transform |
| Artisan | Selected type card looked like keyboard focus | Latched like Scan's mode tile: tinted name plate, chip shadow, filled check badge; equal rows |
| Artisan | VIN didn't fit beside Scan | Scan the VIN barcode is its own full-width row; field full width at `type-field-mono` |
| Artisan | Selected chips were colour only | Check icon plus `accent-ink` border |
| Artisan | Review: an ocean banner carried an instruction; emerald Edit stripe; light values | Help line instead; whole rows tappable with a trailing Edit; `type-card-title` values; type thumbnail |
| Artisan | No step motion | Values in spec §4 |
| Artisan | Even 16 dp gaps everywhere | Groups at 8, between groups 24 |
| Artisan | Failing VIN not tied to its field; second emerald fill in the checkbox | Warn ring on the field; checkbox uses `accent-tint` with an `accent-ink` check; "Next with something missing" drawn |
| Artisan | Floating leave dialog; untokened scrim | Bottom sheets; `--scrim-modal` |
| Artisan | Copies row and printed state cluttered | "One per box"; big-number count; disabled raised state; Printed shows one success line and Print again |
| Artisan | Raw values; repainting press; fields repeating their heading | `--paper`; `::before` press layer; VIN and Price labelled by the heading; keyboard drawn on Make & model |
| Pathfinder | No process-death plan | `SavedStateHandle` draft; save sets "step = Print" in one transaction |
| Pathfinder | Back undefined on Print | Back = Print later / Done; steps popped after Save |
| Pathfinder | No Bluetooth permissions or states | Permission rows, CompanionDeviceManager, and Bluetooth off / not allowed / busy states |
| Pathfinder | Keyboard, landscape and compact height | `adjustResize`, focus rules, keyboards per field, compact-height strip |
| Pathfinder | Compose Multiplatform gaps (`core` is JVM, Android renderer) | KMP QR encoder plus a common Skia renderer; `LabelPrinter` in commonMain |
| Pathfinder | FileProvider authority hard-coded; VIN URLs logged in release | `${applicationId}.fileprovider`; debug-only logging |

## Rejected, with reason

| Seat | Finding | Why |
|---|---|---|
| Advocate | Replace the per-step checks with one "That's right / Change something" screen after the lookup | vPIC usually returns only part of the details for off-road units (make and year here), so most check-ins would hit "Change something" anyway. "From the VIN" markers on each step and on Review put the check where the values are. |
| Artisan | Draw the keyboard on every text step | Drawn on one state (Make & model) to show the bar riding above it; on the others it would hide what's being reviewed. Screenshot tests use a fake keyboard inset (§9). |

## For the owner

D-C1 to D-C9 in `checkin-ui-spec.md` §7. Two need action outside the app if the PT-P910BT is chosen:
- apply for Brother's iOS PPID
- get Brother's written OK that the SDK licence covers PT models

## Deferred

- **Skeptic: offer "Is it one of these?" (untagged Incoming units) when checking in with no VIN.** Deferred because it adds a step to every no-VIN check-in. The "No tag yet" flag on Home covers the main risk. Revisit if duplicates show up.
- **Strict box check on confirm** (scan the crate VIN): D-C7 recommends the soft check first.
- **Font-scale, 360 × 640 and landscape frames:** Roborazzi tests (spec §9).
