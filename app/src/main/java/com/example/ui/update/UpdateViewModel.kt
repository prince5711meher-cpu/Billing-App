package com.example.ui.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.update.ApkDownloader
import com.example.update.ApkInstaller
import com.example.update.DownloadState
import com.example.update.UpdateCheckResult
import com.example.update.UpdateConfig
import com.example.update.UpdateManifest
import com.example.update.UpdatePreferences
import com.example.update.UpdateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AppUpdateUiState(
  val installedVersionCode: Long = 1L,
  val installedVersionName: String = "1.0",
  val lastCheckedAtMs: Long = 0L,
  val isChecking: Boolean = false,
  val checkResult: UpdateCheckResult? = null,
  val downloadState: DownloadState = DownloadState.Idle,
  val hasInstallPermission: Boolean = true,
  val showOptionalPrompt: Boolean = false,
  val installerLaunchError: String? = null,
  val awaitingInstallReturn: Boolean = false
) {
  val availableManifest: UpdateManifest?
    get() = (checkResult as? UpdateCheckResult.UpdateAvailable)?.manifest

  val isMandatoryBlocked: Boolean
    get() = (checkResult as? UpdateCheckResult.UpdateAvailable)?.isMandatory == true
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext
  private val preferences = UpdatePreferences(appContext)
  private val repository = UpdateRepository(appContext, preferences)
  private val downloader = ApkDownloader(appContext)

  private val _uiState = MutableStateFlow(buildInitialState())
  val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

  private var hasLaunchedAutoCheck = false
  private var downloadJob: Job? = null

  init {
    downloader.cleanupObsoleteApks(_uiState.value.installedVersionCode)
    runAutoCheckOnLaunch()
    startPeriodicAutoCheckLoop()
  }

  private fun buildInitialState(): AppUpdateUiState {
    val installed = ApkInstaller.getInstalledVersionInfo(appContext)
    return AppUpdateUiState(
      installedVersionCode = installed.versionCode,
      installedVersionName = installed.versionName,
      lastCheckedAtMs = preferences.getLastCheckedAtMillis(),
      hasInstallPermission = ApkInstaller.canRequestPackageInstalls(appContext)
    )
  }

  fun runAutoCheckOnLaunch() {
    if (hasLaunchedAutoCheck) return
    hasLaunchedAutoCheck = true
    performCheck(forceNetwork = false, isSilentBackground = true)
  }

  fun checkForUpdatesManual() {
    performCheck(forceNetwork = true, isSilentBackground = false)
  }

  fun onAppResumed() {
    refreshInstalledVersionAndPermission()
    if (hasLaunchedAutoCheck && preferences.isAutoCheckDue()) {
      performCheck(forceNetwork = false, isSilentBackground = true)
    }
  }

  private fun startPeriodicAutoCheckLoop() {
    viewModelScope.launch {
      while (isActive) {
        val lastChecked = preferences.getLastCheckedAtMillis()
        val now = System.currentTimeMillis()
        val elapsed = if (lastChecked > 0L) (now - lastChecked).coerceAtLeast(0L) else 0L
        val waitMillis = if (lastChecked <= 0L) {
          UpdateConfig.AUTO_CHECK_INTERVAL_MILLIS
        } else {
          (UpdateConfig.AUTO_CHECK_INTERVAL_MILLIS - elapsed).coerceAtLeast(60_000L)
        }
        delay(waitMillis)
        if (preferences.isAutoCheckDue()) {
          performCheck(forceNetwork = false, isSilentBackground = true)
        }
      }
    }
  }

  private fun performCheck(forceNetwork: Boolean, isSilentBackground: Boolean) {
    if (_uiState.value.isChecking) return
    viewModelScope.launch {
      _uiState.update { it.copy(isChecking = true, installerLaunchError = null) }
      val result = repository.checkForUpdates(forceNetwork = forceNetwork)
      val installed = ApkInstaller.getInstalledVersionInfo(appContext)
      val hasPermission = ApkInstaller.canRequestPackageInstalls(appContext)
      val updatedLastChecked = preferences.getLastCheckedAtMillis()

      var nextDownloadState = _uiState.value.downloadState
      var showPrompt = false

      if (result is UpdateCheckResult.UpdateAvailable) {
        val manifest = result.manifest
        val existingVerified = downloader.findVerifiedExistingApk(manifest)
        if (existingVerified != null) {
          nextDownloadState = DownloadState.VerifiedReadyToInstall(
            manifest = manifest,
            apkFile = existingVerified,
            totalBytes = existingVerified.length()
          )
        } else if (nextDownloadState is DownloadState.VerifiedReadyToInstall &&
          nextDownloadState.manifest.versionCode != manifest.versionCode
        ) {
          nextDownloadState = DownloadState.Idle
        }

        if (!result.isMandatory) {
          val dismissedVersion = preferences.getDismissedOptionalVersionCode()
          showPrompt = !isSilentBackground || manifest.versionCode > dismissedVersion
        }
      } else if (result is UpdateCheckResult.UpToDate) {
        nextDownloadState = DownloadState.Idle
        downloader.cleanupObsoleteApks(installed.versionCode)
      }

      _uiState.update {
        it.copy(
          installedVersionCode = installed.versionCode,
          installedVersionName = installed.versionName,
          lastCheckedAtMs = updatedLastChecked,
          isChecking = false,
          checkResult = result,
          downloadState = nextDownloadState,
          hasInstallPermission = hasPermission,
          showOptionalPrompt = showPrompt
        )
      }
    }
  }

  fun dismissOptionalPrompt() {
    val manifest = _uiState.value.availableManifest
    if (manifest != null) {
      preferences.dismissOptionalVersion(manifest.versionCode)
    }
    _uiState.update { it.copy(showOptionalPrompt = false) }
  }

  fun startDownload(autoInstallWhenReady: Boolean = false) {
    val manifest = _uiState.value.availableManifest ?: return
    val currentDownload = _uiState.value.downloadState
    if (currentDownload is DownloadState.Downloading || currentDownload is DownloadState.Verifying) return

    downloadJob?.cancel()
    downloadJob = viewModelScope.launch {
      _uiState.update {
        it.copy(
          downloadState = DownloadState.Downloading(0L, manifest.apkSizeBytes ?: -1L, 0),
          installerLaunchError = null
        )
      }

      val result = downloader.downloadAndVerify(
        manifest = manifest,
        onProgress = { bytes, total, percent ->
          _uiState.update {
            it.copy(downloadState = DownloadState.Downloading(bytes, total, percent))
          }
        },
        onVerifying = { totalBytes ->
          _uiState.update {
            it.copy(downloadState = DownloadState.Verifying(totalBytes))
          }
        }
      )

      if (result.isSuccess) {
        val apkFile = result.getOrThrow()
        val hasPerm = ApkInstaller.canRequestPackageInstalls(appContext)
        _uiState.update {
          it.copy(
            downloadState = DownloadState.VerifiedReadyToInstall(
              manifest = manifest,
              apkFile = apkFile,
              totalBytes = apkFile.length()
            ),
            hasInstallPermission = hasPerm
          )
        }
        if (autoInstallWhenReady) {
          installVerifiedUpdate()
        }
      } else {
        val msg = result.exceptionOrNull()?.message ?: "Download failed. Please retry."
        _uiState.update {
          it.copy(downloadState = DownloadState.Failed(msg))
        }
      }
    }
  }

  fun cancelDownload() {
    downloadJob?.cancel()
    downloadJob = null
    _uiState.update {
      it.copy(downloadState = DownloadState.Idle, installerLaunchError = null)
    }
  }

  fun installVerifiedUpdate() {
    val ready = _uiState.value.downloadState as? DownloadState.VerifiedReadyToInstall ?: return
    val verifiedFile = downloader.findVerifiedExistingApk(ready.manifest)
    if (verifiedFile == null) {
      _uiState.update {
        it.copy(
          downloadState = DownloadState.Failed("Downloaded APK is missing or failed security verification. Please download again."),
          installerLaunchError = null
        )
      }
      return
    }

    val hasPerm = ApkInstaller.canRequestPackageInstalls(appContext)
    if (!hasPerm) {
      _uiState.update {
        it.copy(
          hasInstallPermission = false,
          installerLaunchError = "Enable \"Allow from this source\" in Android Settings, then return and tap Install Update.",
          awaitingInstallReturn = true
        )
      }
      ApkInstaller.openInstallUnknownAppsSettings(appContext)
      return
    }

    val launchResult = ApkInstaller.launchPackageInstaller(appContext, verifiedFile)
    if (launchResult.isSuccess) {
      _uiState.update {
        it.copy(
          hasInstallPermission = true,
          installerLaunchError = null,
          awaitingInstallReturn = true
        )
      }
    } else {
      _uiState.update {
        it.copy(
          installerLaunchError = launchResult.exceptionOrNull()?.localizedMessage
            ?: "Unable to open Android Package Installer."
        )
      }
    }
  }

  fun openUnknownSourcesSettings() {
    _uiState.update { it.copy(awaitingInstallReturn = true, installerLaunchError = null) }
    ApkInstaller.openInstallUnknownAppsSettings(appContext)
  }

  /**
   * Re-checks installed versionCode and unknown-app install permission after returning from
   * Android Package Installer or Settings. Never assumes an install succeeded just because
   * the installer activity was opened.
   */
  fun refreshInstalledVersionAndPermission() {
    val installed = ApkInstaller.getInstalledVersionInfo(appContext)
    val hasPermission = ApkInstaller.canRequestPackageInstalls(appContext)
    downloader.cleanupObsoleteApks(installed.versionCode)

    val currentResult = _uiState.value.checkResult
    val updatedResult = if (currentResult is UpdateCheckResult.UpdateAvailable &&
      installed.versionCode >= currentResult.manifest.versionCode
    ) {
      UpdateCheckResult.UpToDate(
        installedVersionCode = installed.versionCode,
        installedVersionName = installed.versionName,
        latestManifest = currentResult.manifest
      )
    } else {
      currentResult
    }

    val updatedDownload = if (updatedResult is UpdateCheckResult.UpToDate) {
      DownloadState.Idle
    } else {
      _uiState.value.downloadState
    }

    _uiState.update {
      it.copy(
        installedVersionCode = installed.versionCode,
        installedVersionName = installed.versionName,
        hasInstallPermission = hasPermission,
        checkResult = updatedResult,
        downloadState = updatedDownload,
        awaitingInstallReturn = false,
        showOptionalPrompt = if (updatedResult is UpdateCheckResult.UpToDate) false else it.showOptionalPrompt
      )
    }
  }
}
