# DEV-JOURNAL

Project decisions and verification evidence, newest entry first. Nothing counts as locked unless the owner said "lock it", and each locked item below says so.

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
