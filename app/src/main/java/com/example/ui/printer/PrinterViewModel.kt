package com.example.ui.printer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BusinessSettings
import com.example.data.model.Invoice
import com.example.printer.BluetoothPrinterDevice
import com.example.printer.PairedDevicesResult
import com.example.printer.PaperSizePreset
import com.example.printer.PrintDensity
import com.example.printer.PrintJobState
import com.example.printer.PrintRenderMode
import com.example.printer.PrinterConnectionState
import com.example.printer.PrinterManager
import com.example.printer.PrinterMaxWidthOption
import com.example.printer.PrinterSettings
import com.example.printer.ReceiptAlignment
import com.example.printer.ReceiptFontSize
import com.example.printer.ReceiptLayoutSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PrinterViewModel(application: Application) : AndroidViewModel(application) {

  private val manager = PrinterManager(application)

  val printerSettings: StateFlow<PrinterSettings> = manager.settingsState
  val connectionState: StateFlow<PrinterConnectionState> = manager.connectionState
  val printJobState: StateFlow<PrintJobState> = manager.printJobState
  val pairedDevicesResult: StateFlow<PairedDevicesResult> = manager.pairedDevicesResult
  val bleScanResult: StateFlow<PairedDevicesResult?> = manager.bleScanResult

  val layoutSpec: StateFlow<ReceiptLayoutSpec> = printerSettings
    .map { it.computeLayoutSpec() }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = printerSettings.value.computeLayoutSpec()
    )

  private val _hasBluetoothPermission = MutableStateFlow(manager.hasBluetoothPermission())
  val hasBluetoothPermission: StateFlow<Boolean> = _hasBluetoothPermission.asStateFlow()

  private val _isScanningBle = MutableStateFlow(false)
  val isScanningBle: StateFlow<Boolean> = _isScanningBle.asStateFlow()

  init {
    refreshBluetoothState()
  }

  fun hasScanPermission(): Boolean = manager.hasScanPermission()

  fun refreshBluetoothState() {
    val permitted = manager.hasBluetoothPermission()
    _hasBluetoothPermission.value = permitted
    viewModelScope.launch {
      manager.refreshPairedDevices()
    }
  }

  fun onPermissionResult(granted: Boolean) {
    _hasBluetoothPermission.value = granted || manager.hasBluetoothPermission()
    refreshBluetoothState()
  }

  fun scanNearbyBleDevices() {
    if (_isScanningBle.value) return
    viewModelScope.launch {
      _isScanningBle.value = true
      try {
        manager.scanNearbyBleDevices()
      } finally {
        _isScanningBle.value = false
      }
    }
  }

  fun selectPrinter(device: BluetoothPrinterDevice) {
    manager.settingsRepository.rememberPrinter(device)
  }

  fun connectToDevice(device: BluetoothPrinterDevice) {
    viewModelScope.launch {
      manager.connect(device)
    }
  }

  fun connectRememberedPrinter() {
    viewModelScope.launch {
      manager.reconnectRemembered()
    }
  }

  fun disconnectPrinter() {
    viewModelScope.launch {
      manager.disconnect()
    }
  }

  fun setPaperSizePreset(preset: PaperSizePreset) {
    manager.settingsRepository.setPaperSizePreset(preset)
  }

  fun setCustomWidthMm(widthMm: Float) {
    manager.settingsRepository.setCustomWidthMm(widthMm)
  }

  fun setPrinterMaxWidthOption(option: PrinterMaxWidthOption) {
    manager.settingsRepository.setPrinterMaxWidthOption(option)
  }

  fun setPrintDensity(density: PrintDensity) {
    manager.settingsRepository.setPrintDensity(density)
  }

  fun setTextAlignment(alignment: ReceiptAlignment) {
    manager.settingsRepository.setTextAlignment(alignment)
  }

  fun setFontSize(fontSize: ReceiptFontSize) {
    manager.settingsRepository.setFontSize(fontSize)
  }

  fun setBoldText(bold: Boolean) {
    manager.settingsRepository.setBoldText(bold)
  }

  fun setAutoPrintAfterBill(enabled: Boolean) {
    manager.settingsRepository.setAutoPrintAfterBill(enabled)
  }

  fun setAutoCutPaper(enabled: Boolean) {
    manager.settingsRepository.setAutoCutPaper(enabled)
  }

  fun setRenderMode(mode: PrintRenderMode) {
    manager.settingsRepository.setRenderMode(mode)
  }

  fun printTestReceipt() {
    viewModelScope.launch {
      manager.printTestReceipt()
    }
  }

  fun printInvoice(invoice: Invoice, businessSettings: BusinessSettings) {
    viewModelScope.launch {
      manager.printInvoice(invoice = invoice, businessSettings = businessSettings, isAutoPrint = false)
    }
  }

  fun autoPrintBillIfEnabled(invoice: Invoice, businessSettings: BusinessSettings) {
    val s = printerSettings.value
    if (!s.autoPrintAfterBill) return
    val canPrint = connectionState.value is PrinterConnectionState.Connected ||
      s.rememberedPrinterAddress.isNotBlank()
    if (!canPrint) return
    viewModelScope.launch {
      manager.printInvoice(invoice = invoice, businessSettings = businessSettings, isAutoPrint = true)
    }
  }

  fun retryLastFailedPrint() {
    viewModelScope.launch {
      manager.retryLastFailedPrint()
    }
  }

  fun clearPrintStatus() {
    manager.clearPrintStatus()
  }

  override fun onCleared() {
    manager.release()
    super.onCleared()
  }
}
