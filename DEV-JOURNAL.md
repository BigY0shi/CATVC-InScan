# DEV-JOURNAL

Project decisions and verification evidence, newest entry first. Nothing counts as locked unless the owner said "lock it", and each locked item below says so.

---

## 2026-10-30: Check in and print tag, Phases 0–4

**Branch:** `ui-checkin`, stacked on `ui-scan-pin`. **Nothing here is locked.**

**Owner input:** the printer can't be confirmed until much later. The Brother PT-P910BT is the recommendation to offer the shop. The design works with any printer that meets the `LabelPrinter` contract, and falls back to sending the tag to a printer app.

**Phases:**
- **Phase 0:** Mobbin references (Turo, My BMW, Skyscanner, Grailed, Vestiaire, Whatnot, iOS print options), plus a map of today's check-in, VIN, save and tag code.
- **Phases 1–3:**
  - `docs/ui-rebuild/checkin-ui-spec.md`: the flow is VIN → Type → Make & model → Year & colour → Price → Review, then Print.
  - `prototypes/checkin.html`: 18 states, with a real tag QR code.
- **Phase 4:** Skeptic, Advocate, Pathfinder and Artisan seats, measured checks and anti-slop. Dispositions are in `docs/ui-rebuild/council-review-checkin.md`: about 50 findings fixed, 2 rejected with reasons, 3 deferred. Nine decisions are for the owner (D-C1 to D-C9).

**Corrections found by the review:**
- **The VIN lookup sample was wrong.** NHTSA vPIC returned Polaris, no model and 2024 for the made-up VIN; the mockup claimed "2026 RZR XP 1000". vPIC also reports ATVs and side-by-sides as MOTORCYCLE. The mockup now shows an honest partial lookup, and the type is never guessed.
- **The printer needs groundwork if chosen:**
  - **Tag size:** the PT-P910BT prints at 360 dpi with a 32 mm height on 36 mm tape; the app's preset says 180 dpi.
  - **Android minimum:** Brother SDK v4 needs Android 8; the app's minimum is Android 7.
  - **iOS:** it needs a Brother-issued MFi PPID.
- **Serials by length were unsafe.** "Serial" was decided by length, which turned mistyped VINs into serials. There is now an explicit VIN / Serial number switch.

**Also changed:** three Scan result-card changes (Scan isn't locked yet):
- the incoming card shows colour and VIN ending, with "Check it matches the box"
- a new "Tag out of date" row
- Scan opened from Print knows which tag to expect

**Verified** in Chromium at 402 × 874, all 18 check-in states:
- 353 of 353 text pairs pass AA.
- 137 of 137 tap targets are at least 48.
- No control is missing a name.
- Focus order matches the screen; sheets make the screen behind them inert.
- No console errors.

**Not verified:**
- font scale 1.3 and 2.0, 360 × 640, landscape (Roborazzi tests in the spec)
- on-device printing
- real type images

**Server and app needs:** `checkin-ui-spec.md` §5.
- Editable unit types must ship first.
- Serial field.
- Nullable price and VIN.
- Tag-staleness check on confirm.
- Incoming → In stock only via a tag confirm, shipped together with the mark-sold and legacy fixes.

No app or server code changed.

---

## 2026-10-30: Sign-in screens LOCKED

The owner said "lock in these screens… or at least pin". **Locked:** the sign-in gate as drawn in `docs/ui-rebuild/prototypes/pin.html` and specified in `scan-pin-ui-spec.md` section 3:
- **Who's using this phone?:** raised initials discs over the shop photo, under an 80% scrim.
- **PIN:** flat neutral avatar, name, then "Not Dale? Switch user". Then 4 boxes and an in-app keypad with 44/48 digits that fills to the bottom. The 4th digit signs in.
- **States:** wrong PIN, locked, and the idle lock.
- **Can't reach the shop system:** Try again, and "Log back in", which opens that person's PIN screen.

The lock card in `landing-nav-ui-prompt.md` section 0 now has a SIGN-IN entry.

**Not locked:**
- **The Scan screen** (spec section 2): waiting on the owner's review.
- **Engineering requirements:** offline unlock (D2, §3e), server lockout (D3) and queue survival (§5b). These are decided, not locked.

No app or server code changed.

---

## 2026-10-30: owner answers on Scan and sign-in (D1–D6)

The owner said **yes to D1–D6** in `docs/ui-rebuild/scan-pin-ui-spec.md` §6. These are decided, **not locked**:

1. **D1:** a read shows the result card.
2. **D2:** the same person can unlock offline, checked with a Keystore HMAC.
3. **D3:** server lockout changes (tries left, lock end time, longer repeat locks).
4. **D4:** shop photo behind the who screen, under an 80% scrim.
5. **D5:** 44/48 keypad digit.
6. **D6:** unreachable-screen buttons in the bottom band.

**D7** ("log a unit from its tag"): **not now** (owner, 2026-10-30). The owner's requirement instead: queued check-ins must survive resets. The checked state and the gaps to close are in `scan-pin-ui-spec.md` §5b.

The queue already survives restarts, crashes, reboots and app updates:
- It is a Room table.
- An entry is removed only when the server accepts it, or on an explicit discard.
- The database has no destructive migration.
- Sign out keeps the queue.

Gaps to close:
- A refused check-in can be discarded after its tag is printed.
- The phone can be connected to a different shop system while changes are waiting.

The owner also renamed "Back to Dale" to **"Log back in"**. The meta line above it now says whose session it is: "Dale is still signed in on this phone."

Applied to `landing-nav-ui-prompt.md`, unlocked sections only:
- §4d: scan route and idle lock
- §5: unreachable screen
- §6: Keypad digit row in the type scale
- §8: good scan

No app or server code changed.

---

## 2026-10-30: Scan and sign-in screens, Phases 0–4

**Branch:** `ui-scan-pin`. **Nothing here is locked.**

- **Phase 0:** opened the Mobbin references this time (Cal AI scanner and result sheet, ZARA, HBO Max PIN and wrong code, Disney+ who and PIN, Qantas offline) and mapped what a scan and a sign-in actually do in `InventoryViewModel.onBarcodeScanned`, `Tags.confirm`, `Auth.login` and `ShopSync.matchOnPhone`.
- **Phase 1:** `docs/ui-rebuild/scan-pin-ui-spec.md`.
- **Phases 2–3:** mockups `prototypes/scan.html` (11 states) and `prototypes/pin.html` (8 states) on the locked Home tokens, now shared in `prototypes/inscan-base.css`. The camera content is generated from real data: QR codes in the `LabelPayload` format, the tag text laid out like `Label.lines()`, and a Code 39 VIN with a valid check digit.
- **Phase 4:** Skeptic, Advocate, Pathfinder and Artisan seats, measured checks and anti-slop. Dispositions are in `docs/ui-rebuild/council-review-scan-pin.md`: about 50 findings fixed and 5 rejected with reasons. Seven decisions are left for the owner (D1–D7 in the spec).

**Corrections to my own first draft, found by the review:**
- "Back to Dale" opened a session with no PIN. It now opens Dale's PIN screen.
- D2 said a stored PIN check "adds no new exposure". That was wrong: a plain hash of a 4-digit PIN cracks in seconds from a copied file. Offline unlock now needs a Keystore HMAC.
- The spec said typing does everything a scan does. Moving a unit to In stock needs the label read (handoff §2).

**Verified** in Chromium at 402 × 874, every state:
- Contrast passes on all 252 text pairs, with no failures. Text over the camera is measured against a white scene, and text on the gate against a white photo under the scrim. The lowest is 5.19:1 (`text-2` over a white background).
- 111 of 111 tap targets are at least 48.
- No control is missing a name.
- Focus order runs top to bottom in every state.
- No console errors.
- Anti-slop: clean.

**Measured fix:** the gate scrim went from 75% to 80%, because `text-2` measured 4.36:1 over a white photo.

**Not verified:**
- font scale 1.3 and 2.0
- 360 × 640
- landscape (these are Roborazzi tests in the spec)
- on-device camera behaviour
- real shop photo and unit images

**Server and app needs:** spec §5. In brief:
- 4-digit PIN
- `attemptsLeft`, `lockedUntil` and `expiresAt` from the server
- `employeeId` on queued changes
- `changed:false` on a repeat tag confirm
- ids and initials from `/auth/employees`

No app or server code changed.

---

## 2026-10-30: Home design LOCKED

The owner said "lock it in". **Locked:**
- **Layout A2B1C2:** Stock at a glance first, then Incoming to confirm; two equal quick-job tiles; the ID shown as a tag plate.
- **Find:** a button in the top bar (icon plus the word "Find") that opens a search sheet.
- **Count cards:** three in the first release, kept at their current size. The owner accepts that on iPhone 17 only the top of the first Incoming card shows. "Sold this week" is TBA.

**Reference:**
- `docs/ui-rebuild/prototypes/home-a2b1c2.html`
- `docs/ui-rebuild/landing-nav-ui-prompt.md`, section 0

**Decided but not locked:**
- the idle lock (30 minutes by default, configurable, "Name only" option)
- cross-platform with Android first

**Recommendation only:** Compose Multiplatform as the route to iOS.

---

## 2026-10-30: owner answers on Home, devices and sign-in

These are owner decisions, not locked.

- **Section order stays Stock first.** Search moves to a "Find" button in the top bar that opens a search sheet. Home no longer has a Find field.
- **Devices:** no Pixel Fold. The shop uses current iPhones and iPads, so the app must be cross-platform. Android first.
  - Design frames are iPhone 17 / 17 Pro 402 × 874 pt, Pro Max 440 × 956, and iPad 820 × 1180 (sizes checked against a published list).
  - The recommended route to iOS is Compose Multiplatform (iOS support stable since 1.8.0). This is not decided.
  - The Brother Mobile SDK lists the PT-P910BT for iPhone/iPad.
- **Idle lock:** the phone goes back to the PIN screen after 30 minutes idle by default. Admins can change the time or choose "Name only" sign-in. The shop system must enforce both settings.
  - Conflict with a locked handoff rule (§2): the assistant may start a sale without a tap only for PIN-signed-in employees. Under "Name only", that step needs an explicit confirm.
- **Verified** (mockup at 402 × 874):
  - Contrast passes on all 59 text pairs.
  - All 16 tap targets are at least 48 dp.
  - No control is missing a name.
  - Find opens the sheet with focus in the field, and Cancel returns focus to Find.
  - No console errors.

No app code changed.

---

## 2026-10-30: Home mockup, Phases 2–4

- **Phase 2:** the owner ran `/prototype` and picked **A2B1C2**: Stock first, two equal tiles, ID tag plate. **Approved, not locked.**
- **Phase 3:** single-design mockup at `docs/ui-rebuild/prototypes/home-a2b1c2.html`, polished with `emil-design-eng`. Role tokens (`accent-*`, `good-*`, `info-*`, `warn-*`, `danger-*`, `neutral-*`) mean an accent swap is one block of values. `accent-swap.png` shows blue, with contrast checked.
- **Phase 4:** the review gate ran. Results and dispositions are in `docs/ui-rebuild/council-review-home.md`:
  - 5 adversarial council seats, `ux-ui-audit` probes, and anti-slop on the code.
  - About 26 findings fixed in the mockup and spec, 3 rejected with reasons, 3 waiting on the owner.

**Verified after the fixes**, re-measured in Chromium at 412 dp wide:
- Contrast passes on all 57 text pairs. The Scan glyph is 5.7–9.4:1.
- 16 of 16 tap targets are at least 48 dp.
- No control is missing a name.
- Focus order matches screen order.
- No console errors.

**Waiting on the owner:**
- D1: section order. A1 (Incoming first) is recommended.
- D2: which Pixel Fold model.
- D3: idle lock and "Checking in as Dale" on shared phones.

**Not verified:**
- font scale 1.3 and 2.0 in Compose
- real unit images
- on-device rendering of the depth

No app code changed.

---

## 2026-10-30: unit-types decisions locked

The owner locked the four decisions in `docs/ui-rebuild/unit-types-spec.md`:

1. Removing a type is blocked while units of **any status** use it, including sold and retired.
2. **"Other"** is an undeletable fallback type.
3. **One level of types.** MX, street and electric go through the model and sub-model fields.
4. **Only admins** can add, rename or remove types.

Implementation has not started: no code changed.

---

## 2026-10-29: UI rebuild, design direction (Phases 0–1)

**Branch:** `ui-reset`
**Files:** `docs/ui-rebuild/`

### Locked by the owner

| Decision | Detail | Evidence |
|---|---|---|
| **Palette v4 "true charcoal"** | bg `#1E1F21`, surface `#28292C`, raised `#323337`, emerald `#10B981` for actions only. Ocean blue is used for status only. | `docs/ui-rebuild/palette-v4-true-charcoal.png` |
| **Bottom nav** | Five slots: Home · Stock · [Scan] · Tickets (TBA) · More. Scan is a raised center action. Tickets opens an honest "not built yet" screen until work tickets ship. | — |
| **Depth option B** | Only things you can press get depth (Scan, tiles, count cards, the selected nav pill, the Find field as an inset well). Information surfaces stay flat. | `docs/ui-rebuild/depth-options.png` |
| **PINs are exactly 4 digits** | **Needs a server change:** `server/.../Auth.kt` line 32 currently allows 4–8 digits. Existing employees with longer PINs must be reset. | — |

### Owner requirements (stated, design in progress)

- **Unit types are owner-editable** (add, rename, remove).
- **Removing a type fails loudly** while any unit uses it. The spec is `docs/ui-rebuild/unit-types-spec.md`. Its four defaults were locked on 2026-10-30; see the entry above.
- **Unit types confirmed by the owner:** off-road and electric motorcycles, ATVs, go-karts.
- **Not yet confirmed:** street motorcycles, side-by-sides, snowmobiles, jet skis, dune buggies.

### Recommended, not locked

- **Offline stays non-blocking (amber banner).** A full-screen "Can't reach the shop system" state is used only where the server is required: connecting the phone, signing in, and later finishing a sale. This follows the locked offline-first rule in `docs/ENGINEERING-HANDOFF-2026-10-27.md` §2.
- **Nav labels stay as words** and stop growing at 18 sp. No icon-only nav.
- **Status is shown as icon plus word**, with no pill around it. Pills are used only for tappable chips.
- **Typefaces:** Barlow Semi Condensed (headings), Atkinson Hyperlegible Next (body), Atkinson Hyperlegible Mono (IDs, VINs, ticket numbers only).

### Verification done

- **Contrast:** every palette pairing was computed with the WCAG 2.x relative-luminance formula in a script, not estimated. Main text is 11.4–14.9:1, secondary text 7.6–9.9:1, status ink on cards 6.4–10.7:1, and the Scan glyph 5.7–9.4:1.
- **References:** the 19 Mobbin references in `design-brief.md` were cross-checked against the search tool's output: IDs and app names match.
- **Anti-slop pre-check:** `vectorlab-ux-skills:anti-slop` was run against the written spec (`design-brief.md` §3b). It fixed two problems: the Scan gradient spanned too much lightness, and status pills had depth even though they can't be tapped.

### Not done or not verified

- **No app or server code was changed in this entry.** Nothing was built, installed or tested on the Pixel.
- **No mockups exist yet.** Phase 2 (`prototype` variants) is next.
- **The Phase 4 review gate has not run:** council, anti-slop on the mockups, `ux-ui-audit`, and the Adversarial UITest/UXTest skills.
- **Not run:** SkillDB router (unavailable in that session) and `/slopmonster` (owner says it is a Claude Code skill on the Desktop PC).
- **Size estimates:** nav-label and count-tile widths at large font scales are arithmetic estimates and need a check on the Pixel Fold.
