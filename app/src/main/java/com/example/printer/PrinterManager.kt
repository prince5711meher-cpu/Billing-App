package com.example.printer

import android.content.Context
import com.example.data.model.BusinessSettings
import com.example.data.model.Invoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrinterManager(
  val settingsRepository: PrinterSettingsRepository,
  val transport: PrinterTransport
) {
  constructor(context: Context) : this(
    settingsRepository = PrinterSettingsRepository(context),
    transport = BluetoothClassicSppService(context)
  )

  private val printRepository = ReceiptPrintRepository(transport)

  val settingsState: StateFlow<PrinterSettings> = settingsRepository.settings
  val connectionState: StateFlow<PrinterConnectionState> = transport.connectionState
  val printJobState: StateFlow<PrintJobState> = printRepository.printJobState

  private val _pairedDevicesResult = MutableStateFlow<PairedDevicesResult>(
    PairedDevicesResult.Success(emptyList())
  )
  val pairedDevicesResult: StateFlow<PairedDevicesResult> = _pairedDevicesResult.asStateFlow()

  fun hasBluetoothPermission(): Boolean = transport.hasRequiredPermission()

  fun isBluetoothSupported(): Boolean = transport.isBluetoothSupported()

  fun isBluetoothEnabled(): Boolean = transport.isBluetoothEnabled()

  suspend fun refreshPairedDevices(): PairedDevicesResult {
    val result = transport.getPairedDevices()
    _pairedDevicesResult.value = result
    return result
  }

  suspend fun connect(device: BluetoothPrinterDevice): PrinterConnectionState {
    settingsRepository.rememberPrinter(device)
    return transport.connect(device)
  }

  suspend fun reconnectRemembered(): PrinterConnectionState? {
    val current = settingsState.value
    if (current.rememberedPrinterAddress.isBlank()) return null
    val device = BluetoothPrinterDevice(
      name = current.rememberedPrinterName.ifBlank { "POSIFLOW Printer" },
      address = current.rememberedPrinterAddress,
      isPosiflowBrand = BluetoothClassicSppService.isPosiflowPrinterName(current.rememberedPrinterName),
      isLikelyPrinter = true,
      transportType = current.transportType
    )
    return transport.connect(device)
  }

  suspend fun disconnect() {
    transport.disconnect()
  }

  suspend fun printTestReceipt(): TransportPrintResult {
    return printRepository.printTestReceipt(settingsState.value)
  }

  suspend fun printInvoice(
    invoice: Invoice,
    businessSettings: BusinessSettings,
    isAutoPrint: Boolean = false
  ): TransportPrintResult {
    return printRepository.printInvoice(
      invoice = invoice,
      businessSettings = businessSettings,
      printerSettings = settingsState.value,
      isAutoPrint = isAutoPrint
    )
  }

  suspend fun retryLastFailedPrint(): TransportPrintResult? {
    return printRepository.retryLastFailedPrint()
  }

  fun clearPrintStatus() {
    printRepository.clearPrintStatus()
  }

  fun release() {
    transport.release()
  }
}
