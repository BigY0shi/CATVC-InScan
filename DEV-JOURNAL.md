# DEV-JOURNAL

Project decisions and verification evidence, newest entry first. Nothing counts as locked unless the owner said "lock it", and each locked item below says so.

---

## 2026-11-03: server groundwork deployed to the field-test server

- **Owner:** "can you use /homelab-credentials and /proxmox-homelab to backup and update the inscan server" (2026-11-03).
- **What was running:** commit `ca69184` (M2 QR labels, a branch build that never landed on main as-is), schema 2. No units yet: 2 people, 2 phones, 6 audit rows.
- **Before updating:** main's migrations 1 and 2 were compared with the running build's: identical, so 3 to 8 apply on top. Main serves every route the running build served.
- **Backup (Updating step 1):** `sqlite3 .backup` while running, to `/var/lib/inscan/before-update-2026-11-03-193206.db`. `integrity_check` ok; every table's row count matched the live file. A copy is on the owner's PC, outside the repo.
- **Installed:** the `inscan-server` artifact from CI run 41640790316 (main at `d1b7de9`). It was not built in the container: `gradle.properties` asks for a 4 GB heap and the container has 1 GB. The old install was moved to `/opt/inscan-server.prev-20261103193326`; the existing systemd unit was kept.
- **Verified after the start:**
  - `/health` ok; `/units` without a token answers 401.
  - `schema_version` holds 1 to 8; `integrity_check` ok; row counts unchanged.
  - Roles seeded (Employee, Management, Admin). Both existing people were `admin` and now point at Admin.
  - Tailscale still serves it to the tailnet only.
- **Phone check (Updating step 4), passed 2026-11-03:** the owner's phone, already signed in on the Oct 27 build, reached the updated server and cleared its offline banner (owner: "Yes it connects fine"). Its first failure was Tailscale switched off on the phone, not the server. Shop phones should set Tailscale as Always-on VPN.
- **The phone's build:** its app code matches main's except one shared file with new role and people messages, for routes that don't exist yet. The CI APK can't update it in place: a different signing key.
- **Note for the unit:** stopping the service logs exit 143 (the JVM's normal SIGTERM exit) as a failure. `SuccessExitStatus=143` in the unit would quiet it; not changed.
- **Rolling back:** stop the service, move `inscan.db`, `inscan.db-wal` and `inscan.db-shm` into `.exile/<date>/`, copy the backup in as `inscan.db`, move `/opt/inscan-server.prev-20261103193326` back to `/opt/inscan-server`, start.

---

## 2026-11-03: app icon and splash LOCKED

- **Owner:** "Yeah that's what I was thinking, lock it. I would like it to slow dwn a tiny bit at the end of it's spin, but it's good for now" (2026-11-03).
- **What's locked:** the mark, colours, motion and timing in `splash-ui-spec.md` and `prototypes/splash.html`, including D-SP1 to D-SP6.
- **Changed at the lock, as the owner asked:**
  - The spin eases into its stop: easing `cubic-bezier(.5,0,.2,1)`, where it was `(.55,0,.15,1)`.
  - The spin runs about 290 ms, where it was about 250 ms. The hold is about 300 ms, where it was about 340 ms.
  - The burst is still 760 ms.
- **Lock card:** `landing-nav-ui-prompt.md` §0 has a new SPLASH entry.
- **Merged since the last entry:** #20.
- **Re-shot:** `splash-states.png` and `splash-slow-start.mp4`.

---

## 2026-11-03: splash decisions, yes to all; D-SP3 becomes a burst spinner

- **Owner:** "Yes to all. For SP3, make it a spinner? Have the scanner pop in and then spin the bracket, BUYt not continuously, have it do a kind of wind up - spin - stop - wind up - spin - stop".
- **Decided, not locked:** D-SP1 to D-SP6. D-SP6 was already decided.
- **D-SP3 as built in the mockup:**
  - The brackets lock on (0–300 ms).
  - The scanner pops in (150–450 ms), with a 1.06 overshoot because the owner asked for a pop.
  - From 500 ms, 760 ms bursts repeat: wind up −14° (about 170 ms), spin to +90° (about 250 ms, hard stop), hold (about 340 ms).
  - A quarter turn maps the four brackets onto themselves, so the loop has no seam.
- **Checks:**
  - Rotated bracket corners reach about 32 of the 36 units the splash circle shows, so the spin never clips.
  - With the 1 s cap (D-SP4), most starts show one burst.
  - The spec reads Google's "no spinners" as meaning a separate spinner view, not the icon's own looping animation. That reading is labelled an inference.
- **To check at build time:** keyframed `objectAnimator` support inside an AVD on our minSdk, and whether looping plays on API 31–32.
- **Mockup:** `splash.html` now has 10 states, "Play a cold start" (ready at 0.9 s) and "Play a slow start" (3 s, to show the loop). `splash-states.png` was re-shot.
- **Verified:** all states render with no console errors.

---

## 2026-11-02: splash mark, the scanner inside the first draft's brackets

- **Owner:** "bring back the brackets, but use the ones from your original logo".
- **D-SP1 changed:** the scanner, enlarged to fit, inside the square-ended brackets from the first (tag) draft. The beam and scan line are removed.
- **Animation:** the brackets slide in (0–380 ms) and the scanner swings in (140–540 ms). 600 ms in total.
- **Regenerated:** the five SVG masters, `splash.html` and `splash-states.png`.
- **Verified:** all 8 states render with no console errors. The mark reads at 32 px and as a themed icon.

---

## 2026-11-02: splash mark, the scanner as the main image

- **Owner:** "Keep it emerald. You didn't have to copy it wholesale, but I thought the scanner was a better main image."
- **D-SP6 decided:** emerald.
- **D-SP1 changed:** the scanner alone, larger, firing an emerald beam at a scan line. The reference's brackets and arcs are gone.
- **Variants rendered at 32–160 px:**
  - Scanner in our square brackets: the scanner stays small.
  - Scanner with beam and scan line: chosen. It's the strongest silhouette.
  - Scanner reading a tag: the tag is lost at launcher size.
- **Animation:** the scanner swings in (0–400 ms), the beam fades on (380–520 ms) and the scan line draws (420–650 ms). 700 ms in total.
- **Regenerated:** the five SVG masters, `splash.html` and `splash-states.png`.
- **Verified:** all 8 states render with no console errors.

---

## 2026-11-02: splash mark follows the owner's reference logo

- **Owner:** sent a reference logo (a handheld scanner inside blue rounded scan brackets with signal arcs; wordmark "InScan", tagline "BARCODE READER"), then: "Use something more like that logo".
- **Changed:**
  - D-SP1 is now a handheld scanner in rounded brackets with two arcs, hand-drawn for icon sizes.
  - Auto-tracing the logo gave a messy outline, so the silhouette was redrawn by hand.
  - The splash animation adds the arcs, for 700 ms in total.
  - The five SVG masters, `splash.html` and `splash-states.png` were regenerated.
- **Added D-SP6:** keep emerald rather than the logo's blue, because palette v4 is locked. The "BARCODE READER" tagline isn't used.
- **The tag mark** is kept in the spec as the fallback. Its SVGs are in git history (commit 2d949cd).
- **Verified:** all 8 mockup states render with no console errors. The mark reads at 32 px in the circle and squircle masks, and as a themed icon.

---

## 2026-11-02: app icon and splash screen, first draft

- **Owner:** "We also never did a splash screen" (2026-11-02).
- **Found:**
  - No splash anywhere in the UI rebuild specs.
  - The launcher icon is still the Android Studio template: the green robot in `ic_launcher_foreground.xml` on `#3DDC84`, plus template `.webp` mipmaps.
  - The theme is the template's `Theme.MyApplication`.
- **Made:**
  - `docs/ui-rebuild/splash-ui-spec.md`
  - `prototypes/splash.html`: 8 states plus a playable cold start
  - `prototypes/splash-states.png`
  - Five SVG masters in `docs/ui-rebuild/assets/`
  - Decisions D-SP1 to D-SP5 are all open.
- **Mark explored, kept and dropped:**
  - Kept: a tag inside scan brackets (D-SP1). It reads at 32 px and as a themed icon.
  - Dropped: a bracketed "i", which read as the system info icon.
  - Dropped: a tag with QR modules, which turned to mush at small sizes.
  - Dropped: charcoal icon plates, which vanish on dark wallpapers.
- **Conflict flagged, not overridden:** the lock card says "emerald for actions only". The icon and splash use emerald as brand colour. D-SP2 asks the owner to allow that exception for the icon and splash only.
- **Evidence (checked 2026-11-02):**
  - **Android splash rules** (developer.android.com/guide/topics/ui/splash-screen):
    - Icon without a background: 288 dp canvas inside a 192 dp circle. With a background: 240 dp canvas inside 160 dp.
    - Branding image: 200×80 dp, which the design guidelines recommend against.
    - Animated icon: an AVD of 1,000 ms or less, on a 432 dp canvas.
    - `setKeepOnScreenCondition` is for small local loads only.
    - `androidx.core:core-splashscreen:1.2.0` covers API 23 and up.
  - **iOS 27 SDK** requires a launch screen. Keys: `UILaunchStoryboardName`, `UILaunchStoryboards`, `UILaunchScreen` or `UILaunchScreens`. Source: Apple's release notes as quoted at blakecrosley.com/blog/ios-27-launch-screen-requirement.
  - **Apple's HIG "Launching" page** did not load, so its wording on logos is unverified.
  - **Contrast:** emerald #10B981 on charcoal #1E1F21 is 6.5:1; cream #F2F3F4 on charcoal is 14.85:1; ink #06140E on emerald is 7.43:1.
- **Verified:** all 8 mockup states render with no console errors (Playwright, 402×874 at 2x). The exit state embeds the live `pin.html?shot&state=who`.
- **Not done:** no app code. The template icon and theme are still in `app/`.

---

## 2026-11-02: D-C10, reading the label from a photo (open model, no Gemini)

- **Owner:** "Yes add D-C10, the gemini key was only leaked internally, but we can't use my gemini key for a production build. We should be using a yolo-style open model" (2026-11-02).
- **This replaces `_program-x/RUN-001-PLAN.md` row 14**, which said to move the Gemini key to the server. The rules it carried still hold: read text only and never invent; every filled value stays unconfirmed until a person confirms it; a VIN must pass the check digit.
- **What changed:**
  - `checkin-ui-spec.md` has a new section 3b-2 and decisions D-C10 (decided) and D-C11 (open). It also covers the platform notes, data needs, "do not render" items, tests and reporting events.
  - **D-C10 flow:** a "Photo of the label" button on step 1 takes one still, which is read on the phone. Values only fill empty fields, never the price, type or notes. Each carries "From the label. Not checked yet." One tick on Review confirms them; Save is refused without it. The photo is deleted on Save and never uploaded.
  - `prototypes/checkin.html` gained the states `vin-photo` and `review-photo` (20 states in all). `checkin-states.png` was re-shot.
  - The README's "photo autofill" line no longer says it waits for the server to hold a key.
- **Owner clarified (2026-11-02):** "I was just meaning an open source detection model rather than a paid frontier API". So the requirement is open source and no paid API, not YOLO itself.
- **Owner, later the same day: "Yes paddle OCR is fine".** D-C11 is decided: PaddleOCR. The benchmark only picks the model size (PP-OCRv6 small or tiny, or PP-OCRv5 mobile Latin), with ML Kit as the baseline.
- **D-C11 reasoning:** a YOLO detector boxes objects but doesn't read characters, so the reader needs a text model.
  - The recommendation is PaddleOCR (Apache 2.0), which detects text lines and then reads them, run on the phone with ONNX Runtime.
  - Ultralytics YOLO is AGPL-3.0. This repo has no licence file, so shipping it would mean putting the whole app under AGPL or buying their Enterprise licence.
  - A YOLO-family detector stays parked. If it comes back, the Apache 2.0 option is YOLOX.
  - Benchmark PP-OCRv6 small, PP-OCRv5 mobile and ML Kit Text Recognition v2 on 30 or more real shop labels before choosing.
- **Evidence (checked 2026-11-02):**
  - Ultralytics licence page: "All Ultralytics YOLO trained models fall under the AGPL-3.0 License by default"; an Enterprise licence is required "to use Ultralytics YOLO without open-sourcing your entire project" (ultralytics.com/license).
  - PaddleOCR README: Apache 2.0. 3.7.0 was released on 2026-06-11. PP-OCRv6 comes in tiny 1.5M, small 7.7M and medium 34.5M sizes, with ONNX export (github.com/PaddlePaddle/PaddleOCR).
  - The PP-OCRv5 mobile ONNX build is Apache 2.0 and has a Latin recogniser with 838 classes (huggingface.co/nakamura196/ppocrv5-mobile-web-onnx).
  - YOLOX is Apache 2.0, with ONNX and ncnn deployment (github.com/Megvii-BaseDetection/YOLOX).
  - ML Kit Text Recognition v2 has a bundled artifact, `com.google.mlkit:text-recognition:16.0.1`, adding about 4 MB per script and architecture (developers.google.com/ml-kit/vision/text-recognition/v2/android).
  - ONNX Runtime on iOS: `onnxruntime-objc` on CocoaPods (onnxruntime.ai/docs/get-started/with-obj-c.html). **The Android artifact name is not checked yet.**
  - `ls LICENSE*` in the repo: none.
- **Verified:** all 20 mockup states render with no console errors (Playwright, 402 × 874 at 2x). The new warn marker, `amber-ink` #F4BC50, measures 9.55:1 on `bg` and 8.42:1 on `surface`.
- **Not done:** no app or server code. M1c becomes "label reader on the phone" instead of "Gemini on the server". `analyzeImageWithGemini` and the "Gemini-3.1-Pro Engine" text are still in the app until the check-in flow is built.

---

## 2026-11-02: Stock list LOCKED

- **Owner:** "Lock it." (2026-11-02).
- **What's locked:** the layout, states and copy in `stock-list-ui-spec.md` and `prototypes/stock.html`, including D-S1 to D-S3 and D-H4 on cards.
- **What isn't locked:** the server and app needs in §8.
- **Lock card:** `landing-nav-ui-prompt.md` §0 now has a STOCK entry.
- **Merged since the last entry:** #16 and #17. CI passed on main after #17. The field-test server isn't updated yet.

---

## 2026-11-01: server groundwork built (steps 0 to 11)

The owner approved the plan ("it's a go on server work"). It's built on branch `server-groundwork`, one commit per step. **Not deployed:** deploying is the owner's job.

- **What changed:**
  - **Clock:** one injected clock.
  - **Migrations and times:** Kotlin migration steps; fixed-width UTC times.
  - **`occurredAt`:** clamped and flagged.
  - **Settings:** Shop settings and `ShopCalendar`.
  - **Status changes:** `unit_status_changes` and `in_stock_since`, backfilled.
  - **Refusals:** recorded so they survive the rollback; every sign-in try is recorded.
  - **Roles and permissions:** with the seven guardrails.
  - **Lists:** no ids in unit lists without See Reports.
  - **Imports:** their own action.
  - **Indexes:** for reports, history and Stock.
- **Differences from the plan:** listed in `docs/server-groundwork-plan.md`, "As built". The main ones:
  - `/units` keeps `createdBy` and `updatedBy` as empty strings, because released phones' Moshi parsing requires them.
  - Roles and people have no HTTP routes until their screens are designed.
- **Verification:**
  - **Tests:** 63 server tests pass locally, up from 15. Core tests pass too.
  - **Sabotage checks** turned tests red each time:
    - variable-width stamps (4 tests)
    - a UTC-only calendar (4)
    - a dropped setup-code permission check (1)
    - a dropped index (1)
  - **End to end:**
    - The released server (`main` at `0c3fae5`) built a database through its API: 3 units, a tag printed and confirmed, Sold, and a refused retire.
    - The new server migrated a backup of that database to schema 8. All time values became fixed-width, people kept equal roles, and the backfill wrote 3 status rows.
    - Driven like phones over HTTP, it behaved as planned:
      - An old phone token still signed in.
      - The Employee's list held no ids.
      - Management could make a setup code and the Employee couldn't.
      - An offline check-in kept its phone time.
      - Only Admin could change a Sold unit.
  - **Not verified here:**
    - The app's tests. CI runs them; there is no Android SDK in this container.
    - A real phone.
- **Deploy finding:** a plain copy of `inscan.db` taken after the server stopped held 2 of 13 audit rows; the rest were in `inscan.db-wal`. `DEPLOY-PROXMOX.md` "Updating" now says to back up with `sqlite3 .backup` first and how to roll back.
- **Local builds:** Maven Central rate-limits this container (HTTP 429). A local-only Gradle init script, not in the repo, tries Google's Maven Central mirror first.

---

## 2026-11-01: D-S1 to D-S3 yes; D-H4 on cards: employees see no name

These are owner decisions, not locks. Stock is still not locked.

- **D-S1 to D-S3: yes.** Stock opens on In stock. A search stays in the chosen status, and the chips show matches in other statuses. The phone keeps every unsold unit plus the newest 200 sold.
- **D-H4 on record cards: "employees see no name."** People without See Reports see "checked in Tue" on every card, their own units included. There is no "by you". Managers and admins still see names, online only.
  - This amends the locked Home copy. The lock card in `landing-nav-ui-prompt.md` §0 now says so, and the rule covers Scan's result card and every list card. The locked mockups stay as drawn; the spec text governs.
  - Stock's "Only units checked in by you" filter stays. It filters; it puts no name on a card.
- **Updated:**
  - `stock-list-ui-spec.md`: status line, §5 names, §10, §11.
  - `prototypes/stock.html` card copy and `stock-states.png`, re-shot. Verified: 8 states render, no console errors.
  - `my-history-ui-spec.md` §8.
- **Server groundwork:** the owner said "go" on `docs/server-groundwork-plan.md`. That work is on its own branch.

---

## 2026-10-31: Stock list designed (Phases 0 to 4)

**Owner asked for:** "Stock list".

**What was made:**
- `docs/ui-rebuild/stock-list-ui-spec.md`
- `prototypes/stock.html` (8 states) and `stock-states.png`
- `council-review-stock.md`

**Review gate:**
- Five council seats ran. Craftsman timed queries against sqlite-jdbc 3.53.4.0: about 3 ms per keystroke at 5,000 units. FTS5 is present in the server's SQLite but missing from the phone's.
- Re-measured: 503 of 503 text pairs pass AA, 168 of 168 targets are at least 48, nothing is unnamed and there are no console errors.

**What matters most:**
- **The phone's stored copy is wrong today.** It holds the 500 newest units, is never pruned, and can carry names or a "mine" flag from whoever last synced. It becomes every unsold unit plus the newest sold ones, with no person fields. "Mine" moves to its own table, cleared by the wipe.
- **One count source** now feeds both Home and Stock.
- **One search matcher** in `core`. "ID 3HZW8RTB" failed outright before.
- **Incoming opens oldest first,** as the scan queue.

**Open for the owner:**
- D-S1 to D-S3
- the D-H4 question on Home's record cards

**Local build note:** Maven Central rate-limited this container (HTTP 429) while resolving Gradle plugins. CI runs the same tests.

---

## 2026-10-31: My history LOCKED; D-RS1 to D-RS3 and D-H1 to D-H4 yes

**Owner:** "all yes. lock it, Stock list, and then server groundwork"

**LOCKED:** **My history**, in `my-history-ui-spec.md` and `prototypes/my-history.html`. The server and app needs in §7 are not part of the lock. The lock card gains a MY HIST entry.

**Decided:**
- **Reports:**
  - D-RS1: Export offers each line or the table.
  - D-RS2: "Export report data" is its own permission.
  - D-RS3: online only, with no saved copies.
- **My history:**
  - D-H1: the footnote says who else can see the list.
  - D-H2: no sign-ins in the list.
  - D-H3: Work by person opens "Dale's work". The locked Reports §10 line is amended to match, with a note.
  - D-H4: names are dropped from bulk lists and the phone's copy for people without See Reports.

**Waiting on the owner:** D-H4 reaches the locked Home. Its record cards say "checked in by Dale". Under D-H4, Employees would see "by you" or no name. That changes the copy of a locked screen, so it needs an explicit OK. Home stays as locked until then.

**Next:** design the Stock list. Then the server groundwork plan.

---

## 2026-10-31: Reports LOCKED; My history designed and reviewed

**LOCKED by the owner:** the **Reports screens**, in his words: "Yes that looks great! Lock it in."
- The lock covers the layout, states and copy in `reports-ui-spec.md` and `prototypes/reports.html`.
- The server prerequisites (§14), the first-release slice (§17) and D-RS1 to D-RS3 are requirements and decisions, not part of the lock.
- The lock card in `landing-nav-ui-prompt.md` §0 gains a REPORTS entry.

**New owner requirement:** "Only the manager/admin role can run reports anyway. Regular shop members should just be able to see their own history."
- That became **More → My history**, open to every role. It shows only the person's own work.
- Managers reach the same screen for one person from Work by person. That is D-H3, which amends the locked Reports §10 line; until the owner answers, the locked text stands.

**What was made:**
- Spec: `docs/ui-rebuild/my-history-ui-spec.md`.
- Mockup: `prototypes/my-history.html`, 6 states, with the contact sheet `my-history-states.png`.

**Review gate:**
- Three council seats: Skeptic, Advocate and Artisan. The dispositions are in `council-review-my-history.md`.
- Re-measured: 257 of 257 text pairs pass AA (the lowest is 5.70:1), 68 of 68 targets are at least 48, no unnamed controls, no console errors.
- Re-shooting found a real layout bug: a `min-height` child squeezed by the scroll column. It is fixed.

**What the review found that matters most:**
- **Credit for work.** The server credits whoever *sends* a queued change, not whoever made it. A legacy upload would put its units under the uploader. Both are now prerequisites (Scan spec §5, plus a separate import action).
- **Counting.** One tag confirm writes three audit rows. History and Work by person now share one classifier.
- **Privacy.** Raw before/after JSON holds other people's IDs, so the server returns typed changes instead.

**Changed elsewhere:**
- `roles-permissions-spec.md`:
  - added "See my own history" (all roles)
  - added guardrail 7: nobody is watched without seeing their own record
- `reports-ui-spec.md` §14: `TagPrinted` added to the `occurredAt` list. That section is not part of the lock.

**Open for the owner:**
- D-H1 to D-H4 (my-history spec §8)
- D-RS1 to D-RS3 (Reports spec §15)

---

## 2026-10-30: Reports screens designed (Phases 0 to 4)

**Owner asked:** "go on reports". Designed from `reporting-spec.md` §5 and `roles-permissions-spec.md`. Nothing is locked.

- **Phase 0:** opened 11 Mobbin references; they are listed in the spec's §1. Key references:
  - Jobber: grouped figures, each with its period
  - Starling: date presets that show their real dates and the week start
  - YNAB: thin proportion bars
  - Buddy: the row count before export
- **Spec:** `docs/ui-rebuild/reports-ui-spec.md`. It covers:
  - the list (business view) and Shop system one level down
  - one report template, date range sheet, charts built to the dataviz method, drill-down and CSV export
  - states and the shared-phone wipe
  - definitions, permissions and settings, reporting, and server prerequisites
  - D-RS1 to D-RS3, the test contract and the first-release slice
- **Mockup:** `prototypes/reports.html`, 14 states, with contact sheet `reports-states.png`.
- **Phase 4:** five council seats (Skeptic, Advocate, Artisan, Pathfinder, Craftsman), the measured audit, anti-slop and the palette validator. Dispositions are in `council-review-reports.md`.
  - Re-measured: 650 of 650 text pairs pass AA (lowest 5.70:1), 227 of 227 targets are at least 48, none unnamed, no console errors.

**What the review found that matters most:**
- **The server can't produce honest by-day numbers yet.** It has no `occurredAt`, a global clock, variable-width timestamps, and no time zones in SQLite, and the sample week includes the end of DST. These are now prerequisites in spec §14, with an order in §17.
- **Drill-down counts must match their report by construction.** That needs a server-built `UnitFilter` and a paged `/units/query`; today `/units` caps at 500 with no total.
- **Shared phones:**
  - Offline copies are dropped from the first release (D-RS3).
  - Export files and the More stack are wiped whenever the signed-in person changes, by any path. The sign-in response needs `employeeId`.
  - Person filters need "See Reports".
- **Android export would crash today:** `file_paths.xml` has no cache path. On iPad the share sheet needs an anchor. iOS has no native range picker, so it uses the Material 3 `DateRangePicker` from shared code.

**Changed elsewhere:**
- `roles-permissions-spec.md`: "Export report data" split out as its own permission; role changes now show under Shop system.
- `reporting-spec.md` §5: marked as superseded in detail by the Reports spec.

**Merges seen this session:** the owner merged #11 into main, then opened #13 from `ui-reports` at 4c8f1c3 and merged it into main. #12 merged into `ui-scan-pin` (its base), and its content is identical and already in main through #13. The Reports work is on `ui-reports` after that point.

---

## 2026-10-30: Scan screen LOCKED; shop owner holds Admin

**LOCKED by the owner:** the **Scan screen**, `scan-pin-ui-spec.md` section 2 and `prototypes/scan.html`. It is locked as it stands on `ui-checkin`, including the three result-card rows that check-in added: "Found, still Incoming" with its facts row, "Tag out of date", and "That's a different tag". The lock card in `landing-nav-ui-prompt.md` §0 gains a SCAN entry. The reader and permission rules (2e, 2f) are build requirements, not part of the lock.

**D-P1, answered:** *"Shop owner will get admin, they need complete control. As well as my Nephew, and me."*
- The Admins at launch are the shop owner, Yoshi and Yoshi's nephew.
- Locked unit-types decision 4 holds as written.

**Owner's review:** "Everything looks good." The roles model, guardrails, default permissions and D-R2 are **decided, not locked**.

---

## 2026-10-30: roles and permissions; reporting D-R3 to D-R5

**Owner direction**, in his words: *"Everything in the whole app should be configurable by admin/owners really....but at launch there needs to be 3 roles. Management, Employees, Admin/Tech Support (me)."* He said **yes to D-R3, D-R4 and D-R5**: events kept 13 months, CSV export, and More → Reports. All are decided, **not locked**.

- **New draft:** `docs/ui-rebuild/roles-permissions-spec.md`.
  - The server and app check **permissions**, not role names.
  - Roles are data, and three are seeded at launch.
  - Guardrails: Admin is fixed, there is always an Admin, only Admin edits roles, and every change is audited.
  - It includes a default permission table and the server changes needed.
- **The current code has two roles,** fixed by a database check: `role IN ('employee','admin')` in `Database.kt`. Two admin gates use `isAdmin`: setup codes in `App.kt`, and retire / change-from-Sold in `Units.kt`.
- **D-R1 and D-R2 now map onto permissions** (spec §4).
- **Open: D-P1, which role the shop owner holds.** The recommendation is Admin, alongside tech support. If the owner sits in Management, **locked unit-types decision 4** ("only the owner changes the list") can't hold as written. The conflict is surfaced in spec §5, not overridden.
- **Phase 4 gate:** every spec now also needs a "Permissions and settings" section. I fixed two council/UITest rows that had been stranded outside the gate table.

---

## 2026-10-30: check-in decisions; reporting becomes a rule for every flow

**Check-in:** the owner said **yes to D-C1 to D-C9** in `docs/ui-rebuild/checkin-ui-spec.md` §7. These are decided, **not locked**.

**New owner requirement**, in his words: *"Design every flow with analytics/reporting in mind. The owner/managers should have a 'reports' type area that gives them breakdowns of everything that we keep track of in the app."*

Recorded in `docs/ui-rebuild/reporting-spec.md`:
- **Rules for every flow:**
  - Every spec has a Reporting section.
  - Every change carries who made it and when it happened.
  - Writes go to the audit log; other events go to `app_events`.
  - Refusals are recorded.
  - Every list filters the same way reports break down.
  - There is one shop clock.
  - Events stay on the shop system, with no third-party analytics.
- **What's already recorded (checked):** `audit_log` covers unit create, update and status changes, tag print and confirm, sign-in, failed PINs, and phone enrolment, each with who, phone and before/after.
- **Gaps:**
  - The server stores only the time it received a change, not when it happened.
  - Scans, lookups, print failures and abandoned check-ins aren't recorded.
  - Refusals aren't recorded.
- **The Reports area:** More → Reports, for the owner and managers.
  - Not designed yet.
  - An outline exists with the first set of reports: inventory, intake, tags, sales, scanning, people and phones, data quality.
  - Five owner decisions (D-R1 to D-R5).

**Hooked into the other docs:**
- Reporting sections added to `scan-pin-ui-spec.md` §9 and `checkin-ui-spec.md` §10.
- More → Reports added to the router in `landing-nav-ui-prompt.md` §4d.
- `design-skill-pipeline.md` now fails a spec at the Phase 4 gate if it has no Reporting section.

No app or server code changed.

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
