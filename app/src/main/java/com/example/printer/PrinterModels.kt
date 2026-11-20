package com.example.printer

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

enum class PrinterTransportType {
  BLUETOOTH_CLASSIC_SPP,
  BLE_GATT // Extensible placeholder for future BLE printers
}

enum class PaperSizePreset(
  val label: String,
  val defaultRollWidthMm: Float,
  val defaultPrintableWidthMm: Float
) {
  TWO_INCH("2 inches (50.8mm)", 50.8f, 48.0f),
  THREE_INCH("3 inches (76.2mm)", 76.2f, 72.0f),
  FOUR_INCH("4 inches (101.6mm)", 101.6f, 96.0f),
  CUSTOM("Custom Width", 76.2f, 72.0f)
}

enum class PrinterMaxWidthOption(
  val label: String,
  val maxPrintableMm: Float?,
  val maxDotsAt203Dpi: Int?
) {
  AUTO("Auto (Use Roll Margin)", null, null),
  MM_48("48mm Printable (2\" Printer)", 48.0f, 384),
  MM_72("72mm Printable (3\" Printer)", 72.0f, 576),
  MM_104("104mm Printable (4\" Printer)", 104.0f, 832)
}

enum class PrintDensity(val label: String, val escPosLevel: Byte) {
  LIGHT("Light", 0xFC.toByte()),
  NORMAL("Normal", 0x00.toByte()),
  DARK("Dark", 0x04.toByte())
}

enum class ReceiptAlignment(val label: String, val escPosCode: Int) {
  LEFT("Left", 0),
  CENTER("Center", 1),
  RIGHT("Right", 2)
}

enum class ReceiptFontSize(val label: String) {
  SMALL("Small (Compact)"),
  NORMAL("Normal"),
  LARGE("Large (Tall)")
}

enum class PrintRenderMode(val label: String, val subtitle: String) {
  ESC_POS_TEXT(
    label = "ESC/POS Text",
    subtitle = "Fast native thermal text (uses Rs. for currency)"
  ),
  RASTER_BITMAP(
    label = "Graphics / Raster",
    subtitle = "Supports ₹ symbol & regional Unicode scripts"
  ),
  PLAIN_TEXT(
    label = "Plain Text (Raw)",
    subtitle = "Fallback mode without formatting commands for strict printers"
  )
}

@Immutable
data class ReceiptLayoutSpec(
  val selectedRollWidthMm: Float,
  val desiredPrintableWidthMm: Float,
  val effectivePrintableWidthMm: Float,
  val dotsPerLine: Int,
  val columnsPerLine: Int,
  val exceedsPrinterWidth: Boolean,
  val widthWarning: String?
)

@Immutable
data class PrinterSettings(
  val rememberedPrinterName: String = "",
  val rememberedPrinterAddress: String = "",
  val transportType: PrinterTransportType = PrinterTransportType.BLUETOOTH_CLASSIC_SPP,
  val paperSizePreset: PaperSizePreset = PaperSizePreset.THREE_INCH,
  val customWidthMm: Float = 76.2f,
  val printerMaxWidthOption: PrinterMaxWidthOption = PrinterMaxWidthOption.AUTO,
  val printDensity: PrintDensity = PrintDensity.NORMAL,
  val textAlignment: ReceiptAlignment = ReceiptAlignment.CENTER,
  val fontSize: ReceiptFontSize = ReceiptFontSize.NORMAL,
  val boldText: Boolean = false,
  val autoPrintAfterBill: Boolean = false,
  val autoCutPaper: Boolean = false,
  val renderMode: PrintRenderMode = PrintRenderMode.ESC_POS_TEXT
) {
  companion object {
    const val MIN_CUSTOM_WIDTH_MM = 40.0f
    const val MAX_CUSTOM_WIDTH_MM = 108.0f

    fun validateCustomWidth(rawInput: String): WidthValidationResult {
      val trimmed = rawInput.trim()
      if (trimmed.isEmpty()) {
        return WidthValidationResult.Invalid("Enter width between 40mm and 108mm")
      }
      val parsed = trimmed.toFloatOrNull()
        ?: return WidthValidationResult.Invalid("Enter a valid numeric width (mm)")
      if (parsed.isNaN() || parsed.isInfinite()) {
        return WidthValidationResult.Invalid("Enter a valid numeric width (mm)")
      }
      if (parsed < MIN_CUSTOM_WIDTH_MM || parsed > MAX_CUSTOM_WIDTH_MM) {
        return WidthValidationResult.Invalid(
          "Width must be between ${MIN_CUSTOM_WIDTH_MM.toInt()}mm and ${MAX_CUSTOM_WIDTH_MM.toInt()}mm"
        )
      }
      val rounded = (parsed * 10f).roundToInt() / 10f
      return WidthValidationResult.Valid(rounded)
    }
  }

  val selectedPaperWidthLabel: String
    get() = when (paperSizePreset) {
      PaperSizePreset.TWO_INCH -> "2 inches (50.8mm)"
      PaperSizePreset.THREE_INCH -> "3 inches (76.2mm)"
      PaperSizePreset.FOUR_INCH -> "4 inches (101.6mm)"
      PaperSizePreset.CUSTOM -> {
        val clamped = customWidthMm.coerceIn(MIN_CUSTOM_WIDTH_MM, MAX_CUSTOM_WIDTH_MM)
        val formatted = if (clamped % 1f == 0f) "${clamped.toInt()}mm" else "${(clamped * 10f).roundToInt() / 10f}mm"
        "Custom ($formatted)"
      }
    }

  fun computeLayoutSpec(): ReceiptLayoutSpec {
    val rollMm = when (paperSizePreset) {
      PaperSizePreset.TWO_INCH -> PaperSizePreset.TWO_INCH.defaultRollWidthMm
      PaperSizePreset.THREE_INCH -> PaperSizePreset.THREE_INCH.defaultRollWidthMm
      PaperSizePreset.FOUR_INCH -> PaperSizePreset.FOUR_INCH.defaultRollWidthMm
      PaperSizePreset.CUSTOM -> customWidthMm.coerceIn(MIN_CUSTOM_WIDTH_MM, MAX_CUSTOM_WIDTH_MM)
    }

    val desiredPrintableMm = when (paperSizePreset) {
      PaperSizePreset.TWO_INCH -> PaperSizePreset.TWO_INCH.defaultPrintableWidthMm
      PaperSizePreset.THREE_INCH -> PaperSizePreset.THREE_INCH.defaultPrintableWidthMm
      PaperSizePreset.FOUR_INCH -> PaperSizePreset.FOUR_INCH.defaultPrintableWidthMm
      PaperSizePreset.CUSTOM -> {
        val marginMm = (rollMm * 0.06f).coerceIn(2.8f, 6.0f)
        (rollMm - marginMm).coerceIn(35.0f, 104.0f)
      }
    }

    val printerLimitMm = printerMaxWidthOption.maxPrintableMm
    val exceeds = printerLimitMm != null && desiredPrintableMm > (printerLimitMm + 0.4f)
    val effectivePrintableMm = if (printerLimitMm != null) {
      minOf(desiredPrintableMm, printerLimitMm)
    } else {
      desiredPrintableMm
    }

    // Standard thermal printers operate at ~203 DPI (~8 dots/mm), byte-aligned to 8 dots
    val rawDots = (effectivePrintableMm * 8.0f).roundToInt()
    val alignedDots = ((rawDots / 8) * 8).coerceIn(256, 832)

    val charDotWidth = when (fontSize) {
      ReceiptFontSize.SMALL -> 9
      ReceiptFontSize.NORMAL -> 12
      ReceiptFontSize.LARGE -> 12 // Double-height keeps 12-dot width for readable columns
    }
    val columns = (alignedDots / charDotWidth).coerceIn(24, 90)

    val warning = if (exceeds && printerLimitMm != null) {
      "Selected layout (${formatMm(rollMm)}) exceeds printer's ${formatMm(printerLimitMm)} printable area. Fitted to ${formatMm(effectivePrintableMm)} ($columns cols) to avoid cropping."
    } else null

    return ReceiptLayoutSpec(
      selectedRollWidthMm = rollMm,
      desiredPrintableWidthMm = desiredPrintableMm,
      effectivePrintableWidthMm = effectivePrintableMm,
      dotsPerLine = alignedDots,
      columnsPerLine = columns,
      exceedsPrinterWidth = exceeds,
      widthWarning = warning
    )
  }

  private fun formatMm(mm: Float): String {
    val rounded = (mm * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) "${rounded.toInt()}mm" else "${rounded}mm"
  }
}

sealed interface WidthValidationResult {
  data class Valid(val widthMm: Float) : WidthValidationResult
  data class Invalid(val error: String) : WidthValidationResult
}

@Immutable
data class BluetoothPrinterDevice(
  val name: String,
  val address: String,
  val isPosiflowBrand: Boolean,
  val isLikelyPrinter: Boolean,
  val transportType: PrinterTransportType = PrinterTransportType.BLUETOOTH_CLASSIC_SPP,
  val deviceTypeLabel: String = ""
)

sealed interface PrinterConnectionState {
  data object Disconnected : PrinterConnectionState
  data class Connecting(val deviceName: String, val deviceAddress: String) : PrinterConnectionState
  data class Connected(
    val deviceName: String,
    val deviceAddress: String,
    val protocolLabel: String = "Bluetooth SPP"
  ) : PrinterConnectionState
  data class Error(val message: String, val deviceName: String? = null) : PrinterConnectionState
}

@Immutable
data class PrintJobState(
  val isPrinting: Boolean = false,
  val activeJobKey: String? = null,
  val lastPrintedKey: String? = null,
  val statusMessage: String? = null,
  val errorMessage: String? = null,
  val paperStatusWarning: String? = null
) {
  fun isSuccessFor(jobKey: String): Boolean =
    !isPrinting && errorMessage == null && lastPrintedKey == jobKey && statusMessage != null
}
