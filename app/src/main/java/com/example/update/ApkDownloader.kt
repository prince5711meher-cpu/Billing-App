package com.example.update

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class ApkDownloader(
  private val downloadDir: File,
  private val baseClient: OkHttpClient = defaultDownloadClient(),
  private val allowedHosts: Set<String> = UpdateConfig.ALLOWED_APK_HOSTS,
  private val availableBytesProvider: (File) -> Long = { dir ->
    if (!dir.exists()) dir.mkdirs()
    StatFs(dir.absolutePath).availableBytes
  },
  private val apkVerifier: (apkFile: File, manifest: UpdateManifest) -> Result<Unit> = { _, _ ->
    Result.success(Unit)
  }
) {

  constructor(context: Context) : this(
    downloadDir = File(context.applicationContext.filesDir, UpdateConfig.DOWNLOAD_DIRECTORY_NAME),
    apkVerifier = { apkFile, manifest ->
      val installedCode = ApkInstaller.getInstalledVersionInfo(context.applicationContext).versionCode
      ApkInstaller.verifyDownloadedApk(
        context = context.applicationContext,
        apkFile = apkFile,
        manifest = manifest,
        installedVersionCode = installedCode
      )
    }
  )

  private val redirectSafeClient: OkHttpClient = baseClient.newBuilder()
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

  fun getTargetApkFile(versionCode: Long): File {
    if (!downloadDir.exists()) downloadDir.mkdirs()
    return File(downloadDir, "update_v${versionCode}.apk")
  }

  fun findVerifiedExistingApk(manifest: UpdateManifest): File? {
    val file = getTargetApkFile(manifest.versionCode)
    if (!file.exists() || !file.isFile || file.length() <= 0L) return null
    val actualHash = runCatching { computeSha256(file) }.getOrNull()
    if (actualHash == null || !actualHash.equals(manifest.sha256, ignoreCase = true)) {
      file.delete()
      return null
    }
    if (apkVerifier(file, manifest).isFailure) {
      file.delete()
      return null
    }
    return file
  }

  fun cleanupObsoleteApks(installedVersionCode: Long) {
    if (!downloadDir.exists()) return
    downloadDir.listFiles()?.forEach { file ->
      val name = file.name
      if (name.endsWith(".part")) {
        file.delete()
      } else if (name.startsWith("update_v") && name.endsWith(".apk")) {
        val codeStr = name.removePrefix("update_v").removeSuffix(".apk")
        val code = codeStr.toLongOrNull()
        if (code == null || code <= installedVersionCode) {
          file.delete()
        }
      }
    }
  }

  suspend fun downloadAndVerify(
    manifest: UpdateManifest,
    onProgress: (bytesDownloaded: Long, totalBytes: Long, percent: Int) -> Unit,
    onVerifying: (totalBytes: Long) -> Unit = {}
  ): Result<File> = withContext(Dispatchers.IO) {
    if (!downloadDir.exists() && !downloadDir.mkdirs()) {
      return@withContext Result.failure(IOException("Unable to prepare update storage directory."))
    }

    // Reuse already-verified APK if present
    findVerifiedExistingApk(manifest)?.let { existing ->
      onProgress(existing.length(), existing.length(), 100)
      return@withContext Result.success(existing)
    }

    val targetFile = getTargetApkFile(manifest.versionCode)
    val partFile = File(downloadDir, "update_v${manifest.versionCode}.apk.part")
    if (partFile.exists()) partFile.delete()

    // Check minimum baseline storage before connecting
    val initialAvailable = runCatching { availableBytesProvider(downloadDir) }.getOrDefault(0L)
    if (initialAvailable < UpdateConfig.STORAGE_BUFFER_BYTES) {
      return@withContext Result.failure(
        IOException("Insufficient storage space. Free at least 50 MB and try again.")
      )
    }

    var activeResponse: Response? = null
    try {
      // Validate initial manifest URL host
      UpdateValidator.validateHttpsUrl(
        url = manifest.apkUrl,
        allowedHosts = allowedHosts,
        requireApkExtension = true
      )

      activeResponse = executeWithValidatedRedirects(manifest.apkUrl)
      val body = activeResponse.body
        ?: return@withContext Result.failure(IOException("Empty APK response from server."))

      val contentLength = body.contentLength()
      if (contentLength > UpdateConfig.MAX_APK_BYTES) {
        return@withContext Result.failure(IOException("APK exceeds maximum allowed size."))
      }

      val requiredBytes = if (contentLength > 0L) {
        contentLength + UpdateConfig.STORAGE_BUFFER_BYTES
      } else {
        UpdateConfig.MIN_UNKNOWN_SIZE_REQUIRED_BYTES
      }
      val availableBytes = runCatching { availableBytesProvider(downloadDir) }.getOrDefault(0L)
      if (availableBytes < requiredBytes) {
        return@withContext Result.failure(
          IOException("Insufficient storage space to download update.")
        )
      }

      val digest = MessageDigest.getInstance("SHA-256")
      var totalRead = 0L
      var lastReportedPercent = -1
      var lastReportTimeMs = 0L
      val buffer = ByteArray(16 * 1024)

      body.byteStream().use { input ->
        FileOutputStream(partFile).use { output ->
          while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read == -1) break

            totalRead += read
            if (totalRead > UpdateConfig.MAX_APK_BYTES) {
              throw IOException("Downloaded APK exceeds maximum permitted size.")
            }

            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)

            val now = System.currentTimeMillis()
            val percent = if (contentLength > 0L) {
              ((totalRead * 100L) / contentLength).toInt().coerceIn(0, 100)
            } else {
              0
            }
            if (percent != lastReportedPercent || now - lastReportTimeMs >= 150L) {
              lastReportedPercent = percent
              lastReportTimeMs = now
              onProgress(totalRead, contentLength, percent)
            }
          }
          output.flush()
        }
      }

      if (totalRead <= 0L) {
        partFile.delete()
        return@withContext Result.failure(IOException("Downloaded APK file is empty."))
      }

      if (contentLength > 0L && totalRead != contentLength) {
        partFile.delete()
        return@withContext Result.failure(IOException("Incomplete APK download. Please retry."))
      }

      onVerifying(totalRead)

      // 1. Verify SHA-256 checksum
      val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
      if (!actualSha256.equals(manifest.sha256, ignoreCase = true)) {
        partFile.delete()
        return@withContext Result.failure(
          SecurityException("APK checksum verification failed. File was deleted for safety.")
        )
      }

      // 2. Verify APK package ID, versionCode, and trusted signing certificate
      val archiveCheck = apkVerifier(partFile, manifest)
      if (archiveCheck.isFailure) {
        partFile.delete()
        val cause = archiveCheck.exceptionOrNull()
        return@withContext Result.failure(
          cause as? SecurityException
            ?: SecurityException(cause?.message ?: "APK signature verification failed.", cause)
        )
      }

      // 3. Only promote verified replacement over targetFile after all security checks pass
      if (targetFile.exists()) {
        targetFile.delete()
      }
      if (!partFile.renameTo(targetFile)) {
        partFile.copyTo(targetFile, overwrite = true)
        partFile.delete()
      }

      Result.success(targetFile)
    } catch (e: CancellationException) {
      partFile.delete()
      throw e
    } catch (e: Exception) {
      partFile.delete()
      Result.failure(e)
    } finally {
      activeResponse?.close()
    }
  }

  private suspend fun executeWithValidatedRedirects(initialUrl: String): Response {
    var currentUrl = initialUrl
    var redirectCount = 0
    val maxRedirects = 5

    while (true) {
      coroutineContext.ensureActive()
      val request = Request.Builder()
        .url(currentUrl)
        .get()
        .build()

      val call = redirectSafeClient.newCall(request)
      val response = try {
        call.execute()
      } catch (e: Exception) {
        if (call.isCanceled()) throw CancellationException("Download cancelled", e)
        throw e
      }

      if (response.code in listOf(301, 302, 303, 307, 308)) {
        val location = response.header("Location")
        response.close()
        if (location.isNullOrBlank()) {
          throw IOException("Redirect location missing.")
        }
        redirectCount++
        if (redirectCount > maxRedirects) {
          throw IOException("Too many redirects while downloading APK.")
        }
        val nextUrl = URI(currentUrl).resolve(location).toString()
        UpdateValidator.validateHttpsUrl(
          url = nextUrl,
          allowedHosts = allowedHosts,
          requireApkExtension = false
        )
        currentUrl = nextUrl
        continue
      }

      if (!response.isSuccessful) {
        val code = response.code
        response.close()
        throw IOException("Download failed with HTTP $code")
      }

      return response
    }
  }

  fun computeSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(16 * 1024)
    FileInputStream(file).use { input ->
      while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  companion object {
    fun defaultDownloadClient(): OkHttpClient = OkHttpClient.Builder()
      .connectTimeout(UpdateConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .readTimeout(UpdateConfig.DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .build()
  }
}
