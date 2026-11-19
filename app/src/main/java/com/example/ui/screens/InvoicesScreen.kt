package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.domain.MoneyFormatter
import com.example.data.model.BusinessSettings
import com.example.data.model.Invoice
import com.example.data.model.Payment
import com.example.ui.printer.PrinterViewModel
import com.example.ui.printer.ReceiptPrintActionSection
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CashColor
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UdharColor
import com.example.ui.theme.UpiColor
import com.example.ui.theme.WarningOrange
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(
  viewModel: BillingViewModel,
  settingsViewModel: SettingsViewModel,
  printerViewModel: PrinterViewModel = viewModel(),
  selectedInvoice: Invoice? = null,
  onClearSelectedInvoice: () -> Unit = {},
  onEditInvoice: (Invoice) -> Unit = {},
  onOpenPrinterSettings: () -> Unit = {}
) {
  val context = LocalContext.current
  val invoices by viewModel.invoicesState.collectAsStateWithLifecycle()
  val settings by settingsViewModel.settingsState.collectAsStateWithLifecycle()
  val printerSettings by printerViewModel.printerSettings.collectAsStateWithLifecycle()
  val printerConnState by printerViewModel.connectionState.collectAsStateWithLifecycle()
  val printJobState by printerViewModel.printJobState.collectAsStateWithLifecycle()
  val printerLayoutSpec by printerViewModel.layoutSpec.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  val chipScrollState = rememberScrollState()

  val colorScheme = MaterialTheme.colorScheme
  val surfaceColor = colorScheme.surface
  val borderColor = colorScheme.outline
  val textPrimary = colorScheme.onSurface
  val textSecondary = colorScheme.onSurfaceVariant

  var searchQuery by remember { mutableStateOf("") }
  var filterStatus by remember { mutableStateOf("ALL") }
  var activeDetailInvoice by remember(selectedInvoice) { mutableStateOf(selectedInvoice) }

  var showPaymentDialog by remember { mutableStateOf(false) }
  var showCancelDialog by remember { mutableStateOf(false) }
  var cancelReason by remember { mutableStateOf("") }
  var paymentAmountInput by remember { mutableStateOf("") }
  var paymentMethodInput by remember { mutableStateOf("CASH") }

  // Filter & pre-format invoices once when data/query/filter changes (zero formatting work during scroll)
  val filteredRows = remember(invoices, searchQuery, filterStatus) {
    val q = searchQuery.trim()
    invoices.asSequence()
      .filter { inv ->
        val matchesSearch = q.isEmpty() ||
          inv.invoiceNumber.contains(q, ignoreCase = true) ||
          inv.customerName.contains(q, ignoreCase = true)
        val matchesFilter = when (filterStatus) {
          "PAID" -> inv.status == "PAID"
          "PARTIAL" -> inv.status == "PARTIALLY_PAID"
          "UNPAID" -> inv.status == "UNPAID"
          "QUOTATION" -> inv.documentType == "QUOTATION"
          "CANCELLED" -> inv.status == "CANCELLED"
          else -> true
        }
        matchesSearch && matchesFilter
      }
      .map { inv ->
        InvoiceRowUi(
          invoice = inv,
          subtitle = "${inv.customerName} • ${inv.items.size} items",
          formattedTotal = MoneyFormatter.formatPaise(inv.grandTotalMinor),
          formattedDue = if (inv.balanceDueMinor > 0L) {
            "Due: ${MoneyFormatter.formatPaise(inv.balanceDueMinor)}"
          } else null
        )
      }
      .toList()
  }

  val filterChips = remember(invoices.size) {
    listOf(
      "ALL" to "All (${invoices.size})",
      "PAID" to "Paid",
      "PARTIAL" to "Part Due",
      "UNPAID" to "Unpaid",
      "QUOTATION" to "Estimates",
      "CANCELLED" to "Cancelled"
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    Text(
      text = "Bills & Estimates",
      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Search Field
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      modifier = Modifier.fillMaxWidth(),
      placeholder = { Text("Search by bill # or customer note...") },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(Icons.Default.Close, contentDescription = "Clear")
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(12.dp)
    )

    Spacer(modifier = Modifier.height(10.dp))

    // Filter Chips Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(chipScrollState),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      for ((key, label) in filterChips) {
        FilterChip(
          selected = filterStatus == key,
          onClick = { filterStatus = key },
          label = { Text(label, fontSize = 12.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryBlue,
            selectedLabelColor = SurfaceLight
          )
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // List of Invoices
    if (filteredRows.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = if (searchQuery.isNotBlank()) "No bills matching \"$searchQuery\"" else "No bills in this filter",
          style = MaterialTheme.typography.bodyMedium,
          color = textSecondary
        )
      }
    } else {
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(
          items = filteredRows,
          key = { it.invoice.id.ifBlank { it.invoice.invoiceNumber } },
          contentType = { "invoice_item" }
        ) { row ->
          val invoice = row.invoice
          InvoiceRowCard(
            row = row,
            surfaceColor = surfaceColor,
            borderColor = borderColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onClick = { activeDetailInvoice = invoice },
            onEditClick = if (invoice.status != "CANCELLED") {
              { onEditInvoice(invoice) }
            } else null
          )
        }
      }
    }
  }

  // Invoice Detail Modal Sheet
  activeDetailInvoice?.let { rawInv ->
    val inv = invoices.find { it.id == rawInv.id } ?: rawInv
    ModalBottomSheet(
      onDismissRequest = {
        activeDetailInvoice = null
        onClearSelectedInvoice()
      },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 10.dp)
      ) {
        // Receipt Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = inv.invoiceNumber,
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            val dateStr = remember(inv.id, inv.createdAt) {
              inv.createdAt?.toDate()?.let {
                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it)
              } ?: "Date: Pending sync"
            }
            Text(text = dateStr, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
          }
          StatusBadge(status = inv.status)
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = BorderLight)
        Spacer(modifier = Modifier.height(12.dp))

        // Customer details
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text("Billed To:", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text(inv.customerName, fontWeight = FontWeight.SemiBold)
            if (inv.customerPhone.isNotBlank()) {
              Text(inv.customerPhone, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
            }
          }
          Column(horizontalAlignment = Alignment.End) {
            Text("Type:", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text(if (inv.documentType == "QUOTATION") "Estimate" else "Bill", fontWeight = FontWeight.SemiBold)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Line items summary table
        Text("Line Items:", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
        Spacer(modifier = Modifier.height(6.dp))

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            inv.items.forEachIndexed { i, item ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("${i + 1}. ${item.description}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                  Text(
                    "${item.quantity} ${item.unitLabel} × ${MoneyFormatter.formatPaise(item.rateMinor)}",
                    fontSize = 11.sp,
                    color = TextSecondary
                  )
                  if (item.discountMinor > 0) {
                    val discLabel = if (item.discountType == "PERCENTAGE" && item.discountValue > 0.0) {
                      "Item Disc (${item.discountValue.toInt()}%): -${MoneyFormatter.formatPaise(item.discountMinor)}"
                    } else {
                      "Item Disc: -${MoneyFormatter.formatPaise(item.discountMinor)}"
                    }
                    Text(discLabel, fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.Medium)
                  }
                }
                Text(MoneyFormatter.formatPaise(item.lineTotalMinor), fontSize = 13.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Totals Breakdown
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Subtotal", color = TextSecondary, fontSize = 13.sp)
          Text(MoneyFormatter.formatPaise(inv.subtotalMinor), fontSize = 13.sp)
        }
        if (inv.discountMinor > 0) {
          Spacer(modifier = Modifier.height(4.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val overallLabel = if (inv.discountType == "PERCENTAGE" && inv.discountValue > 0.0) {
              "Overall Discount (${inv.discountValue}%)"
            } else {
              "Overall Discount"
            }
            Text(overallLabel, color = TextSecondary, fontSize = 13.sp)
            Text("-${MoneyFormatter.formatPaise(inv.discountMinor)}", color = SuccessGreen, fontSize = 13.sp)
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = BorderLight)
        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Grand Total", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Text(MoneyFormatter.formatPaise(inv.grandTotalMinor), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryBlue)
        }

        val (methodLabel, methodColor) = when (inv.paymentMethod) {
          "UDHAR" -> Pair("Pay Later / Udhar (उधार)", UdharColor)
          "UPI" -> Pair("UPI (ऑनलाइन)", UpiColor)
          else -> Pair("Cash (नकद)", CashColor)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Payment Mode", color = TextSecondary, fontSize = 13.sp)
          Text(methodLabel, color = methodColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Amount Paid", color = TextSecondary, fontSize = 13.sp)
          Text(MoneyFormatter.formatPaise(inv.amountPaidMinor), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        if (inv.balanceDueMinor > 0) {
          Spacer(modifier = Modifier.height(4.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Balance Due (उधार)", color = UdharColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(MoneyFormatter.formatPaise(inv.balanceDueMinor), color = UdharColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
          }
        }

        if (inv.status == "CANCELLED" && inv.cancelledReason.isNotBlank()) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Cancellation reason: ${inv.cancelledReason}",
            style = MaterialTheme.typography.labelSmall.copy(color = WarningOrange)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val detailJobKey = "INVOICE_${inv.id.ifBlank { inv.invoiceNumber }}"
        ReceiptPrintActionSection(
          jobKey = detailJobKey,
          connectionState = printerConnState,
          printJobState = printJobState,
          printerSettings = printerSettings,
          layoutSpec = printerLayoutSpec,
          onPrintBill = { printerViewModel.printInvoice(inv, settings) },
          onRetryPrint = { printerViewModel.retryLastFailedPrint() },
          onOpenPrinterSettings = {
            activeDetailInvoice = null
            onClearSelectedInvoice()
            onOpenPrinterSettings()
          }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons Row 1: Edit Bill & WhatsApp Share
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (inv.status != "CANCELLED") {
            Button(
              onClick = {
                activeDetailInvoice = null
                onClearSelectedInvoice()
                onEditInvoice(inv)
              },
              modifier = Modifier
                .weight(1f)
                .height(48.dp),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
              Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Edit Bill", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
          }

          Button(
            onClick = {
              val shareText = buildSavedInvoiceShareText(inv, settings)
              val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
              }
              context.startActivity(Intent.createChooser(sendIntent, "Share Receipt on WhatsApp"))
            },
            modifier = Modifier
              .weight(1f)
              .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("WhatsApp", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
          }
        }

        // Action Buttons Row 2: Collect Due & Cancel Bill
        if (inv.status != "CANCELLED") {
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            if (inv.balanceDueMinor > 0) {
              OutlinedButton(
                onClick = {
                  paymentAmountInput = (inv.balanceDueMinor / 100.0).toString()
                  showPaymentDialog = true
                },
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp),
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Collect Due", fontSize = 13.sp)
              }
            }

            OutlinedButton(
              onClick = { showCancelDialog = true },
              modifier = Modifier
                .weight(1f)
                .height(48.dp),
              shape = RoundedCornerShape(10.dp)
            ) {
              Text("Cancel Bill", color = WarningOrange, fontSize = 13.sp)
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  // Record Payment Dialog
  if (showPaymentDialog && activeDetailInvoice != null) {
    val inv = invoices.find { it.id == activeDetailInvoice?.id } ?: activeDetailInvoice!!
    AlertDialog(
      onDismissRequest = { showPaymentDialog = false },
      title = { Text("Collect Payment for ${inv.invoiceNumber}") },
      text = {
        Column {
          Text("Balance Due: ${MoneyFormatter.formatPaise(inv.balanceDueMinor)}")
          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = paymentAmountInput,
            onValueChange = { paymentAmountInput = it },
            label = { Text("Payment Amount (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )

          Spacer(modifier = Modifier.height(10.dp))
          Text("Payment Method:", style = MaterialTheme.typography.labelSmall)
          Spacer(modifier = Modifier.height(4.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("CASH", "UPI", "CARD").forEach { m ->
              FilterChip(
                selected = paymentMethodInput == m,
                onClick = { paymentMethodInput = m },
                label = { Text(m, fontSize = 11.sp) }
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val amountPaise = MoneyFormatter.parseRupeesToPaise(paymentAmountInput)
            if (amountPaise > 0) {
              val payment = Payment(
                customerId = inv.customerId,
                customerName = inv.customerName,
                invoiceId = inv.id,
                invoiceNumber = inv.invoiceNumber,
                method = paymentMethodInput,
                amountMinor = amountPaise,
                notes = "Partial settlement for ${inv.invoiceNumber}"
              )
              viewModel.recordPayment(
                payment = payment,
                onSuccess = {
                  showPaymentDialog = false
                  activeDetailInvoice = null
                }
              )
            }
          }
        ) {
          Text("Confirm Collection")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showPaymentDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Cancel Invoice Dialog
  if (showCancelDialog && activeDetailInvoice != null) {
    val inv = invoices.find { it.id == activeDetailInvoice?.id } ?: activeDetailInvoice!!
    AlertDialog(
      onDismissRequest = { showCancelDialog = false },
      title = { Text("Cancel Invoice ${inv.invoiceNumber}?") },
      text = {
        Column {
          Text("Cancelling an invoice updates your sales and udhar summary automatically.")
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = cancelReason,
            onValueChange = { cancelReason = it },
            label = { Text("Reason for cancellation") },
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (cancelReason.isNotBlank()) {
              viewModel.cancelInvoice(
                invoiceId = inv.id,
                reason = cancelReason.trim(),
                onSuccess = {
                  showCancelDialog = false
                  activeDetailInvoice = null
                  cancelReason = ""
                },
                onError = {}
              )
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = WarningOrange)
        ) {
          Text("Confirm Cancellation")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showCancelDialog = false }) {
          Text("Back")
        }
      }
    )
  }
}

fun buildSavedInvoiceShareText(inv: Invoice, settings: BusinessSettings): String {
  val sb = StringBuilder()
  sb.appendLine("🧾 *${settings.businessName}*")
  sb.appendLine("📍 ${settings.address}")
  sb.appendLine("📞 ${settings.phone}")
  sb.appendLine("--------------------------------")
  sb.appendLine("Bill No: *${inv.invoiceNumber}*")
  sb.appendLine("Customer: ${inv.customerName}")
  sb.appendLine("Status: ${inv.status}")
  sb.appendLine("--------------------------------")
  inv.items.forEachIndexed { i, item ->
    sb.appendLine("${i + 1}. ${item.description}")
    sb.appendLine("   ${item.quantity} ${item.unitLabel} × ${MoneyFormatter.formatPaise(item.rateMinor)} = ${MoneyFormatter.formatPaise(item.lineTotalMinor)}")
    if (item.discountMinor > 0) {
      val discSuffix = if (item.discountType == "PERCENTAGE" && item.discountValue > 0.0) {
        " (${item.discountValue}%)"
      } else ""
      sb.appendLine("   Item Discount$discSuffix: -${MoneyFormatter.formatPaise(item.discountMinor)}")
    }
  }
  sb.appendLine("--------------------------------")
  sb.appendLine("Subtotal: ${MoneyFormatter.formatPaise(inv.subtotalMinor)}")
  if (inv.discountMinor > 0) {
    val overallSuffix = if (inv.discountType == "PERCENTAGE" && inv.discountValue > 0.0) {
      " (${inv.discountValue}%)"
    } else ""
    sb.appendLine("Overall Discount$overallSuffix: -${MoneyFormatter.formatPaise(inv.discountMinor)}")
  }
  sb.appendLine("*Grand Total: ${MoneyFormatter.formatPaise(inv.grandTotalMinor)}*")
  when (inv.paymentMethod) {
    "UDHAR" -> {
      sb.appendLine("Payment Status: *🔴 Pay Later / Udhar (उधार)*")
      sb.appendLine("⚠️ *Balance Due: ${MoneyFormatter.formatPaise(inv.balanceDueMinor)}*")
    }
    "UPI" -> {
      if (inv.amountPaidMinor >= inv.grandTotalMinor) {
        sb.appendLine("Payment Status: *🟢 Full Paid via UPI (ऑनलाइन)*")
      } else {
        sb.appendLine("Payment Status: *🟡 Partial UPI (${MoneyFormatter.formatPaise(inv.amountPaidMinor)})*")
        sb.appendLine("⚠️ *Balance Due (Udhar): ${MoneyFormatter.formatPaise(inv.balanceDueMinor)}*")
      }
    }
    else -> {
      if (inv.amountPaidMinor >= inv.grandTotalMinor) {
        sb.appendLine("Payment Status: *🟢 Full Paid in Cash (नकद)*")
      } else {
        sb.appendLine("Payment Status: *🟡 Partial Cash (${MoneyFormatter.formatPaise(inv.amountPaidMinor)})*")
        sb.appendLine("⚠️ *Balance Due (Udhar): ${MoneyFormatter.formatPaise(inv.balanceDueMinor)}*")
      }
    }
  }
  sb.appendLine("--------------------------------")
  sb.appendLine(settings.termsAndConditions)
  return sb.toString()
}
