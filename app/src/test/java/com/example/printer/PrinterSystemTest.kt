package com.example.printer

import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BusinessSettings
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrinterSystemTest {

  private class FakePrinterTransport : PrinterTransport {
    override val transportType = PrinterTransportType.BLUETOOTH_CLASSIC_SPP
    private val _state = MutableStateFlow<PrinterConnectionState>(PrinterConnectionState.Disconnected)
    override val connectionState: StateFlow<PrinterConnectionState> = _state

    var permissionGranted = true
    var bluetoothEnabled = true
    var failWriteMessage: String? = null
    var paperWarning: String? = null
    val sentPayloads = mutableListOf<ByteArray>()
    var paired = emptyList<BluetoothPrinterDevice>()

    override fun hasRequiredPermission(): Boolean = permissionGranted
    override fun isBluetoothEnabled(): Boolean = bluetoothEnabled
    override fun isBluetoothSupported(): Boolean = true

    override suspend fun getPairedDevices(): PairedDevicesResult {
      if (!permissionGranted) return PairedDevicesResult.PermissionRequired
      if (!bluetoothEnabled) return PairedDevicesResult.BluetoothDisabled
      return PairedDevicesResult.Success(paired)
    }

    override suspend fun connect(device: BluetoothPrinterDevice): PrinterConnectionState {
      val next = PrinterConnectionState.Connected(device.name, device.address)
      _state.value = next
      return next
    }

    override suspend fun disconnect() {
      _state.value = PrinterConnectionState.Disconnected
    }

    override suspend fun writeReceiptBytes(data: ByteArray): TransportPrintResult {
      val current = _state.value
      if (current !is PrinterConnectionState.Connected) {
        return TransportPrintResult.Failed("Printer is not connected", disconnected = true)
      }
      failWriteMessage?.let { msg ->
        return TransportPrintResult.Failed(msg, disconnected = true)
      }
      sentPayloads.add(data)
      return TransportPrintResult.Sent(
        deviceName = current.deviceName,
        bytesWritten = data.size,
        paperStatusWarning = paperWarning
      )
    }

    override fun release() {}
  }

  @Test
  fun defaultPaperSizeIsThreeInchesAndCustomValidationRejectsOutOfBounds() {
    val defaults = PrinterSettings()
    assertEquals(PaperSizePreset.THREE_INCH, defaults.paperSizePreset)
    val spec = defaults.computeLayoutSpec()
    assertEquals(76.2f, spec.selectedRollWidthMm, 0.01f)
    assertEquals(72.0f, spec.effectivePrintableWidthMm, 0.01f)
    assertEquals(576, spec.dotsPerLine)
    assertEquals(48, spec.columnsPerLine)
    assertFalse(spec.exceedsPrinterWidth)
    assertNull(spec.widthWarning)

    assertTrue(PrinterSettings.validateCustomWidth("") is WidthValidationResult.Invalid)
    assertTrue(PrinterSettings.validateCustomWidth("abc") is WidthValidationResult.Invalid)
    assertTrue(PrinterSettings.validateCustomWidth("39.9") is WidthValidationResult.Invalid)
    assertTrue(PrinterSettings.validateCustomWidth("108.1") is WidthValidationResult.Invalid)

    val validMin = PrinterSettings.validateCustomWidth("40")
    assertEquals(WidthValidationResult.Valid(40.0f), validMin)
    val validMax = PrinterSettings.validateCustomWidth("108")
    assertEquals(WidthValidationResult.Valid(108.0f), validMax)
  }

  @Test
  fun selectingLargerPaperThanPrinterSpecShowsWarningAndSafelyFitsColumns() {
    val settings = PrinterSettings(
      paperSizePreset = PaperSizePreset.THREE_INCH,
      printerMaxWidthOption = PrinterMaxWidthOption.MM_48
    )
    val spec = settings.computeLayoutSpec()
    assertTrue(spec.exceedsPrinterWidth)
    assertNotNull(spec.widthWarning)
    assertEquals(48.0f, spec.effectivePrintableWidthMm, 0.01f)
    assertEquals(384, spec.dotsPerLine)
    assertEquals(32, spec.columnsPerLine)
  }

  @Test
  fun escPosCommandBuilderAndTestReceiptContainExpectedCommandsAndLines() {
    val settings = PrinterSettings(
      paperSizePreset = PaperSizePreset.TWO_INCH,
      boldText = false
    )
    val testReceipt = ReceiptFormatter.buildTestReceipt(
      printerSettings = settings,
      timestampMillis = 1_700_000_000_000L
    )
    val bytes = ReceiptFormatter.toEscPosBytes(testReceipt, settings)
    // Must start with ESC @ (0x1B, 0x40) followed by ESC ! 00 (0x1B, 0x21, 0x00)
    assertEquals(0x1B.toByte(), bytes[0])
    assertEquals(0x40.toByte(), bytes[1])
    assertEquals(0x1B.toByte(), bytes[2])
    assertEquals(0x21.toByte(), bytes[3])
    assertEquals(0x00.toByte(), bytes[4])

    // Default autoCutPaper = false omits GS V cut command (safe for portable tear-bar printers)
    assertFalse(containsSequence(bytes, byteArrayOf(0x1D, 0x56)))

    val withCutBytes = ReceiptFormatter.toEscPosBytes(
      testReceipt,
      settings.copy(autoCutPaper = true)
    )
    assertTrue(containsSequence(withCutBytes, byteArrayOf(0x1D, 0x56, 0x42, 0x00)))

    val ascii = String(bytes, StandardCharsets.US_ASCII)
    assertTrue(ascii.contains("MEHER BASAN BHANDAR"))
    assertTrue(ascii.contains("PRINTER TEST"))
    assertTrue(ascii.contains("Brand: POSIFLOW"))
    assertTrue(ascii.contains("Connection: Bluetooth"))
    assertTrue(ascii.contains("Paper Width: 2 inches (50.8mm)"))
    assertTrue(ascii.contains("Printing: Test"))
    assertTrue(ascii.contains("TEST PRINT SUCCESSFUL"))
  }

  private fun containsSequence(haystack: ByteArray, needle: ByteArray): Boolean {
    if (needle.isEmpty() || haystack.size < needle.size) return false
    for (i in 0..(haystack.size - needle.size)) {
      var match = true
      for (j in needle.indices) {
        if (haystack[i + j] != needle[j]) {
          match = false
          break
        }
      }
      if (match) return true
    }
    return false
  }

  @Test
  fun invoiceReceiptWrapsLongProductNamesAcrossAllPaperSizesWithoutCropping() {
    val invoice = Invoice(
      id = "inv-1",
      invoiceNumber = "BILL-0042",
      customerName = "Ramesh Kumar",
      subtotalMinor = 125_000L, // Rs. 1,250.00
      discountMinor = 5_000L,   // Rs. 50.00
      grandTotalMinor = 120_000L,
      amountPaidMinor = 100_000L,
      balanceDueMinor = 20_000L,
      paymentMethod = "UPI",
      items = listOf(
        InvoiceItem(
          description = "Heavy Duty Stainless Steel Dinner Thali Set Deluxe Mirror Finish",
          quantity = 2.0,
          unitLabel = "sets",
          rateMinor = 62_500L,
          lineTotalMinor = 125_000L
        )
      )
    )
    val business = BusinessSettings(
      businessName = "Meher Basan Bhandar",
      address = "Main Market, Bolangir",
      phone = "+91 98765 43210",
      termsAndConditions = "Goods once sold are returnable in 7 days."
    )

    val presetsToTest = listOf(
      PrinterSettings(paperSizePreset = PaperSizePreset.TWO_INCH),
      PrinterSettings(paperSizePreset = PaperSizePreset.THREE_INCH),
      PrinterSettings(paperSizePreset = PaperSizePreset.FOUR_INCH),
      PrinterSettings(paperSizePreset = PaperSizePreset.CUSTOM, customWidthMm = 58f)
    )

    for (presetSettings in presetsToTest) {
      val receipt = ReceiptFormatter.buildInvoiceReceipt(invoice, business, presetSettings)
      val cols = receipt.layoutSpec.columnsPerLine
      val bytes = ReceiptFormatter.toEscPosBytes(receipt, presetSettings)
      val ascii = String(bytes, StandardCharsets.US_ASCII)

      assertTrue(ascii.contains("MEHER BASAN BHANDAR"))
      assertTrue(ascii.contains("BILL-0042"))
      assertTrue(ascii.contains("Rs.1,250.00"))
      assertTrue(ascii.contains("Rs.1,200.00"))
      assertTrue(ascii.contains("Balance Due"))
      assertTrue(ascii.contains("Rs.200.00"))
      assertFalse("Tax lines must stay omitted when zero", ascii.contains("CGST"))

      // Verify wrapped text elements stay within column budget
      receipt.elements.filterIsInstance<ReceiptElement.TextLine>().forEach { line ->
        assertTrue(
          "Line '${line.text}' (${line.text.length}) exceeded $cols columns",
          line.text.length <= cols
        )
      }
    }
  }

  @Test
  fun rasterBitmapModeProducesGsV0CommandsForInvoice() {
    val invoice = Invoice(
      id = "inv-raster",
      invoiceNumber = "BILL-0099",
      subtotalMinor = 50_000L,
      grandTotalMinor = 50_000L,
      amountPaidMinor = 50_000L,
      items = listOf(
        InvoiceItem(
          description = "Brass Diya",
          quantity = 1.0,
          unitLabel = "pcs",
          rateMinor = 50_000L,
          lineTotalMinor = 50_000L
        )
      )
    )
    val settings = PrinterSettings(
      paperSizePreset = PaperSizePreset.TWO_INCH,
      renderMode = PrintRenderMode.RASTER_BITMAP
    )
    val formatted = ReceiptFormatter.buildInvoiceReceipt(
      invoice = invoice,
      businessSettings = BusinessSettings(businessName = "Meher Basan Bhandar"),
      printerSettings = settings
    )
    val rasterBytes = ReceiptBitmapRenderer.renderToEscPosBytes(formatted, settings)

    assertTrue(rasterBytes.size > 500)
    // Look for GS v 0 (0x1D 0x76 0x30 0x00) header
    var foundGsV0 = false
    for (i in 0 until rasterBytes.size - 4) {
      if (
        rasterBytes[i] == 0x1D.toByte() &&
        rasterBytes[i + 1] == 0x76.toByte() &&
        rasterBytes[i + 2] == 0x30.toByte() &&
        rasterBytes[i + 3] == 0x00.toByte()
      ) {
        foundGsV0 = true
        break
      }
    }
    assertTrue("Expected GS v 0 raster graphics command", foundGsV0)
  }

  @Test
  fun repositoryPersistsRememberedPrinterAndPaperSettings() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val repo1 = PrinterSettingsRepository(context)
    val device = BluetoothPrinterDevice(
      name = "POSIFLOW-58",
      address = "00:11:22:33:44:55",
      isPosiflowBrand = true,
      isLikelyPrinter = true
    )
    repo1.rememberPrinter(device)
    repo1.setCustomWidthMm(58.5f)
    repo1.setBoldText(true)
    repo1.setAutoPrintAfterBill(true)
    repo1.setPrintDensity(PrintDensity.DARK)

    val repo2 = PrinterSettingsRepository(context)
    val loaded = repo2.settings.value
    assertEquals("POSIFLOW-58", loaded.rememberedPrinterName)
    assertEquals("00:11:22:33:44:55", loaded.rememberedPrinterAddress)
    assertEquals(PaperSizePreset.CUSTOM, loaded.paperSizePreset)
    assertEquals(58.5f, loaded.customWidthMm, 0.01f)
    assertTrue(loaded.boldText)
    assertTrue(loaded.autoPrintAfterBill)
    assertEquals(PrintDensity.DARK, loaded.printDensity)
  }

  @Test
  fun printerManagerHandlesDisconnectsEmptyBillsAndDuplicateAutoPrints() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val settingsRepo = PrinterSettingsRepository(context)
    val fakeTransport = FakePrinterTransport()
    val manager = PrinterManager(settingsRepo, fakeTransport)

    // 1. Disconnected test print fails gracefully without pretending success
    val disconnectedResult = manager.printTestReceipt()
    assertTrue(disconnectedResult is TransportPrintResult.Failed)
    assertNotNull(manager.printJobState.value.errorMessage)

    // 2. Connect to POSIFLOW device
    val posiflow = BluetoothPrinterDevice(
      name = "POSIFLOW-80BT",
      address = "AA:BB:CC:DD:EE:FF",
      isPosiflowBrand = true,
      isLikelyPrinter = true
    )
    val connState = manager.connect(posiflow)
    assertTrue(connState is PrinterConnectionState.Connected)

    // 3. Empty bill rejected
    val emptyInvoice = Invoice(id = "empty-1", invoiceNumber = "BILL-0001", items = emptyList())
    val emptyResult = manager.printInvoice(emptyInvoice, BusinessSettings())
    assertTrue(emptyResult is TransportPrintResult.Failed)
    assertEquals(0, fakeTransport.sentPayloads.size)

    // 4. Valid auto-print succeeds once and deduplicates repeat auto-print on same bill
    val validInvoice = Invoice(
      id = "inv-auto-1",
      invoiceNumber = "BILL-0002",
      grandTotalMinor = 15_000L,
      amountPaidMinor = 15_000L,
      items = listOf(
        InvoiceItem(
          description = "Steel Spoon",
          quantity = 3.0,
          rateMinor = 5_000L,
          lineTotalMinor = 15_000L
        )
      )
    )
    val autoFirst = manager.printInvoice(validInvoice, BusinessSettings(), isAutoPrint = true)
    assertTrue(autoFirst is TransportPrintResult.Sent)
    assertEquals(1, fakeTransport.sentPayloads.size)

    val autoDuplicate = manager.printInvoice(validInvoice, BusinessSettings(), isAutoPrint = true)
    assertTrue(autoDuplicate is TransportPrintResult.Failed)
    assertEquals(1, fakeTransport.sentPayloads.size)

    // 5. Explicit manual reprint succeeds
    val manualReprint = manager.printInvoice(validInvoice, BusinessSettings(), isAutoPrint = false)
    assertTrue(manualReprint is TransportPrintResult.Sent)
    assertEquals(2, fakeTransport.sentPayloads.size)

    // 6. Disconnected with remembered printer auto-reconnects seamlessly on print
    manager.disconnect()
    assertTrue(manager.connectionState.value is PrinterConnectionState.Disconnected)
    val autoReconnectedPrint = manager.printTestReceipt()
    assertTrue(autoReconnectedPrint is TransportPrintResult.Sent)
    assertTrue(manager.connectionState.value is PrinterConnectionState.Connected)
    assertEquals(3, fakeTransport.sentPayloads.size)
  }

  @Test
  fun plainTextFallbackContainsNoEscOrGsControlBytesAndEndsWithLineFeeds() {
    val settings = PrinterSettings(
      paperSizePreset = PaperSizePreset.THREE_INCH,
      renderMode = PrintRenderMode.PLAIN_TEXT
    )
    val receipt = ReceiptFormatter.buildTestReceipt(settings)
    val bytes = ReceiptFormatter.toPlainTextBytes(receipt)

    assertFalse(bytes.any { it == 0x1B.toByte() || it == 0x1D.toByte() })
    val ascii = String(bytes, StandardCharsets.US_ASCII)
    assertTrue(ascii.contains("MEHER BASAN BHANDAR"))
    assertTrue(ascii.contains("TEST PRINT SUCCESSFUL"))
    assertTrue(ascii.endsWith("\n\n\n\n"))
  }

  @Test
  fun testAndInvoiceReceiptsPlaceLogoHeaderFirstAndDecodeMonochromeAssetAcrossPaperWidths() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val logoProvider = AndroidReceiptLogoProvider(context)

    val widthsToCheck = listOf(
      PrinterSettings(paperSizePreset = PaperSizePreset.TWO_INCH),
      PrinterSettings(paperSizePreset = PaperSizePreset.THREE_INCH),
      PrinterSettings(paperSizePreset = PaperSizePreset.FOUR_INCH),
      PrinterSettings(paperSizePreset = PaperSizePreset.CUSTOM, customWidthMm = 40f),
      PrinterSettings(paperSizePreset = PaperSizePreset.CUSTOM, customWidthMm = 108f)
    )

    val sampleInvoice = Invoice(
      id = "inv-logo-check",
      invoiceNumber = "BILL-1001",
      grandTotalMinor = 25_000L,
      amountPaidMinor = 25_000L,
      items = listOf(
        InvoiceItem(
          description = "Copper Kalash",
          quantity = 1.0,
          unitLabel = "pcs",
          rateMinor = 25_000L,
          lineTotalMinor = 25_000L
        )
      )
    )

    for (settings in widthsToCheck) {
      val testReceipt = ReceiptFormatter.buildTestReceipt(settings)
      assertTrue(
        "Test receipt must start with LogoHeader",
        testReceipt.elements.firstOrNull() is ReceiptElement.LogoHeader
      )
      val firstTestText = testReceipt.elements.drop(1).firstOrNull() as? ReceiptElement.TextLine
      assertNotNull(firstTestText)
      assertTrue(firstTestText!!.text.contains("MEHER BASAN BHANDAR"))

      val invoiceReceipt = ReceiptFormatter.buildInvoiceReceipt(
        invoice = sampleInvoice,
        businessSettings = BusinessSettings(businessName = "Meher Basan Bhandar"),
        printerSettings = settings
      )
      assertTrue(
        "Invoice receipt must start with LogoHeader",
        invoiceReceipt.elements.firstOrNull() is ReceiptElement.LogoHeader
      )

      val dots = invoiceReceipt.layoutSpec.dotsPerLine
      val logoBitmap = logoProvider.getLogoBitmap(dots)
      assertNotNull("Logo bitmap should decode for $dots dots", logoBitmap)
      assertTrue("Logo width ${logoBitmap!!.width} must fit within $dots dots", logoBitmap.width <= dots)
      assertEquals("Logo width must be byte-aligned", 0, logoBitmap.width % 8)

      val escPosBytes = ReceiptFormatter.toEscPosBytes(invoiceReceipt, settings, logoBitmap)
      assertTrue(
        "ESC_POS_TEXT with logo must emit GS v 0 raster command",
        containsSequence(escPosBytes, byteArrayOf(0x1D, 0x76, 0x30, 0x00))
      )
      val escPosAscii = String(escPosBytes, StandardCharsets.US_ASCII)
      assertTrue(escPosAscii.contains("MEHER BASAN BHANDAR"))
      assertTrue(escPosAscii.contains("BILL-1001"))

      val rasterBytes = ReceiptBitmapRenderer.renderToEscPosBytes(invoiceReceipt, settings, logoBitmap)
      assertTrue(
        "RASTER_BITMAP with logo must emit GS v 0 raster command",
        containsSequence(rasterBytes, byteArrayOf(0x1D, 0x76, 0x30, 0x00))
      )
    }
  }

  @Test
  fun plainTextPrintModePrintsLogoWithoutMutatingSavedRenderMode() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val settingsRepo = PrinterSettingsRepository(context)
    settingsRepo.setRenderMode(PrintRenderMode.PLAIN_TEXT)
    assertEquals(PrintRenderMode.PLAIN_TEXT, settingsRepo.settings.value.renderMode)

    val fakeTransport = FakePrinterTransport()
    val logoProvider = AndroidReceiptLogoProvider(context)
    val manager = PrinterManager(
      settingsRepository = settingsRepo,
      transport = fakeTransport,
      logoProvider = logoProvider
    )

    val posiflow = BluetoothPrinterDevice(
      name = "POSIFLOW-80BT",
      address = "AA:BB:CC:DD:EE:11",
      isPosiflowBrand = true,
      isLikelyPrinter = true
    )
    manager.connect(posiflow)

    val result = manager.printTestReceipt()
    assertTrue(result is TransportPrintResult.Sent)
    assertEquals(1, fakeTransport.sentPayloads.size)
    assertTrue(
      "Plain-text print with logo should emit raster GS v 0 payload",
      containsSequence(fakeTransport.sentPayloads.first(), byteArrayOf(0x1D, 0x76, 0x30, 0x00))
    )
    assertEquals(
      "Saved renderMode must stay PLAIN_TEXT",
      PrintRenderMode.PLAIN_TEXT,
      settingsRepo.settings.value.renderMode
    )
  }
}
