package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Customer(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val phone: String = "",
  val address: String = "",
  val gstin: String = "",
  val group: String = "Regular",
  val totalOutstandingMinor: Long = 0L, // in paise
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)
