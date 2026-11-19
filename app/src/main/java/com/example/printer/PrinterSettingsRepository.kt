package com.example.printer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PrinterSettingsRepository(context: Context) {

  private val prefs: SharedPreferences = context.applicationContext
    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _settings = MutableStateFlow(loadSettings())
  val settings: StateFlow<PrinterSettings> = _settings.asStateFlow()

  private fun loadSettings(): PrinterSettings {
    val presetName = prefs.getString(KEY_PAPER_PRESET, PaperSizePreset.THREE_INCH.name)
    val preset = runCatching { PaperSizePreset.valueOf(presetName ?: "") }
      .getOrDefault(PaperSizePreset.THREE_INCH)

    val maxWidthName = prefs.getString(KEY_PRINTER_MAX_WIDTH, PrinterMaxWidthOption.AUTO.name)
    val maxWidth = runCatching { PrinterMaxWidthOption.valueOf(maxWidthName ?: "") }
      .getOrDefault(PrinterMaxWidthOption.AUTO)

    val densityName = prefs.getString(KEY_DENSITY, PrintDensity.NORMAL.name)
    val density = runCatching { PrintDensity.valueOf(densityName ?: "") }
      .getOrDefault(PrintDensity.NORMAL)

    val alignmentName = prefs.getString(KEY_ALIGNMENT, ReceiptAlignment.CENTER.name)
    val alignment = runCatching { ReceiptAlignment.valueOf(alignmentName ?: "") }
      .getOrDefault(ReceiptAlignment.CENTER)

    val fontSizeName = prefs.getString(KEY_FONT_SIZE, ReceiptFontSize.NORMAL.name)
    val fontSize = runCatching { ReceiptFontSize.valueOf(fontSizeName ?: "") }
      .getOrDefault(ReceiptFontSize.NORMAL)

    val renderModeName = prefs.getString(KEY_RENDER_MODE, PrintRenderMode.ESC_POS_TEXT.name)
    val renderMode = runCatching { PrintRenderMode.valueOf(renderModeName ?: "") }
      .getOrDefault(PrintRenderMode.ESC_POS_TEXT)

    val transportName = prefs.getString(KEY_TRANSPORT_TYPE, PrinterTransportType.BLUETOOTH_CLASSIC_SPP.name)
    val transportType = runCatching { PrinterTransportType.valueOf(transportName ?: "") }
      .getOrDefault(PrinterTransportType.BLUETOOTH_CLASSIC_SPP)

    val customMm = prefs.getFloat(KEY_CUSTOM_WIDTH_MM, 76.2f)
      .takeIf { !it.isNaN() && !it.isInfinite() }
      ?.coerceIn(PrinterSettings.MIN_CUSTOM_WIDTH_MM, PrinterSettings.MAX_CUSTOM_WIDTH_MM)
      ?: 76.2f

    return PrinterSettings(
      rememberedPrinterName = prefs.getString(KEY_PRINTER_NAME, "") ?: "",
      rememberedPrinterAddress = prefs.getString(KEY_PRINTER_ADDRESS, "") ?: "",
      transportType = transportType,
      paperSizePreset = preset,
      customWidthMm = customMm,
      printerMaxWidthOption = maxWidth,
      printDensity = density,
      textAlignment = alignment,
      fontSize = fontSize,
      boldText = prefs.getBoolean(KEY_BOLD_TEXT, false),
      autoPrintAfterBill = prefs.getBoolean(KEY_AUTO_PRINT, false),
      renderMode = renderMode
    )
  }

  private inline fun updateAndPersist(transform: (PrinterSettings) -> PrinterSettings) {
    _settings.update { current ->
      val updated = transform(current)
      saveSettings(updated)
      updated
    }
  }

  private fun saveSettings(s: PrinterSettings) {
    prefs.edit()
      .putString(KEY_PRINTER_NAME, s.rememberedPrinterName)
      .putString(KEY_PRINTER_ADDRESS, s.rememberedPrinterAddress)
      .putString(KEY_TRANSPORT_TYPE, s.transportType.name)
      .putString(KEY_PAPER_PRESET, s.paperSizePreset.name)
      .putFloat(KEY_CUSTOM_WIDTH_MM, s.customWidthMm)
      .putString(KEY_PRINTER_MAX_WIDTH, s.printerMaxWidthOption.name)
      .putString(KEY_DENSITY, s.printDensity.name)
      .putString(KEY_ALIGNMENT, s.textAlignment.name)
      .putString(KEY_FONT_SIZE, s.fontSize.name)
      .putBoolean(KEY_BOLD_TEXT, s.boldText)
      .putBoolean(KEY_AUTO_PRINT, s.autoPrintAfterBill)
      .putString(KEY_RENDER_MODE, s.renderMode.name)
      .apply()
  }

  fun rememberPrinter(device: BluetoothPrinterDevice) {
    updateAndPersist {
      it.copy(
        rememberedPrinterName = device.name,
        rememberedPrinterAddress = device.address,
        transportType = device.transportType
      )
    }
  }

  fun clearRememberedPrinter() {
    updateAndPersist {
      it.copy(
        rememberedPrinterName = "",
        rememberedPrinterAddress = ""
      )
    }
  }

  fun setPaperSizePreset(preset: PaperSizePreset) {
    updateAndPersist { current ->
      val nextCustom = if (preset == PaperSizePreset.CUSTOM) {
        current.customWidthMm.coerceIn(
          PrinterSettings.MIN_CUSTOM_WIDTH_MM,
          PrinterSettings.MAX_CUSTOM_WIDTH_MM
        )
      } else {
        current.customWidthMm
      }
      current.copy(paperSizePreset = preset, customWidthMm = nextCustom)
    }
  }

  fun setCustomWidthMm(widthMm: Float) {
    val clamped = widthMm.coerceIn(
      PrinterSettings.MIN_CUSTOM_WIDTH_MM,
      PrinterSettings.MAX_CUSTOM_WIDTH_MM
    )
    updateAndPersist {
      it.copy(
        paperSizePreset = PaperSizePreset.CUSTOM,
        customWidthMm = clamped
      )
    }
  }

  fun setPrinterMaxWidthOption(option: PrinterMaxWidthOption) {
    updateAndPersist { it.copy(printerMaxWidthOption = option) }
  }

  fun setPrintDensity(density: PrintDensity) {
    updateAndPersist { it.copy(printDensity = density) }
  }

  fun setTextAlignment(alignment: ReceiptAlignment) {
    updateAndPersist { it.copy(textAlignment = alignment) }
  }

  fun setFontSize(fontSize: ReceiptFontSize) {
    updateAndPersist { it.copy(fontSize = fontSize) }
  }

  fun setBoldText(bold: Boolean) {
    updateAndPersist { it.copy(boldText = bold) }
  }

  fun setAutoPrintAfterBill(enabled: Boolean) {
    updateAndPersist { it.copy(autoPrintAfterBill = enabled) }
  }

  fun setRenderMode(mode: PrintRenderMode) {
    updateAndPersist { it.copy(renderMode = mode) }
  }

  companion object {
    private const val PREFS_NAME = "meher_basan_printer_prefs"
    private const val KEY_PRINTER_NAME = "printer_name"
    private const val KEY_PRINTER_ADDRESS = "printer_address"
    private const val KEY_TRANSPORT_TYPE = "transport_type"
    private const val KEY_PAPER_PRESET = "paper_preset"
    private const val KEY_CUSTOM_WIDTH_MM = "custom_width_mm"
    private const val KEY_PRINTER_MAX_WIDTH = "printer_max_width"
    private const val KEY_DENSITY = "print_density"
    private const val KEY_ALIGNMENT = "text_alignment"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_BOLD_TEXT = "bold_text"
    private const val KEY_AUTO_PRINT = "auto_print_after_bill"
    private const val KEY_RENDER_MODE = "render_mode"
  }
}
