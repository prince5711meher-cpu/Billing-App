package com.example.printer

import com.example.data.model.BusinessSettings
import com.example.data.model.Invoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReceiptPrintRepository(
  private val transport: PrinterTransport,
  private val logoProvider: ReceiptLogoProvider? = null
) {
  private val printMutex = Mutex()
  private val _printJobState = MutableStateFlow(PrintJobState())
  val printJobState: StateFlow<PrintJobState> = _printJobState.asStateFlow()

  private var lastAutoPrintedInvoiceKey: String? = null
  private var lastFailedRequest: PendingPrintPayload? = null

  private data class PendingPrintPayload(
    val jobKey: String,
    val receipt: FormattedReceipt,
    val settings: PrinterSettings
  )

  suspend fun printTestReceipt(settings: PrinterSettings): TransportPrintResult {
    val receipt = ReceiptFormatter.buildTestReceipt(
      printerSettings = settings,
      printerBrand = "POSIFLOW",
      connectionLabel = "Bluetooth"
    )
    return executePrint(
      jobKey = "TEST_${System.currentTimeMillis()}",
      receipt = receipt,
      settings = settings
    )
  }

  suspend fun printInvoice(
    invoice: Invoice,
    businessSettings: BusinessSettings,
    printerSettings: PrinterSettings,
    isAutoPrint: Boolean = false
  ): TransportPrintResult {
    if (invoice.items.isEmpty()) {
      val err = TransportPrintResult.Failed("Cannot print an empty bill with no items.")
      _printJobState.value = PrintJobState(
        isPrinting = false,
        errorMessage = err.message
      )
      return err
    }

    val jobKey = "INVOICE_${invoice.id.ifBlank { invoice.invoiceNumber }}"

    if (isAutoPrint && lastAutoPrintedInvoiceKey == jobKey) {
      return TransportPrintResult.Failed("Auto-print already submitted for ${invoice.invoiceNumber}.")
    }

    // Prevent accidental duplicate concurrent print when already printing same job
    val current = _printJobState.value
    if (current.isPrinting && current.activeJobKey == jobKey) {
      return TransportPrintResult.Failed("Already printing ${invoice.invoiceNumber}...")
    }

    if (isAutoPrint) {
      lastAutoPrintedInvoiceKey = jobKey
    }

    val receipt = ReceiptFormatter.buildInvoiceReceipt(
      invoice = invoice,
      businessSettings = businessSettings,
      printerSettings = printerSettings
    )
    return executePrint(
      jobKey = jobKey,
      receipt = receipt,
      settings = printerSettings
    )
  }

  suspend fun retryLastFailedPrint(): TransportPrintResult? {
    val pending = lastFailedRequest ?: return null
    return executePrint(
      jobKey = pending.jobKey,
      receipt = pending.receipt,
      settings = pending.settings
    )
  }

  fun clearPrintStatus() {
    _printJobState.value = PrintJobState()
  }

  private suspend fun executePrint(
    jobKey: String,
    receipt: FormattedReceipt,
    settings: PrinterSettings
  ): TransportPrintResult = printMutex.withLock {
    _printJobState.value = PrintJobState(
      isPrinting = true,
      activeJobKey = jobKey,
      lastPrintedKey = _printJobState.value.lastPrintedKey,
      statusMessage = "Formatting & sending to printer..."
    )

    val payloadBytes = try {
      val hasLogoHeader = receipt.elements.any { it is ReceiptElement.LogoHeader }
      val logoBitmap = if (hasLogoHeader) {
        runCatching { logoProvider?.getLogoBitmap(receipt.layoutSpec.dotsPerLine) }.getOrNull()
      } else {
        null
      }
      when (settings.renderMode) {
        PrintRenderMode.ESC_POS_TEXT ->
          ReceiptFormatter.toEscPosBytes(receipt, settings, logoBitmap)
        PrintRenderMode.RASTER_BITMAP ->
          ReceiptBitmapRenderer.renderToEscPosBytes(receipt, settings, logoBitmap)
        PrintRenderMode.PLAIN_TEXT -> {
          if (logoBitmap != null) {
            // Print via ESC/POS raster path so monochrome logo still prints without changing saved settings
            ReceiptBitmapRenderer.renderToEscPosBytes(receipt, settings, logoBitmap)
          } else {
            ReceiptFormatter.toPlainTextBytes(receipt)
          }
        }
      }
    } catch (e: Exception) {
      lastFailedRequest = PendingPrintPayload(jobKey, receipt, settings)
      val msg = "Failed to prepare receipt: ${e.localizedMessage ?: "formatting error"}"
      _printJobState.value = PrintJobState(
        isPrinting = false,
        errorMessage = msg
      )
      return@withLock TransportPrintResult.Failed(msg)
    }

    val result = transport.writeReceiptBytes(payloadBytes)
    when (result) {
      is TransportPrintResult.Sent -> {
        lastFailedRequest = null
        val warning = result.paperStatusWarning ?: receipt.layoutSpec.widthWarning
        val baseStatus = if (result.paperStatusWarning != null) {
          "Sent to ${result.deviceName}, but ${result.paperStatusWarning}"
        } else {
          "Sent ${receipt.title} (${result.bytesWritten} B) to ${result.deviceName}. Check printer output."
        }
        _printJobState.value = PrintJobState(
          isPrinting = false,
          lastPrintedKey = jobKey,
          statusMessage = baseStatus,
          errorMessage = null,
          paperStatusWarning = warning
        )
      }
      is TransportPrintResult.Failed -> {
        lastFailedRequest = PendingPrintPayload(jobKey, receipt, settings)
        val connErrorMsg = if (result.disconnected) {
          (transport.connectionState.value as? PrinterConnectionState.Error)?.message
        } else null
        _printJobState.value = PrintJobState(
          isPrinting = false,
          lastPrintedKey = _printJobState.value.lastPrintedKey,
          statusMessage = null,
          errorMessage = connErrorMsg ?: result.message,
          paperStatusWarning = receipt.layoutSpec.widthWarning
        )
      }
    }
    return@withLock result
  }
}
