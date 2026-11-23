# Test builds for the shop

A test build is the app as the shop will install it: app ID `com.countryatv.inscan`, signed with the shop's own key. Because the key stays the same, each new build installs over the one before it and the phone keeps its data.

CI makes one on every push to `main`, once the key is saved in the repository's secrets. Until then CI only makes the debug APK, as before.

## One-time setup: make the key and save it

Do this once, on the owner's PC. The commands are PowerShell.

**The short way** is one script. It asks for one password (twice), makes `inscan-release.jks` in your user folder, checks the key opens with that password, saves the key and password in the repository's secrets, and starts the first test build. It refuses to run if a key is already there.

```powershell
pwsh -File scripts\make-test-key.ps1
```

Then do step 2 below (the backup). Steps 1 and 3 are what the script does, for doing it by hand.

1. **Make the key.** `keytool` comes with any JDK and asks for a password; pick a long one and keep it in a password manager.

   ```powershell
   keytool -genkeypair -keystore "$HOME\inscan-release.jks" -storetype PKCS12 -alias upload -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Country ATV InScan"
   ```

2. **Back up `inscan-release.jks` and its password**, somewhere outside this repo. This repo is public: the key must never be committed (`.gitignore` already refuses `*.jks`).
   - If the key or its password is lost, no later build can update the app on a phone. Every phone would need the app removed and installed fresh.

3. **Save the key in the repository's secrets.** The first command sends the file; the other two ask for the password from step 1 (the same password both times).

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\inscan-release.jks")) | gh secret set KEYSTORE_BASE64 --repo BigY0shi/CATVC-InScan
   ```

   ```powershell
   gh secret set STORE_PASSWORD --repo BigY0shi/CATVC-InScan
   ```

   ```powershell
   gh secret set KEY_PASSWORD --repo BigY0shi/CATVC-InScan
   ```

## Making a test build

Push or merge to `main`, or start one by hand:

```powershell
gh workflow run CI --ref main --repo BigY0shi/CATVC-InScan
```

When the run finishes, its `inscan-test-apk` artifact holds `inscan-test-<number>.apk`. The number is the CI run number; it is also the build's version code, and the version name is `1.0-test.<number>`.

```powershell
gh run download --repo BigY0shi/CATVC-InScan --name inscan-test-apk
```

The "Build signed test APK" step prints the signer's SHA-256. It must be the same on every build.

## Putting it on a phone

1. The phone needs an internet connection, Wi-Fi or mobile data. Nothing else has to be installed: the test server has a public `https://` address (see "Public address" in `DEPLOY-PROXMOX.md`). The owner gives the shop that address.
2. Send the APK to the phone and open it. Android asks once to allow installs from that source.
3. Open the app and connect it: the shop system address, a one-time setup code and a name for the phone. `DEPLOY-PROXMOX.md` has the steps for the code.
4. Each employee signs in with their PIN.

Later builds: open the new APK on the phone. It installs over the old one.

## What a test build can't replace

- A phone that has an earlier build (app ID `com.example`, or the debug APK from CI) keeps it as a separate app with its own data. Remove the old one once the test build is connected.
- The debug APK from CI now has the same app ID as the test build but a different key, so a phone can hold one or the other, not both.

## Building one by hand

```sh
export KEYSTORE_PATH=/path/to/inscan-release.jks STORE_PASSWORD=... KEY_PASSWORD=...
export VERSION_CODE=123 VERSION_NAME=1.0-test.123
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Without `KEYSTORE_PATH` the release build still compiles, but the APK is unsigned and a phone will refuse it.
