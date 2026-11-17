package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class CashSession(
  @DocumentId val id: String = "",
  val userId: String = "",
  val openingCashMinor: Long = 0L,      // in paise
  val expectedCashMinor: Long = 0L,     // in paise
  val countedCashMinor: Long = 0L,      // in paise
  val varianceMinor: Long = 0L,         // in paise (counted - expected)
  val status: String = "OPEN",          // OPEN or CLOSED
  val closingNotes: String = "",
  @ServerTimestamp val openedAt: Timestamp? = null,
  @ServerTimestamp val closedAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)
