package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BusinessSettings
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SettingsRepository(private val db: FirebaseFirestore) {

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

  fun observeSettings(): Flow<BusinessSettings> = flow {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("settings").document("business")
    emitAll(
      docRef.snapshots()
        .map { snapshot ->
          snapshot.toObject(BusinessSettings::class.java) ?: BusinessSettings(userId = uid)
        }
        .catch { error ->
          if (error is CancellationException) throw error
          handleFirestoreError(error, OperationType.GET, docRef.path)
          throw error
        }
    )
  }

  suspend fun saveSettings(settings: BusinessSettings): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("settings").document("business")
    return try {
      val payload = hashMapOf<String, Any?>(
        "userId" to uid,
        "businessName" to settings.businessName,
        "address" to settings.address,
        "phone" to settings.phone,
        "email" to settings.email,
        "gstin" to settings.gstin,
        "stateCode" to settings.stateCode,
        "invoicePrefix" to settings.invoicePrefix,
        "termsAndConditions" to settings.termsAndConditions,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }
}
