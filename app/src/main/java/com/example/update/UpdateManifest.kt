package com.example.update

import java.io.File

data class UpdateManifest(
  val versionCode: Long,
  val versionName: String,
  val minSupportedVersionCode: Long,
  val releaseNotes: List<String>,
  val apkUrl: String,
  val sha256: String,
  val mandatory: Boolean,
  val apkSizeBytes: Long? = null
) {
  fun isUpdateAvailableFor(installedVersionCode: Long): Boolean {
    return versionCode > installedVersionCode
  }

  fun isMandatoryFor(installedVersionCode: Long): Boolean {
    if (!isUpdateAvailableFor(installedVersionCode)) return false
    return mandatory || installedVersionCode < minSupportedVersionCode
  }
}

sealed interface UpdateCheckResult {
  data class UpdateAvailable(
    val manifest: UpdateManifest,
    val isMandatory: Boolean,
    val fromCache: Boolean = false
  ) : UpdateCheckResult

  data class UpToDate(
    val installedVersionCode: Long,
    val installedVersionName: String,
    val latestManifest: UpdateManifest? = null
  ) : UpdateCheckResult

  data object NotConfigured : UpdateCheckResult

  data class Error(
    val message: String,
    val isOfflineOrNetwork: Boolean
  ) : UpdateCheckResult
}

sealed interface DownloadState {
  data object Idle : DownloadState

  data class Downloading(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progressPercent: Int
  ) : DownloadState

  data class Verifying(
    val totalBytes: Long
  ) : DownloadState

  data class VerifiedReadyToInstall(
    val manifest: UpdateManifest,
    val apkFile: File,
    val totalBytes: Long = apkFile.length()
  ) : DownloadState

  data class Failed(
    val message: String
  ) : DownloadState
}
