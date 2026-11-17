package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Customer
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

class CustomerRepository(private val db: FirebaseFirestore) {

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

  fun observeCustomers(): Flow<List<Customer>> = flow {
    val uid = requireUserId()
    val path = "users/$uid/customers"
    emitAll(
      db.collection("users").document(uid).collection("customers")
        .orderBy("name", Query.Direction.ASCENDING)
        .snapshots()
        .map { snapshot -> snapshot.toObjects(Customer::class.java) }
        .catch { error ->
          if (error is CancellationException) throw error
          handleFirestoreError(error, OperationType.LIST, path)
          throw error
        }
    )
  }

  suspend fun createCustomer(customer: Customer): Result<String> {
    val uid = requireUserId()
    val path = "users/$uid/customers"
    val docRef = db.collection("users").document(uid).collection("customers").document()
    return try {
      val payload = hashMapOf<String, Any?>(
        "userId" to uid,
        "name" to customer.name,
        "phone" to customer.phone,
        "address" to customer.address,
        "gstin" to customer.gstin,
        "group" to customer.group,
        "totalOutstandingMinor" to customer.totalOutstandingMinor,
        "notes" to customer.notes,
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

  suspend fun updateCustomer(customer: Customer): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("customers").document(customer.id)
    return try {
      docRef.update(
        mapOf(
          "name" to customer.name,
          "phone" to customer.phone,
          "address" to customer.address,
          "gstin" to customer.gstin,
          "group" to customer.group,
          "totalOutstandingMinor" to customer.totalOutstandingMinor,
          "notes" to customer.notes,
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
