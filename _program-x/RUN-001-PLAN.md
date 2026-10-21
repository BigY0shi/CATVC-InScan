# Program X run 001: owner decisions vs `docs/HANDOFF.md`

**Status:** DRAFT for owner review. Nothing here is approved until the owner says "lock it".
**Baseline:** `docs/HANDOFF.md` (merged in #2, dated 2026-10-18) is the canonical spec. This file does
not restate it. It records what the owner decided in the 2026-10-19 session, maps each decision onto
HANDOFF §11, and lists where a decision **overrides** a HANDOFF proposal.
**RunState:** checkpoint 2 cleared. Checkpoint 3: danger levels set by the owner (2026-10-20); `requiresApproval` for `start_sale` pending.

---

## 1. HANDOFF §11 decisions: status after 2026-10-19

| # | Decision | Status | Owner's call / note |
|---|---|---|---|
| 1 | Shop server vs Firestore | **made** | Shop server. |
| 2 | Label symbology | **made** | QR carrying an ID plus the entered details (§2 B). Code 128 dropped. |
| 3 | Stock number format | **made** | No sequential numbers; device-generated unique IDs (§2 B). |
| 4 | Offline tag printing | **made** | Yes: phones create records and print offline, then sync (§2 B). |
| 5 | DL scan to prefill customer | open | |
| 6 | PIN vs password | open | HANDOFF: PIN + enrolled device. |
| 7 | Printer model and connection | **made** | Small, fast, cheap Bluetooth thermal printer with waterproof media; the app must be able to drive it (§2 A). Model TBD. |
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

## 2. Conflicts with HANDOFF: owner's answers (2026-10-20)

**A. Label stock: resolved.** Labels go on **boxes**, not bikes, so wash-bay durability doesn't apply.
Sun fade is handled by telling staff and reprinting. Any small, fast, cheap Bluetooth thermal printer with
waterproof media qualifies; SUPVAN was only an example. Hard requirement: InScan must be able to drive it
(published SDK or a known protocol) and read back a printed/failed status.

**B. Identity and offline: resolved, overrides HANDOFF §7.1.2.**
- No server-issued sequential stock numbers. Each record gets a **device-generated unique ID**
  (ULID/UUID), so a phone can create entries offline with no number blocks.
- The QR carries **the ID plus the entered details** (make, model, sub-model, color, VIN, type), so a
  label is readable with no server. **Excluded from the QR:** price, status, customer data. They change
  or are private.
- On scan, the app reads the ID and shows the **current** record; the encoded details are the offline
  fallback, shown as "from the label, may be out of date".
- Each phone keeps a local store plus an outbox and syncs to the server when it can reach it (HANDOFF §6.8).
  Two phones editing the same record offline: last write wins, both versions kept in the audit log, and
  the screen says the record was changed elsewhere. Never a silent overwrite.
- Sales still complete online only (R1/R2 are checked on the server).

**C. Parts requests / work orders: resolved.** Everything in the app, including admin.

**D. `countryatv_start_sale` danger: medium (owner).** The other levels in HANDOFF §7.5 stand.
Proposed `requiresApproval = 0`: the verification screen and the DRAFT watermark already gate the paperwork.

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
