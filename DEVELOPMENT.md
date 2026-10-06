# Development

StageDock is a native Android app (Kotlin, Jetpack Compose) that runs as a 2D panel on Quest.

## Build

1. Open the project in Android Studio (Koala or newer, JDK 17). It uses the bundled Gradle 8.9 wrapper.
2. Build a debug APK: `gradlew assembleDebug`
3. Install it on a headset in developer mode: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Debug builds are signed with your local debug key, so they won't install over a release build. Uninstall one before installing the other.

## Project layout

| File | Purpose |
| --- | --- |
| `data/StageApi.kt` | synthriderz.com client. Endpoint and field names live in `ApiConfig`. |
| `install/StageInstaller.kt` | Download, unzip, atomic write, remove, list installed. |
| `install/InstallRegistry.kt` | Remembers which files each stage installed, so status survives restarts. |
| `MainViewModel.kt` | Paging, search debounce, install state. |
| `ui/StageScreen.kt` | Browse grid, Installed tab, access banner. |

## How installs work

- Quest needs a stage's `.stagedroid` file, not the PC `.stage`. The app picks the `.stagedroid` entry from the stage's `files` and downloads it by ID.
- Downloads go to the app cache first, then are written to `SynthRidersUC/CustomStages` as `<name>.stagedroid.part` and renamed, so the game never sees a half-written file.
- Zip downloads are unpacked, keeping only `.stagedroid` entries with sanitised names.

## API

Stage list: `GET https://synthriderz.com/api/models/stages`, a NestJS CRUD endpoint.

- `select=id,name,description,user.id,user.username,download_url,cover_url,cover_version,published_at,download_count,upvote_count,downvote_count,vote_diff,score,rating`
- `join[]=files&join[]=files.file`
- `limit=24&page=N&sort=score,DESC` (or `published_at,DESC`, `download_count,DESC`)
- Search: `s={"$and":[{"$or":[{"name":{"$contL":"q"}},{"user.username":{"$contL":"q"}}]}]}`
- Response: `{ data, count, total, page, pageCount }`

Download: `GET /api/models/stages/{id}/download?file_id={fileId}`. Without `file_id` the server returns 500.

Debug output is logged under the `StageDock` tag: `adb logcat -s StageDock`

## Releasing

Releases are built and signed by GitHub Actions (`.github/workflows/release.yml`) when a release is published.

### One-time setup

Create the signing key outside the project folder (for example `C:\Keys`), so it can never be committed:

    keytool -genkeypair -v -keystore StageDock.jks -alias StageDock -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=StageDock, OU=StageDock, O=StageDock, L=, ST=, C="

Back up the key and its password. Every release must be signed with the same key, or updates won't install over existing copies.

Add repository secrets under Settings > Secrets and variables > Actions:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Keys\StageDock.jks")) \| Set-Clipboard` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password (same as above unless set differently) |

### Each release

1. Raise `versionCode` by 1 and set `versionName` in `app/build.gradle.kts`, then commit.
2. On GitHub, go to Releases > Draft a new release, create the tag `v<versionName>` (for example `v0.2.0`) and publish.
3. The workflow attaches the signed APK to the release. It fails if the tag doesn't match `versionName` or the APK isn't signed by `CN=StageDock`.

### Local release builds (optional)

Copy `keystore.properties.example` to `keystore.properties`, point `storeFile` at the key (for example `C:/Keys/StageDock.jks`), fill in the passwords and run `gradlew assembleRelease`. Both files are git-ignored. Don't add them through a web upload.
