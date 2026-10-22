# CATVC-InScan

Country ATV InScan is the Android app CountryATV staff use to log incoming units, look them up by barcode, and prepare sale documents.

The plan and the defect register live in [`docs/HANDOFF.md`](docs/HANDOFF.md). The owner's decisions on top of it are in [`_program-x/RUN-001-PLAN.md`](_program-x/RUN-001-PLAN.md).

## What it does today

- **Scan or type a barcode.** The phone camera reads QR, Data Matrix, Code 128, Code 39, EAN-13 and UPC-A codes with Google ML Kit, on the device. If the camera can't start, the app says so and you type the barcode in.
- **VIN lookup.** A 17-character VIN is decoded with the NHTSA vPIC service. If the phone is offline or NHTSA can't decode it, the app says which, and you fill the details in by hand.
- **Catalog and inventory.** Details saved for a barcode fill in automatically the next time it's scanned. Logged units appear on the inventory screen.
- **Sale documents.** Bill of Sale, Finance Terms Summary, and a Title & Registration Worksheet for copying onto Oregon DMV Form 735-226. A document is only made when the dealer details (Settings), buyer details and vehicle details it needs are filled in. Nothing is defaulted or invented.
- **Email and print.** Email opens your mail app with the PDF attached; it's sent when you press Send there. Print opens Android's print screen.

Not built yet: the shared shop server, customer verification screen, required-documents checklist, stock-tag printing, and photo autofill (switched off until the server can hold the AI key). See HANDOFF §8 for the milestones.

## Shop server (`server/`)

The shared system of record every phone talks to (milestone M1, in progress). Ktor + SQLite, one process, one database file. The rules it shares with the app live in `core/`.

```sh
./gradlew :server:installDist
export INSCAN_DB=/srv/inscan/shop.db            # the database file (back it up)
server/build/install/server/bin/server add-employee "Name" admin   # asks for a PIN; repeat per employee
server/build/install/server/bin/server setup-code                  # one-time code to set up a phone
server/build/install/server/bin/server serve                       # INSCAN_HOST (default 127.0.0.1), INSCAN_PORT (8080)
```

Phones are set up once with a setup code, then each employee signs in with their PIN. Every change is recorded in the audit log with who made it and from which phone.

## Build

Requirements: JDK 21 and the Android SDK (platform 36.1). Android Studio provides both.

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # app unit and Robolectric tests
./gradlew :core:test :server:test  # shared rules and shop server tests (no Android SDK needed)
```

Debug builds use the standard Android debug key. Release builds read `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD` from the environment. Never commit a keystore.
