# Program X run 001: owner decisions vs `docs/HANDOFF.md`

**Status:** DRAFT for owner review. Nothing here is approved until the owner says "lock it".
**Baseline:** `docs/HANDOFF.md` (merged in #2, dated 2026-10-18) is the canonical spec. This file does
not restate it. It records what the owner decided in the 2026-10-19 session, maps each decision onto
HANDOFF §11, and lists where a decision **overrides** a HANDOFF proposal.
**RunState:** checkpoint 2 cleared. Parked at checkpoint 3 (danger levels).

---

## 1. HANDOFF §11 decisions: status after 2026-10-19

| # | Decision | Status | Owner's call / note |
|---|---|---|---|
| 1 | Shop server vs Firestore | **made** | Shop server. |
| 2 | Label symbology | **made** | QR. The QR holds the stock number only; number also printed as text. Code 128 dropped (no 1D scanner planned). |
| 3 | Stock number format | open | HANDOFF proposal `CAyy-nnnn` + check char stands unless the owner objects. |
| 4 | Offline tag printing | open | See §2 conflict B: a phone-attached printer changes this. |
| 5 | DL scan to prefill customer | open | |
| 6 | PIN vs password | open | HANDOFF: PIN + enrolled device. |
| 7 | Printer model and connection | **made, conflicts with HANDOFF** | Owner: "small format, quick print speed, relatively cheap, available waterproof adhesive medium"; brand doesn't matter. See §2 conflict A. |
| 8 | Tag placement / "stamping" | partly | Label applied to the merchandise ("bag it and tag it"). Placement per unit type still open. |
| 9 | Required docs per sale type + state | partly | **State: Oregon** (shop in Central Point). Docs = point-of-sale/proof-of-sale + DMV title/registration. Owner's real pack is coming; until then use the **official public Oregon forms** as stand-ins (§3), flagged provisional. |
| 10 | Signatures | open | |
| 11 | Retention | open | Oregon rules to check. |
| 12 | Auto prep WO on intake | open | |
| 13 | Client for agent tools | **made** | A chat **inside InScan**. Not an external AI app, not agent-os Jarvis. |
| 14 | Gemini photo feature | **made, overrides HANDOFF rec** | Owner: keep the ML feature to speed up entry. Constraint carried: it reads text from photos (box label, VIN plate, certificate of origin) and never invents; every AI-filled field shows unconfirmed until a person confirms it; VIN must pass the check digit. Key moves server-side. |
| 15 | Package rename | open | |
| 16 | Where the server runs | owner leaning | "A shop server, or even something like a Hetzner box." Recommendation: Hetzner Cloud, Hillsboro OR, account in the shop's name, Tailscale-only API. Needs an explicit pick; costs money. |
| 17 | Repo public? | open | Still recommend private before customer-handling code lands. |
| new | YOLO / object-detection autofill | **made** | Parked. Revisit only if logs show label-reading isn't enough. |
| new | Parts requests + work orders | **made** | In scope. The owner confirmed them as **InScan features**; HANDOFF §7.4 places them in a browser portal. See §2 conflict C. |

## 2. Conflicts the owner needs to see

**A. Printer and label stock (HANDOFF §7.1.4 vs owner's criteria).**
HANDOFF proposes a network ZPL **thermal-transfer** printer on synthetic stock, because direct-thermal
labels fade with heat/UV and may not survive a wash bay. The owner's criteria are small, fast, cheap,
waterproof media. The session picked a SUPVAN T50M-class printer (driver available: MIT-licensed
[khromov/supvan-katasymbol-printer-web-ui](https://github.com/khromov/supvan-katasymbol-printer-web-ui),
Bluetooth, `7E 5A` frames). Brother PT-P910BT was rejected on tape cost (24mm TZe listed ~£35.54 ex VAT)
and inconsistent speed listings (20 vs 80 mm/s).
*Unverified:* whether SUPVAN media is direct-thermal or survives washing/UV. **Decision:** accept the
fade risk and test one label through a wash before buying in bulk (HANDOFF §13 mitigation), or follow HANDOFF.

**B. Where printing happens (HANDOFF: server → printer over TCP 9100; SUPVAN: phone → printer over Bluetooth).**
A Bluetooth printer is driven by the phone, not the server. Consequences:
- The print step lives in the app behind a `LabelPrinter` interface. "Printed" requires the printer's
  status reply (classic BT/SPP; the community README says the printer doesn't acknowledge data frames over BLE).
- HANDOFF's byte-exact server print test becomes an app-side driver test against a fake BT transport.
- HANDOFF's confirm-scan step (§7.1.1 step 7) stays and matters more: it's the real proof the tag works.
- Offline printing (decision 4) becomes possible only with per-device number blocks.

**C. Parts requests / work orders: InScan screens vs browser portal.**
Owner confirmed them as InScan features. Proposal: build them in InScan first; keep the browser portal for
admin only (dealer profile, document matrix, templates, users). Revisit a mechanics portal at M4.

**D. Danger level of `countryatv_start_sale` (HANDOFF §7.5: medium).**
Proposal: **high**. It writes customer details that print on DMV paperwork, and pipeline Phase 3 rates
anything a regulator can see as high. Owner sets it at checkpoint 3.

## 3. Provisional Oregon document stand-ins (`provisional: true`)

Real public forms, not invented templates. The sale screen shows "Paperwork list not yet confirmed by the
shop" while any rule is provisional. The owner's pack replaces this list.

| Doc | Applies to | Source / confidence |
|---|---|---|
| Vehicle Bill of Sale, DMV 735-501 (or the dealer's own) | all | [form rev 10-26](https://www.oregon.gov/odot/forms/dmv/501fill.pdf). DMV Chapter E: the bill of sale need not be on the DMV form. |
| Application for Title and Registration, 735-226 | titled/registered units | [form](https://www.oregon.gov/odot/forms/dmv/226fill.pdf); [Chapter B instructions](https://www.oregon.gov/odot/dmv/docs/chapter_b.pdf) |
| Dealer release of interest | all | [Chapter E](https://www.oregon.gov/odot/dmv/docs/chapter_e.pdf): required for every vehicle a dealer sells |
| Odometer disclosure | non-ATV, by age | ATVs exempt, [Chapter H](https://oregon.gov/odot/dmv/docs/chapter_h.pdf) |
| Lienholder on 735-226 | financed | title is mailed to the lienholder |
| Certificate of origin (new units) | new | **unverified** |
| Oregon privilege tax, 0.5% of retail price, never-registered vehicles | new | [OAR 735-030-0025](https://oregon.public.law/rules/oar_735-030-0025); ATV coverage **unverified**; HANDOFF §2.3: never hardcode a rate |
| Off-road (ATV) permit / titling route | off-road | **unknown** |

## 4. What this file adopts from HANDOFF unchanged

Everything not listed above, notably: `:core` shared Kotlin module + Ktor/SQLite server (cloud-testable;
supersedes this file's earlier Python/FastAPI guess), RuleMap R1–R3 enforcement points, data model v2,
outbox with "Shop system offline" banner and sales online-only (supersedes this file's earlier
online-only-v1 proposal), milestones M0–M6, the defect register, build-environment facts (no Android SDK
here; `dl.google.com` blocked), and testing rules.

## 5. Next step once the owner answers

Checkpoint 3 (danger levels, conflict D), conflicts A–C, and decision 16 → start **M0** (make it honest
and buildable) per HANDOFF §8. M0 first needs CI or `dl.google.com` allowed (HANDOFF §10).
