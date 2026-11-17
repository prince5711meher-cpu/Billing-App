package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.data.model.Payment
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

class BillingRepository(private val db: FirebaseFirestore) {

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

  private fun invoiceItemsToMapList(items: List<InvoiceItem>): List<HashMap<String, Any?>> {
    return items.map { item ->
      hashMapOf(
        "description" to item.description,
        "quantity" to item.quantity,
        "unitLabel" to item.unitLabel,
        "rateMinor" to item.rateMinor,
        "discountType" to item.discountType,
        "discountValue" to item.discountValue,
        "discountMinor" to item.discountMinor,
        "hsnSac" to item.hsnSac,
        "taxRateBps" to item.taxRateBps,
        "taxableAmountMinor" to item.taxableAmountMinor,
        "cgstMinor" to item.cgstMinor,
        "sgstMinor" to item.sgstMinor,
        "igstMinor" to item.igstMinor,
        "lineTotalMinor" to item.lineTotalMinor
      )
    }
  }

  fun observeInvoices(): Flow<List<Invoice>> = flow {
    val uid = requireUserId()
    val path = "users/$uid/invoices"
    emitAll(
      db.collection("users").document(uid).collection("invoices")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(Invoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

  suspend fun createInvoice(invoice: Invoice): Result<String> {
    val uid = requireUserId()
    val path = "users/$uid/invoices"
    val docRef = db.collection("users").document(uid).collection("invoices").document()
    return try {
      val payload = invoice.copy(
        id = docRef.id,
        userId = uid,
        createdAt = null,
        updatedAt = null
      )
      val map = hashMapOf<String, Any?>(
        "userId" to uid,
        "invoiceNumber" to payload.invoiceNumber,
        "documentType" to payload.documentType,
        "customerId" to payload.customerId,
        "customerName" to payload.customerName,
        "customerPhone" to payload.customerPhone,
        "customerAddress" to payload.customerAddress,
        "customerGstin" to payload.customerGstin,
        "billingMode" to payload.billingMode,
        "placeOfSupply" to payload.placeOfSupply,
        "subtotalMinor" to payload.subtotalMinor,
        "discountType" to payload.discountType,
        "discountValue" to payload.discountValue,
        "discountMinor" to payload.discountMinor,
        "taxableTotalMinor" to payload.taxableTotalMinor,
        "cgstMinor" to payload.cgstMinor,
        "sgstMinor" to payload.sgstMinor,
        "igstMinor" to payload.igstMinor,
        "grandTotalMinor" to payload.grandTotalMinor,
        "amountPaidMinor" to payload.amountPaidMinor,
        "balanceDueMinor" to payload.balanceDueMinor,
        "paymentMethod" to payload.paymentMethod,
        "status" to payload.status,
        "notes" to payload.notes,
        "items" to invoiceItemsToMapList(payload.items),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(map).await()

      if (payload.amountPaidMinor > 0 && payload.documentType == "INVOICE") {
        recordPayment(
          Payment(
            customerId = payload.customerId,
            customerName = payload.customerName,
            invoiceId = docRef.id,
            invoiceNumber = payload.invoiceNumber,
            method = payload.paymentMethod,
            amountMinor = payload.amountPaidMinor,
            notes = "Initial payment on invoice ${payload.invoiceNumber}"
          )
        )
      }

      if (payload.customerId.isNotBlank() && payload.balanceDueMinor > 0) {
        val custRef = db.collection("users").document(uid).collection("customers").document(payload.customerId)
        custRef.update("totalOutstandingMinor", FieldValue.increment(payload.balanceDueMinor)).await()
      }

      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateInvoice(invoiceId: String, invoice: Invoice): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/invoices/$invoiceId"
    return try {
      val uid = requireUserId()
      require(invoiceId.isNotBlank()) { "Invoice ID must not be blank for update" }
      val docRef = db.collection("users").document(uid).collection("invoices").document(invoiceId)
      docPath = docRef.path

      val updateMap = hashMapOf<String, Any?>(
        "documentType" to invoice.documentType,
        "customerId" to invoice.customerId,
        "customerName" to invoice.customerName,
        "customerPhone" to invoice.customerPhone,
        "customerAddress" to invoice.customerAddress,
        "customerGstin" to invoice.customerGstin,
        "billingMode" to invoice.billingMode,
        "placeOfSupply" to invoice.placeOfSupply,
        "subtotalMinor" to invoice.subtotalMinor,
        "discountType" to invoice.discountType,
        "discountValue" to invoice.discountValue,
        "discountMinor" to invoice.discountMinor,
        "taxableTotalMinor" to invoice.taxableTotalMinor,
        "cgstMinor" to invoice.cgstMinor,
        "sgstMinor" to invoice.sgstMinor,
        "igstMinor" to invoice.igstMinor,
        "grandTotalMinor" to invoice.grandTotalMinor,
        "amountPaidMinor" to invoice.amountPaidMinor,
        "balanceDueMinor" to invoice.balanceDueMinor,
        "paymentMethod" to invoice.paymentMethod,
        "status" to invoice.status,
        "notes" to invoice.notes,
        "items" to invoiceItemsToMapList(invoice.items),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.update(updateMap).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun cancelInvoice(invoiceId: String, reason: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("invoices").document(invoiceId)
    return try {
      val snapshot = docRef.get().await()
      val inv = snapshot.toObject(Invoice::class.java)
        ?: return Result.failure(IllegalStateException("Invoice not found"))

      docRef.update(
        mapOf(
          "status" to "CANCELLED",
          "cancelledReason" to reason,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()

      if (inv.customerId.isNotBlank() && inv.balanceDueMinor > 0) {
        val custRef = db.collection("users").document(uid).collection("customers").document(inv.customerId)
        custRef.update("totalOutstandingMinor", FieldValue.increment(-inv.balanceDueMinor)).await()
      }

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  fun observePayments(): Flow<List<Payment>> = flow {
    val uid = requireUserId()
    val path = "users/$uid/payments"
    emitAll(
      db.collection("users").document(uid).collection("payments")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(Payment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

  suspend fun recordPayment(payment: Payment): Result<String> {
    val uid = requireUserId()
    val path = "users/$uid/payments"
    val docRef = db.collection("users").document(uid).collection("payments").document()
    return try {
      val payload = hashMapOf<String, Any?>(
        "userId" to uid,
        "customerId" to payment.customerId,
        "customerName" to payment.customerName,
        "invoiceId" to payment.invoiceId,
        "invoiceNumber" to payment.invoiceNumber,
        "paymentDate" to payment.paymentDate,
        "method" to payment.method,
        "amountMinor" to payment.amountMinor,
        "reference" to payment.reference,
        "notes" to payment.notes,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(payload).await()

      if (payment.invoiceId.isNotBlank()) {
        val invRef = db.collection("users").document(uid).collection("invoices").document(payment.invoiceId)
        val invSnap = invRef.get().await()
        val currentInv = invSnap.toObject(Invoice::class.java)
        if (currentInv != null) {
          val newPaid = currentInv.amountPaidMinor + payment.amountMinor
          val newDue = maxOf(0L, currentInv.grandTotalMinor - newPaid)
          val newStatus = if (newDue == 0L) "PAID" else "PARTIALLY_PAID"
          invRef.update(
            mapOf(
              "amountPaidMinor" to newPaid,
              "balanceDueMinor" to newDue,
              "status" to newStatus,
              "updatedAt" to FieldValue.serverTimestamp()
            )
          ).await()
        }
      }

      if (payment.customerId.isNotBlank()) {
        val custRef = db.collection("users").document(uid).collection("customers").document(payment.customerId)
        custRef.update("totalOutstandingMinor", FieldValue.increment(-payment.amountMinor)).await()
      }

      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }
}
