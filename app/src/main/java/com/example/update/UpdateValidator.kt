package com.example.update

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

object UpdateValidator {

  private val SHA256_REGEX = Regex("^[a-f0-9]{64}$")

  fun parseAndValidateManifest(
    rawJson: String,
    allowedManifestApkHosts: Set<String> = UpdateConfig.ALLOWED_MANIFEST_APK_HOSTS
  ): Result<UpdateManifest> {
    return runCatching {
      if (rawJson.toByteArray(Charsets.UTF_8).size > UpdateConfig.MAX_MANIFEST_BYTES) {
        throw IllegalArgumentException("Update manifest exceeds maximum allowed size.")
      }

      val json = JSONObject(rawJson)

      val versionCodeRaw = json.opt("versionCode")
      if (versionCodeRaw !is Int && versionCodeRaw !is Long) {
        throw IllegalArgumentException("Invalid or missing versionCode.")
      }
      val versionCode = (versionCodeRaw as Number).toLong()
      if (versionCode <= 0L) {
        throw IllegalArgumentException("versionCode must be positive.")
      }

      val versionNameRaw = json.opt("versionName")
      if (versionNameRaw !is String || versionNameRaw.trim().isEmpty() || versionNameRaw.trim().length > 32) {
        throw IllegalArgumentException("Invalid or missing versionName.")
      }
      val versionName = versionNameRaw.trim()

      val minSupportedRaw = json.opt("minSupportedVersionCode")
      if (minSupportedRaw !is Int && minSupportedRaw !is Long) {
        throw IllegalArgumentException("Invalid or missing minSupportedVersionCode.")
      }
      val minSupportedVersionCode = (minSupportedRaw as Number).toLong()
      if (minSupportedVersionCode < 1L || minSupportedVersionCode > versionCode) {
        throw IllegalArgumentException("minSupportedVersionCode must be between 1 and versionCode.")
      }

      val notesRaw = json.opt("releaseNotes")
      if (notesRaw !is JSONArray) {
        throw IllegalArgumentException("releaseNotes must be a JSON array.")
      }
      val notes = buildList {
        for (i in 0 until minOf(notesRaw.length(), 25)) {
          val entry = notesRaw.opt(i)
          if (entry !is String) {
            throw IllegalArgumentException("releaseNotes entries must be strings.")
          }
          val cleaned = entry.trim()
          if (cleaned.isNotEmpty()) add(cleaned)
        }
      }

      val apkUrlRaw = json.opt("apkUrl")
      if (apkUrlRaw !is String) {
        throw IllegalArgumentException("Invalid or missing apkUrl.")
      }
      val apkUrl = apkUrlRaw.trim()
      validateHttpsUrl(
        url = apkUrl,
        allowedHosts = allowedManifestApkHosts,
        requireApkExtension = true
      )

      val sha256Raw = json.opt("sha256")
      if (sha256Raw !is String) {
        throw IllegalArgumentException("Invalid or missing sha256 checksum.")
      }
      val sha256 = sha256Raw.trim().lowercase()
      if (!SHA256_REGEX.matches(sha256)) {
        throw IllegalArgumentException("sha256 must be a valid 64-character hex digest.")
      }

      val mandatoryRaw = json.opt("mandatory")
      if (mandatoryRaw !is Boolean) {
        throw IllegalArgumentException("mandatory must be a boolean.")
      }

      UpdateManifest(
        versionCode = versionCode,
        versionName = versionName,
        minSupportedVersionCode = minSupportedVersionCode,
        releaseNotes = notes,
        apkUrl = apkUrl,
        sha256 = sha256,
        mandatory = mandatoryRaw
      )
    }
  }

  fun validateManifestEndpointUrl(url: String): Boolean {
    return runCatching {
      if (UpdateConfig.isPlaceholderManifestUrl(url)) {
        throw IllegalArgumentException("Placeholder manifest URL.")
      }
      val uri = URI(url.trim())
      if (!uri.scheme.equals("https", ignoreCase = true)) {
        throw IllegalArgumentException("Manifest URL must use HTTPS.")
      }
      if (uri.userInfo != null) {
        throw IllegalArgumentException("Manifest URL must not contain credentials.")
      }
      if (uri.host.isNullOrBlank()) {
        throw IllegalArgumentException("Manifest URL host is missing.")
      }
      true
    }.getOrDefault(false)
  }

  fun validateHttpsUrl(
    url: String,
    allowedHosts: Set<String>,
    requireApkExtension: Boolean = false
  ): URI {
    val trimmed = url.trim()
    if (trimmed.isEmpty() || trimmed.contains("YOUR_", ignoreCase = true)) {
      throw IllegalArgumentException("Download URL is not configured.")
    }
    val uri = URI(trimmed)
    if (!uri.scheme.equals("https", ignoreCase = true)) {
      throw IllegalArgumentException("Only HTTPS URLs are permitted.")
    }
    if (uri.userInfo != null) {
      throw IllegalArgumentException("URLs with embedded credentials are not permitted.")
    }
    val host = uri.host?.lowercase()?.trim()
      ?: throw IllegalArgumentException("URL host is missing.")
    if (host !in allowedHosts) {
      throw IllegalArgumentException("Unsupported APK host: $host")
    }
    val path = uri.path ?: ""
    if (path.isBlank()) {
      throw IllegalArgumentException("APK URL path is missing.")
    }
    if (requireApkExtension && !path.lowercase().endsWith(".apk")) {
      throw IllegalArgumentException("APK URL must point to an .apk file.")
    }
    return uri
  }
}
