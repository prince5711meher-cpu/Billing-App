package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import com.example.BuildConfig
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateSystemTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val validApkBytes = "fake-signed-apk-payload-for-test".toByteArray()
  private val validSha256 = sha256Hex(validApkBytes)
  private val validApkUrl =
    "https://github.com/prince5711meher-cpu/meher-basan-updates/releases/download/v1.0.2/meher-basan-v2.apk"
  private val validManifestUrl =
    "https://prince5711meher-cpu.github.io/meher-basan-updates/update.json"

  private fun buildManifestJson(
    versionCode: Long = 2L,
    versionName: String = "1.0",
    minSupportedVersionCode: Long = 1L,
    releaseNotesJson: String = "\"First public release. Includes app updates and improvements.\"",
    apkUrl: String = validApkUrl,
    sha256: String = validSha256,
    mandatory: Boolean = false
  ): String = """
    {
      "versionCode": $versionCode,
      "versionName": "$versionName",
      "minSupportedVersionCode": $minSupportedVersionCode,
      "releaseNotes": $releaseNotesJson,
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
  fun config_preservesPackageIdAndProductionManifestUrl() {
    assertEquals("com.aistudio.meherbasan.xqzlk", BuildConfig.APPLICATION_ID)
    assertEquals(2, BuildConfig.VERSION_CODE)
    assertEquals(validManifestUrl, UpdateConfig.MANIFEST_URL)
    assertFalse(UpdateConfig.isPlaceholderManifestUrl())
    assertTrue(UpdateValidator.validateManifestEndpointUrl(UpdateConfig.MANIFEST_URL))
  }

  @Test
  fun parseAndValidateManifest_supportsStringAndArrayNotesAndRejectsInvalidInputs() {
    // Exact manifest format from server (String releaseNotes + uppercase sha256)
    val serverJson = buildManifestJson(
      versionCode = 2L,
      versionName = "1.0",
      minSupportedVersionCode = 1L,
      releaseNotesJson = "\"First public release. Includes app updates and improvements.\"",
      apkUrl = validApkUrl,
      sha256 = "C0C34DDDF9FCA08D4B37C8A43D497BEAEFF191202523B3F7137E325833130DA1",
      mandatory = false
    )
    val parsedServer = UpdateValidator.parseAndValidateManifest(serverJson)
    assertTrue(parsedServer.isSuccess)
    val manifest = parsedServer.getOrThrow()
    assertEquals(2L, manifest.versionCode)
    assertEquals("1.0", manifest.versionName)
    assertEquals(listOf("First public release. Includes app updates and improvements."), manifest.releaseNotes)
    assertEquals("c0c34dddf9fca08d4b37c8a43d497beaeff191202523b3f7137e325833130da1", manifest.sha256)

    // Array releaseNotes also supported
    val arrayNotes = UpdateValidator.parseAndValidateManifest(
      buildManifestJson(releaseNotesJson = "[\"Improved billing\", \"Fixed invoice issues\"]")
    )
    assertTrue(arrayNotes.isSuccess)
    assertEquals(2, arrayNotes.getOrThrow().releaseNotes.size)

    // Non-HTTPS URL rejected
    assertTrue(
      UpdateValidator.parseAndValidateManifest(
        buildManifestJson(apkUrl = "http://github.com/a/b/releases/download/v1/app.apk")
      ).isFailure
    )

    // Unsupported host rejected
    assertTrue(
      UpdateValidator.parseAndValidateManifest(
        buildManifestJson(apkUrl = "https://evil.example.com/app-release.apk")
      ).isFailure
    )

    // Malformed SHA-256 rejected
    assertTrue(
      UpdateValidator.parseAndValidateManifest(
        buildManifestJson(sha256 = "NOT_A_SHA256")
      ).isFailure
    )

    // minSupportedVersionCode > versionCode rejected
    assertTrue(
      UpdateValidator.parseAndValidateManifest(
        buildManifestJson(versionCode = 2, minSupportedVersionCode = 3)
      ).isFailure
    )

    // Wrong field types (string versionCode, numeric releaseNotes, malformed JSON) rejected
    assertTrue(
      UpdateValidator.parseAndValidateManifest(
        """{"versionCode":"2","versionName":"1.0","minSupportedVersionCode":1,"releaseNotes":"ok","apkUrl":"$validApkUrl","sha256":"$validSha256","mandatory":false}"""
      ).isFailure
    )
    assertTrue(
      UpdateValidator.parseAndValidateManifest(
        buildManifestJson(releaseNotesJson = "12345")
      ).isFailure
    )
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
      installedVersionProvider = { InstalledVersionInfo(2L, "1.0") }
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
      installedVersionProvider = { InstalledVersionInfo(2L, "1.0") }
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
      installedVersionProvider = { InstalledVersionInfo(2L, "1.0") }
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
  fun repository_usesCacheWithin12HoursAndHandlesOfflineAndHttpErrorsGracefully() = runBlocking {
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

    // HTTP 404 server error -> returns Error without crashing
    val http404Repo = UpdateRepository(
      context = context,
      preferences = prefs,
      httpClient = fakeHttpClient { req -> jsonResponse(req, "Not Found", code = 404) },
      manifestUrl = validManifestUrl,
      installedVersionProvider = { InstalledVersionInfo(1L, "1.0") },
      timeProvider = { currentNow }
    )
    val httpErrRes = http404Repo.checkForUpdates(forceNetwork = true)
    assertTrue(httpErrRes is UpdateCheckResult.Error)
  }

  @Test
  fun downloader_verifiesSha256AndCertificateDeletesCorruptAndRejectsUntrustedRedirectAndLowStorage() = runBlocking {
    val dir = tempFolder.newFolder("apk_updates")
    val manifest = UpdateValidator.parseAndValidateManifest(buildManifestJson(versionCode = 2)).getOrThrow()
    var verifyingCalled = false

    // 1. Successful download + checksum + certificate verification callback
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
      availableBytesProvider = { 500L * 1024L * 1024L },
      apkVerifier = { _, _ -> Result.success(Unit) }
    )
    val okRes = okDownloader.downloadAndVerify(
      manifest = manifest,
      onProgress = { _, _, _ -> },
      onVerifying = { verifyingCalled = true }
    )
    assertTrue(okRes.isSuccess)
    assertTrue(verifyingCalled)
    val apkFile = okRes.getOrThrow()
    assertTrue(apkFile.exists())
    assertEquals(validSha256, okDownloader.computeSha256(apkFile))

    // 2. Checksum mismatch -> deletes corrupted .part file without leaving target APK
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
    val badHashRes = badHashDownloader.downloadAndVerify(manifest, onProgress = { _, _, _ -> })
    assertTrue(badHashRes.isFailure)
    assertTrue(badHashRes.exceptionOrNull() is SecurityException)
    assertNull(badHashDownloader.findVerifiedExistingApk(manifest))
    assertFalse(File(dir, "update_v2.apk.part").exists())

    // 3. Invalid signing certificate -> rejected and deleted even if SHA-256 matches manifest
    val badCertDownloader = ApkDownloader(
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
      availableBytesProvider = { 500L * 1024L * 1024L },
      apkVerifier = { _, _ -> Result.failure(SecurityException("Unexpected signing certificate")) }
    )
    val badCertRes = badCertDownloader.downloadAndVerify(manifest, onProgress = { _, _, _ -> })
    assertTrue(badCertRes.isFailure)
    assertTrue(badCertRes.exceptionOrNull() is SecurityException)
    assertFalse(File(dir, "update_v2.apk").exists())
    assertFalse(File(dir, "update_v2.apk.part").exists())

    // 4. Disallowed redirect host blocked
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
    val redirectRes = redirectDownloader.downloadAndVerify(manifest, onProgress = { _, _, _ -> })
    assertTrue(redirectRes.isFailure)

    // 5. Insufficient storage rejected
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
    val lowSpaceRes = lowSpaceDownloader.downloadAndVerify(manifest, onProgress = { _, _, _ -> })
    assertTrue(lowSpaceRes.isFailure)
  }

  @Test
  @Suppress("DEPRECATION")
  fun installer_validatesPackageIdVersionCodeAndTrustedSigningCertificate() {
    val trustedCertBytes = "trusted-meher-basan-cert-der".toByteArray()
    val trustedCertSha256 = sha256Hex(trustedCertBytes)
    val wrongCertBytes = "attacker-self-signed-cert".toByteArray()
    val manifest = UpdateValidator.parseAndValidateManifest(buildManifestJson(versionCode = 3)).getOrThrow()

    fun makePackageInfo(pkg: String, code: Long, certBytes: ByteArray): PackageInfo {
      return PackageInfo().apply {
        packageName = pkg
        longVersionCode = code
        signatures = arrayOf(Signature(certBytes))
      }
    }

    // Valid package, newer versionCode, matching cert passes
    ApkInstaller.verifyPackageInfo(
      packageInfo = makePackageInfo("com.aistudio.meherbasan.xqzlk", 3L, trustedCertBytes),
      expectedPackageName = "com.aistudio.meherbasan.xqzlk",
      manifest = manifest,
      installedVersionCode = 2L,
      trustedCertSha256 = trustedCertSha256
    )

    // Wrong package ID fails
    var failedWrongPkg = false
    try {
      ApkInstaller.verifyPackageInfo(
        packageInfo = makePackageInfo("com.other.fakeapp", 3L, trustedCertBytes),
        expectedPackageName = "com.aistudio.meherbasan.xqzlk",
        manifest = manifest,
        installedVersionCode = 2L,
        trustedCertSha256 = trustedCertSha256
      )
    } catch (_: SecurityException) {
      failedWrongPkg = true
    }
    assertTrue(failedWrongPkg)

    // Wrong versionCode fails
    var failedWrongCode = false
    try {
      ApkInstaller.verifyPackageInfo(
        packageInfo = makePackageInfo("com.aistudio.meherbasan.xqzlk", 2L, trustedCertBytes),
        expectedPackageName = "com.aistudio.meherbasan.xqzlk",
        manifest = manifest,
        installedVersionCode = 2L,
        trustedCertSha256 = trustedCertSha256
      )
    } catch (_: SecurityException) {
      failedWrongCode = true
    }
    assertTrue(failedWrongCode)

    // Wrong signing certificate fails
    var failedWrongCert = false
    try {
      ApkInstaller.verifyPackageInfo(
        packageInfo = makePackageInfo("com.aistudio.meherbasan.xqzlk", 3L, wrongCertBytes),
        expectedPackageName = "com.aistudio.meherbasan.xqzlk",
        manifest = manifest,
        installedVersionCode = 2L,
        trustedCertSha256 = trustedCertSha256
      )
    } catch (_: SecurityException) {
      failedWrongCert = true
    }
    assertTrue(failedWrongCert)
  }

  @Test
  fun installer_failsCleanlyWhenApkMissingAndLaunchesInstallerWhenValid() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val missingFile = File(tempFolder.root, "does_not_exist.apk")
    val missingResult = ApkInstaller.launchPackageInstaller(context, missingFile)
    assertTrue(missingResult.isFailure)

    // Place valid dummy file in configured FileProvider path (`filesDir/apk_updates`)
    val apkDir = File(context.filesDir, UpdateConfig.DOWNLOAD_DIRECTORY_NAME).apply { mkdirs() }
    val apkFile = File(apkDir, "update_v2.apk").apply { writeBytes(validApkBytes) }
    val shadowPm = Shadows.shadowOf(context.packageManager)
    shadowPm.setCanRequestPackageInstalls(true)

    val launchRes = ApkInstaller.launchPackageInstaller(context, apkFile)
    assertTrue(launchRes.isSuccess)

    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val nextIntent = Shadows.shadowOf(app).nextStartedActivity
    assertEquals(Intent.ACTION_VIEW, nextIntent.action)
    assertEquals("application/vnd.android.package-archive", nextIntent.type)
    assertEquals("content", nextIntent.data?.scheme)
    assertTrue((nextIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
  }

  private fun sha256Hex(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256")
      .digest(bytes)
      .joinToString("") { "%02x".format(it) }
  }
}
