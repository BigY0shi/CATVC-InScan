# InScan: app icon and splash screen

- **Status:** **LOCKED** by the owner on 2026-11-03 ("lock it"), with one change he asked for at the lock: the spin eases into its stop a little more. Owner, 2026-11-03: "Yes to all", with D-SP3 changed to a burst spinner ("wind up - spin - stop - wind up - spin - stop"). History, 2026-11-02: the owner sent a reference logo (a handheld scanner inside blue scan brackets, "InScan / BARCODE READER") and said "Use something more like that logo". Then: "Keep it emerald. You didn't have to copy it wholesale, but I thought the scanner was a better main image." Then: "bring back the brackets, but use the ones from your original logo". So D-SP1 is the scanner as the main image inside the first draft's square scan brackets, and D-SP6 (emerald) is decided. Nothing else here is decided or locked until the owner says so.
- **Why now:** the owner pointed out the app never had a splash screen. The repo also has no app icon of its own: `app/src/main/res` still holds the Android Studio template (the green robot on a grid). A splash on Android 12+ is built from the app icon, so this spec covers both.
- **Mockup:** `prototypes/splash.html` (10 states, plus "Play a cold start" and "Play a slow start"). Contact sheet: `prototypes/splash-states.png`.
- **Icon masters (SVG, 108-unit adaptive grid):** `assets/inscan-icon-foreground.svg`, `assets/inscan-icon-background.svg`, `assets/inscan-icon-monochrome.svg`, `assets/inscan-icon-full.svg` (store and iOS), `assets/inscan-splash-mark.svg`.
- **Builds on:** `landing-nav-ui-prompt.md` §0 (palette v4, dark charcoal only, emerald for actions only; see D-SP2) and `scan-pin-ui-spec.md` §3 (the first screen after the splash).

---

## 1. References (opened and looked at this session)

| Reference | What we take | What we leave |
|---|---|---|
| **Owner's reference logo** (sent 2026-11-02, not in the repo) | The handheld scanner as the main image | Its rounded brackets and signal arcs (the brackets are the first draft's square ones instead), the blue (D-SP6), the thin seam lines (lost below 96 px) and the wordmark |
| [Spotify, launch](https://mobbin.com/screens/259aa2ac-78f1-49be-a3fc-91851f61dbbd) | One brand-colour mark alone, centred on near-black. Nothing else. | Its round plate (ours has no plate on the splash) |
| [WHOOP, launch](https://mobbin.com/screens/062557a9-3719-49fa-8803-e703fb75dd48) | A single simple mark that reads at a glance | Pure black (ours is the locked charcoal) |
| [Finimize, launch](https://mobbin.com/screens/9f3a778f-c90e-4573-9815-866324462613) | | The wordmark under the mark: Android 12+ can't draw text on its splash, and the name is already under the launcher icon |

---

## 2. Platform rules (checked 2026-11-02)

**Android** ([Splash screens, developer.android.com](https://developer.android.com/guide/topics/ui/splash-screen)):
- From Android 12 the **system** draws the splash before any app code runs. It is a single opaque background colour, a centred icon, an optional icon background, and an optional branding image at the bottom. Google's design guidelines recommend against the branding image.
- **Icon without an icon background:** 288×288 dp canvas; the mark must fit inside a 192 dp circle (the inner two-thirds). With an icon background: 240 dp canvas, 160 dp circle.
- **Animated icon:** an AnimatedVectorDrawable; no more than 1,000 ms recommended on phones. Its canvas is 432 dp (4× the 108 dp adaptive grid), with the visible area in the inner 288 dp.
- It is dismissed when the app draws its first frame. `setKeepOnScreenCondition` can hold it for small local loads only, and the condition runs before every frame, so it must be cheap. No spinners. It never shows on a hot start.
- `androidx.core:core-splashscreen:1.2.0` brings the same splash to API 23 and up, so it covers our minSdk 24 (or 26 if D-C6 raises it).

**iPhone and iPad** (the app is Android-only today; this is ready for the iOS route):
- Apps built with the iOS 27 SDK **must** have a launch screen, or App Store submission is rejected. One of `UILaunchStoryboardName`, `UILaunchStoryboards`, `UILaunchScreen` or `UILaunchScreens` in Info.plist. Source: Apple's iOS 27 release notes, as quoted in [this write-up](https://blakecrosley.com/blog/ios-27-launch-screen-requirement).
- `UILaunchScreen` takes a background colour and an image and needs no storyboard. The launch screen is static: no animation.
- **Not checked:** Apple's HIG page on launching wouldn't load here, so its wording on logos in launch screens is unverified. Read it before the iOS build.

---

## 3. The app icon

**The mark (D-SP1):** a handheld scanner as the main image, inside four square-ended scan brackets (the first draft's). The scanner is one solid silhouette: chunky head with its window cut out, angled grip, trigger. It fills the frame as far as the brackets allow. It reads at 32 px and as a single-colour themed icon.

- **Launcher (adaptive):** emerald plate (`#10B981`), mark in `on-emerald` ink (`#06140E`). Ink on emerald measures 7.43:1. The scanner's window is cut out, so the plate shows through.
- **Safe zone:** everything sits inside the 66 dp safe circle of the 108 dp grid, so circle, squircle and rounded-square masks never clip it.
- **Themed icons (Android 13+):** `inscan-icon-monochrome.svg` is the same shape in one colour; the launcher tints it.
- **Store / iOS:** `inscan-icon-full.svg` (plate plus mark, full bleed). Export 512×512 for Google Play and 1024×1024 for the App Store; iOS applies its own corner mask.
- **Why emerald and not charcoal:** a charcoal icon disappears on dark wallpapers. The emerald plate is the most findable thing on a crowded home screen, and it's the shop's brand colour.

---

## 4. The splash

### 4a. Android (cold start)

- **Background:** `--bg` charcoal `#1E1F21`, the same in day and night (the app is dark only).
- **Icon:** `inscan-splash-mark.svg` with **no icon background**: emerald brackets, cream (`--text`) scanner with its window cut out. Emerald on charcoal measures 6.5:1, cream 14.85:1.
- **No branding image, no wordmark, no text, no spinner.**
- **Motion (D-SP3, owner's burst spinner):**
  1. 0–300 ms: the four brackets slide in from 8 units out and fade up, as if the camera found its frame.
  2. 150–450 ms: the scanner pops in: scale 0.4 → 1.06 → 1 and fades up. This is the one place a slight overshoot is used, because the owner asked for a pop.
  3. From 500 ms the bracket frame turns in bursts, **760 ms each, repeating until the app is ready**:
     - wind up: back 14° over about 170 ms, ease-in-out
     - spin: forward a quarter turn (to 90°) over about 290 ms. It's fast at first and slows a little into the stop (owner, at the lock), with no bounce.
     - stop: hold still for about 300 ms
  4. A quarter turn puts the four brackets back on the same shape, so each burst starts where the last one ended with no visible seam. The scanner never turns; only the frame does.
  - **Fits the mask:** turned to any angle, the bracket corners stay within 32 of the 36 units the splash circle shows, so the spin never clips.
  - **With D-SP4's 1-second cap**, most starts show the pop and one burst. The loop only matters if a start runs long. On Android 12+ the system may cut the animation at any frame; every frame is a clean picture.
  - **Google's guidance:** it says "don't add spinners", meaning a separate progress spinner drawn over or after the splash. Here the icon itself animates, and Google suggests a looping icon animation when startup can run past 1 s, so this sits inside the guidance. That reading is an inference.
  - Easing is a long-tail decel (`cubic-bezier(.2,.8,.2,1)`), no bounce. If the app is ready early, the system skips the rest, and that looks fine because every frame is a sensible picture.
- **Exit:** a 200 ms fade to the first screen (`setOnExitAnimationListener`, alpha 1 → 0, then `remove()`).
- **What it waits for (D-SP4):** only the local "who is signed in on this phone" read (Room or DataStore). **Never the network.** The app is offline-first, and the first screen already handles "Can't reach the shop system". A hard cap of 1,000 ms releases the splash even if that read is slow.
- **First screen after it:** "Who's using this phone?" (sign-in, locked), or Home if a session is still open.

### 4b. iPhone and iPad

- `UILaunchScreen` with `UIColorName` = a `LaunchBackground` colour asset (`#1E1F21`) and `UIImageName` = the splash mark at rest (from `inscan-splash-mark.svg`, same size as Android's 192 dp circle).
- Static: iOS doesn't animate launch screens, and we don't fake one with an extra animated screen after launch, since that would add time to every start.

---

## 5. Accessibility

- No text on the splash. TalkBack and VoiceOver announce the app by name as it opens.
- **Reduce motion:** Android's "Remove animations" plays the icon at its end state, with no pop and no spin. The mockup does the same under `prefers-reduced-motion`.
- The splash never stays up to cover slow work, so it can't trap anyone on a screen with nothing to do.

---

## 6. Build notes

**Android:**
- Add `androidx.core:core-splashscreen:1.2.0`.
- **Theme:** add `Theme.InScan.Starting`, parent `Theme.SplashScreen`, with:
  - `windowSplashScreenBackground` = `#1E1F21`
  - `windowSplashScreenAnimatedIcon` = `@drawable/avd_inscan_splash`
  - `windowSplashScreenAnimationDuration` = `1260` (the pop plus one burst). It's only read on API 31–32; whether a looping AVD keeps looping there must be checked on a device.
  - `postSplashScreenTheme` = the app theme
- **Manifest:** apply the theme to the launcher activity.
- **Code:** call `installSplashScreen()` before `super.onCreate()`.
- `avd_inscan_splash.xml`: the mark on a 432 dp canvas (the 108 grid × 4):
  - **Brackets:** the four bracket paths sit inside one `<group>` pivoted at the canvas centre. Each path slides and fades in once. The group's `rotation` runs a keyframed `objectAnimator` (0 → −14 → 90 → 90) with `repeatCount="infinite"` and `startOffset="500"`.
  - **Scanner:** its `<group>` animates `scaleX`, `scaleY` and alpha once.
  - **To check at build time:** keyframes inside an AVD `objectAnimator` (`propertyValuesHolder` with `<keyframe>`) on our minSdk. If they aren't supported, build each burst as three sequential animators in a repeating set.
- **Launcher icon:**
  - Replace the template `mipmap-*/ic_launcher*.webp` files and the `ic_launcher_foreground`/`ic_launcher_background` drawables.
  - Convert the three SVG masters to vector drawables (Android Studio → Vector Asset).
  - Keep `<monochrome>` pointing at the monochrome layer.
- `Theme.MyApplication` is the template's theme name. Rename it to `Theme.InScan` in the same change.

**iOS** (when the iOS route starts):
- `UILaunchScreen` in Info.plist.
- `LaunchBackground` colour and `SplashMark` image in the asset catalogue.
- The 1024 icon in `AppIcon`.

**Compose Multiplatform:** neither the icon nor the splash is shared code. They are platform resources on each side, drawn from the same SVG masters.

---

## 7. Decisions

Owner answer, 2026-11-03: **yes to all**, with D-SP3 changed to the burst spinner (§4a). **LOCKED** the same day.

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D-SP1 | The mark | **The scanner as the main image, inside the first draft's square scan brackets (owner's direction, 2026-11-02).** | The owner picked the scanner from his reference logo and the brackets from the first draft. Also tried: the reference's rounded brackets with arcs, the scanner alone firing a beam, the scanner reading a tag, a tag in brackets, a bracketed "i" (read as the info icon), a tag with QR modules. |
| D-SP2 | Emerald on the icon and splash, when the lock card says "emerald for actions only" | **Yes, as a brand exception limited to the app icon and the splash.** Inside the app the rule stands. | The lock card governs screens. An icon and a splash aren't UI anyone taps, and a charcoal icon vanishes on dark wallpapers. |
| D-SP3 | Animate the splash icon | **Decided (owner): the scanner pops in, then the brackets wind up, spin a quarter turn and stop, repeating until the app is ready.** Android only; iOS stays static. | It's free: the system plays it while the app loads, and it never adds time. |
| D-SP4 | What the splash waits for | **Only the local sign-in read, capped at 1,000 ms. Never the network.** | Offline-first. A splash that waits on the shop server would hang in the yard with no signal. |
| D-SP5 | Wordmark or branding image on the splash | **No.** | Google's guidelines recommend against the branding image, Android 12+ can't draw text there, and "InScan" is already under the icon. |
| D-SP6 | The reference logo's blue | **Decided (owner, 2026-11-02): "Keep it emerald."** The blue isn't in palette v4, which is locked. | Keeps the icon, splash and app one colour family. The reference logo can stay blue for print or signage if the owner wants. Its "BARCODE READER" tagline undersells the app (check-in, tags, stock, reports), so it isn't used here. |

---

## 8. Do not render

- A wordmark, tagline, version number or shop name on the splash.
- A spinner or progress bar on the splash.
- Any gradient, glow or "tech" texture behind the mark.
- A second, fake animated splash screen after the system splash.

---

## 9. Reporting

This follows the owner's rule that every flow is designed with reporting in mind (`reporting-spec.md` §1).

- **Event `app.start`:**
  - `cold` or `warm`
  - milliseconds from process start to the first screen drawn
  - whether the 1,000 ms cap released the splash
  - the phone's device id (already on every event)
- **Reports it feeds:** a "slow starts" column on the **Phones** report (`reporting-spec.md`, "Phones connected"; `reports-ui-spec.md`, phone check-in), so the admin can see which shop phone is struggling before someone complains.

**Configurable:** nothing. The system draws the splash before any app code or shop setting can load, so there's no setting to change.

---

## 10. Build and test contract

- **Screenshot tests:** the launcher icon in circle, squircle and rounded-square masks, and themed, at 48 dp and 108 dp.
- **Behaviour tests:**
  - The keep-on-screen condition releases when the local session read finishes, and after 1,000 ms at the latest.
  - With the shop server unreachable, the splash still clears within the cap and "Can't reach the shop system" shows next.
  - A warm start shows no splash.
- **On device:** a cold start on the oldest shop phone, recorded, to confirm the 600 ms animation plays and the exit fade is clean.
