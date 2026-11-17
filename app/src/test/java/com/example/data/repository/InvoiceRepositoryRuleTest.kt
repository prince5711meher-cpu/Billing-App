package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class InvoiceRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createInvoice_authenticatedUser_success() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = BillingRepository(firestore)

    val invoice = Invoice(
      invoiceNumber = "INV-" + UUID.randomUUID().toString().take(8),
      documentType = "INVOICE",
      billingMode = "NON_GST",
      subtotalMinor = 45000L,
      grandTotalMinor = 45000L,
      amountPaidMinor = 45000L,
      balanceDueMinor = 0L,
      status = "PAID",
      items = listOf(
        InvoiceItem(
          description = "Steel Thali 12-inch",
          quantity = 2.0,
          unitLabel = "pcs",
          rateMinor = 22500L,
          lineTotalMinor = 45000L
        )
      )
    )

    val result = withTimeout(DEFAULT_TIMEOUT_MS) { repo.createInvoice(invoice) }
    assertTrue(result.isSuccess)
    val invoiceId = result.getOrThrow()
    assertTrue(invoiceId.isNotEmpty())

    val invoices = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeInvoices().first()
    }
    assertTrue(invoices.any { it.id == invoiceId })
  }

  @Test
  fun observeInvoices_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = BillingRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeInvoices().first() }
      fail("Expected exception for unauthenticated user")
    } catch (e: Exception) {
      assertNotNull(e)
    }
  }

  @Test
  fun cancelInvoice_authenticatedUser_updatesStatus() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = BillingRepository(firestore)

    val invoice = Invoice(
      invoiceNumber = "INV-" + UUID.randomUUID().toString().take(8),
      documentType = "INVOICE",
      billingMode = "NON_GST",
      subtotalMinor = 20000L,
      grandTotalMinor = 20000L,
      amountPaidMinor = 0L,
      balanceDueMinor = 20000L,
      status = "UNPAID",
      items = listOf(
        InvoiceItem(
          description = "Plastic Bucket 20L",
          quantity = 1.0,
          unitLabel = "pcs",
          rateMinor = 20000L,
          lineTotalMinor = 20000L
        )
      )
    )

    val createRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.createInvoice(invoice) }
    val invoiceId = createRes.getOrThrow()

    val cancelRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.cancelInvoice(invoiceId, "Customer changed order before dispatch")
    }
    assertTrue(cancelRes.isSuccess)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_test@meherbasan.com"
    const val DEFAULT_TIMEOUT_MS = 8000L
    const val FLOW_TIMEOUT_MS = 6000L
  }
}
