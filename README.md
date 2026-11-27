# CATVC-InScan

Country ATV InScan is the Android app Country ATV staff use to check in incoming units, tag them, find them again by scanning, and prepare sale documents. Every phone talks to one shared shop server.

**Where it stands:** a signed test build is ready for the shop to try against a field-test server. It has been checked on an emulator, not yet on a real phone on mobile data.

Where to read more:

- [`DEV-JOURNAL.md`](DEV-JOURNAL.md): what was done, checked and decided, newest first.
- [`docs/ENGINEERING-HANDOFF-2026-10-27.md`](docs/ENGINEERING-HANDOFF-2026-10-27.md): the decisions the build follows and how the code is laid out.
- [`_program-x/RUN-001-PLAN.md`](_program-x/RUN-001-PLAN.md): the owner's decisions on the plan.
- [`docs/(old)HANDOFF.md`](<docs/(old)HANDOFF.md>): the first plan and defect register, kept for reference.

## What it does today

- **Connect once, then sign in with a PIN.** A phone is connected to the shop system one time with a setup code. After that each person taps their name and enters their own PIN. The phone locks after 30 minutes idle.
- **Check in a unit.** Enter the details (or scan the VIN), set the asking price, review, then print the tag. A unit starts as Incoming. The tag carries a QR code and the unit's details, never the price.
- **Print the tag.** The app draws the tag for the label size set on the phone and hands the image to the label printer's app through Android's share screen. A unit can be saved now and tagged later; it shows as "No tag yet" until then.
- **Scan.** The phone camera reads QR, Data Matrix, Code 128, Code 39, EAN-13 and UPC-A codes with Google ML Kit, on the device. If the camera can't start, the app says so and you type the code in.
- **VIN lookup.** A 17-character VIN is decoded with the NHTSA vPIC service. If the phone is offline or NHTSA can't decode it, the app says which, and you fill the details in by hand.
- **Stock.** A list of the shop's units with sort, filter and find, and a page for each unit.
- **Reports and My history.** Under More, for the people whose role allows them.
- **Works without signal.** Changes made offline are kept on the phone and sent when it is back online. Anything the server refuses is listed under "Needs attention".
- **Sale documents (Classic screens).** Bill of Sale, Finance Terms Summary, and a Title & Registration Worksheet for copying onto Oregon DMV Form 735-226, with email and print. These still live in the earlier layout, under More → Classic screens, until each one moves to the new layout. A document is only made when the dealer, buyer and vehicle details it needs are filled in. Nothing is defaulted or invented.

Not built yet: work tickets (the Tickets tab says so), the customer verification screen, the required-documents checklist, the gated sale flow, and reading the label from a photo (designed, see `docs/ui-rebuild/checkin-ui-spec.md` D-C10).

## Shop server (`server/`)

The shared system of record every phone talks to. Ktor + SQLite, one process, one database file. The rules it shares with the app live in `core/`.

```sh
./gradlew :server:installDist
export INSCAN_DB=/srv/inscan/shop.db       # the database file (back it up)
cd server/build/install/inscan-server/bin
./inscan-server add-employee "Name" Admin  # asks for a PIN (4 to 8 digits); repeat per person
./inscan-server setup-code                 # one-time code to connect a phone (lasts 24 hours)
./inscan-server setup-code 168             # the same, lasting 168 hours (a week); 1 to 720 allowed
./inscan-server serve                      # INSCAN_HOST (default 127.0.0.1), INSCAN_PORT (8080)
```

Roles are Employee (the default), Management and Admin. The PIN is typed at a prompt, never passed on the command line. A setup code works once. It lasts 24 hours unless you give a number of hours after `setup-code`, up to 720 (30 days), for a code sent to the shop ahead of time. Five wrong setup codes from one network address lock that address out for 15 minutes. Every change is recorded in the audit log with who made it and from which phone.

Where it runs:

- [`docs/DEPLOY-PROXMOX.md`](docs/DEPLOY-PROXMOX.md): the field-test server, a container on the owner's Proxmox box. Section 4b gives it a public `https://` address, so the shop's phones need only an internet connection.
- [`docs/DEPLOY-VPS.md`](docs/DEPLOY-VPS.md): the same server on a rented machine with its own address.

## Build

Requirements: JDK 21 and the Android SDK (platform 36.1). Android Studio provides both. The app runs on Android 7 and newer.

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # app unit and Robolectric tests
./gradlew :core:test :server:test  # shared rules and shop server tests (no Android SDK needed)
```

Debug builds use the standard Android debug key. Release builds read `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD` from the environment and only talk to `https://` addresses. This repo is public: never commit a keystore, a password or the test server's address.

## Test build for the shop

The build the shop installs is the signed test build CI makes from `main`: `inscan-test-<build number>.apk`, version `1.0-test.<build number>`. Every build is signed with the same key, so a newer one installs over an older one. [`docs/TEST-BUILD.md`](docs/TEST-BUILD.md) covers the one-time key setup, making a build and putting it on a phone.

**Quick start guide for the shop:** [`docs/InScan-Quick-Start.pdf`](docs/InScan-Quick-Start.pdf), three pages: put the app on the phone, connect it, sign in, and what to do when something goes wrong. This copy shows an example address. The copy sent to the shop carries the real one and is not kept in this repo. The source is in [`docs/quick-start/`](docs/quick-start/).
