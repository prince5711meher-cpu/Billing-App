package com.example.ui.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderLight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UdharColor
import com.example.ui.theme.WarningOrange
import com.example.update.DownloadState
import com.example.update.UpdateCheckResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsUpdateSection(
  uiState: AppUpdateUiState,
  onCheckUpdates: () -> Unit,
  onDownloadUpdate: () -> Unit,
  onCancelDownload: () -> Unit,
  onInstallUpdate: () -> Unit,
  onOpenInstallPermission: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("settings_app_updates_card"),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, BorderLight),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .background(PrimaryBlue.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = PrimaryBlue)
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "App Updates",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Self-hosted APK updater",
              fontSize = 12.sp,
              color = TextSecondary
            )
          }
        }

        UpdateStatusBadge(uiState = uiState)
      }

      Spacer(modifier = Modifier.height(12.dp))
      HorizontalDivider(color = BorderLight)
      Spacer(modifier = Modifier.height(12.dp))

      // Installed vs Latest Version Rows
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text("Installed Version", fontSize = 13.sp, color = TextSecondary)
        Text(
          text = "v${uiState.installedVersionName} (Code ${uiState.installedVersionCode})",
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextPrimary
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      val latestLabel = when (val res = uiState.checkResult) {
        is UpdateCheckResult.UpdateAvailable ->
          "v${res.manifest.versionName} (Code ${res.manifest.versionCode})"
        is UpdateCheckResult.UpToDate ->
          res.latestManifest?.let { "v${it.versionName} (Code ${it.versionCode})" }
            ?: "v${uiState.installedVersionName} (Latest)"
        is UpdateCheckResult.NotConfigured -> "Manifest URL not configured"
        is UpdateCheckResult.Error -> "Unavailable"
        null -> "Not checked yet"
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text("Latest Available", fontSize = 13.sp, color = TextSecondary)
        Text(
          text = latestLabel,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = if (uiState.availableManifest != null) PrimaryBlue else TextPrimary
        )
      }

      if (uiState.lastCheckedAtMs > 0L) {
        Spacer(modifier = Modifier.height(6.dp))
        val formattedTime = remember(uiState.lastCheckedAtMs) {
          SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(uiState.lastCheckedAtMs))
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Last Checked", fontSize = 12.sp, color = TextSecondary)
          Text(formattedTime, fontSize = 12.sp, color = TextSecondary)
        }
      }

      // Status / Error Feedback Box
      when (val res = uiState.checkResult) {
        is UpdateCheckResult.NotConfigured -> {
          Spacer(modifier = Modifier.height(10.dp))
          StatusNoticeBox(
            text = "Development placeholder URL active. Configure UpdateConfig.MANIFEST_URL with your Cloudflare Pages URL to enable live update checks.",
            tint = WarningOrange,
            icon = Icons.Default.ErrorOutline
          )
        }
        is UpdateCheckResult.Error -> {
          Spacer(modifier = Modifier.height(10.dp))
          StatusNoticeBox(
            text = res.message,
            tint = if (res.isOfflineOrNetwork) WarningOrange else UdharColor,
            icon = if (res.isOfflineOrNetwork) Icons.Default.CloudOff else Icons.Default.ErrorOutline
          )
        }
        is UpdateCheckResult.UpToDate -> {
          Spacer(modifier = Modifier.height(10.dp))
          StatusNoticeBox(
            text = "You are on the latest version of Billing App.",
            tint = SuccessGreen,
            icon = Icons.Default.CheckCircle
          )
        }
        else -> Unit
      }

      // Release notes when an update is available
      val manifest = uiState.availableManifest
      if (manifest != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "What's New in v${manifest.versionName}",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (manifest.releaseNotes.isEmpty()) {
          Text("• Bug fixes and stability improvements", fontSize = 12.sp, color = TextSecondary)
        } else {
          manifest.releaseNotes.forEach { note ->
            Text(
              text = "• $note",
              fontSize = 12.sp,
              color = TextSecondary,
              modifier = Modifier.padding(vertical = 2.dp)
            )
          }
        }
      }

      // Download progress / Ready / Error section
      Spacer(modifier = Modifier.height(12.dp))
      DownloadProgressAndErrorBlock(
        downloadState = uiState.downloadState,
        hasInstallPermission = uiState.hasInstallPermission,
        installerLaunchError = uiState.installerLaunchError,
        onCancelDownload = onCancelDownload,
        onOpenInstallPermission = onOpenInstallPermission
      )

      // Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = onCheckUpdates,
          enabled = !uiState.isChecking && uiState.downloadState !is DownloadState.Downloading,
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("check_updates_button"),
          shape = RoundedCornerShape(10.dp)
        ) {
          if (uiState.isChecking) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Checking...", fontSize = 13.sp)
          } else {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Check Updates", fontSize = 13.sp)
          }
        }

        if (manifest != null) {
          when (uiState.downloadState) {
            is DownloadState.VerifiedReadyToInstall -> {
              Button(
                onClick = onInstallUpdate,
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("install_update_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
              ) {
                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Install Update", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
              }
            }
            is DownloadState.Downloading -> Unit
            else -> {
              Button(
                onClick = onDownloadUpdate,
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("download_update_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
              ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download Update", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Installation Instructions & Data Safety Note
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = "Installation Instructions",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
        Text(
          text = "1. Tap Download Update and wait for SHA-256 security verification.",
          fontSize = 11.sp,
          color = TextSecondary
        )
        Text(
          text = "2. If Android asks, enable \"Allow from this source\" for Billing App.",
          fontSize = 11.sp,
          color = TextSecondary
        )
        Text(
          text = "3. Confirm Install in the Android installer. Your login, bills, Udhar, and items stay completely safe.",
          fontSize = 11.sp,
          color = TextSecondary
        )
      }
    }
  }
}

@Composable
fun OptionalUpdateDialog(
  uiState: AppUpdateUiState,
  onDismissLater: () -> Unit,
  onUpdateNow: () -> Unit,
  onCancelDownload: () -> Unit,
  onInstallUpdate: () -> Unit,
  onOpenInstallPermission: () -> Unit
) {
  val manifest = uiState.availableManifest ?: return

  AlertDialog(
    onDismissRequest = {
      if (uiState.downloadState !is DownloadState.Downloading) {
        onDismissLater()
      }
    },
    icon = {
      Icon(
        imageVector = Icons.Default.SystemUpdate,
        contentDescription = null,
        tint = PrimaryBlue,
        modifier = Modifier.size(32.dp)
      )
    },
    title = {
      Text(
        text = "Update Available (v${manifest.versionName})",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "Installed: v${uiState.installedVersionName} (Code ${uiState.installedVersionCode}) → New: v${manifest.versionName} (Code ${manifest.versionCode})",
          fontSize = 12.sp,
          color = TextSecondary
        )

        if (manifest.releaseNotes.isNotEmpty()) {
          Text("What's New:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
          manifest.releaseNotes.forEach { note ->
            Text("• $note", fontSize = 12.sp, color = TextPrimary)
          }
        }

        DownloadProgressAndErrorBlock(
          downloadState = uiState.downloadState,
          hasInstallPermission = uiState.hasInstallPermission,
          installerLaunchError = uiState.installerLaunchError,
          onCancelDownload = onCancelDownload,
          onOpenInstallPermission = onOpenInstallPermission
        )

        Text(
          text = "All your bills, Udhar dues, saved items, and Google login will remain intact.",
          fontSize = 11.sp,
          color = TextSecondary
        )
      }
    },
    confirmButton = {
      when (uiState.downloadState) {
        is DownloadState.VerifiedReadyToInstall -> {
          Button(
            onClick = onInstallUpdate,
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
            modifier = Modifier.testTag("dialog_install_update_button")
          ) {
            Text("Install Update")
          }
        }
        is DownloadState.Downloading -> {
          OutlinedButton(onClick = onCancelDownload) {
            Text("Cancel Download")
          }
        }
        else -> {
          Button(
            onClick = onUpdateNow,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            modifier = Modifier.testTag("dialog_update_now_button")
          ) {
            Text("Update Now")
          }
        }
      }
    },
    dismissButton = {
      if (uiState.downloadState !is DownloadState.Downloading) {
        TextButton(
          onClick = onDismissLater,
          modifier = Modifier.testTag("dialog_update_later_button")
        ) {
          Text("Later")
        }
      }
    }
  )
}

@Composable
fun MandatoryUpdateScreen(
  uiState: AppUpdateUiState,
  onRetryCheck: () -> Unit,
  onUpdateNow: () -> Unit,
  onCancelDownload: () -> Unit,
  onInstallUpdate: () -> Unit,
  onOpenInstallPermission: () -> Unit
) {
  val manifest = uiState.availableManifest ?: return

  Surface(
    modifier = Modifier
      .fillMaxSize()
      .testTag("mandatory_update_screen"),
    color = MaterialTheme.colorScheme.background
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(72.dp)
          .background(PrimaryBlue.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.SystemUpdate,
          contentDescription = "Required App Update",
          tint = PrimaryBlue,
          modifier = Modifier.size(40.dp)
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "Update Required",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = TextPrimary
      )

      Spacer(modifier = Modifier.height(8.dp))

      val reasonText = if (uiState.installedVersionCode < manifest.minSupportedVersionCode) {
        "Version v${uiState.installedVersionName} (Code ${uiState.installedVersionCode}) is no longer supported. Please update to v${manifest.versionName} (Code ${manifest.versionCode}) to continue billing safely."
      } else {
        "A mandatory update to v${manifest.versionName} (Code ${manifest.versionCode}) is required before continuing."
      }

      Text(
        text = reasonText,
        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
      )

      Spacer(modifier = Modifier.height(16.dp))

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderLight)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "Release Notes — v${manifest.versionName}",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
          )
          if (manifest.releaseNotes.isEmpty()) {
            Text("• Critical update & stability fixes", fontSize = 13.sp, color = TextSecondary)
          } else {
            manifest.releaseNotes.forEach { note ->
              Text("• $note", fontSize = 13.sp, color = TextPrimary)
            }
          }

          DownloadProgressAndErrorBlock(
            downloadState = uiState.downloadState,
            hasInstallPermission = uiState.hasInstallPermission,
            installerLaunchError = uiState.installerLaunchError,
            onCancelDownload = onCancelDownload,
            onOpenInstallPermission = onOpenInstallPermission
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      when (uiState.downloadState) {
        is DownloadState.VerifiedReadyToInstall -> {
          Button(
            onClick = onInstallUpdate,
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("mandatory_install_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
          ) {
            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Install Update Now", fontWeight = FontWeight.SemiBold)
          }
        }
        is DownloadState.Downloading -> {
          OutlinedButton(
            onClick = onCancelDownload,
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cancel Download")
          }
        }
        else -> {
          Button(
            onClick = onUpdateNow,
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("mandatory_update_now_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
          ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Update Now", fontWeight = FontWeight.SemiBold)
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedButton(
        onClick = onRetryCheck,
        enabled = !uiState.isChecking && uiState.downloadState !is DownloadState.Downloading,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("mandatory_retry_button"),
        shape = RoundedCornerShape(12.dp)
      ) {
        if (uiState.isChecking) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Checking...")
        } else {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Retry")
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Security, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Your account, bills, Udhar, and settings remain preserved.",
          fontSize = 12.sp,
          color = TextSecondary
        )
      }
    }
  }
}

@Composable
private fun DownloadProgressAndErrorBlock(
  downloadState: DownloadState,
  hasInstallPermission: Boolean,
  installerLaunchError: String?,
  onCancelDownload: () -> Unit,
  onOpenInstallPermission: () -> Unit
) {
  when (downloadState) {
    is DownloadState.Downloading -> {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          val sizeText = if (downloadState.totalBytes > 0L) {
            "${formatMb(downloadState.bytesDownloaded)} / ${formatMb(downloadState.totalBytes)}"
          } else {
            "${formatMb(downloadState.bytesDownloaded)} downloaded"
          }
          Text("Downloading APK ($sizeText)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
          Text(
            text = "${downloadState.progressPercent}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue
          )
        }

        if (downloadState.totalBytes > 0L) {
          LinearProgressIndicator(
            progress = { (downloadState.progressPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp),
            color = PrimaryBlue
          )
        } else {
          LinearProgressIndicator(
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp),
            color = PrimaryBlue
          )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onCancelDownload) {
            Text("Cancel Download", color = WarningOrange, fontSize = 12.sp)
          }
        }
      }
    }

    is DownloadState.VerifiedReadyToInstall -> {
      StatusNoticeBox(
        text = "APK verified (SHA-256 OK). Ready to install v${downloadState.manifest.versionName}.",
        tint = SuccessGreen,
        icon = Icons.Default.CheckCircle
      )
      if (!hasInstallPermission) {
        Spacer(modifier = Modifier.height(8.dp))
        StatusNoticeBox(
          text = "Permission required: Allow Billing App to install unknown apps in Android Settings.",
          tint = WarningOrange,
          icon = Icons.Default.Security
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
          onClick = onOpenInstallPermission,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text("Open Install Permission Settings")
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
    }

    is DownloadState.Failed -> {
      StatusNoticeBox(
        text = downloadState.message,
        tint = UdharColor,
        icon = Icons.Default.ErrorOutline
      )
      Spacer(modifier = Modifier.height(8.dp))
    }

    DownloadState.Idle -> Unit
  }

  if (!installerLaunchError.isNullOrBlank()) {
    StatusNoticeBox(
      text = installerLaunchError,
      tint = WarningOrange,
      icon = Icons.Default.ErrorOutline
    )
    Spacer(modifier = Modifier.height(8.dp))
  }
}

@Composable
private fun UpdateStatusBadge(uiState: AppUpdateUiState) {
  val (label, color) = when {
    uiState.isChecking -> "Checking" to PrimaryBlue
    uiState.downloadState is DownloadState.Downloading -> "Downloading" to PrimaryBlue
    uiState.downloadState is DownloadState.VerifiedReadyToInstall -> "Ready" to SuccessGreen
    uiState.isMandatoryBlocked -> "Required" to UdharColor
    uiState.availableManifest != null -> "Available" to WarningOrange
    uiState.checkResult is UpdateCheckResult.UpToDate -> "Up to date" to SuccessGreen
    uiState.checkResult is UpdateCheckResult.Error -> "Error" to WarningOrange
    uiState.checkResult is UpdateCheckResult.NotConfigured -> "Setup" to WarningOrange
    else -> "Idle" to TextSecondary
  }

  Box(
    modifier = Modifier
      .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = label,
      color = color,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
private fun StatusNoticeBox(
  text: String,
  tint: androidx.compose.ui.graphics.Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(tint.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
      .padding(10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text(text = text, fontSize = 12.sp, color = TextPrimary)
  }
}

private fun formatMb(bytes: Long): String {
  val mb = bytes.toDouble() / (1024.0 * 1024.0)
  return String.format(Locale.getDefault(), "%.1f MB", mb)
}
