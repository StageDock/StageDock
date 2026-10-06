# StageDock

Native Android (Kotlin, Jetpack Compose) 2D-panel app for Meta Quest. Browses custom stages from the synthriderz.com community index and installs them on-headset into Synth Riders' user content folder.

## Build and sideload

1. Open the `StageDock` folder in Android Studio (Koala or newer, JDK 17). It uses the bundled Gradle 8.9 wrapper.
2. Sync, then build the debug APK (or `./gradlew assembleDebug`).
3. With the headset in developer mode: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. On the headset: Library > Unknown Sources > StageDock.

## Release builds

### 1. Create the StageDock signing key (once)

    keytool -genkeypair -v -keystore StageDock.jks -alias StageDock -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=StageDock, OU=StageDock, O=StageDock, L=, ST=, C="

Back up `StageDock.jks` and its passwords. Every update must be signed with this same key or it won't install over existing copies. The key file and `keystore.properties` are git-ignored.

### 2. Build

Copy `keystore.properties.example` to `keystore.properties` and fill in the passwords, then:

    gradlew assembleRelease

The signed APK is at `app/build/outputs/apk/release/StageDock-<version>-release.apk`. Verify the signer with:

    keytool -printcert -jarfile app/build/outputs/apk/release/StageDock-0.1.0-release.apk

### 3. Releasing through GitHub

The repo lives at `github.com/StageDock/StageDock`. Publishing a GitHub release runs `.github/workflows/release.yml`, which builds, signs and attaches the APK to that release.

Repository secrets (Settings > Secrets and variables > Actions):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("StageDock.jks")) \| Set-Clipboard` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_PASSWORD` | the key password (same as above unless you set a different one) |

To release, bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts` and commit. Then on GitHub go to Releases > Draft a new release, create a tag matching the version (e.g. `v0.1.0`) and publish. The APK is attached a few minutes later.

The workflow fails if the tag doesn't match `versionName`, and checks the APK is signed by `CN=StageDock`.

## Storage access

- Target folder: `/sdcard/SynthRidersUC/CustomStages`
- Needs `MANAGE_EXTERNAL_STORAGE` (All files access). Use the in-app Grant button, or:
  `adb shell appops set --uid com.stagedock.app MANAGE_EXTERNAL_STORAGE allow`
- Quest needs the `.stagedroid` build of a stage, not the PC `.stage`. Stages without a `.stagedroid` file show as PC only.
- Downloads land in the app cache, then are written as `<name>.stagedroid.part` and renamed, so the game never sees half-written files.
- Zip downloads are unpacked; only `.stagedroid` entries are kept and names are sanitised.
- Restart Synth Riders after installing so it rescans custom content.

## Project layout

| File | Purpose |
| --- | --- |
| `data/StageApi.kt` | synthriderz.com client. All endpoint paths and JSON field names live in `ApiConfig`. |
| `install/StageInstaller.kt` | Download, unzip, atomic write, remove, list installed. |
| `install/InstallRegistry.kt` | Persists which files each stage ID installed, so status survives restarts. |
| `MainViewModel.kt` | Paging, search debounce, install state. |
| `ui/StageScreen.kt` | Browse grid, Installed tab, access banner. |

## API

StageDock talks to `https://synthriderz.com/api/models/stages`, a NestJS CRUD endpoint:

- `select=id,name,description,user.id,user.username,download_url,cover_url,cover_version,published_at,download_count,...`
- `join[]=files&join[]=files.file` to find the `.stagedroid` file and its ID
- Downloads use `/api/models/stages/{id}/download?file_id={fileId}`. Without `file_id` the server returns 500.
- `limit=24&page=N&sort=score,DESC` (also `published_at,DESC` and `download_count,DESC` from the sort chips)
- `s={"$and":[{"$or":[{"name":{"$contL":"q"}},{"user.username":{"$contL":"q"}}]}]}` for case-insensitive search

The response is expected as `{ data, count, total, page, pageCount }`. On first load the app logs the first item's keys and raw JSON under the `StageDock` tag:

    adb logcat -s StageDock

Use that to confirm the field names, especially where the filename lives under `files[].file`.

