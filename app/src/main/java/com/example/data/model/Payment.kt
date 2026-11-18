package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Immutable
@IgnoreExtraProperties
data class Payment(
  @DocumentId val id: String = "",
  val userId: String = "",
  val customerId: String = "",
  val customerName: String = "",
  val invoiceId: String = "",
  val invoiceNumber: String = "",
  val paymentDate: String = "",
  val method: String = "CASH",          // CASH, UPI, BANK, CARD, OTHER
  val amountMinor: Long = 0L,           // in paise
  val reference: String = "",
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)
