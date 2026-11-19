package com.example.update

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import okio.Buffer
import java.net.URI

object UpdateValidator {

  private val SHA256_REGEX = Regex("^[a-f0-9]{64}$")
  private val moshi: Moshi = Moshi.Builder()
    .add(RawManifest::class.java, RawManifestAdapter)
    .build()
  private val manifestAdapter: JsonAdapter<RawManifest> = moshi.adapter(RawManifest::class.java)

  fun parseAndValidateManifest(
    rawJson: String,
    allowedManifestApkHosts: Set<String> = UpdateConfig.ALLOWED_MANIFEST_APK_HOSTS
  ): Result<UpdateManifest> {
    return runCatching {
      val utf8Bytes = rawJson.toByteArray(Charsets.UTF_8)
      if (utf8Bytes.size > UpdateConfig.MAX_MANIFEST_BYTES) {
        throw IllegalArgumentException("Update manifest exceeds maximum allowed size.")
      }
      if (rawJson.isBlank()) {
        throw IllegalArgumentException("Update manifest is empty.")
      }

      val raw = Buffer().use { buffer ->
        buffer.write(utf8Bytes)
        val reader = JsonReader.of(buffer)
        reader.isLenient = false
        val parsed = manifestAdapter.fromJson(reader)
          ?: throw IllegalArgumentException("Update manifest is empty.")
        if (reader.peek() != JsonReader.Token.END_DOCUMENT) {
          throw IllegalArgumentException("Unexpected trailing JSON data in update manifest.")
        }
        parsed
      }

      val versionCode = raw.versionCode
        ?: throw IllegalArgumentException("Invalid or missing versionCode.")
      if (versionCode <= 0L) {
        throw IllegalArgumentException("versionCode must be positive.")
      }

      val versionName = raw.versionName?.trim()
      if (versionName.isNullOrEmpty() || versionName.length > 32) {
        throw IllegalArgumentException("Invalid or missing versionName.")
      }

      val minSupportedVersionCode = raw.minSupportedVersionCode
        ?: throw IllegalArgumentException("Invalid or missing minSupportedVersionCode.")
      if (minSupportedVersionCode < 1L || minSupportedVersionCode > versionCode) {
        throw IllegalArgumentException("minSupportedVersionCode must be between 1 and versionCode.")
      }

      val notes = raw.releaseNotes
        ?: throw IllegalArgumentException("Invalid or missing releaseNotes.")

      val apkUrl = raw.apkUrl?.trim()
        ?: throw IllegalArgumentException("Invalid or missing apkUrl.")
      validateHttpsUrl(
        url = apkUrl,
        allowedHosts = allowedManifestApkHosts,
        requireApkExtension = true
      )

      val sha256 = raw.sha256?.trim()?.lowercase()
        ?: throw IllegalArgumentException("Invalid or missing sha256 checksum.")
      if (!SHA256_REGEX.matches(sha256)) {
        throw IllegalArgumentException("sha256 must be a valid 64-character hex digest.")
      }

      val mandatory = raw.mandatory
        ?: throw IllegalArgumentException("mandatory must be a boolean.")

      val apkSizeBytes = raw.apkSizeBytes
      if (apkSizeBytes != null && (apkSizeBytes <= 0L || apkSizeBytes > UpdateConfig.MAX_APK_BYTES)) {
        throw IllegalArgumentException("apkSizeBytes must be between 1 and ${UpdateConfig.MAX_APK_BYTES}.")
      }

      UpdateManifest(
        versionCode = versionCode,
        versionName = versionName,
        minSupportedVersionCode = minSupportedVersionCode,
        releaseNotes = notes,
        apkUrl = apkUrl,
        sha256 = sha256,
        mandatory = mandatory,
        apkSizeBytes = apkSizeBytes
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
    if (
      trimmed.isEmpty() ||
      trimmed.contains("YOUR_USERNAME", ignoreCase = true) ||
      trimmed.contains("YOUR_REPO", ignoreCase = true) ||
      trimmed.contains("YOUR_UPDATE_DOMAIN", ignoreCase = true)
    ) {
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

  private data class RawManifest(
    val versionCode: Long?,
    val versionName: String?,
    val minSupportedVersionCode: Long?,
    val releaseNotes: List<String>?,
    val apkUrl: String?,
    val sha256: String?,
    val mandatory: Boolean?,
    val apkSizeBytes: Long?
  )

  private object RawManifestAdapter : JsonAdapter<RawManifest>() {
    private val options = JsonReader.Options.of(
      "versionCode",
      "versionName",
      "minSupportedVersionCode",
      "releaseNotes",
      "apkUrl",
      "sha256",
      "mandatory",
      "apkSizeBytes",
      "sizeBytes"
    )

    override fun fromJson(reader: JsonReader): RawManifest? {
      if (reader.peek() == JsonReader.Token.NULL) {
        return reader.nextNull()
      }
      if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
        throw JsonDataException("Expected JSON object for update manifest.")
      }

      var versionCode: Long? = null
      var versionName: String? = null
      var minSupportedVersionCode: Long? = null
      var releaseNotes: List<String>? = null
      var apkUrl: String? = null
      var sha256: String? = null
      var mandatory: Boolean? = null
      var apkSizeBytes: Long? = null

      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.selectName(options)) {
          0 -> versionCode = readStrictLong(reader, "versionCode")
          1 -> versionName = readStrictString(reader, "versionName")
          2 -> minSupportedVersionCode = readStrictLong(reader, "minSupportedVersionCode")
          3 -> releaseNotes = readReleaseNotes(reader)
          4 -> apkUrl = readStrictString(reader, "apkUrl")
          5 -> sha256 = readStrictString(reader, "sha256")
          6 -> mandatory = readStrictBoolean(reader, "mandatory")
          7, 8 -> {
            if (reader.peek() == JsonReader.Token.NULL) {
              reader.nextNull<Unit>()
            } else {
              apkSizeBytes = readStrictLong(reader, "apkSizeBytes")
            }
          }
          else -> {
            reader.skipName()
            reader.skipValue()
          }
        }
      }
      reader.endObject()

      return RawManifest(
        versionCode = versionCode,
        versionName = versionName,
        minSupportedVersionCode = minSupportedVersionCode,
        releaseNotes = releaseNotes,
        apkUrl = apkUrl,
        sha256 = sha256,
        mandatory = mandatory,
        apkSizeBytes = apkSizeBytes
      )
    }

    override fun toJson(writer: com.squareup.moshi.JsonWriter, value: RawManifest?) {
      throw UnsupportedOperationException("Serialization is not supported.")
    }

    private fun readStrictLong(reader: JsonReader, field: String): Long {
      if (reader.peek() != JsonReader.Token.NUMBER) {
        throw IllegalArgumentException("Invalid or missing $field.")
      }
      return try {
        reader.nextLong()
      } catch (e: Exception) {
        throw IllegalArgumentException("$field must be a whole integer.", e)
      }
    }

    private fun readStrictString(reader: JsonReader, field: String): String {
      if (reader.peek() != JsonReader.Token.STRING) {
        throw IllegalArgumentException("Invalid or missing $field.")
      }
      return reader.nextString()
    }

    private fun readStrictBoolean(reader: JsonReader, field: String): Boolean {
      if (reader.peek() != JsonReader.Token.BOOLEAN) {
        throw IllegalArgumentException("$field must be a boolean.")
      }
      return reader.nextBoolean()
    }

    private fun readReleaseNotes(reader: JsonReader): List<String> {
      return when (reader.peek()) {
        JsonReader.Token.STRING -> {
          val rawText = reader.nextString().trim()
          if (rawText.isEmpty()) {
            emptyList()
          } else {
            rawText.lines()
              .map { it.trim() }
              .filter { it.isNotEmpty() }
              .take(25)
          }
        }
        JsonReader.Token.BEGIN_ARRAY -> {
          val list = ArrayList<String>()
          reader.beginArray()
          while (reader.hasNext()) {
            if (reader.peek() != JsonReader.Token.STRING) {
              throw IllegalArgumentException("releaseNotes entries must be strings.")
            }
            val cleaned = reader.nextString().trim()
            if (cleaned.isNotEmpty() && list.size < 25) {
              list.add(cleaned)
            }
          }
          reader.endArray()
          list
        }
        else -> throw IllegalArgumentException("releaseNotes must be a string or JSON array of strings.")
      }
    }
  }
}
