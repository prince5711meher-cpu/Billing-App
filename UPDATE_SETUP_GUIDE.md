# APK Self-Update Setup & Release Guide — Meher Basan Bhandar (Billing App)

This guide explains how the in-app APK updater works and how to publish signed updates safely using **Google AI Studio**, **GitHub Releases**, and **Cloudflare Pages**.

---

## 1. How the Update Checker Works

1. **Manifest Check**:
   - On app launch (`UpdateViewModel`), the app checks `UpdateConfig.MANIFEST_URL` over **HTTPS only** if at least **12 hours** (`AUTO_CHECK_INTERVAL_MILLIS`) have passed since the last successful check.
   - Inside **Settings → App Updates**, users can also tap **Check Updates** at any time to force an immediate check.
   - Repeated Compose recompositions do not trigger duplicate network requests.
2. **Validation & Caching**:
   - `UpdateValidator` parses and strictly validates `update.json`:
     - Rejects non-HTTPS URLs, malformed SHA-256 hashes, unsupported download hosts, payloads larger than 64 KB, and invalid `versionCode` / `minSupportedVersionCode`.
     - Compares `manifest.versionCode` against the installed app's `BuildConfig.VERSION_CODE` / `PackageInfo`. Downgrades are never offered.
   - Valid manifests and timestamps are cached in isolated `SharedPreferences` (`app_update_prefs`).
   - Offline states or temporary server errors never lock users out of the billing app.
3. **Optional vs. Mandatory Updates**:
   - **Optional** (`mandatory == false` and `installedVersionCode >= minSupportedVersionCode`): Shows an update dialog with **Update Now** and **Later**. Tapping **Later** lets the user keep working while the update remains available in **Settings → App Updates**.
   - **Mandatory** (`mandatory == true` or `installedVersionCode < minSupportedVersionCode`): Blocks access to the main billing screens with a full-screen prompt showing release notes, **Update Now**, and **Retry**.
4. **Secure Download & Install**:
   - `ApkDownloader` streams the APK with **OkHttp** into app-specific internal storage (`filesDir/apk_updates/`), checks available disk space first, enforces the `ALLOWED_APK_HOSTS` allowlist across every HTTP redirect, verifies the downloaded file's **SHA-256** digest, and deletes any corrupted or incomplete `.part` files automatically.
   - `ApkInstaller` shares the verified APK via `FileProvider` (`${applicationId}.fileprovider`), checks `canRequestPackageInstalls()`, guides the user to **Install unknown apps** if needed, and opens Android's official package installer.
   - On `ON_RESUME`, the app re-checks the installed version instead of assuming installation succeeded.

---

## 2. How to Host `update.json` on Cloudflare Pages

1. Create a small static folder (or Git repository) containing one file named `update.json`:

```json
{
  "versionCode": 2,
  "versionName": "1.1",
  "minSupportedVersionCode": 1,
  "releaseNotes": [
    "Improved billing speed",
    "Fixed invoice issues",
    "Performance improvements"
  ],
  "apkUrl": "https://github.com/YOUR_USERNAME/YOUR_REPO/releases/download/v1.1/app-release.apk",
  "sha256": "PASTE_64_CHAR_LOWERCASE_SHA256_HERE",
  "mandatory": false
}
```

2. Deploy this folder to **Cloudflare Pages** (either via **Workers & Pages → Create → Upload assets** or connected Git repo).
3. Cloudflare Pages gives you an HTTPS URL (`https://<your-project>.pages.dev/update.json`).
4. Set that exact HTTPS URL in `app/src/main/java/com/example/update/UpdateConfig.kt`:

```kotlin
const val MANIFEST_URL = "https://<your-project>.pages.dev/update.json"
```

---

## 3. How to Upload APKs to GitHub Releases

1. Export the **release-signed APK** from Google AI Studio (or build it with your release keystore).
2. In your GitHub repository, go to **Releases → Draft a new release**.
3. Create a tag matching the version (for example `v1.1`), add a title and release notes.
4. Attach the APK file (for example `app-release.apk`) under **Attach binaries**.
5. Publish the release, then right-click the attached `.apk` asset and copy its HTTPS download link:
   `https://github.com/<username>/<repo>/releases/download/v1.1/app-release.apk`
6. Paste this URL into `"apkUrl"` in `update.json`.
   - Keep `"github.com"` and GitHub's CDN hosts (`objects.githubusercontent.com`, `release-assets.githubusercontent.com`, `github-releases.githubusercontent.com`) in `UpdateConfig.ALLOWED_APK_HOSTS`.

---

## 4. How to Calculate SHA-256

Run one of the following commands against the exact APK uploaded to GitHub Releases:

- **Linux / macOS**:
  ```bash
  sha256sum app-release.apk
  # or on macOS:
  shasum -a 256 app-release.apk
  ```
- **Windows (PowerShell)**:
  ```powershell
  Get-FileHash .\app-release.apk -Algorithm SHA256
  ```

Copy the **64-character lowercase hex digest** into `"sha256"` in `update.json`. Any single-byte mismatch causes the app to reject and delete the downloaded APK automatically.

---

## 5. How to Publish a New Version

1. Before exporting the new APK in `app/build.gradle.kts`, increment **both**:
   - `versionCode` (e.g., `1` → `2`) — must be strictly higher than the previous release.
   - `versionName` (e.g., `"1.0"` → `"1.1"`).
2. Keep `applicationId` (`com.aistudio.meherbasan.xqzlk`) and your release signing certificate **unchanged**.
3. Export the signed APK.
4. Compute its SHA-256 hash.
5. Upload the APK to a new GitHub Release.
6. Update `update.json` with the new `versionCode`, `versionName`, `releaseNotes`, `apkUrl`, and `sha256`, and redeploy Cloudflare Pages.

---

## 6. How to Publish Mandatory Updates

There are two ways to require an update:

- **Force everyone below the latest release**: Set `"mandatory": true` in `update.json`. Every user whose installed `versionCode < manifest.versionCode` must update before accessing billing.
- **Block only very old versions**: Keep `"mandatory": false` and raise `"minSupportedVersionCode"` (for example, `versionCode: 5`, `minSupportedVersionCode: 3`). Users on version 1 or 2 are required to update, while users on version 3 or 4 see an optional update prompt.

---

## 7. How to Test Updates (Real Android Device Checklist)

Automated JVM tests cannot simulate Android's package installer signature verification. Always test on a real Android phone before rolling out to shop staff:

1. **Install Base Version**: Install release v1 (`versionCode = 1`) onto your test phone. Sign in with Google, create a sample bill, saved item, and setting.
2. **No Update**: Point `update.json` to `versionCode: 1` → verify Settings shows **Up to date**.
3. **Optional Update**: Publish v2 (`versionCode: 2`, `mandatory: false`) → open the app → verify the dialog appears, **Later** allows continued billing, and **Settings → App Updates** lets you download & install.
4. **Checksum Mismatch**: Temporarily change 1 character in `update.json`'s `sha256` → verify download aborts with checksum error and deletes the file.
5. **Missing Install Permission**: With "Install unknown apps" turned OFF for Billing App, tap **Install Update** → verify the app guides you to Android's permission toggle.
6. **In-Place Install & Data Preservation**: Enable permission, tap **Install Update**, confirm Android's installer → reopen app → verify `Settings → App Updates` shows `v1.1 (Code 2)`, and verify your **Google login session, Firestore bills, Udhar dues, saved items, and store profile** are all still present.
7. **Mandatory Update**: Install v1 (`versionCode = 1`) and point `update.json` (`versionCode: 2`) with `mandatory: true` (or `minSupportedVersionCode: 2`) → verify billing is blocked until updated, and verify turning off internet/making server unreachable does **not** lock out users on a fresh check failure.

---

## 8. How to Verify Signing Compatibility (CRITICAL)

Android **rejects** an in-place APK update (`"App not installed as package conflicts with an existing package"`) unless **both** conditions hold:
1. Both APKs have the exact same `applicationId` (`com.aistudio.meherbasan.xqzlk`).
2. Both APKs are signed with the **exact same release certificate**.

Verify before publishing every release APK:

```bash
apksigner verify --print-certs v1.apk
apksigner verify --print-certs v2.apk
```

Confirm that the **Signer #1 certificate SHA-256 digest** printed for `v2.apk` is **100% identical** to `v1.apk` (and matches the release keystore configured via `KEYSTORE_PATH` / `my-upload-key.jks` in AI Studio's release signing configuration). Do **not** mix debug-signed APKs with release-signed APKs, because their signing certificates differ and Android will refuse the update.
