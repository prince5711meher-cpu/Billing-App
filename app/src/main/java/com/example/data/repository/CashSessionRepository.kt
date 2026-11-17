package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CashSession
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CashSessionRepository(private val db: FirebaseFirestore) {

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

  fun observeSessions(): Flow<List<CashSession>> = flow {
    val uid = requireUserId()
    val path = "users/$uid/cash_sessions"
    emitAll(
      db.collection("users").document(uid).collection("cash_sessions")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot -> snapshot.toObjects(CashSession::class.java) }
        .catch { error ->
          if (error is CancellationException) throw error
          handleFirestoreError(error, OperationType.LIST, path)
          throw error
        }
    )
  }

  suspend fun openSession(openingCashMinor: Long): Result<String> {
    val uid = requireUserId()
    val path = "users/$uid/cash_sessions"
    val docRef = db.collection("users").document(uid).collection("cash_sessions").document()
    return try {
      val payload = hashMapOf<String, Any?>(
        "userId" to uid,
        "openingCashMinor" to openingCashMinor,
        "expectedCashMinor" to openingCashMinor,
        "countedCashMinor" to 0L,
        "varianceMinor" to 0L,
        "status" to "OPEN",
        "closingNotes" to "",
        "openedAt" to FieldValue.serverTimestamp(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun closeSession(
    sessionId: String,
    expectedCashMinor: Long,
    countedCashMinor: Long,
    closingNotes: String
  ): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("cash_sessions").document(sessionId)
    return try {
      val variance = countedCashMinor - expectedCashMinor
      docRef.update(
        mapOf(
          "status" to "CLOSED",
          "closedAt" to FieldValue.serverTimestamp(),
          "expectedCashMinor" to expectedCashMinor,
          "countedCashMinor" to countedCashMinor,
          "varianceMinor" to variance,
          "closingNotes" to closingNotes,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }
}
