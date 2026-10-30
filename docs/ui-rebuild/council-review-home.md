# Home (A2B1C2): Phase 4 review gate

- **Date:** 2026-10-30
- **Reviewed:** `prototypes/home-a2b1c2.html` and `landing-nav-ui-prompt.md`

**How it was reviewed:**
- `agentic-council` adversarial seats, each starting fresh: Skeptic, Advocate, Artisan, Pathfinder and Craftsman.
- `ux-ui-audit` probes: core, heuristics, perception, LTR and focus.
- `vectorlab-ux-skills:anti-slop`, run against the mockup code.

**Not run:**
- `design-critique`: the Advocate seat covered heuristics.
- `/slopmonster` and the Adversarial UITest/UXTest skills: they are on the Desktop PC.

Every finding is **fixed**, **rejected with a reason**, or **waiting on the owner**.

## Results after fixes (re-measured)

| Check | Result |
|---|---|
| Contrast | 57 of 57 text pairs pass. The Scan glyph measures 5.7–9.4:1 against its gradient (the probe can't measure gradients). |
| Tap targets | 16 of 16 are at least 48 dp. |
| Accessible names | None missing. |
| Focus / reading order | Matches the screen: topbar.user → home.find → jobs → count cards → incoming → nav. |
| Anti-slop | **Clean.** The only gradient is the single-hue Scan. No glass, blur or glow. Shadows are the documented dark-screen exception, with offsets of 3 dp or less. No status pills. |

## Owner answers (2026-10-30)

| # | Answer | Applied |
|---|---|---|
| D1 | **Keep Stock first.** Move search out of Home into the title bar, opening an overlay. | Find is now an icon-plus-word button in the top bar; it opens a search sheet. Home is about 100 pt shorter. On iPhone 17 (402 × 874 pt, safe areas drawn), the Incoming header sits at y = 652 and the first card's top 40 pt shows above the nav. The Incoming count card (on screen) opens the same list. |
| D2 | **No Fold.** Design for current iPhone and iPad. Cross-platform; Android first. | Frames are iPhone 17 / 17 Pro 402 × 874, Pro Max 440 × 956, and iPad 820 × 1180. Fold and hinge rules removed. Compose Multiplatform recommended for iOS, not decided. |
| D3 | **Back to the PIN screen after 30 minutes idle by default.** Configurable, including no PIN at all. | Shop settings (admin): sign-in "Name and PIN" or "Name only", and lock after 15 / 30 / 60 minutes or never. Both are enforced by the shop system. "Name only" means the in-app assistant needs an explicit confirm to start a sale (handoff §2). |

## Originally waiting on the owner

| # | Decision | Recommendation | Evidence |
|---|---|---|---|
| D1 | **Section order.** On A2 (Stock first, your pick), the first unit to confirm starts at y = 766 dp, behind the Scan button (819). On a shorter Fold cover screen it's fully off screen. A1 starts it at y = 416. Four of five seats flagged this. | **A1: Incoming first.** The work that needs someone goes first; the counts sit under it. | `order-a2-vs-a1.png` |
| D2 | **Which Pixel Fold?** The original Fold's cover screen is about 411 × 797 dp; the 9 Pro Fold's is about 411 × 923 dp. That changes how much fits on the first screen. | Tell me the model and I'll redraw the frame with system bars. | Pathfinder |
| D3 | **Shared-phone identity.** The only cue for who is signed in is the top-right chip. | Return to "Who's using this phone?" after 10 minutes idle. Show "Checking in as Dale" on the Review step and the Scan header. | Skeptic |

## Fixed

| Seat(s) | Finding | Fix |
|---|---|---|
| Advocate, audit | Visual order ≠ focus and reading order (CSS `order`). WCAG 1.3.2, 2.4.3 | Sections are in screen order in the markup. Spec §11: never reorder visually. |
| Advocate, Skeptic, Pathfinder | Fixed heights clip at font scale 1.3–2.0 (tiles, Find) | Tiles and Find use `min-height`. The Find hint moves to persistent supporting text (also fixes WCAG 3.3.2). |
| Skeptic | "Sold this week" has no server query or date filter, so the card wouldn't match its list | First release has 3 cards (2 + 1 wide). Sold this week is TBA until both exist. |
| Skeptic | Nothing shows how fresh the counts are | Strip reads "Up to date at 2:14 pm" (last two-way sync). Stale: "Last updated …". |
| Skeptic | Incoming order undefined, so the oldest box is hidden; "Tue" is ambiguous after a week | Oldest first. Weekday within 6 days, date after. "See all 6". |
| Advocate | Confirm instructions sat on the wrong control | Line under the Incoming header: "Scan the box tag to confirm it." Count context: "Not scanned yet". |
| Skeptic | Hint lines are wrong with real data (zero, "paperwork" is a reserved feature) | Zero-state lines added. Sale pending: "Sale started, not finished". |
| Skeptic | Long model names wrap to 4 lines | Name clamps to 2 lines. |
| Skeptic | Find keyboard not set for IDs and VINs | All caps, no autocorrect. Matching uses `ItemId.normalizeShort` (O→0, I/L→1, checked in `core`). Jump only on an exact 8-character ID or 17-character VIN. |
| Advocate, Pathfinder | Card read in a confusing order; the ID is read as a word; the timestamp would re-announce every minute | Card merged with a set description. Image decorative. IDs use `VerbatimTtsAnnotation`. The strip is not a live region. |
| Advocate | Scan focus ring sits on the slot, not the button | Ring follows the circle. |
| Artisan | Records and actions look alike | Records use the `bg` fill and don't scale on press. Tactile controls keep raised fill and scale. |
| Artisan | Section headers no bigger than labels | Section 21/28 Bold, Barlow. |
| Artisan | Uneven count-card rows; 1 dp nav label offset | Equal-height rows; Scan label aligned. |
| Craftsman, Artisan | Stylesheet patched in layers (`stone-tint` was the background colour, then overridden) | One `:root`, one rule per component. Spacing and shape tokens added. |
| Craftsman, Artisan | Amber, rust and stone had no role tokens; depth tokens unnamed | `warn-*`, `danger-*`, `neutral-*` roles. Depth tokens named in spec §6. |
| Craftsman | Type scale missing families and roles | Family column, plus app title, tile label, tag, ID and ID-plate rows. |
| Craftsman | No test-identity contract | Spec §11 testTags; the mockup carries the same values as `data-testid`. |
| Craftsman | Pressed states exist only as CSS `:active` | Spec §11: stateless tactile composables with `pressed: Boolean`. |
| Craftsman | Data rules undefined (sort, week, time zone) | Spec §11 data rules, with an injected clock. |
| Craftsman | Mockup and spec disagree on press motion and tab timing | Spec §8 updated to the tested values; spec wins (§11). |
| Pathfinder | Unfolded layout (rail + 2 × 360 columns = 872 dp) doesn't fit the Fold's inner screen | Spec §4b: window size classes; one column at Medium; 320 dp minimum columns split at the hinge. |
| Pathfinder | M3 `NavigationBar` clips the raised Scan | Spec §4c: custom non-clipping bar, `Role.Tab` and `Role.Button`. |
| Pathfinder | Predictive back is on by default when targeting Android 16 | Spec §8: predictive back, `PredictiveBackHandler` on overlays. |
| Pathfinder | No route when the camera is refused, which leaves Scan dead | Spec §4d: `scan/permission` route with "Open settings" and "Type the tag ID instead". |
| Pathfinder | Confirm haptic needs API 30 | Spec §8: fallback below API 30. Haptics are never the only signal. |
| Pathfinder | Nav label cap was estimated | Measured "Tickets" at 58 dp in an 82 dp slot. Cap expressed in dp. |

## Rejected, with reason

| Seat | Finding | Why it stays |
|---|---|---|
| Artisan | Drop the 40 dp badge (it reads as a generic stat card) | The owner chose the Jira-style badge card. The space problem it pointed at is handled by D1. |
| Artisan | Outline the ID plate (it's the brightest thing on the card) | Its job is to match the white thermal tag on the box, and the owner chose it. Revisit once real unit images are in. |
| Skeptic | Drop the type word from line 3 (the image repeats it) | A type with no image falls back to the generic image, and screen readers skip the image. The text is the reliable copy. |

## Deferred to the Compose build

- Generate `Theme.kt` from an exported tokens file (Craftsman).
- Font-scale 1.3 and 2.0 frames: HTML can't emulate Android `sp`, so these are Roborazzi tests (spec §11).
- Edge-to-edge system-bar frame: waits on D2.
