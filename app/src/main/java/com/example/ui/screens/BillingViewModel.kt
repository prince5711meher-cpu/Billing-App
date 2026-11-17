package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.domain.InvoiceCalculationResult
import com.example.data.domain.InvoiceEngine
import com.example.data.model.Customer
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.data.repository.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NewBillDraft(
  val editingInvoiceId: String? = null,
  val editingInvoiceNumber: String? = null,
  val documentType: String = "INVOICE",     // INVOICE or QUOTATION
  val billingMode: String = "NON_GST",      // NON_GST by default
  val customer: Customer? = null,
  val walkInName: String = "",
  val walkInPhone: String = "",
  val placeOfSupply: String = "21-Odisha",
  val items: List<InvoiceItem> = emptyList(),
  val discountType: String = "FIXED",       // FIXED or PERCENTAGE (overall discount)
  val discountValue: Double = 0.0,
  val paymentMethod: String = "CASH",       // CASH, UPI, UDHAR
  val amountPaidMinor: Long = 0L,
  val notes: String = ""
) {
  val isEditing: Boolean
    get() = editingInvoiceId != null

  val calculation: InvoiceCalculationResult
    get() = InvoiceEngine.calculateInvoice(
      items = items,
      discountType = discountType,
      discountValue = discountValue,
      billingMode = billingMode,
      placeOfSupply = placeOfSupply,
      sellerStateCode = "21-Odisha",
      amountPaidMinor = amountPaidMinor
    )
}

class BillingViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = BillingRepository(application)

  val invoicesState: StateFlow<List<Invoice>> = repository.observeInvoices()
    .catch { emit(emptyList()) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  val paymentsState = repository.observePayments()
    .catch { emit(emptyList()) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  private val _draft = MutableStateFlow(NewBillDraft())
  val draft: StateFlow<NewBillDraft> = _draft.asStateFlow()

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

  private fun resolveUpdatedAmountPaid(
    current: NewBillDraft,
    oldGrandTotal: Long,
    newGrandTotal: Long
  ): Long {
    return when {
      current.paymentMethod == "UDHAR" -> 0L
      current.items.isEmpty() -> newGrandTotal
      current.amountPaidMinor == oldGrandTotal -> newGrandTotal
      else -> minOf(current.amountPaidMinor, newGrandTotal)
    }
  }

  fun startEditingInvoice(invoice: Invoice) {
    val recalculatedItems = invoice.items.map { item ->
      val hasExplicitDiscountValue = item.discountValue > 0.0
      InvoiceEngine.calculateLineItem(
        description = item.description,
        quantity = item.quantity,
        unitLabel = item.unitLabel,
        rateMinor = item.rateMinor,
        discountMinor = item.discountMinor,
        discountType = item.discountType,
        discountValue = if (hasExplicitDiscountValue) item.discountValue else null,
        isGstMode = false
      )
    }
    val displayWalkInName = if (
      invoice.customerName == "Counter Customer" ||
      invoice.customerName == "Walk-in Customer"
    ) "" else invoice.customerName

    val resolvedPaymentMethod = when (invoice.paymentMethod.uppercase()) {
      "UPI", "UDHAR", "CASH" -> invoice.paymentMethod.uppercase()
      "CREDIT" -> "UDHAR"
      else -> if (invoice.amountPaidMinor == 0L && invoice.balanceDueMinor > 0L) "UDHAR" else "CASH"
    }

    _draft.value = NewBillDraft(
      editingInvoiceId = invoice.id,
      editingInvoiceNumber = invoice.invoiceNumber,
      documentType = invoice.documentType,
      billingMode = "NON_GST",
      walkInName = displayWalkInName,
      walkInPhone = invoice.customerPhone,
      placeOfSupply = invoice.placeOfSupply.ifBlank { "21-Odisha" },
      items = recalculatedItems,
      discountType = invoice.discountType,
      discountValue = invoice.discountValue,
      paymentMethod = resolvedPaymentMethod,
      amountPaidMinor = invoice.amountPaidMinor,
      notes = invoice.notes
    )
  }

  fun setDocumentType(type: String) {
    _draft.value = _draft.value.copy(documentType = type)
  }

  fun setBillingMode(mode: String) {
    val current = _draft.value
    val oldGrandTotal = current.calculation.grandTotalMinor
    val isGst = mode.equals("GST", ignoreCase = true)
    val recalculatedItems = current.items.map { item ->
      InvoiceEngine.calculateLineItem(
        description = item.description,
        quantity = item.quantity,
        unitLabel = item.unitLabel,
        rateMinor = item.rateMinor,
        discountMinor = item.discountMinor,
        discountType = item.discountType,
        discountValue = item.discountValue,
        hsnSac = item.hsnSac,
        taxRateBps = item.taxRateBps,
        isGstMode = isGst
      )
    }
    val calc = InvoiceEngine.calculateInvoice(
      items = recalculatedItems,
      discountType = current.discountType,
      discountValue = current.discountValue,
      billingMode = mode
    )
    _draft.value = current.copy(
      billingMode = mode,
      items = recalculatedItems,
      amountPaidMinor = resolveUpdatedAmountPaid(current, oldGrandTotal, calc.grandTotalMinor)
    )
  }

  fun setCustomer(customer: Customer?) {
    _draft.value = _draft.value.copy(customer = customer)
  }

  fun setWalkInDetails(name: String, phone: String) {
    _draft.value = _draft.value.copy(walkInName = name, walkInPhone = phone)
  }

  fun addItem(
    description: String,
    quantity: Double,
    unitLabel: String,
    rateMinor: Long,
    discountType: String = "FIXED",
    discountValue: Double = 0.0,
    discountMinor: Long = 0L,
    hsnSac: String = "",
    taxRateBps: Long = 0L
  ) {
    val current = _draft.value
    val oldGrandTotal = current.calculation.grandTotalMinor
    val isGst = current.billingMode.equals("GST", ignoreCase = true)
    val item = InvoiceEngine.calculateLineItem(
      description = description,
      quantity = quantity,
      unitLabel = unitLabel,
      rateMinor = rateMinor,
      discountMinor = discountMinor,
      discountType = discountType,
      discountValue = if (discountValue > 0.0) discountValue else if (discountMinor > 0L) null else 0.0,
      hsnSac = hsnSac,
      taxRateBps = taxRateBps,
      isGstMode = isGst
    )

    val updatedItems = current.items + item
    val calc = InvoiceEngine.calculateInvoice(
      items = updatedItems,
      discountType = current.discountType,
      discountValue = current.discountValue,
      billingMode = current.billingMode
    )
    _draft.value = current.copy(
      items = updatedItems,
      amountPaidMinor = resolveUpdatedAmountPaid(current, oldGrandTotal, calc.grandTotalMinor)
    )
  }

  fun updateItem(
    index: Int,
    description: String,
    quantity: Double,
    unitLabel: String,
    rateMinor: Long,
    discountType: String = "FIXED",
    discountValue: Double = 0.0,
    hsnSac: String = "",
    taxRateBps: Long = 0L
  ) {
    val current = _draft.value
    if (index !in current.items.indices) return

    val oldGrandTotal = current.calculation.grandTotalMinor
    val isGst = current.billingMode.equals("GST", ignoreCase = true)
    val updatedItem = InvoiceEngine.calculateLineItem(
      description = description,
      quantity = quantity,
      unitLabel = unitLabel,
      rateMinor = rateMinor,
      discountType = discountType,
      discountValue = discountValue,
      hsnSac = hsnSac,
      taxRateBps = taxRateBps,
      isGstMode = isGst
    )

    val updatedItems = current.items.toMutableList().apply { set(index, updatedItem) }
    val calc = InvoiceEngine.calculateInvoice(
      items = updatedItems,
      discountType = current.discountType,
      discountValue = current.discountValue,
      billingMode = current.billingMode
    )
    _draft.value = current.copy(
      items = updatedItems,
      amountPaidMinor = resolveUpdatedAmountPaid(current, oldGrandTotal, calc.grandTotalMinor)
    )
  }

  fun removeItem(index: Int) {
    val current = _draft.value
    if (index in current.items.indices) {
      val oldGrandTotal = current.calculation.grandTotalMinor
      val updated = current.items.toMutableList().apply { removeAt(index) }
      val calc = InvoiceEngine.calculateInvoice(
        items = updated,
        discountType = current.discountType,
        discountValue = current.discountValue,
        billingMode = current.billingMode
      )
      _draft.value = current.copy(
        items = updated,
        amountPaidMinor = resolveUpdatedAmountPaid(current, oldGrandTotal, calc.grandTotalMinor)
      )
    }
  }

  fun setDiscount(type: String, value: Double) {
    val current = _draft.value
    val oldGrandTotal = current.calculation.grandTotalMinor
    val calc = InvoiceEngine.calculateInvoice(
      items = current.items,
      discountType = type,
      discountValue = value,
      billingMode = current.billingMode
    )
    _draft.value = current.copy(
      discountType = type,
      discountValue = value,
      amountPaidMinor = resolveUpdatedAmountPaid(current, oldGrandTotal, calc.grandTotalMinor)
    )
  }

  fun setPaymentMethod(method: String) {
    val current = _draft.value
    val calc = current.calculation
    val newPaid = when (method) {
      "UDHAR" -> 0L
      "CASH", "UPI" -> if (current.amountPaidMinor == 0L) calc.grandTotalMinor else current.amountPaidMinor
      else -> current.amountPaidMinor
    }
    _draft.value = current.copy(paymentMethod = method, amountPaidMinor = newPaid)
  }

  fun setAmountPaid(paise: Long) {
    _draft.value = _draft.value.copy(amountPaidMinor = paise)
  }

  fun setNotes(notes: String) {
    _draft.value = _draft.value.copy(notes = notes)
  }

  fun resetDraft() {
    _draft.value = NewBillDraft()
  }

  fun saveInvoice(
    invoicePrefix: String = "BILL",
    onSuccess: (invoiceId: String, savedInvoice: Invoice) -> Unit,
    onError: (String) -> Unit
  ) {
    val current = _draft.value
    if (current.items.isEmpty()) {
      onError("Please add at least one line item to the bill")
      return
    }

    val calc = current.calculation
    val existingId = current.editingInvoiceId
    val invoiceNum = if (existingId != null && !current.editingInvoiceNumber.isNullOrBlank()) {
      current.editingInvoiceNumber
    } else {
      val existingCount = invoicesState.value.size + 1
      val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
      "$invoicePrefix-$dateStr-${existingCount.toString().padStart(4, '0')}"
    }

    val cust = current.customer
    val resolvedCustomerName = cust?.name ?: current.walkInName.trim().ifBlank { "Counter Customer" }
    val status = when {
      current.documentType == "QUOTATION" -> "QUOTATION"
      calc.balanceDueMinor == 0L -> "PAID"
      current.amountPaidMinor == 0L -> "UNPAID"
      else -> "PARTIALLY_PAID"
    }

    val invoice = Invoice(
      id = existingId ?: "",
      invoiceNumber = invoiceNum,
      documentType = current.documentType,
      customerId = cust?.id ?: "",
      customerName = resolvedCustomerName,
      customerPhone = cust?.phone ?: current.walkInPhone,
      customerAddress = cust?.address ?: "",
      customerGstin = cust?.gstin ?: "",
      billingMode = current.billingMode,
      placeOfSupply = current.placeOfSupply,
      subtotalMinor = calc.subtotalMinor,
      discountType = current.discountType,
      discountValue = current.discountValue,
      discountMinor = calc.discountMinor,
      taxableTotalMinor = calc.taxableTotalMinor,
      cgstMinor = calc.cgstMinor,
      sgstMinor = calc.sgstMinor,
      igstMinor = calc.igstMinor,
      grandTotalMinor = calc.grandTotalMinor,
      amountPaidMinor = current.amountPaidMinor,
      balanceDueMinor = calc.balanceDueMinor,
      paymentMethod = current.paymentMethod,
      status = status,
      notes = current.notes,
      items = calc.items
    )

    viewModelScope.launch {
      _isSaving.value = true
      if (existingId != null) {
        val result = repository.updateInvoice(existingId, invoice)
        _isSaving.value = false
        if (result.isSuccess) {
          resetDraft()
          onSuccess(existingId, invoice)
        } else {
          onError(result.exceptionOrNull()?.localizedMessage ?: "Failed to update bill")
        }
      } else {
        val result = repository.createInvoice(invoice)
        _isSaving.value = false
        if (result.isSuccess) {
          val newId = result.getOrThrow()
          resetDraft()
          onSuccess(newId, invoice.copy(id = newId))
        } else {
          onError(result.exceptionOrNull()?.localizedMessage ?: "Failed to save invoice")
        }
      }
    }
  }

  fun recordPayment(payment: com.example.data.model.Payment, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
    viewModelScope.launch {
      val res = repository.recordPayment(payment)
      if (res.isSuccess) {
        onSuccess()
      } else {
        onError(res.exceptionOrNull()?.localizedMessage ?: "Failed to record payment")
      }
    }
  }

  fun cancelInvoice(invoiceId: String, reason: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    viewModelScope.launch {
      val res = repository.cancelInvoice(invoiceId, reason)
      if (res.isSuccess) {
        onSuccess()
      } else {
        onError(res.exceptionOrNull()?.localizedMessage ?: "Failed to cancel invoice")
      }
    }
  }
}
