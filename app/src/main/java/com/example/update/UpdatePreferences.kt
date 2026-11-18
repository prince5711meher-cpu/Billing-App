package com.example.update

import android.content.Context
import android.content.SharedPreferences

/**
 * Isolated preferences store for update check timestamps, cached valid manifests,
 * and dismissed optional update versions. Never modifies Firebase/business data.
 */
class UpdatePreferences(context: Context) {

  private val prefs: SharedPreferences = context.applicationContext
    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getLastCheckedAtMillis(): Long = prefs.getLong(KEY_LAST_CHECKED_AT_MS, 0L)

  fun getCachedManifestJson(): String? = prefs.getString(KEY_CACHED_MANIFEST_JSON, null)

  fun saveValidManifestCheck(rawJson: String, checkedAtMillis: Long) {
    prefs.edit()
      .putString(KEY_CACHED_MANIFEST_JSON, rawJson)
      .putLong(KEY_LAST_CHECKED_AT_MS, checkedAtMillis)
      .apply()
  }

  fun isAutoCheckDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val last = getLastCheckedAtMillis()
    if (last <= 0L) return true
    return (nowMillis - last) >= UpdateConfig.AUTO_CHECK_INTERVAL_MILLIS
  }

  fun getDismissedOptionalVersionCode(): Long =
    prefs.getLong(KEY_DISMISSED_OPTIONAL_VERSION_CODE, -1L)

  fun dismissOptionalVersion(versionCode: Long) {
    prefs.edit()
      .putLong(KEY_DISMISSED_OPTIONAL_VERSION_CODE, versionCode)
      .apply()
  }

  fun clearDismissedOptionalVersion() {
    prefs.edit().remove(KEY_DISMISSED_OPTIONAL_VERSION_CODE).apply()
  }

  companion object {
    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_LAST_CHECKED_AT_MS = "last_checked_at_ms"
    private const val KEY_CACHED_MANIFEST_JSON = "cached_manifest_json"
    private const val KEY_DISMISSED_OPTIONAL_VERSION_CODE = "dismissed_optional_version_code"
  }
}
