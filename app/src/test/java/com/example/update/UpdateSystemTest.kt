package com.example.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class UpdateSystemTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val validApkBytes = "fake-signed-apk-payload-for-test".toByteArray()
  private val validSha256 = sha256Hex(validApkBytes)
  private val validApkUrl = "https://github.com/example-owner/example-repo/releases/download/v1.1/app-release.apk"
  private val validManifestUrl = "https://updates.example.pages.dev/update.json"

  private fun buildManifestJson(
    versionCode: Long = 2L,
    versionName: String = "1.1",
    minSupportedVersionCode: Long = 1L,
    apkUrl: String = validApkUrl,
    sha256: String = validSha256,
    mandatory: Boolean = false
  ): String = """
    {
      "versionCode": $versionCode,
      "versionName": "$versionName",
      "minSupportedVersionCode": $minSupportedVersionCode,
      "releaseNotes": [
        "Improved billing",
        "Fixed invoice issues"
      ],
      "apkUrl": "$apkUrl",
      "sha256": "$sha256",
      "mandatory": $mandatory
    }
  """.trimIndent()

  private fun fakeHttpClient(handler: (okhttp3.Request) -> Response): OkHttpClient {
    return OkHttpClient.Builder()
      .addInterceptor(Interceptor { chain -> handler(chain.request()) })
      .build()
  }

  private fun jsonResponse(request: okhttp3.Request, body: String, code: Int = 200): Response {
    return Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(code)
      .message(if (code == 200) "OK" else "Error")
      .body(body.toResponseBody("application/json".toMediaType()))
      .build()
  }

  @Test
  fun parseAndValidateManifest_rejectsInvalidUrlsHostsHashesAndVersions() {
    // Non-HTTPS URL
    assertTrue(
      UpdateValidator.parseAndValidateManifest(
        buildManifestJson(apkUrl = "http://github.com/a/b/releases/download/v1/app.apk")
      ).isFailure
    )

    // Unsupported host
    assertTrue(
      UpdateValidator.parseAndValidateManifest(
        buildManifestJson(apkUrl = "https://evil.example.com/app-release.apk")
      ).isFailure
    )

    // Malformed SHA-256
    assertTrue(
      UpdateValidator.parseAndValidateManifest(
        buildManifestJson(sha256 = "NOT_A_SHA256")
      ).isFailure
    )

    // minSupportedVersionCode > versionCode
    assertTrue(
      UpdateValidator.parseAndValidateManifest(
        buildManifestJson(versionCode = 2, minSupportedVersionCode = 3)
      ).isFailure
    )

    // Malformed JSON
    assertTrue(
      UpdateValidator.parseAndValidateManifest("{ broken json").isFailure
    )
  }

  @Test
  fun repository_noUpdateAvailableAndNeverSuggestsDowngrade() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = UpdatePreferences(context)
    val repoSame = UpdateRepository(
      context = context,
      preferences = prefs,
      httpClient = fakeHttpClient { req -> jsonResponse(req, buildManifestJson(versionCode = 2)) },
      manifestUrl = validManifestUrl,
      installedVersionProvider = { InstalledVersionInfo(2L, "1.1") }
    )

    val sameResult = repoSame.checkForUpdates(forceNetwork = true)
    assertTrue(sameResult is UpdateCheckResult.UpToDate)

    // Downgrade attempt: remote versionCode 1 < installed 2 (even with mandatory=true)
    val repoDowngrade = UpdateRepository(
      context = context,
      preferences = prefs,
      httpClient = fakeHttpClient { req ->
        jsonResponse(req, buildManifestJson(versionCode = 1, mandatory = true))
      },
      manifestUrl = validManifestUrl,
      installedVersionProvider = { InstalledVersionInfo(2L, "1.1") }
    )
    val downgradeResult = repoDowngrade.checkForUpdates(forceNetwork = true)
    assertTrue(downgradeResult is UpdateCheckResult.UpToDate)
  }

  @Test
  fun repository_detectsOptionalAndMandatoryAndMinSupportedUpdates() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = UpdatePreferences(context)

    val optionalRepo = UpdateRepository(
      context = context,
      preferences = prefs,
      httpClient = fakeHttpClient { req ->
        jsonResponse(req, buildManifestJson(versionCode = 3, minSupportedVersionCode = 2, mandatory = false))
      },
      manifestUrl = validManifestUrl,
      installedVersionProvider = { InstalledVersionInfo(2L, "1.1") }
    )
    val optionalRes = optionalRepo.checkForUpdates(forceNetwork = true) as UpdateCheckResult.UpdateAvailable
    assertFalse(optionalRes.isMandatory)

    // Installed versionCode 1 < minSupportedVersionCode 2 -> becomes mandatory even if mandatory=false
    val minSupportedRepo = UpdateRepository(
      context = context,
      preferences = prefs,
      httpClient = fakeHttpClient { req ->
        jsonResponse(req, buildManifestJson(versionCode = 3, minSupportedVersionCode = 2, mandatory = false))
      },
      manifestUrl = validManifestUrl,
      installedVersionProvider = { InstalledVersionInfo(1L, "1.0") }
    )
    val forcedRes = minSupportedRepo.checkForUpdates(forceNetwork = true) as UpdateCheckResult.UpdateAvailable
    assertTrue(forcedRes.isMandatory)
  }

  @Test
  fun repository_usesCacheWithin12HoursAndHandlesOfflineGracefully() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = UpdatePreferences(context)
    var networkHits = 0
    var currentNow = 1_000_000_000L

    val repo = UpdateRepository(
      context = context,
      preferences = prefs,
      httpClient = fakeHttpClient { req ->
        networkHits++
        jsonResponse(req, buildManifestJson(versionCode = 2, mandatory = false))
      },
      manifestUrl = validManifestUrl,
      installedVersionProvider = { InstalledVersionInfo(1L, "1.0") },
      timeProvider = { currentNow }
    )

    val first = repo.checkForUpdates(forceNetwork = false) as UpdateCheckResult.UpdateAvailable
    assertFalse(first.fromCache)
    assertEquals(1, networkHits)

    // Within 12 hours -> reads cached manifest without hitting network
    currentNow += 60_000L
    val cached = repo.checkForUpdates(forceNetwork = false) as UpdateCheckResult.UpdateAvailable
    assertTrue(cached.fromCache)
    assertEquals(1, networkHits)

    // Offline server after 12 hours -> returns Error (does NOT lock user out)
    currentNow += UpdateConfig.AUTO_CHECK_INTERVAL_MILLIS + 1000L
    val offlineRepo = UpdateRepository(
      context = context,
      preferences = prefs,
      httpClient = fakeHttpClient { throw IOException("No network") },
      manifestUrl = validManifestUrl,
      installedVersionProvider = { InstalledVersionInfo(1L, "1.0") },
      timeProvider = { currentNow }
    )
    val offlineRes = offlineRepo.checkForUpdates(forceNetwork = false)
    assertTrue(offlineRes is UpdateCheckResult.Error)
    assertTrue((offlineRes as UpdateCheckResult.Error).isOfflineOrNetwork)
  }

  @Test
  fun downloader_verifiesSha256DeletesCorruptAndRejectsUntrustedRedirectAndLowStorage() = runBlocking {
    val dir = tempFolder.newFolder("apk_updates")
    val manifest = UpdateValidator.parseAndValidateManifest(buildManifestJson()).getOrThrow()

    // 1. Successful download + checksum verification
    val okDownloader = ApkDownloader(
      downloadDir = dir,
      baseClient = fakeHttpClient { req ->
        Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(validApkBytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
          .build()
      },
      availableBytesProvider = { 500L * 1024L * 1024L }
    )
    val okRes = okDownloader.downloadAndVerify(manifest) { _, _, _ -> }
    assertTrue(okRes.isSuccess)
    val apkFile = okRes.getOrThrow()
    assertTrue(apkFile.exists())
    assertEquals(validSha256, okDownloader.computeSha256(apkFile))

    // 2. Checksum mismatch -> deletes corrupted file
    apkFile.delete()
    val badHashDownloader = ApkDownloader(
      downloadDir = dir,
      baseClient = fakeHttpClient { req ->
        Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body("tampered-apk-bytes".toByteArray().toResponseBody("application/vnd.android.package-archive".toMediaType()))
          .build()
      },
      availableBytesProvider = { 500L * 1024L * 1024L }
    )
    val badHashRes = badHashDownloader.downloadAndVerify(manifest) { _, _, _ -> }
    assertTrue(badHashRes.isFailure)
    assertTrue(badHashRes.exceptionOrNull() is SecurityException)
    assertNull(badHashDownloader.findVerifiedExistingApk(manifest))
    assertFalse(File(dir, "update_v2.apk.part").exists())

    // 3. Disallowed redirect host blocked
    val redirectDownloader = ApkDownloader(
      downloadDir = dir,
      baseClient = fakeHttpClient { req ->
        Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(302)
          .message("Found")
          .header("Location", "https://malicious.example.com/evil.apk")
          .body(ByteArray(0).toResponseBody(null))
          .build()
      },
      availableBytesProvider = { 500L * 1024L * 1024L }
    )
    val redirectRes = redirectDownloader.downloadAndVerify(manifest) { _, _, _ -> }
    assertTrue(redirectRes.isFailure)

    // 4. Insufficient storage rejected
    val lowSpaceDownloader = ApkDownloader(
      downloadDir = dir,
      baseClient = fakeHttpClient { req ->
        Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(validApkBytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
          .build()
      },
      availableBytesProvider = { 1024L } // 1 KB available
    )
    val lowSpaceRes = lowSpaceDownloader.downloadAndVerify(manifest) { _, _, _ -> }
    assertTrue(lowSpaceRes.isFailure)
  }

  @Test
  fun installer_failsCleanlyWhenApkMissing() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val missingFile = File(tempFolder.root, "does_not_exist.apk")
    val result = ApkInstaller.launchPackageInstaller(context, missingFile)
    assertTrue(result.isFailure)
  }

  private fun sha256Hex(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256")
      .digest(bytes)
      .joinToString("") { "%02x".format(it) }
  }
}
