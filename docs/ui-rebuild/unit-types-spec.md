# InScan: editable unit types (spec draft)

**Status:** draft. Nothing is locked until Yoshi says "lock it".
**Checked against:** repo `BigY0shi/CATVC-InScan` at `57c191f`, read-only.

## Where the six types came from

They are **not** from the shop's website, and they were not guessed in this session. They are hard-coded in the repo as `enum class UnitType` in `core/src/main/kotlin/com/countryatv/core/Inventory.kt`: ATV, Side-by-Side (UTV), Dirt Bike, Snowmobile, Jet Ski, Other. **Nobody has checked them against what Country ATV actually works on.**

**Where the hard-coding reaches:**

| File | What it does with the type |
|---|---|
| `server/.../Units.kt:266` | Rejects any unit whose type isn't in the enum |
| `server/.../Units.kt:126` and `:166` | Stores the type's **label text** in `units.unit_type`, for example "Side-by-Side (UTV)" |
| `core/.../LabelPayload.kt` | The QR label carries the type as text in `ty=` |
| `app/.../InventoryViewModel.kt:315` and `app/.../shop/LegacyPlan.kt:26` | The app maps typed input onto the enum |

Because units store the label text, simply renaming a type would orphan every unit that uses it. Editable types need a **stable ID**.

## Requirements (Yoshi, 2026-10-29)

1. Unit types are editable: add, rename, remove.
2. Removing a type that any vehicle still uses **fails loudly**.
3. A type can only be removed once every vehicle on it has been moved to another type.

## Starting list (Yoshi's grouping)

Confirmed by Yoshi on 2026-10-29: off-road motorcycles, electric motorcycles, ATVs and go-karts.

| Type | Covers | Verified? |
|---|---|---|
| Two wheels | off-road (MX) and electric motorcycles; street bikes | **off-road and electric confirmed.** Street not yet |
| Four wheels | ATVs, go-karts | **confirmed** |
| Side-by-side | covered 4-wheel UTVs | not yet |
| Snow | snowmobiles | **unverified** |
| Water | jet skis | **unverified** |
| Desert | dune buggies | **unverified** |
| Other | anything else | fallback |

Because the list is editable, it doesn't have to be right on day one. The owner fixes it in the app.

## Design

### Server

**Migration:** appended to `Database.MIGRATIONS` (append-only, as the repo requires).
- New table `unit_types`:

  | Column | Type |
  |---|---|
  | `id` | TEXT, primary key |
  | `name` | TEXT, unique, not null |
  | `image_key` | TEXT, nullable |
  | `sort_order` | INT |
  | `is_fallback` | INT |
  | `created_at`, `updated_at` | timestamps |

- **Seed:** the six current enum labels, so nothing breaks on upgrade. "Other" gets `is_fallback = 1`.
- **New column `units.unit_type_id`:** backfilled by matching the existing `unit_type` text to the seeded names.
  - If a stored value matches no seeded name, the migration **stops and lists those units**. It never quietly maps them to Other.
  - The old `unit_type` text column stays where it is. Nothing is dropped.

**Endpoints:**
- `GET /unit-types`: any signed-in employee.
- `POST /unit-types`, `PATCH /unit-types/{id}` (rename, image, order) and `DELETE /unit-types/{id}`: **admin only**, using the existing `isAdmin`.

**DELETE rules:**
- Inside **one transaction**, count the units on that type, then delete only if the count is zero. The transaction stops a check-in arriving between the count and the delete.
- **In use:** `409` with `Problem.UNIT_TYPE_IN_USE` and details `{inStock, incoming, salePending, sold, retired}`.
- **The fallback type can't be deleted:** `409 Problem.UNIT_TYPE_IS_FALLBACK`.
- Every add, rename and delete goes to `audit_log`, like other writes.

**Unit validation:** `Units.kt:266` changes from "is it in the enum" to "does this ID exist in `unit_types`".

### Offline phones

- A phone keeps a cached copy of the type list and refreshes it on each sync.
- A phone that is offline can queue a unit using a type that has since been deleted. The server refuses that change, and it lands in the existing **Needs attention** review with the copy "That unit type was removed. Pick another type, then send." This reuses the refusal mechanism that is already there.

### QR labels

- `ty=` keeps carrying the type **name as it was at print time**, so a label stays readable without a server.
- Scanning still shows the **current** record. The label's details are the offline fallback, already marked "from the label, may be out of date".
- After a rename, existing labels don't need reprinting.

### App UI

- **New screen: More → Unit types (admin only).** A list with each type's image, name and unit count. It lets an admin:
  - add a type
  - rename one
  - choose its image from the bundled set
  - drag to reorder
  - remove one

- **Remove that fails loudly:**
  - The confirmation reads:
    - Title: **Can't remove "Snow"**
    - Body: "3 units still use this type: 2 in stock, 1 sold. Change their type first, then remove it."
    - Buttons: **Show those 3 units**, which opens Stock filtered to that type, and **Close**.
  - The server enforces the rule as well, so a stale phone can't get around it.

- **Check-in, "Type" step:** a grid of type cards with images, in the admin's chosen order.
- **Images:** one per type, from a bundled set. A type with no image shows the generic Other image. Uploading custom images is **out of scope** for now, because it would need file storage on the server.

### Tests

These follow the repo's method: run against the real server in-process, and assert on the server's own database.
- Delete is refused when the type is in use, with correct counts per status.
- Delete succeeds once the count is zero.
- The fallback type can't be deleted.
- A rename shows up on existing units.
- The migration backfills all six current labels.
- The migration stops on an unknown stored label.
- A queued offline create with a deleted type lands in Needs attention.
- **Sabotage check:** remove the count check and confirm the in-use test goes red.

## Decisions (recommended defaults)

1. **Count all statuses, including sold and retired, when blocking a delete.** Sold records keep their history, so their type has to stay valid.
2. **Keep an undeletable "Other"** as the fallback, so there is always a type to move units to.
3. **One level of types.** "Two wheels" is the type, and MX, street and electric come through the model and sub-model fields that already exist (`sub_model`, `sm=` on the label). Two levels (type, then kind) adds a picker step at every check-in.
4. **Admin-only editing.** Employees pick types; only the owner changes the list.
