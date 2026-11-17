package com.example.data.domain

import com.example.data.model.InvoiceItem
import org.junit.Assert.assertEquals
import org.junit.Test

class InvoiceEngineTest {

  @Test
  fun calculateLineItem_nonGst_computesCorrectLineTotal() {
    val item = InvoiceEngine.calculateLineItem(
      description = "Brass Puja Thali",
      quantity = 3.0,
      unitLabel = "pcs",
      rateMinor = 45000L, // ₹450.00
      discountMinor = 5000L, // ₹50.00
      isGstMode = false
    )

    // 3 * 45000 = 135000, discount 5000 = 130000 paise (₹1,300.00)
    assertEquals(130000L, item.taxableAmountMinor)
    assertEquals(130000L, item.lineTotalMinor)
    assertEquals(0L, item.cgstMinor)
    assertEquals(0L, item.sgstMinor)
    assertEquals(0L, item.igstMinor)
  }

  @Test
  fun calculateLineItem_percentageDiscount_computesCorrectDiscountAndNet() {
    val item = InvoiceEngine.calculateLineItem(
      description = "Copper Water Bottle",
      quantity = 2.0,
      unitLabel = "pcs",
      rateMinor = 50000L, // ₹500.00 -> ₹1,000.00 gross
      discountType = "PERCENTAGE",
      discountValue = 10.0, // 10% = ₹100.00
      isGstMode = false
    )

    assertEquals(10000L, item.discountMinor)
    assertEquals("PERCENTAGE", item.discountType)
    assertEquals(90000L, item.taxableAmountMinor)
    assertEquals(90000L, item.lineTotalMinor)
  }

  @Test
  fun calculateLineItem_intraStateGst_splitsCgstAndSgst() {
    val item = InvoiceEngine.calculateLineItem(
      description = "Stainless Steel Pressure Cooker 5L",
      quantity = 1.0,
      unitLabel = "pcs",
      rateMinor = 150000L, // ₹1,500.00
      discountMinor = 0L,
      taxRateBps = 1200L, // 12% GST
      isGstMode = true,
      isInterState = false
    )

    // 12% on 150000 is 18000 paise tax -> CGST 9000, SGST 9000
    assertEquals(150000L, item.taxableAmountMinor)
    assertEquals(9000L, item.cgstMinor)
    assertEquals(9000L, item.sgstMinor)
    assertEquals(0L, item.igstMinor)
    assertEquals(168000L, item.lineTotalMinor)
  }

  @Test
  fun calculateInvoice_fixedDiscountAndPartialPayment_computesBalanceDue() {
    val items = listOf(
      InvoiceItem(quantity = 2.0, rateMinor = 10000L, lineTotalMinor = 20000L), // ₹200
      InvoiceItem(quantity = 1.0, rateMinor = 30000L, lineTotalMinor = 30000L)  // ₹300
    )

    val result = InvoiceEngine.calculateInvoice(
      items = items,
      discountType = "FIXED",
      discountValue = 50.0, // ₹50 discount -> 5000 paise
      billingMode = "NON_GST",
      amountPaidMinor = 35000L // Paid ₹350.00
    )

    assertEquals(50000L, result.subtotalMinor) // ₹500
    assertEquals(5000L, result.discountMinor)  // ₹50
    assertEquals(45000L, result.grandTotalMinor) // ₹450
    assertEquals(10000L, result.balanceDueMinor) // ₹100 due
  }

  @Test
  fun calculateInvoice_itemAndOverallDiscounts_combinesProperly() {
    val item1 = InvoiceEngine.calculateLineItem(
      description = "Item A",
      quantity = 2.0,
      unitLabel = "pcs",
      rateMinor = 50000L, // ₹1,000 gross
      discountType = "PERCENTAGE",
      discountValue = 10.0, // -₹100 -> ₹900
      isGstMode = false
    )
    val item2 = InvoiceEngine.calculateLineItem(
      description = "Item B",
      quantity = 1.0,
      unitLabel = "pcs",
      rateMinor = 20000L, // ₹200 gross
      discountType = "FIXED",
      discountValue = 50.0, // -₹50 -> ₹150
      isGstMode = false
    )

    val result = InvoiceEngine.calculateInvoice(
      items = listOf(item1, item2),
      discountType = "FIXED",
      discountValue = 50.0, // Overall ₹50 off on ₹1,050 subtotal -> ₹1,000 grand total
      billingMode = "NON_GST",
      amountPaidMinor = 100000L
    )

    assertEquals(120000L, result.itemsGrossSubtotalMinor) // ₹1,200 gross
    assertEquals(15000L, result.itemDiscountTotalMinor)   // ₹150 item discounts
    assertEquals(105000L, result.subtotalMinor)           // ₹1,050 subtotal
    assertEquals(5000L, result.discountMinor)             // ₹50 overall discount
    assertEquals(100000L, result.grandTotalMinor)         // ₹1,000 grand total
    assertEquals(0L, result.balanceDueMinor)
  }
}
