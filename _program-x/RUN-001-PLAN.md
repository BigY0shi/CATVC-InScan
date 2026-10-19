# Program X run 001 — CountryATV InScan: build plan

**Status:** DRAFT for owner review. Nothing here is approved until the owner says "lock it".
**RunState:** checkpoint 2 cleared (2026-10-19). Phase 3 drafted; **parked at checkpoint 3 (danger levels)**.
**Repo HEAD at drafting:** `6b07b53`. No code changed.

---

## 1. Locked decisions

| # | Decision | Owner call |
|---|---|---|
| A | System of record | One small server on Hetzner Cloud, Hillsboro OR (US-West). Account in the shop's name. API reachable over Tailscale only. |
| B | Agent surface | Chat built into InScan. No external AI app, no agent-os Jarvis. |
| C | Jurisdiction | Central Point, Oregon. Required docs = point-of-sale docs + Oregon DMV title/registration docs. Real shop paperwork pending; provisional list in §5. |
| D | Scope | New Inventory Entry, Vehicle Sold, New Parts Request, New Workshop Order. Parts and work orders are new features. |
| — | Tagging | Shop prints its own QR tag per unit. QR holds the stock number only (e.g. `CATV-000412`), stock number also printed as text. Server issues numbers. |
| — | Printer | SUPVAN T50M-class thermal printer behind a `LabelPrinter` interface. Classic Bluetooth (SPP) + post-job status check. Driver ported from khromov/supvan-katasymbol-printer-web-ui (MIT). |
| — | AI autofill | Kept. Reads text from photos (box label, VIN plate, MSO); never invents. Every AI-filled field is "unconfirmed" until a person taps it. |
| — | YOLO detector | Parked. Revisit only if call logs show label-reading autofill is not enough. |

## 2. Architecture

```
InScan (Android, each mechanic's phone)
  ├─ Entry / Sale / Parts / Work order screens
  ├─ Customer verification screen (checkbox per field)   <- only place verification is created
  ├─ Chat (agent)  ──calls──► server tool endpoints (agent service account)
  ├─ LabelPrinter (SUPVAN driver, BT SPP)
  └─ QR scan (any camera, ML Kit)
            │ HTTPS over Tailscale
            ▼
Shop server (Hetzner, Hillsboro)
  ├─ REST API  (Python + FastAPI, SQLite WAL)          <- inference: matches owner defaults; confirm
  ├─ Rules engine: required-docs per sale type, Sold gate
  ├─ AI proxy: holds the model API key; phones never see it
  ├─ Audit log: every write, who, when, before/after
  └─ Backups: nightly off-box (target TBD)
```

**Online-first, fail loud.** v1 has no offline queue. If the server is unreachable, the app says
"I can't reach the shop system right now, so nothing was saved" and keeps the form filled.
An offline queue is a later decision, not a silent default.

**Identity.** Each mechanic signs in (name + PIN). Verification records and Sold actions carry the
person's name. The agent uses a separate service account that **lacks** the `verify_customer` scope,
so no model can tick verification boxes. `mark_sold` from the agent needs in-app human approval (§4).

## 3. Data model (server)

| Entity | Key fields | Notes |
|---|---|---|
| `Unit` | `stock_no` (PK, server-issued), `vin?`, make, model, sub_model, year, color, unit_type, price, status (`incoming`/`in_stock`/`sale_pending`/`sold`), `field_source{}` | `field_source` per field: `typed` / `ai_read` / `nhtsa` / `template`, plus `confirmed_by`. Price never defaulted. |
| `ModelTemplate` | make, model, sub_model, year, colorways | Learned once per model; prefills later units. |
| `LabelPrint` | stock_no, printer_id, requested_by, result (`confirmed`/`unconfirmed`/`failed`), status_bytes | Only `confirmed` shows as printed. |
| `Customer` | legal name, address, phone, email, DL no./state, DOB if required | PII. Never logged in plain text. |
| `Sale` | stock_no, customer_id, sale_type (cash/financed × street/off-road), price, fees, taxes, lienholder?, sale_date (server clock), status | Drafts until Sold gate passes. |
| `Verification` | sale_id, employee, `values_hash`, per-field ticks, timestamp | Any later edit to a hashed field invalidates it. |
| `RequiredDocRule` | sale_type, doc_code, required_if, `provisional` | Data, not code. §5 seeds it. |
| `SaleDocument` | sale_id, doc_code, status (`missing`/`generated`/`signed`/`filed`), file | Generated PDFs use real dealer details from config. |
| `PartsRequest` | part, part_no?, qty, for_unit / for_work_order, requester, status | |
| `WorkOrder` | unit or customer bike (VIN/desc), complaint, assigned mechanic, contact, status, notes | |
| `DealerProfile` | legal name, DBA, Oregon dealer cert no., address | Replaces every hardcoded Moab/Utah string. Blank = documents refuse to generate. |

**Sold gate (server-enforced):** `mark_sold` succeeds only if (1) every `RequiredDocRule` for the
sale type has a `SaleDocument` at `signed` or `filed`, (2) a valid `Verification` exists whose hash
matches current customer values, (3) `DealerProfile` is complete. Otherwise it returns the plain-words
list of what's missing.

## 4. ToolSpec draft — CHECKPOINT 3: owner sets danger

Tools are server endpoints the in-app agent may call. Budget: 10 of default 12.
"Danger" per pipeline §3: anything a customer or regulator can see is **high**.

| Tool | Intent | Proposed danger | Why |
|---|---|---|---|
| `countryatv_find_unit` | Look up by stock no., VIN, or description | none | read |
| `countryatv_list_inventory` | Units by status | none | read |
| `countryatv_decode_vin` | Check digit + NHTSA decode | none | read |
| `countryatv_add_unit` | New inventory entry; returns stock no. Printing happens on the phone. | low | reversible, internal |
| `countryatv_start_sale` | Sale draft with customer info | **high** (was medium) | writes PII that goes on DMV paperwork |
| `countryatv_get_sale_checklist` | Docs done/missing, verification status | none | read |
| `countryatv_mark_sold` | Attempt Sold; refuses with gaps | high | externally visible, regulator-facing |
| `countryatv_create_parts_request` | Part, qty, unit or work order, requester | low | internal |
| `countryatv_create_work_order` | Bike, complaint, mechanic, contact | low | internal, contains customer contact |
| `countryatv_list_open_orders` | Open parts requests and work orders | none | read |

Not tools (human-only, in-app screens): customer verification, document signing, label printing.

Error style (rule 3), every tool: plain words, what happened, what to do.
"That VIN has 16 characters. VINs have 17. Check the sticker on the frame."
"Not sold yet. Still needed: buyer signature on the bill of sale. Customer info isn't verified on the phone yet."

## 5. Provisional required-documents list (Oregon) — `provisional: true`

Seeded from public Oregon DMV sources **as stand-ins** until the shop's real deal files arrive.
While any rule is provisional, the sale screen shows "Paperwork list not yet confirmed by the shop".

| Doc | Applies to | Source / status |
|---|---|---|
| Bill of sale (dealer's own, or DMV 735-501) | all | Form 735-501 rev 10-26 [oregon.gov](https://www.oregon.gov/odot/forms/dmv/501fill.pdf). Chapter E: bill of sale need not be on the DMV form. |
| Application for Title & Registration, 735-226 | titled/registered units | [oregon.gov](https://www.oregon.gov/odot/forms/dmv/226fill.pdf); instructions [Chapter B](https://www.oregon.gov/odot/dmv/docs/chapter_b.pdf) |
| Dealer release of interest | all | Chapter E: dealers must provide one for every vehicle sold [Chapter E](https://www.oregon.gov/odot/dmv/docs/chapter_e.pdf) |
| Odometer disclosure | non-ATV, when required by age | ATVs exempt [Chapter H](https://oregon.gov/odot/dmv/docs/chapter_h.pdf) |
| Lienholder on 735-226 | financed | Title mailed to lienholder (DMV letter) |
| Manufacturer's certificate of origin (new units) | new units | **inference, unverified** |
| Oregon privilege tax (0.5%, never-registered vehicles) | new units | [OAR 735-030-0025](https://oregon.public.law/rules/oar_735-030-0025); ATV coverage **unverified** |
| Off-road (ATV) permit / titling route | off-road | **unknown**: DMV vs other agency not confirmed |

**Not using random online "dummy" templates.** The real Oregon forms are public and free, so the
stand-ins are the actual forms; only the per-sale-type rules are provisional.

## 6. Phase 4 prerequisites: fix or remove in InScan

Line refs come from the phase-1 recon; this session re-checked only `MainScreen.kt:3209`,
`DocumentPdfExporter.kt:286-287` and `InventoryViewModel.kt:434-435`. Re-verify each when fixing.

| Finding | Action |
|---|---|
| 1 Back arrow / onClose call `completeSale()` (`MainScreen.kt:3209`, `:281`) | Back = discard draft with confirm. Sold only via gate. |
| 2 Hardcoded Moab dealer, Utah license (`DocumentPdfExporter.kt:184, 286-287, 41-42`) | Pull from `DealerProfile`; refuse if blank. |
| 3 Fake buyer prefill (`InventoryViewModel.kt:361-371`) | Remove. Empty fields. |
| 4 Blank fields print "N/A" | Block generation; list the missing fields. |
| 5 `johndoe@example.com` fallback (`MainScreen.kt:2203, 2714`) | Remove; require recipient. |
| 6 Hardcoded sale dates, defaulted finance terms | Server date; finance terms required when financed. |
| 7 Gemini invents VIN / price (`InventoryViewModel.kt:434-435`) | Prompt reads only; returns null for unseen fields; check digit enforced. |
| 8 Buyer info not persisted; no doc model | §3 entities. |
| 9 README "Silent Fallbacks" | Remove claim and behaviour. |
| 10 Unparseable price → 0.0 (`InventoryViewModel.kt:302, 324, 376`) | Validation error in plain words. |
| 11 `fallbackToDestructiveMigration()` (`AppDatabase.kt:23`) | Local DB becomes cache only; real migrations, `exportSchema = true`. |
| 12 Swallowed email/print/NHTSA errors | Surface every failure. |
| 13 Fictional printers + fake 1.8 s "spooled" | Delete; replace with `LabelPrinter` + real status. |
| 14 Raw exception text on screen | Map to plain messages; raw detail to logs only. |
| 15 Gemini key in APK + logged via `?key=` and BODY logging (`GeminiClient.kt:77, 92`) | Move key to server proxy; strip from APK; logging off for auth'd calls. Rotate the current key (it has shipped). |

## 7. Build order

1. Server skeleton: entities, auth, audit log, Sold gate, provisional rules. Offline smoke against an in-memory DB (asserts DB rows, not response strings; red when the gate is broken).
2. InScan cleanup: §6 items 1–15.
3. Inventory entry + stock numbers + QR scan lookup.
4. SUPVAN driver port + `LabelPrinter` + status check. Bench test on a real printer (needs the physical printer).
5. AI autofill via server proxy, read-only, confirm-per-field.
6. Sale flow: draft → verification screen → documents → gate.
7. Parts requests + work orders.
8. In-app agent bound to §4 tools.
9. Hetzner deploy, Tailscale, backups. **Spends money: owner approval at that step.**
10. Phase 8 enablement: capability card, prompt patterns, boundary list, error table, three-tasks-unaided test with a mechanic.

## 8. Open items

- **Checkpoint 3:** danger levels in §4 (esp. `start_sale` → high).
- **Server repo:** new repo (e.g. `CATVC-InScan-server`) or `server/` inside this repo? Recommendation: inside this repo while it's one team; ask before creating anything.
- **Agent model:** keep Gemini (already integrated) behind the server proxy, or switch. Recommendation: keep Gemini for v1.
- Real shop deal files (3, redacted) to replace §5 provisional rules.
- Exact SUPVAN model to buy (T50M Pro is what the community driver verified).
