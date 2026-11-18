package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import java.io.File

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
          android.content.pm.PackageManager.PackageInfoFlags.of(0L)
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
