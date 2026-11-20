package com.example.printer

import android.graphics.Bitmap
import com.example.data.domain.MoneyFormatter
import com.example.data.model.BusinessSettings
import com.example.data.model.Invoice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

sealed interface ReceiptElement {
  data class LogoHeader(
    val contentDescription: String = "Meher Basan Bhandar Logo"
  ) : ReceiptElement

  data class TextLine(
    val text: String,
    val bold: Boolean = false,
    val alignment: ReceiptAlignment = ReceiptAlignment.LEFT,
    val largeHeader: Boolean = false
  ) : ReceiptElement

  data class KeyValueLine(
    val left: String,
    val right: String,
    val bold: Boolean = false
  ) : ReceiptElement

  data class Divider(val doubleLine: Boolean = false) : ReceiptElement
  data object BlankLine : ReceiptElement
}

data class FormattedReceipt(
  val title: String,
  val layoutSpec: ReceiptLayoutSpec,
  val elements: List<ReceiptElement>
)

object ReceiptFormatter {

  fun formatMoney(paise: Long, asciiSafe: Boolean): String {
    val formatted = MoneyFormatter.formatPaise(paise)
    return if (asciiSafe) formatted.replace("₹", "Rs.") else formatted
  }

  fun buildTestReceipt(
    printerSettings: PrinterSettings,
    printerBrand: String = "POSIFLOW",
    connectionLabel: String = "Bluetooth",
    timestampMillis: Long = System.currentTimeMillis(),
    includeLogo: Boolean = true
  ): FormattedReceipt {
    val spec = printerSettings.computeLayoutSpec()
    val cols = spec.columnsPerLine
    val headerAlign = printerSettings.textAlignment
    val dateText = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
      .format(Date(timestampMillis))

    val elements = buildList {
      if (includeLogo) {
        add(ReceiptElement.LogoHeader())
      }
      add(
        ReceiptElement.TextLine(
          text = "MEHER BASAN BHANDAR",
          bold = true,
          alignment = headerAlign,
          largeHeader = cols >= 32
        )
      )
      add(
        ReceiptElement.TextLine(
          text = "PRINTER TEST",
          bold = true,
          alignment = headerAlign
        )
      )
      add(ReceiptElement.Divider(doubleLine = true))
      addAll(wrapText("Brand: $printerBrand", cols).map {
        ReceiptElement.TextLine(it, bold = printerSettings.boldText, alignment = ReceiptAlignment.LEFT)
      })
      addAll(wrapText("Connection: $connectionLabel", cols).map {
        ReceiptElement.TextLine(it, bold = printerSettings.boldText, alignment = ReceiptAlignment.LEFT)
      })
      addAll(wrapText("Paper Width: ${printerSettings.selectedPaperWidthLabel}", cols).map {
        ReceiptElement.TextLine(it, bold = printerSettings.boldText, alignment = ReceiptAlignment.LEFT)
      })
      addAll(wrapText("Printable: ${spec.effectivePrintableWidthMm.toInt()}mm ($cols cols)", cols).map {
        ReceiptElement.TextLine(it, bold = printerSettings.boldText, alignment = ReceiptAlignment.LEFT)
      })
      addAll(wrapText("Printing: Test", cols).map {
        ReceiptElement.TextLine(it, bold = printerSettings.boldText, alignment = ReceiptAlignment.LEFT)
      })
      addAll(wrapText("Time: $dateText", cols).map {
        ReceiptElement.TextLine(it, bold = printerSettings.boldText, alignment = ReceiptAlignment.LEFT)
      })
      add(ReceiptElement.Divider(doubleLine = false))
      addAll(wrapText("TEST PRINT SUCCESSFUL", cols).map {
        ReceiptElement.TextLine(it, bold = true, alignment = headerAlign)
      })
      add(ReceiptElement.Divider(doubleLine = true))
    }

    return FormattedReceipt(
      title = "Printer Test",
      layoutSpec = spec,
      elements = elements
    )
  }

  fun buildInvoiceReceipt(
    invoice: Invoice,
    businessSettings: BusinessSettings,
    printerSettings: PrinterSettings,
    includeLogo: Boolean = true
  ): FormattedReceipt {
    val spec = printerSettings.computeLayoutSpec()
    val cols = spec.columnsPerLine
    val headerAlign = printerSettings.textAlignment
    val asciiSafe = printerSettings.renderMode != PrintRenderMode.RASTER_BITMAP
    val defaultBold = printerSettings.boldText

    val shopName = businessSettings.businessName
      .trim()
      .ifBlank { "MEHER BASAN BHANDAR" }
      .let { if (it == "Billing App") "MEHER BASAN BHANDAR" else it }

    val dateObj = invoice.createdAt?.toDate() ?: Date()
    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(dateObj)

    val elements = mutableListOf<ReceiptElement>()

    if (includeLogo) {
      elements.add(ReceiptElement.LogoHeader())
    }

    // Header
    wrapText(shopName.uppercase(Locale.getDefault()), cols).forEachIndexed { idx, line ->
      elements.add(
        ReceiptElement.TextLine(
          text = line,
          bold = true,
          alignment = headerAlign,
          largeHeader = idx == 0 && cols >= 32
        )
      )
    }

    if (businessSettings.address.isNotBlank()) {
      wrapText(businessSettings.address.trim(), cols).forEach { line ->
        elements.add(ReceiptElement.TextLine(line, bold = defaultBold, alignment = headerAlign))
      }
    }
    if (businessSettings.phone.isNotBlank()) {
      wrapText("Ph: ${businessSettings.phone.trim()}", cols).forEach { line ->
        elements.add(ReceiptElement.TextLine(line, bold = defaultBold, alignment = headerAlign))
      }
    }
    if (businessSettings.gstin.isNotBlank()) {
      wrapText("GSTIN: ${businessSettings.gstin.trim()}", cols).forEach { line ->
        elements.add(ReceiptElement.TextLine(line, bold = defaultBold, alignment = headerAlign))
      }
    }

    elements.add(ReceiptElement.Divider(doubleLine = true))

    val docLabel = if (invoice.documentType == "QUOTATION") "ESTIMATE" else "TAX / CASH BILL"
    elements.add(ReceiptElement.TextLine(docLabel, bold = true, alignment = headerAlign))
    if (invoice.status == "CANCELLED") {
      elements.add(ReceiptElement.TextLine("*** CANCELLED ***", bold = true, alignment = headerAlign))
    }

    elements.add(ReceiptElement.KeyValueLine("Bill No:", invoice.invoiceNumber, bold = true))
    wrapText("Date: $dateStr", cols).forEach {
      elements.add(ReceiptElement.TextLine(it, bold = defaultBold))
    }

    val customerName = invoice.customerName.trim()
    if (customerName.isNotBlank()) {
      wrapText("Customer: $customerName", cols).forEach {
        elements.add(ReceiptElement.TextLine(it, bold = defaultBold))
      }
    }
    if (invoice.customerPhone.isNotBlank()) {
      wrapText("Phone: ${invoice.customerPhone.trim()}", cols).forEach {
        elements.add(ReceiptElement.TextLine(it, bold = defaultBold))
      }
    }

    elements.add(ReceiptElement.Divider(doubleLine = false))
    elements.add(ReceiptElement.KeyValueLine("ITEM / QTY x RATE", "AMOUNT", bold = true))
    elements.add(ReceiptElement.Divider(doubleLine = false))

    // Items - wrap product names cleanly, never truncate amounts
    invoice.items.forEachIndexed { index, item ->
      val prefixName = "${index + 1}. ${item.description.trim()}"
      wrapText(prefixName, cols).forEach { nameLine ->
        elements.add(ReceiptElement.TextLine(nameLine, bold = true))
      }

      val qtyText = formatQty(item.quantity)
      val unitText = item.unitLabel.trim().ifBlank { "pcs" }
      val rateText = formatMoney(item.rateMinor, asciiSafe)
      val lineTotalText = formatMoney(item.lineTotalMinor, asciiSafe)
      val leftSpec = " $qtyText $unitText x $rateText"

      if (leftSpec.length + 1 + lineTotalText.length <= cols) {
        elements.add(ReceiptElement.KeyValueLine(leftSpec, lineTotalText, bold = defaultBold))
      } else {
        wrapText(leftSpec, cols).forEach { wrapped ->
          elements.add(ReceiptElement.TextLine(wrapped, bold = defaultBold))
        }
        elements.add(ReceiptElement.KeyValueLine(" Line Total:", lineTotalText, bold = true))
      }

      if (item.discountMinor > 0L) {
        val discText = "-${formatMoney(item.discountMinor, asciiSafe)}"
        val discLabel = if (item.discountType == "PERCENTAGE" && item.discountValue > 0.0) {
          " Disc (${formatQty(item.discountValue)}%)"
        } else {
          " Item Disc"
        }
        elements.add(ReceiptElement.KeyValueLine(discLabel, discText, bold = defaultBold))
      }
    }

    elements.add(ReceiptElement.Divider(doubleLine = false))

    // Totals
    elements.add(
      ReceiptElement.KeyValueLine(
        "Subtotal",
        formatMoney(invoice.subtotalMinor, asciiSafe),
        bold = defaultBold
      )
    )

    if (invoice.discountMinor > 0L) {
      val discTitle = if (invoice.discountType == "PERCENTAGE" && invoice.discountValue > 0.0) {
        "Discount (${formatQty(invoice.discountValue)}%)"
      } else {
        "Overall Discount"
      }
      elements.add(
        ReceiptElement.KeyValueLine(
          discTitle,
          "-${formatMoney(invoice.discountMinor, asciiSafe)}",
          bold = defaultBold
        )
      )
    }

    if (invoice.cgstMinor > 0L) {
      elements.add(ReceiptElement.KeyValueLine("CGST", formatMoney(invoice.cgstMinor, asciiSafe), bold = defaultBold))
    }
    if (invoice.sgstMinor > 0L) {
      elements.add(ReceiptElement.KeyValueLine("SGST", formatMoney(invoice.sgstMinor, asciiSafe), bold = defaultBold))
    }
    if (invoice.igstMinor > 0L) {
      elements.add(ReceiptElement.KeyValueLine("IGST", formatMoney(invoice.igstMinor, asciiSafe), bold = defaultBold))
    }

    elements.add(ReceiptElement.Divider(doubleLine = true))
    elements.add(
      ReceiptElement.KeyValueLine(
        "GRAND TOTAL",
        formatMoney(invoice.grandTotalMinor, asciiSafe),
        bold = true
      )
    )
    elements.add(ReceiptElement.Divider(doubleLine = true))

    val paymentLabel = when (invoice.paymentMethod.uppercase(Locale.getDefault())) {
      "UDHAR", "CREDIT" -> "Udhar (Pay Later)"
      "UPI" -> "UPI / Online"
      "CARD" -> "Card"
      else -> "Cash"
    }
    elements.add(ReceiptElement.KeyValueLine("Payment Mode", paymentLabel, bold = defaultBold))
    elements.add(
      ReceiptElement.KeyValueLine(
        "Amount Paid",
        formatMoney(invoice.amountPaidMinor, asciiSafe),
        bold = defaultBold
      )
    )
    if (invoice.balanceDueMinor > 0L) {
      elements.add(
        ReceiptElement.KeyValueLine(
          "Balance Due",
          formatMoney(invoice.balanceDueMinor, asciiSafe),
          bold = true
        )
      )
    }

    elements.add(ReceiptElement.Divider(doubleLine = false))

    val terms = businessSettings.termsAndConditions.trim()
    if (terms.isNotBlank()) {
      wrapText(terms, cols).forEach { line ->
        elements.add(ReceiptElement.TextLine(line, bold = defaultBold, alignment = headerAlign))
      }
    }
    if (!terms.contains("thank you", ignoreCase = true)) {
      elements.add(
        ReceiptElement.TextLine(
          text = "Thank you for shopping with us!",
          bold = true,
          alignment = headerAlign
        )
      )
    }

    return FormattedReceipt(
      title = invoice.invoiceNumber.ifBlank { "Bill Receipt" },
      layoutSpec = spec,
      elements = elements
    )
  }

  fun toEscPosBytes(
    receipt: FormattedReceipt,
    printerSettings: PrinterSettings,
    logoBitmap: Bitmap? = null
  ): ByteArray {
    val builder = EscPosCommandBuilder()
      .initialize()
      .setDensity(printerSettings.printDensity)

    val cols = receipt.layoutSpec.columnsPerLine
    val maxDots = receipt.layoutSpec.dotsPerLine

    for (el in receipt.elements) {
      when (el) {
        is ReceiptElement.LogoHeader -> {
          if (logoBitmap != null && !logoBitmap.isRecycled && logoBitmap.width > 0 && logoBitmap.height > 0) {
            val fitted = ReceiptBitmapRenderer.fitBitmapToDots(logoBitmap, maxDots)
            try {
              builder.setAlignment(ReceiptAlignment.CENTER)
              builder.appendRasterBitmap(fitted)
              builder.feedLines(1)
            } finally {
              if (fitted !== logoBitmap) {
                fitted.recycle()
              }
            }
          }
        }
        is ReceiptElement.BlankLine -> {
          builder.writeLine("")
        }
        is ReceiptElement.Divider -> {
          builder.setAlignment(ReceiptAlignment.LEFT)
          builder.setFontSize(printerSettings.fontSize)
          builder.setBold(false)
          val char = if (el.doubleLine) '=' else '-'
          builder.writeLine(char.toString().repeat(cols))
        }
        is ReceiptElement.TextLine -> {
          builder.setAlignment(el.alignment)
          if (el.largeHeader) {
            builder.setDoubleSizeHeader(true)
          } else {
            builder.setFontSize(printerSettings.fontSize)
          }
          builder.setBold(el.bold || printerSettings.boldText)
          builder.writeLine(el.text)
          if (el.largeHeader) {
            builder.setDoubleSizeHeader(false)
          }
        }
        is ReceiptElement.KeyValueLine -> {
          builder.setAlignment(ReceiptAlignment.LEFT)
          builder.setFontSize(printerSettings.fontSize)
          builder.setBold(el.bold || printerSettings.boldText)
          val formattedLines = formatKeyValueLines(el.left, el.right, cols)
          for (line in formattedLines) {
            builder.writeLine(line)
          }
        }
      }
    }

    builder
      .setBold(false)
      .setAlignment(ReceiptAlignment.LEFT)
      .feedLines(4)
    if (printerSettings.autoCutPaper) {
      builder.cutPartial()
    }
    return builder.build()
  }

  fun toPlainTextBytes(receipt: FormattedReceipt): ByteArray {
    val cols = receipt.layoutSpec.columnsPerLine
    val sb = StringBuilder(1024)
    for (el in receipt.elements) {
      when (el) {
        is ReceiptElement.LogoHeader -> Unit
        is ReceiptElement.BlankLine -> sb.append('\n')
        is ReceiptElement.Divider -> {
          val char = if (el.doubleLine) '=' else '-'
          sb.append(char.toString().repeat(cols)).append('\n')
        }
        is ReceiptElement.TextLine -> {
          val clean = EscPosCommandBuilder.sanitizeForEscPosText(el.text)
          val aligned = when (el.alignment) {
            ReceiptAlignment.LEFT -> clean
            ReceiptAlignment.CENTER -> {
              val pad = ((cols - clean.length) / 2).coerceAtLeast(0)
              " ".repeat(pad) + clean
            }
            ReceiptAlignment.RIGHT -> {
              val pad = (cols - clean.length).coerceAtLeast(0)
              " ".repeat(pad) + clean
            }
          }
          sb.append(aligned).append('\n')
        }
        is ReceiptElement.KeyValueLine -> {
          formatKeyValueLines(
            EscPosCommandBuilder.sanitizeForEscPosText(el.left),
            EscPosCommandBuilder.sanitizeForEscPosText(el.right),
            cols
          ).forEach { line ->
            sb.append(line).append('\n')
          }
        }
      }
    }
    sb.append("\n\n\n\n")
    return sb.toString().toByteArray(java.nio.charset.StandardCharsets.US_ASCII)
  }

  fun formatKeyValueLines(left: String, right: String, columns: Int): List<String> {
    val cleanRight = right.trim()
    val cleanLeft = left.trimEnd()
    if (cleanLeft.length + 1 + cleanRight.length <= columns) {
      val spaces = (columns - cleanLeft.length - cleanRight.length).coerceAtLeast(1)
      return listOf(cleanLeft + " ".repeat(spaces) + cleanRight)
    }

    val wrappedLeft = wrapText(cleanLeft, columns).toMutableList()
    if (wrappedLeft.isEmpty()) {
      return listOf(cleanRight.padStart(columns))
    }
    val lastLine = wrappedLeft.last()
    return if (lastLine.length + 1 + cleanRight.length <= columns) {
      val spaces = (columns - lastLine.length - cleanRight.length).coerceAtLeast(1)
      wrappedLeft[wrappedLeft.lastIndex] = lastLine + " ".repeat(spaces) + cleanRight
      wrappedLeft
    } else {
      wrappedLeft.add(cleanRight.padStart(columns.coerceAtLeast(cleanRight.length)))
      wrappedLeft
    }
  }

  fun wrapText(text: String, maxWidth: Int): List<String> {
    if (text.isBlank()) return emptyList()
    val width = maxWidth.coerceAtLeast(12)
    val result = mutableListOf<String>()

    for (paragraph in text.split('\n')) {
      val trimmed = paragraph.trim()
      if (trimmed.isEmpty()) continue
      val words = trimmed.split(Regex("\\s+"))
      var current = StringBuilder()

      for (word in words) {
        if (word.length > width) {
          if (current.isNotEmpty()) {
            result.add(current.toString())
            current = StringBuilder()
          }
          var idx = 0
          while (idx < word.length) {
            val end = minOf(idx + width, word.length)
            result.add(word.substring(idx, end))
            idx = end
          }
        } else if (current.isEmpty()) {
          current.append(word)
        } else if (current.length + 1 + word.length <= width) {
          current.append(' ').append(word)
        } else {
          result.add(current.toString())
          current = StringBuilder(word)
        }
      }
      if (current.isNotEmpty()) {
        result.add(current.toString())
      }
    }
    return result
  }

  private fun formatQty(value: Double): String {
    return if (value % 1.0 == 0.0) {
      value.toLong().toString()
    } else {
      val rounded = (value * 100.0).roundToLong() / 100.0
      rounded.toString()
    }
  }
}
