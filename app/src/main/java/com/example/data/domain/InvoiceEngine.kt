package com.example.data.domain

import com.example.data.model.InvoiceItem
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

object MoneyFormatter {
  private val inrLocale = Locale("en", "IN")
  private val formatter = NumberFormat.getNumberInstance(inrLocale).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
  }

  fun formatPaise(paise: Long): String {
    val rupees = paise / 100.0
    return "₹" + formatter.format(rupees)
  }

  fun formatRupeesDecimal(rupees: Double): String {
    return "₹" + formatter.format(rupees)
  }

  fun parseRupeesToPaise(input: String): Long {
    val clean = input.replace("₹", "").replace(",", "").trim()
    val value = clean.toDoubleOrNull() ?: 0.0
    return (value * 100).roundToLong()
  }
}

data class InvoiceCalculationResult(
  val items: List<InvoiceItem>,
  val itemsGrossSubtotalMinor: Long,
  val itemDiscountTotalMinor: Long,
  val subtotalMinor: Long,
  val discountMinor: Long,
  val taxableTotalMinor: Long,
  val cgstMinor: Long,
  val sgstMinor: Long,
  val igstMinor: Long,
  val grandTotalMinor: Long,
  val balanceDueMinor: Long
)

object InvoiceEngine {

  fun calculateLineItem(
    description: String,
    quantity: Double,
    unitLabel: String,
    rateMinor: Long,
    discountMinor: Long = 0L,
    discountType: String = "FIXED",
    discountValue: Double? = null,
    hsnSac: String = "",
    taxRateBps: Long = 0L,
    isGstMode: Boolean = false,
    isInterState: Boolean = false
  ): InvoiceItem {
    val safeQty = maxOf(0.0, quantity)
    val safeRate = maxOf(0L, rateMinor)
    val rawAmount = (safeQty * safeRate).roundToLong()

    val resolvedDiscountType = if (discountValue != null) {
      if (discountType.equals("PERCENTAGE", ignoreCase = true)) "PERCENTAGE" else "FIXED"
    } else {
      "FIXED"
    }
    val resolvedDiscountValue = if (discountValue != null) {
      maxOf(0.0, discountValue)
    } else {
      maxOf(0L, discountMinor) / 100.0
    }

    val computedDiscountMinor = if (discountValue != null) {
      when (resolvedDiscountType) {
        "PERCENTAGE" -> {
          val pct = resolvedDiscountValue.coerceIn(0.0, 100.0)
          (rawAmount * (pct / 100.0)).roundToLong()
        }
        else -> {
          val fixedPaise = (resolvedDiscountValue * 100).roundToLong()
          minOf(rawAmount, maxOf(0L, fixedPaise))
        }
      }
    } else {
      minOf(rawAmount, maxOf(0L, discountMinor))
    }

    val netAmount = maxOf(0L, rawAmount - computedDiscountMinor)

    val (cgst, sgst, igst) = if (isGstMode && taxRateBps > 0) {
      val totalTax = (netAmount * (taxRateBps / 10000.0)).roundToLong()
      if (isInterState) {
        Triple(0L, 0L, totalTax)
      } else {
        val half = totalTax / 2
        Triple(half, totalTax - half, 0L)
      }
    } else {
      Triple(0L, 0L, 0L)
    }

    val lineTotal = netAmount + cgst + sgst + igst

    return InvoiceItem(
      description = description,
      quantity = safeQty,
      unitLabel = unitLabel,
      rateMinor = safeRate,
      discountType = resolvedDiscountType,
      discountValue = resolvedDiscountValue,
      discountMinor = computedDiscountMinor,
      hsnSac = hsnSac,
      taxRateBps = taxRateBps,
      taxableAmountMinor = netAmount,
      cgstMinor = cgst,
      sgstMinor = sgst,
      igstMinor = igst,
      lineTotalMinor = lineTotal
    )
  }

  fun calculateInvoice(
    items: List<InvoiceItem>,
    discountType: String,
    discountValue: Double,
    billingMode: String,
    placeOfSupply: String = "",
    sellerStateCode: String = "",
    amountPaidMinor: Long = 0L
  ): InvoiceCalculationResult {
    val itemsGrossSubtotal = items.sumOf { (it.quantity * it.rateMinor).roundToLong() }
    val itemDiscountTotal = items.sumOf { item ->
      val gross = (item.quantity * item.rateMinor).roundToLong()
      minOf(gross, maxOf(0L, item.discountMinor))
    }
    val subtotal = maxOf(0L, itemsGrossSubtotal - itemDiscountTotal)

    val discountMinor = when (discountType.uppercase()) {
      "PERCENTAGE" -> {
        val pct = discountValue.coerceIn(0.0, 100.0)
        (subtotal * (pct / 100.0)).roundToLong()
      }
      else -> { // FIXED
        val fixedPaise = (discountValue * 100).roundToLong()
        minOf(subtotal, maxOf(0L, fixedPaise))
      }
    }

    val isGst = billingMode.equals("GST", ignoreCase = true)
    val taxableTotal = maxOf(0L, subtotal - discountMinor)

    val (cgstTotal, sgstTotal, igstTotal) = if (isGst) {
      val rawCgst = items.sumOf { it.cgstMinor }
      val rawSgst = items.sumOf { it.sgstMinor }
      val rawIgst = items.sumOf { it.igstMinor }
      if (discountMinor > 0 && subtotal > 0) {
        val ratio = taxableTotal.toDouble() / subtotal.toDouble()
        Triple(
          (rawCgst * ratio).roundToLong(),
          (rawSgst * ratio).roundToLong(),
          (rawIgst * ratio).roundToLong()
        )
      } else {
        Triple(rawCgst, rawSgst, rawIgst)
      }
    } else {
      Triple(0L, 0L, 0L)
    }

    val grandTotal = if (isGst) {
      taxableTotal + cgstTotal + sgstTotal + igstTotal
    } else {
      taxableTotal
    }

    val balanceDue = maxOf(0L, grandTotal - amountPaidMinor)

    return InvoiceCalculationResult(
      items = items,
      itemsGrossSubtotalMinor = itemsGrossSubtotal,
      itemDiscountTotalMinor = itemDiscountTotal,
      subtotalMinor = subtotal,
      discountMinor = discountMinor,
      taxableTotalMinor = taxableTotal,
      cgstMinor = cgstTotal,
      sgstMinor = sgstTotal,
      igstMinor = igstTotal,
      grandTotalMinor = grandTotal,
      balanceDueMinor = balanceDue
    )
  }
}
