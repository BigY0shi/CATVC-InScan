# App rebuild: from locked designs to the Compose app

- **Goal (owner, 2026-11-03):** an owner demo. Every locked screen built in the app and looking like its mockup.
- **Data:** screens show this phone's real copy of the shop's units. Sample units appear only in tests.
- **Rule:** each screen is checked with a Roborazzi screenshot at the mockup's frame size (402 × 874) and compared side by side with the mockup before its PR.

## Order

1. **Foundation** (this branch): palette v4 tokens, type (bundled OFL fonts), depth B, shared components, the five-slot bottom bar with the raised Scan button, the shell, and Home (A2B1C2).
   - Screens not rebuilt yet open from **More → Classic screens**, so nothing the shop uses goes missing during the move.
2. **Screens**, one branch and PR each, built on the foundation:
   - sign-in gate (who, PIN, locked, unreachable): `scan-pin-ui-spec.md` §3, `pin.html`
   - Scan: `scan-pin-ui-spec.md` §2, `scan.html`
   - Check in, with Print tag: `checkin-ui-spec.md`, `checkin.html`
   - Stock list, search sheet and unit detail: `stock-list-ui-spec.md`, `stock.html`
   - Reports and My history: `reports-ui-spec.md`, `my-history-ui-spec.md`
3. **Server routes** these need: `/units/query`, report endpoints, My history.
4. **Splash and launcher icon:** `splash-ui-spec.md`.

## Deferred

- Reading the label from a photo (D-C10, D-C11): waits on the 30-label benchmark.
- iOS.
- A stable signing key for CI builds: proposed, waiting on the owner.
