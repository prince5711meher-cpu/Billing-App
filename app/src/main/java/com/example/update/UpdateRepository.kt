package com.example.update

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class UpdateRepository(
  private val context: Context,
  private val preferences: UpdatePreferences = UpdatePreferences(context),
  private val httpClient: OkHttpClient = defaultManifestClient(),
  private val manifestUrl: String = UpdateConfig.MANIFEST_URL,
  private val installedVersionProvider: () -> InstalledVersionInfo = {
    ApkInstaller.getInstalledVersionInfo(context)
  },
  private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

  suspend fun checkForUpdates(forceNetwork: Boolean): UpdateCheckResult {
    val installed = installedVersionProvider()

    if (!UpdateValidator.validateManifestEndpointUrl(manifestUrl)) {
      // Placeholder or invalid development manifest URL: never lock out users.
      return UpdateCheckResult.NotConfigured
    }

    val now = timeProvider()
    if (!forceNetwork && !preferences.isAutoCheckDue(now)) {
      evaluateCachedManifest(installed)?.let { return it }
    }

    return fetchAndValidateRemoteManifest(installed, now)
  }

  fun evaluateCachedManifestOnly(): UpdateCheckResult? {
    val installed = installedVersionProvider()
    return evaluateCachedManifest(installed)
  }

  private fun evaluateCachedManifest(installed: InstalledVersionInfo): UpdateCheckResult? {
    val cachedRaw = preferences.getCachedManifestJson() ?: return null
    val manifest = UpdateValidator.parseAndValidateManifest(cachedRaw).getOrNull() ?: return null
    return mapManifestToResult(manifest, installed, fromCache = true)
  }

  private suspend fun fetchAndValidateRemoteManifest(
    installed: InstalledVersionInfo,
    nowMillis: Long
  ): UpdateCheckResult = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url(manifestUrl)
        .header("Accept", "application/json")
        .header("Cache-Control", "no-cache")
        .get()
        .build()

      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          return@withContext UpdateCheckResult.Error(
            message = "Update server returned HTTP ${response.code}.",
            isOfflineOrNetwork = response.code >= 500
          )
        }

        val body = response.body
          ?: return@withContext UpdateCheckResult.Error(
            message = "Update manifest response was empty.",
            isOfflineOrNetwork = false
          )

        val contentLength = body.contentLength()
        if (contentLength > UpdateConfig.MAX_MANIFEST_BYTES) {
          return@withContext UpdateCheckResult.Error(
            message = "Update manifest exceeds maximum permitted size.",
            isOfflineOrNetwork = false
          )
        }

        val rawBytes = body.byteStream().use { stream ->
          val buffer = ByteArray(8192)
          val out = java.io.ByteArrayOutputStream()
          var total = 0L
          while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            total += read
            if (total > UpdateConfig.MAX_MANIFEST_BYTES) {
              return@withContext UpdateCheckResult.Error(
                message = "Update manifest exceeds maximum permitted size.",
                isOfflineOrNetwork = false
              )
            }
            out.write(buffer, 0, read)
          }
          out.toByteArray()
        }

        val rawJson = rawBytes.toString(Charsets.UTF_8)
        val parsed = UpdateValidator.parseAndValidateManifest(rawJson)
        if (parsed.isFailure) {
          return@withContext UpdateCheckResult.Error(
            message = parsed.exceptionOrNull()?.message ?: "Invalid update manifest format.",
            isOfflineOrNetwork = false
          )
        }

        val manifest = parsed.getOrThrow()
        preferences.saveValidManifestCheck(rawJson, nowMillis)
        return@withContext mapManifestToResult(manifest, installed, fromCache = false)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: UnknownHostException) {
      return@withContext UpdateCheckResult.Error(
        message = "Offline or unable to reach update server.",
        isOfflineOrNetwork = true
      )
    } catch (e: IOException) {
      return@withContext UpdateCheckResult.Error(
        message = "Network unavailable while checking for updates.",
        isOfflineOrNetwork = true
      )
    } catch (e: Exception) {
      return@withContext UpdateCheckResult.Error(
        message = e.localizedMessage ?: "Failed to check for updates.",
        isOfflineOrNetwork = false
      )
    }
  }

  private fun mapManifestToResult(
    manifest: UpdateManifest,
    installed: InstalledVersionInfo,
    fromCache: Boolean
  ): UpdateCheckResult {
    return if (manifest.isUpdateAvailableFor(installed.versionCode)) {
      UpdateCheckResult.UpdateAvailable(
        manifest = manifest,
        isMandatory = manifest.isMandatoryFor(installed.versionCode),
        fromCache = fromCache
      )
    } else {
      UpdateCheckResult.UpToDate(
        installedVersionCode = installed.versionCode,
        installedVersionName = installed.versionName,
        latestManifest = manifest
      )
    }
  }

  companion object {
    fun defaultManifestClient(): OkHttpClient = OkHttpClient.Builder()
      .connectTimeout(UpdateConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .readTimeout(UpdateConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .build()
  }
}
