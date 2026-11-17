package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class InvoiceItem(
  val description: String = "",
  val quantity: Double = 1.0,
  val unitLabel: String = "pcs",
  val rateMinor: Long = 0L,           // Rate per unit in paise
  val discountType: String = "FIXED", // FIXED or PERCENTAGE
  val discountValue: Double = 0.0,    // Rupees for FIXED, % for PERCENTAGE
  val discountMinor: Long = 0L,       // Computed line discount in paise
  val hsnSac: String = "",
  val taxRateBps: Long = 0L,          // Tax rate in basis points (e.g. 500 for 5%, 1200 for 12%, 1800 for 18%)
  val taxableAmountMinor: Long = 0L,  // Net taxable amount in paise (after item discount)
  val cgstMinor: Long = 0L,           // CGST in paise
  val sgstMinor: Long = 0L,           // SGST in paise
  val igstMinor: Long = 0L,           // IGST in paise
  val lineTotalMinor: Long = 0L       // Total line amount in paise
)
