package com.example.printer

import android.content.Context
import com.example.data.model.BusinessSettings
import com.example.data.model.Invoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrinterManager(
  val settingsRepository: PrinterSettingsRepository,
  val transport: PrinterTransport,
  private val logoProvider: ReceiptLogoProvider? = null
) {
  constructor(context: Context) : this(
    settingsRepository = PrinterSettingsRepository(context),
    transport = BluetoothClassicSppService(context),
    logoProvider = AndroidReceiptLogoProvider(context.applicationContext)
  )

  private val printRepository = ReceiptPrintRepository(transport, logoProvider)

  val settingsState: StateFlow<PrinterSettings> = settingsRepository.settings
  val connectionState: StateFlow<PrinterConnectionState> = transport.connectionState
  val printJobState: StateFlow<PrintJobState> = printRepository.printJobState

  private val _pairedDevicesResult = MutableStateFlow<PairedDevicesResult>(
    PairedDevicesResult.Success(emptyList())
  )
  val pairedDevicesResult: StateFlow<PairedDevicesResult> = _pairedDevicesResult.asStateFlow()

  private val _bleScanResult = MutableStateFlow<PairedDevicesResult?>(null)
  val bleScanResult: StateFlow<PairedDevicesResult?> = _bleScanResult.asStateFlow()

  fun hasBluetoothPermission(): Boolean = transport.hasRequiredPermission()

  fun hasScanPermission(): Boolean = transport.hasScanPermission()

  fun isBluetoothSupported(): Boolean = transport.isBluetoothSupported()

  fun isBluetoothEnabled(): Boolean = transport.isBluetoothEnabled()

  suspend fun refreshPairedDevices(): PairedDevicesResult {
    val result = transport.getPairedDevices()
    _pairedDevicesResult.value = result
    return result
  }

  suspend fun scanNearbyBleDevices(): PairedDevicesResult {
    val result = transport.scanNearbyBleDevices()
    _bleScanResult.value = result
    return result
  }

  suspend fun connect(device: BluetoothPrinterDevice): PrinterConnectionState {
    settingsRepository.rememberPrinter(device)
    val state = transport.connect(device)
    if (state is PrinterConnectionState.Connected) {
      val actualTransport = if (state.protocolLabel.startsWith("BLE", ignoreCase = true)) {
        PrinterTransportType.BLE_GATT
      } else {
        PrinterTransportType.BLUETOOTH_CLASSIC_SPP
      }
      if (actualTransport != device.transportType) {
        settingsRepository.rememberPrinter(device.copy(transportType = actualTransport))
      }
    }
    return state
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
    val state = transport.connect(device)
    if (state is PrinterConnectionState.Connected) {
      val actualTransport = if (state.protocolLabel.startsWith("BLE", ignoreCase = true)) {
        PrinterTransportType.BLE_GATT
      } else {
        PrinterTransportType.BLUETOOTH_CLASSIC_SPP
      }
      if (actualTransport != current.transportType) {
        settingsRepository.rememberPrinter(device.copy(transportType = actualTransport))
      }
    }
    return state
  }

  suspend fun disconnect() {
    transport.disconnect()
  }

  /**
   * Automatically reconnects to the saved printer if disconnected so one-tap Print / Auto-Print
   * still works after app restart or idle timeouts.
   */
  private suspend fun ensureConnectedIfRemembered() {
    if (connectionState.value is PrinterConnectionState.Connected) return
    if (settingsState.value.rememberedPrinterAddress.isNotBlank()) {
      reconnectRemembered()
    }
  }

  suspend fun printTestReceipt(): TransportPrintResult {
    ensureConnectedIfRemembered()
    return printRepository.printTestReceipt(settingsState.value)
  }

  suspend fun printInvoice(
    invoice: Invoice,
    businessSettings: BusinessSettings,
    isAutoPrint: Boolean = false
  ): TransportPrintResult {
    ensureConnectedIfRemembered()
    return printRepository.printInvoice(
      invoice = invoice,
      businessSettings = businessSettings,
      printerSettings = settingsState.value,
      isAutoPrint = isAutoPrint
    )
  }

  suspend fun retryLastFailedPrint(): TransportPrintResult? {
    ensureConnectedIfRemembered()
    return printRepository.retryLastFailedPrint()
  }

  fun clearPrintStatus() {
    printRepository.clearPrintStatus()
  }

  fun release() {
    transport.release()
  }
}
