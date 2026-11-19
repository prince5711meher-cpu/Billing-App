package com.example.update

/**
 * Centralized configuration for self-hosted APK updates
 * (GitHub Pages manifest + GitHub Releases APK delivery).
 */
object UpdateConfig {
  const val MANIFEST_URL =
    "https://prince5711meher-cpu.github.io/meher-basan-updates/update.json"

  /**
   * Expected release signing certificate SHA-256 fingerprint for Meher Basan Bhandar.
   * Any downloaded APK whose signing certificate does not match is rejected and deleted.
   */
  const val TRUSTED_SIGNING_CERT_SHA256 =
    "3E:07:4C:DA:DD:EC:DA:29:AB:88:FF:00:42:63:AE:65:00:DC:FC:D0:53:0C:A7:1D:3D:23:8E:9A:B4:A3:9D:F3"

  /**
   * Allowed hosts for manifest apkUrl and any HTTP 3xx CDN redirects served by GitHub Releases.
   */
  val ALLOWED_APK_HOSTS: Set<String> = setOf(
    "github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
    "github-releases.githubusercontent.com"
  )

  /**
   * Primary host permitted inside update.json's `apkUrl` field.
   */
  val ALLOWED_MANIFEST_APK_HOSTS: Set<String> = setOf(
    "github.com"
  )

  const val CONNECT_TIMEOUT_SECONDS = 15L
  const val READ_TIMEOUT_SECONDS = 30L
  const val DOWNLOAD_READ_TIMEOUT_SECONDS = 60L

  const val AUTO_CHECK_INTERVAL_MILLIS = 12L * 60L * 60L * 1000L // 12 hours

  const val DOWNLOAD_DIRECTORY_NAME = "apk_updates"

  const val MAX_MANIFEST_BYTES = 64L * 1024L // 64 KB
  const val MAX_APK_BYTES = 150L * 1024L * 1024L // 150 MB
  const val STORAGE_BUFFER_BYTES = 20L * 1024L * 1024L // 20 MB headroom
  const val MIN_UNKNOWN_SIZE_REQUIRED_BYTES = 50L * 1024L * 1024L // 50 MB fallback check

  fun normalizeSha256Fingerprint(fingerprint: String): String {
    return fingerprint.replace(":", "").replace(" ", "").trim().lowercase()
  }

  fun isPlaceholderManifestUrl(url: String = MANIFEST_URL): Boolean {
    return url.isBlank() ||
      url.contains("YOUR_UPDATE_DOMAIN", ignoreCase = true) ||
      url.contains("YOUR_USERNAME", ignoreCase = true)
  }
}
