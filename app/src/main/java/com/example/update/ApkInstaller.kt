package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import java.io.File
import java.security.MessageDigest

data class InstalledVersionInfo(
  val versionCode: Long,
  val versionName: String
)

object ApkInstaller {

  fun getInstalledVersionInfo(context: Context): InstalledVersionInfo {
    return runCatching {
      val pm = context.packageManager
      val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(
          context.packageName,
          PackageManager.PackageInfoFlags.of(0L)
        )
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
      }
      InstalledVersionInfo(
        versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        versionName = packageInfo.versionName ?: BuildConfig.VERSION_NAME
      )
    }.getOrElse {
      InstalledVersionInfo(
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        versionName = BuildConfig.VERSION_NAME
      )
    }
  }

  /**
   * Parses the downloaded APK archive and verifies:
   * 1. Valid Android APK archive structure
   * 2. Exact package ID match ([expectedPackageName])
   * 3. Exact versionCode match with manifest and strictly newer than [installedVersionCode]
   * 4. Signing certificate SHA-256 fingerprint matches [trustedCertSha256]
   */
  fun verifyDownloadedApk(
    context: Context,
    apkFile: File,
    manifest: UpdateManifest,
    installedVersionCode: Long,
    expectedPackageName: String = context.packageName,
    trustedCertSha256: String = UpdateConfig.TRUSTED_SIGNING_CERT_SHA256
  ): Result<Unit> {
    return runCatching {
      if (!apkFile.exists() || !apkFile.isFile || apkFile.length() <= 0L) {
        throw SecurityException("Downloaded APK file is missing or empty.")
      }

      val pm = context.packageManager
      val archiveInfo = readPackageArchiveInfo(pm, apkFile)
        ?: throw SecurityException("Downloaded file is not a valid Android APK package.")

      verifyPackageInfo(
        packageInfo = archiveInfo,
        expectedPackageName = expectedPackageName,
        manifest = manifest,
        installedVersionCode = installedVersionCode,
        trustedCertSha256 = trustedCertSha256
      )
    }
  }

  internal fun verifyPackageInfo(
    packageInfo: PackageInfo,
    expectedPackageName: String,
    manifest: UpdateManifest,
    installedVersionCode: Long,
    trustedCertSha256: String = UpdateConfig.TRUSTED_SIGNING_CERT_SHA256
  ) {
    if (packageInfo.packageName != expectedPackageName) {
      throw SecurityException(
        "APK package ID mismatch: expected $expectedPackageName but found ${packageInfo.packageName}."
      )
    }

    val apkVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    if (apkVersionCode != manifest.versionCode) {
      throw SecurityException(
        "APK versionCode mismatch: manifest specifies ${manifest.versionCode} but APK has $apkVersionCode."
      )
    }
    if (apkVersionCode <= installedVersionCode) {
      throw SecurityException(
        "APK versionCode ($apkVersionCode) is not newer than installed version ($installedVersionCode)."
      )
    }

    val normalizedExpectedCert = UpdateConfig.normalizeSha256Fingerprint(trustedCertSha256)
    require(normalizedExpectedCert.length == 64) {
      "Trusted signing certificate SHA-256 configuration is invalid."
    }

    val signerBytesList = extractSignerCertificates(packageInfo)
    if (signerBytesList.isEmpty()) {
      throw SecurityException("APK does not contain any verifiable signing certificates.")
    }

    val signerFingerprints = signerBytesList.map { certBytes ->
      sha256Hex(certBytes)
    }

    if (!signerFingerprints.all { it == normalizedExpectedCert }) {
      throw SecurityException(
        "APK signing certificate does not match trusted release certificate. File rejected for safety."
      )
    }
  }

  private fun readPackageArchiveInfo(pm: PackageManager, apkFile: File): PackageInfo? {
    val path = apkFile.absolutePath
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val info = pm.getPackageArchiveInfo(
        path,
        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
      )
      if (info?.signingInfo != null) return info
      // Fallback for legacy/test environments if signingInfo wasn't populated
      @Suppress("DEPRECATION")
      val legacyInfo = pm.getPackageArchiveInfo(
        path,
        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNATURES.toLong())
      )
      return legacyInfo ?: info
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      @Suppress("DEPRECATION")
      val info = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
      if (info?.signingInfo != null) return info
      @Suppress("DEPRECATION")
      val legacyInfo = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
      return legacyInfo ?: info
    }

    @Suppress("DEPRECATION")
    return pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
  }

  private fun extractSignerCertificates(packageInfo: PackageInfo): List<ByteArray> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val signingInfo = packageInfo.signingInfo
      if (signingInfo != null) {
        val signatures = if (signingInfo.hasMultipleSigners()) {
          signingInfo.apkContentsSigners
        } else {
          signingInfo.apkContentsSigners?.takeIf { it.isNotEmpty() }
            ?: signingInfo.signingCertificateHistory
        }
        val list = signatures?.mapNotNull { it?.toByteArray() }.orEmpty()
        if (list.isNotEmpty()) return list
      }
    }

    @Suppress("DEPRECATION")
    return packageInfo.signatures?.mapNotNull { it?.toByteArray() }.orEmpty()
  }

  internal fun sha256Hex(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256")
      .digest(bytes)
      .joinToString("") { "%02x".format(it) }
  }

  fun canRequestPackageInstalls(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.packageManager.canRequestPackageInstalls()
    } else {
      true
    }
  }

  fun openInstallUnknownAppsSettings(context: Context): Result<Unit> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return Result.success(Unit)
    }
    return runCatching {
      val intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    }
  }

  fun launchPackageInstaller(context: Context, apkFile: File): Result<Unit> {
    if (!apkFile.exists() || !apkFile.isFile || apkFile.length() <= 0L) {
      return Result.failure(IllegalStateException("APK file not found. Please download the update again."))
    }
    if (!canRequestPackageInstalls(context)) {
      return Result.failure(
        SecurityException("Permission to install unknown apps is required.")
      )
    }

    return runCatching {
      val authority = "${context.packageName}.fileprovider"
      val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

      val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(installIntent)
    }
  }
}
