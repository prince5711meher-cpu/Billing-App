package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest

@Keep
@IgnoreExtraProperties
data class SavedItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val normalizedName: String = "",
  val unitLabel: String = "pcs",
  val rateMinor: Long = 0L,
  val defaultDiscountType: String = "FIXED",
  val defaultDiscountValue: Double = 0.0,
  val hsnSac: String = "",
  val taxRateBps: Long = 0L,
  val useCount: Long = 1L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  companion object {
    fun normalize(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

    fun buildDocumentId(name: String): String {
      val normalized = normalize(name)
      val slug = normalized
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .take(50)
      val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
      val hashHex = digest.take(6).joinToString("") { "%02x".format(it) }
      return if (slug.isNotEmpty()) "item_${slug}_$hashHex" else "item_$hashHex"
    }
  }
}
