package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Invoice(
  @DocumentId val id: String = "",
  val userId: String = "",
  val invoiceNumber: String = "",
  val documentType: String = "INVOICE", // INVOICE or QUOTATION
  val customerId: String = "",
  val customerName: String = "Walk-in Customer",
  val customerPhone: String = "",
  val customerAddress: String = "",
  val customerGstin: String = "",
  val billingMode: String = "NON_GST",  // NON_GST or GST
  val placeOfSupply: String = "",
  val subtotalMinor: Long = 0L,         // in paise
  val discountType: String = "FIXED",   // FIXED or PERCENTAGE
  val discountValue: Double = 0.0,
  val discountMinor: Long = 0L,         // in paise
  val taxableTotalMinor: Long = 0L,     // in paise
  val cgstMinor: Long = 0L,             // in paise
  val sgstMinor: Long = 0L,             // in paise
  val igstMinor: Long = 0L,             // in paise
  val grandTotalMinor: Long = 0L,       // in paise
  val amountPaidMinor: Long = 0L,       // in paise
  val balanceDueMinor: Long = 0L,       // in paise
  val paymentMethod: String = "CASH",   // CASH, UPI, CARD, SPLIT, CREDIT
  val status: String = "PAID",          // PAID, PARTIALLY_PAID, UNPAID, CANCELLED
  val notes: String = "",
  val cancelledReason: String = "",
  val items: List<InvoiceItem> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)
