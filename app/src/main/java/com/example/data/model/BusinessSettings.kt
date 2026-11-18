package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Immutable
@IgnoreExtraProperties
data class BusinessSettings(
  @DocumentId val id: String = "business",
  val userId: String = "",
  val businessName: String = "Billing App",
  val address: String = "Main Market, Commercial Plaza",
  val phone: String = "+91 98765 43210",
  val email: String = "billingapp@example.com",
  val gstin: String = "",
  val stateCode: String = "21-Odisha",
  val invoicePrefix: String = "BILL",
  val termsAndConditions: String = "Goods once sold are returnable within 7 days with original bill. Thank you for your business!",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)
