# InScan: app icon and splash screen

- **Status:** draft for the owner (2026-11-02). Nothing here is decided or locked until the owner says so.
- **Why now:** the owner pointed out the app never had a splash screen. The repo also has no app icon of its own: `app/src/main/res` still holds the Android Studio template (the green robot on a grid). A splash on Android 12+ is built from the app icon, so this spec covers both.
- **Mockup:** `prototypes/splash.html` (8 states plus "Play a cold start"). Contact sheet: `prototypes/splash-states.png`.
- **Icon masters (SVG, 108-unit adaptive grid):** `assets/inscan-icon-foreground.svg`, `assets/inscan-icon-background.svg`, `assets/inscan-icon-monochrome.svg`, `assets/inscan-icon-full.svg` (store and iOS), `assets/inscan-splash-mark.svg`.
- **Builds on:** `landing-nav-ui-prompt.md` §0 (palette v4, dark charcoal only, emerald for actions only; see D-SP2) and `scan-pin-ui-spec.md` §3 (the first screen after the splash).

---

## 1. References (opened and looked at this session)

| Reference | What we take | What we leave |
|---|---|---|
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

**The mark (D-SP1):** a luggage tag inside four scan brackets. It is the app's one job, check it in, tag it, scan it, drawn as a picture. It reads at 32 px and as a single-colour themed icon.

- **Launcher (adaptive):** emerald plate (`#10B981`), mark in `on-emerald` ink (`#06140E`). Ink on emerald measures 7.43:1. The tag's hole is cut out, so the plate shows through.
- **Safe zone:** everything sits inside the 66 dp safe circle of the 108 dp grid, so circle, squircle and rounded-square masks never clip it.
- **Themed icons (Android 13+):** `inscan-icon-monochrome.svg` is the same shape in one colour; the launcher tints it.
- **Store / iOS:** `inscan-icon-full.svg` (plate plus mark, full bleed). Export 512×512 for Google Play and 1024×1024 for the App Store; iOS applies its own corner mask.
- **Why emerald and not charcoal:** a charcoal icon disappears on dark wallpapers. The emerald plate is the most findable thing on a crowded home screen, and it's the shop's brand colour.

---

## 4. The splash

### 4a. Android (cold start)

- **Background:** `--bg` charcoal `#1E1F21`, the same in day and night (the app is dark only).
- **Icon:** `inscan-splash-mark.svg` with **no icon background**: emerald brackets, cream (`--text`) tag with the hole cut out. Emerald on charcoal measures 6.5:1, cream 14.85:1.
- **No branding image, no wordmark, no text, no spinner.**
- **Motion (D-SP3), 600 ms, one play:**
  1. 0–380 ms: the four brackets slide in from 8 units out and fade up, as if the camera found its frame.
  2. 140–540 ms: the tag slides in from the left, turning 10° into its resting angle, and fades up.
  3. 540–600 ms: at rest. The icon holds until the app is ready.
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
- **Reduce motion:** Android's "Remove animations" plays the icon at its end state; the mockup does the same under `prefers-reduced-motion`.
- The splash never stays up to cover slow work, so it can't trap anyone on a screen with nothing to do.

---

## 6. Build notes

**Android:**
- Add `androidx.core:core-splashscreen:1.2.0`.
- **Theme:** add `Theme.InScan.Starting`, parent `Theme.SplashScreen`, with:
  - `windowSplashScreenBackground` = `#1E1F21`
  - `windowSplashScreenAnimatedIcon` = `@drawable/avd_inscan_splash`
  - `windowSplashScreenAnimationDuration` = `600`, only read on API 31–32
  - `postSplashScreenTheme` = the app theme
- **Manifest:** apply the theme to the launcher activity.
- **Code:** call `installSplashScreen()` before `super.onCreate()`.
- `avd_inscan_splash.xml`: the mark on a 432 dp canvas (the 108 grid × 4), animating the four bracket paths (translate and alpha) and the tag group (translate, rotate and alpha) to the timings in 4a.
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

## 7. Decisions for the owner

| # | Decision | Recommendation | Why |
|---|---|---|---|
| D-SP1 | The mark | **A tag inside scan brackets.** | It shows what the app does. A bracketed "i" was tried and read as the system's info icon. A QR pattern inside the tag turned to mush at small sizes. |
| D-SP2 | Emerald on the icon and splash, when the lock card says "emerald for actions only" | **Yes, as a brand exception limited to the app icon and the splash.** Inside the app the rule stands. | The lock card governs screens. An icon and a splash aren't UI anyone taps, and a charcoal icon vanishes on dark wallpapers. |
| D-SP3 | Animate the splash icon | **Yes, 600 ms, Android only.** iOS stays static. | It's free: the system plays it while the app loads, and it never adds time. |
| D-SP4 | What the splash waits for | **Only the local sign-in read, capped at 1,000 ms. Never the network.** | Offline-first. A splash that waits on the shop server would hang in the yard with no signal. |
| D-SP5 | Wordmark or branding image on the splash | **No.** | Google's guidelines recommend against the branding image, Android 12+ can't draw text there, and "InScan" is already under the icon. |

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
