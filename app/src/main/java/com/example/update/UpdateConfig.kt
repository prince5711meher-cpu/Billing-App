package com.example.update

/**
 * Centralized configuration for self-hosted APK updates
 * (Cloudflare Pages manifest + GitHub Releases APK delivery).
 *
 * Replace [MANIFEST_URL] with your production Cloudflare Pages URL before publishing releases.
 */
object UpdateConfig {
  const val MANIFEST_URL = "https://YOUR_UPDATE_DOMAIN.pages.dev/update.json"

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

  fun isPlaceholderManifestUrl(url: String = MANIFEST_URL): Boolean {
    return url.isBlank() || url.contains("YOUR_UPDATE_DOMAIN", ignoreCase = true)
  }
}
