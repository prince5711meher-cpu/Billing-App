package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.SavedItem
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ItemRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      Firebase.app,
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
  }

  fun observeItems(): Flow<List<SavedItem>> = flow {
    val uid = requireUserId()
    val path = "users/$uid/items"
    emitAll(
      db.collection("users").document(uid).collection("items")
        .orderBy("updatedAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(SavedItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun upsertItem(item: SavedItem, existingItem: SavedItem? = null): Result<String> {
    val uid = requireUserId()
    val cleanName = item.name.trim()
    require(cleanName.isNotEmpty()) { "Item name must not be blank" }
    val normalized = SavedItem.normalize(cleanName)
    val docId = existingItem?.id?.takeIf { it.isNotBlank() } ?: SavedItem.buildDocumentId(cleanName)
    val docRef = db.collection("users").document(uid).collection("items").document(docId)

    val safeRate = maxOf(0L, item.rateMinor)
    val safeUnit = item.unitLabel.trim().ifBlank { "pcs" }.take(32)
    val safeDiscType = if (item.defaultDiscountType.equals("PERCENTAGE", ignoreCase = true)) "PERCENTAGE" else "FIXED"
    val safeDiscValue = maxOf(0.0, item.defaultDiscountValue)
    val safeHsn = item.hsnSac.trim().take(32)
    val safeTaxBps = maxOf(0L, item.taxRateBps)

    return try {
      val snapshotExists = existingItem != null || try {
        docRef.get().await().exists()
      } catch (_: Exception) {
        false
      }

      if (snapshotExists) {
        val updateMap = hashMapOf<String, Any>(
          "name" to cleanName.take(128),
          "normalizedName" to normalized.take(128),
          "unitLabel" to safeUnit,
          "rateMinor" to safeRate,
          "defaultDiscountType" to safeDiscType,
          "defaultDiscountValue" to safeDiscValue,
          "hsnSac" to safeHsn,
          "taxRateBps" to safeTaxBps,
          "useCount" to maxOf(1L, item.useCount),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.update(updateMap).await()
      } else {
        val createMap = hashMapOf<String, Any>(
          "userId" to uid,
          "name" to cleanName.take(128),
          "normalizedName" to normalized.take(128),
          "unitLabel" to safeUnit,
          "rateMinor" to safeRate,
          "defaultDiscountType" to safeDiscType,
          "defaultDiscountValue" to safeDiscValue,
          "hsnSac" to safeHsn,
          "taxRateBps" to safeTaxBps,
          "useCount" to maxOf(1L, item.useCount),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(createMap).await()
      }
      Result.success(docId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (existingItem != null) OperationType.UPDATE else OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteItem(itemId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("items").document(itemId)
    return try {
      require(itemId.isNotBlank()) { "Item ID must not be blank" }
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }
}
