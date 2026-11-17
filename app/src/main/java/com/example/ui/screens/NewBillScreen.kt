package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.domain.InvoiceEngine
import com.example.data.domain.MoneyFormatter
import com.example.data.model.BusinessSettings
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.data.model.SavedItem
import com.example.ui.theme.BorderLight
import com.example.ui.theme.CashColor
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UdharColor
import com.example.ui.theme.UpiColor
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewBillScreen(
  viewModel: BillingViewModel,
  settingsViewModel: SettingsViewModel,
  itemsViewModel: ItemsViewModel,
  onBillSaved: (String) -> Unit,
  onCancelEdit: () -> Unit = {}
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val draft by viewModel.draft.collectAsState()
  val isSaving by viewModel.isSaving.collectAsState()
  val settings by settingsViewModel.settingsState.collectAsState()
  val savedItems by itemsViewModel.itemsState.collectAsState()

  // Form input states for adding an item
  var itemDescription by remember { mutableStateOf("") }
  var itemQty by remember { mutableStateOf("1") }
  var itemUnit by remember { mutableStateOf("pcs") }
  var itemRate by remember { mutableStateOf("") }
  var itemDiscountType by remember { mutableStateOf("FIXED") }
  var itemDiscountValue by remember { mutableStateOf("") }

  // Smooth text inputs for overall discount and received amount
  var overallDiscountInput by remember(draft.editingInvoiceId) {
    mutableStateOf(formatCleanNumber(draft.discountValue))
  }
  var amountPaidInput by remember(draft.editingInvoiceId) {
    mutableStateOf(formatCleanPaiseInput(draft.amountPaidMinor))
  }

  LaunchedEffect(draft.amountPaidMinor) {
    val parsed = MoneyFormatter.parseRupeesToPaise(amountPaidInput)
    if (parsed != draft.amountPaidMinor) {
      amountPaidInput = formatCleanPaiseInput(draft.amountPaidMinor)
    }
  }

  LaunchedEffect(draft.discountValue) {
    val parsed = overallDiscountInput.toDoubleOrNull() ?: 0.0
    if (draft.discountValue == 0.0 && parsed != 0.0) {
      overallDiscountInput = ""
    } else if (draft.discountValue > 0.0 && parsed != draft.discountValue) {
      overallDiscountInput = formatCleanNumber(draft.discountValue)
    }
  }

  // Edit Line Item state
  var editingItemIndex by remember { mutableStateOf<Int?>(null) }

  // Save success sheet state
  var showSuccessSheet by remember { mutableStateOf(false) }
  var savedReceiptWasEdit by remember { mutableStateOf(false) }
  var lastSavedInvoiceSummary by remember { mutableStateOf<String?>(null) }

  val calculation = draft.calculation

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      // Sticky Grand Total & Final Action Footer
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "Total Payable",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val (modeLabel, modeColor) = when (draft.paymentMethod) {
                  "UDHAR" -> Pair("Udhar (उधार)", UdharColor)
                  "UPI" -> Pair("UPI (ऑनलाइन)", UpiColor)
                  else -> Pair("Cash (नकद)", CashColor)
                }
                Box(
                  modifier = Modifier
                    .background(modeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(text = modeLabel, color = modeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
              }
              Text(
                text = MoneyFormatter.formatPaise(calculation.grandTotalMinor),
                style = MaterialTheme.typography.headlineMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = PrimaryBlue
                )
              )
            }

            if (draft.amountPaidMinor < calculation.grandTotalMinor || draft.paymentMethod == "UDHAR") {
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "Balance Due (उधार)",
                  style = MaterialTheme.typography.bodySmall.copy(color = UdharColor, fontWeight = FontWeight.SemiBold)
                )
                Text(
                  text = MoneyFormatter.formatPaise(calculation.balanceDueMinor),
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = UdharColor
                  )
                )
              }
            } else {
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "Paid in Full",
                  style = MaterialTheme.typography.bodySmall.copy(color = SuccessGreen)
                )
                Text(
                  text = MoneyFormatter.formatPaise(draft.amountPaidMinor),
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                  )
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = {
                val wasEditing = draft.isEditing
                viewModel.resetDraft()
                overallDiscountInput = ""
                amountPaidInput = ""
                if (wasEditing) {
                  onCancelEdit()
                }
              },
              modifier = Modifier
                .weight(1f)
                .height(48.dp),
              shape = RoundedCornerShape(10.dp)
            ) {
              Text(if (draft.isEditing) "Cancel Edit" else "Clear")
            }

            Button(
              onClick = {
                val wasEdit = draft.isEditing
                viewModel.saveInvoice(
                  invoicePrefix = settings.invoicePrefix,
                  onSuccess = { invoiceId, savedInvoice ->
                    overallDiscountInput = ""
                    amountPaidInput = ""
                    savedReceiptWasEdit = wasEdit
                    lastSavedInvoiceSummary = buildSavedInvoiceShareText(savedInvoice, settings)
                    showSuccessSheet = true
                    onBillSaved(invoiceId)
                  },
                  onError = { msg ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                  }
                )
              },
              enabled = !isSaving && draft.items.isNotEmpty(),
              modifier = Modifier
                .weight(2f)
                .height(48.dp)
                .testTag("save_bill_button"),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
              if (isSaving) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  color = SurfaceLight,
                  strokeWidth = 2.dp
                )
              } else {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = when {
                    draft.isEditing -> "Update Bill"
                    draft.documentType == "QUOTATION" -> "Save Quotation"
                    else -> "Save & Print Bill"
                  },
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Edit Mode Notice Banner
      if (draft.isEditing) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.12f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                  imageVector = Icons.Default.Edit,
                  contentDescription = null,
                  tint = PrimaryBlue,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    text = "Editing ${draft.editingInvoiceNumber ?: "Saved Bill"}",
                    style = MaterialTheme.typography.titleSmall.copy(
                      fontWeight = FontWeight.Bold,
                      color = PrimaryBlue
                    )
                  )
                  Text(
                    text = "Edit items, item discounts, overall discount, or payment and tap Update Bill.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                  )
                }
              }
              IconButton(
                onClick = {
                  viewModel.resetDraft()
                  overallDiscountInput = ""
                  amountPaidInput = ""
                  onCancelEdit()
                }
              ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel Edit", tint = PrimaryBlue)
              }
            }
          }
        }
      }

      // Header: Document Type and Billing Mode Switchers
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = if (draft.isEditing) "Edit Counter Bill" else "New Billing Counter",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )

              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                  selected = draft.documentType == "INVOICE",
                  onClick = { viewModel.setDocumentType("INVOICE") },
                  label = { Text("Bill", fontSize = 12.sp) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryBlue,
                    selectedLabelColor = SurfaceLight
                  )
                )
                FilterChip(
                  selected = draft.documentType == "QUOTATION",
                  onClick = { viewModel.setDocumentType("QUOTATION") },
                  label = { Text("Estimate", fontSize = 12.sp) }
                )
              }
            }
          }
        }
      }

      // Manual Item Entry Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Add Item Line (Manual Entry)",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
              value = itemDescription,
              onValueChange = { itemDescription = it },
              label = { Text("Item Description (e.g. Steel Thali, Pressure Cooker 3L)") },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("item_description_input"),
              singleLine = true,
              shape = RoundedCornerShape(10.dp)
            )

            val trimmedQuery = itemDescription.trim()
            val normalizedQuery = SavedItem.normalize(trimmedQuery)
            val matchingSuggestions = remember(savedItems, trimmedQuery, normalizedQuery) {
              if (trimmedQuery.isEmpty()) {
                emptyList()
              } else {
                val exactAlreadyFilled = savedItems.any { it.normalizedName == normalizedQuery }
                if (exactAlreadyFilled) {
                  emptyList()
                } else {
                  savedItems.filter { it.name.contains(trimmedQuery, ignoreCase = true) }.take(5)
                }
              }
            }

            if (matchingSuggestions.isNotEmpty()) {
              Spacer(modifier = Modifier.height(6.dp))
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("item_suggestions_list"),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.06f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.25f))
              ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                  matchingSuggestions.forEachIndexed { idx, suggestion ->
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                          itemDescription = suggestion.name
                          itemRate = formatCleanPaiseInput(suggestion.rateMinor)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = suggestion.name,
                          style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                          )
                        )
                      }
                      Text(
                        text = MoneyFormatter.formatPaise(suggestion.rateMinor),
                        style = MaterialTheme.typography.titleSmall.copy(
                          fontWeight = FontWeight.Bold,
                          color = PrimaryBlue
                        )
                      )
                    }
                    if (idx < matchingSuggestions.lastIndex) {
                      HorizontalDivider(color = BorderLight.copy(alpha = 0.5f))
                    }
                  }
                }
              }
            } else if (trimmedQuery.isEmpty() && savedItems.isNotEmpty()) {
              val quickPicks = remember(savedItems) {
                savedItems.sortedByDescending { it.useCount }.take(6)
              }
              Spacer(modifier = Modifier.height(6.dp))
              LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                items(quickPicks, key = { it.id }) { pick ->
                  FilterChip(
                    selected = false,
                    onClick = {
                      itemDescription = pick.name
                      itemRate = formatCleanPaiseInput(pick.rateMinor)
                    },
                    label = {
                      Text(
                        text = "${pick.name} • ${MoneyFormatter.formatPaise(pick.rateMinor)}",
                        fontSize = 11.sp
                      )
                    }
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Qty, Unit, Rate row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedTextField(
                value = itemQty,
                onValueChange = { itemQty = it },
                label = { Text("Qty") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                  .weight(1f)
                  .testTag("item_qty_input"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              OutlinedTextField(
                value = itemUnit,
                onValueChange = { itemUnit = it },
                label = { Text("Unit") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )

              OutlinedTextField(
                value = itemRate,
                onValueChange = { itemRate = it },
                label = { Text("Rate (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                  .weight(1.5f)
                  .testTag("item_rate_input"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Item-level Discount Row
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Item Disc:", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.width(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  FilterChip(
                    selected = itemDiscountType == "FIXED",
                    onClick = { itemDiscountType = "FIXED" },
                    label = { Text("₹ Flat", fontSize = 11.sp) }
                  )
                  FilterChip(
                    selected = itemDiscountType == "PERCENTAGE",
                    onClick = { itemDiscountType = "PERCENTAGE" },
                    label = { Text("% Off", fontSize = 11.sp) }
                  )
                }
              }

                OutlinedTextField(
                value = itemDiscountValue,
                onValueChange = { itemDiscountValue = it },
                placeholder = { Text("0") },
                label = { Text(if (itemDiscountType == "PERCENTAGE") "Disc %" else "Disc ₹", fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                  .width(105.dp)
                  .testTag("item_discount_input"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
              onClick = {
                val qty = itemQty.toDoubleOrNull() ?: 1.0
                val ratePaise = MoneyFormatter.parseRupeesToPaise(itemRate)
                val discVal = maxOf(0.0, itemDiscountValue.toDoubleOrNull() ?: 0.0)
                if (itemDescription.isBlank()) {
                  coroutineScope.launch { snackbarHostState.showSnackbar("Enter item description") }
                  return@Button
                }
                if (qty <= 0.0) {
                  coroutineScope.launch { snackbarHostState.showSnackbar("Quantity must be greater than 0") }
                  return@Button
                }
                if (ratePaise <= 0L) {
                  coroutineScope.launch { snackbarHostState.showSnackbar("Enter valid item rate") }
                  return@Button
                }

                val cleanName = itemDescription.trim()
                val cleanUnit = itemUnit.trim().ifBlank { "pcs" }

                viewModel.addItem(
                  description = cleanName,
                  quantity = qty,
                  unitLabel = cleanUnit,
                  rateMinor = ratePaise,
                  discountType = itemDiscountType,
                  discountValue = discVal
                )

                itemsViewModel.saveOrUpdateFromBilling(
                  name = cleanName,
                  rateMinor = ratePaise
                )

                // Reset item entry fields
                itemDescription = ""
                itemQty = "1"
                itemRate = ""
                itemDiscountValue = ""
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("add_item_button"),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Add Line Item", fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }

      // Line Items List Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Bill Items (${draft.items.size})",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
          )
          if (draft.items.isNotEmpty()) {
            Text(
              text = "Tap any item to edit",
              style = MaterialTheme.typography.labelSmall.copy(color = PrimaryBlue)
            )
          }
        }
      }

      if (draft.items.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
              .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
              .padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Default.Receipt,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(36.dp)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "No items added yet",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
              )
              Text(
                text = "Enter item description and rate above to start billing",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
              )
            }
          }
        }
      } else {
        itemsIndexed(draft.items) { index, item ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { editingItemIndex = index }
              .testTag("bill_item_card_$index"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${index + 1}. ${item.description}",
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "${formatCleanNumber(item.quantity).ifBlank { "0" }} ${item.unitLabel} × ${MoneyFormatter.formatPaise(item.rateMinor)}",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                if (item.discountMinor > 0) {
                  val discLabel = if (item.discountType == "PERCENTAGE" && item.discountValue > 0.0) {
                    "Disc ${formatCleanNumber(item.discountValue)}%: -${MoneyFormatter.formatPaise(item.discountMinor)}"
                  } else {
                    "Item Disc: -${MoneyFormatter.formatPaise(item.discountMinor)}"
                  }
                  Text(
                    text = discLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                      color = SuccessGreen,
                      fontWeight = FontWeight.Medium
                    )
                  )
                }
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = MoneyFormatter.formatPaise(item.lineTotalMinor),
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                  )
                )
                IconButton(
                  onClick = { editingItemIndex = index },
                  modifier = Modifier.testTag("edit_item_$index")
                ) {
                  Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit item ${item.description}",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(19.dp)
                  )
                }
                IconButton(
                  onClick = { viewModel.removeItem(index) },
                  modifier = Modifier.testTag("delete_item_$index")
                ) {
                  Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove item ${item.description}",
                    tint = WarningOrange,
                    modifier = Modifier.size(19.dp)
                  )
                }
              }
            }
          }
        }
      }

      // Bill Summary & Overall Discount Card
      if (draft.items.isNotEmpty()) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                text = "Summary & Overall Discount",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
              )

              Spacer(modifier = Modifier.height(10.dp))

              if (calculation.itemDiscountTotalMinor > 0) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text("Items Gross", color = TextSecondary, fontSize = 13.sp)
                  Text(
                    MoneyFormatter.formatPaise(calculation.itemsGrossSubtotalMinor),
                    fontSize = 13.sp
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text("Item Discounts Total", color = SuccessGreen, fontSize = 13.sp)
                  Text(
                    "-${MoneyFormatter.formatPaise(calculation.itemDiscountTotalMinor)}",
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                  )
                }
                Spacer(modifier = Modifier.height(6.dp))
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Subtotal", color = TextSecondary)
                Text(
                  MoneyFormatter.formatPaise(calculation.subtotalMinor),
                  fontWeight = FontWeight.SemiBold
                )
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Overall Discount Controller Row
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text("Overall Disc:", color = TextSecondary, fontSize = 13.sp)
                  Spacer(modifier = Modifier.width(6.dp))
                  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                      selected = draft.discountType == "FIXED",
                      onClick = { viewModel.setDiscount("FIXED", overallDiscountInput.toDoubleOrNull() ?: 0.0) },
                      label = { Text("₹ Flat", fontSize = 11.sp) }
                    )
                    FilterChip(
                      selected = draft.discountType == "PERCENTAGE",
                      onClick = { viewModel.setDiscount("PERCENTAGE", overallDiscountInput.toDoubleOrNull() ?: 0.0) },
                      label = { Text("% Off", fontSize = 11.sp) }
                    )
                  }
                }

                OutlinedTextField(
                  value = overallDiscountInput,
                  onValueChange = { text ->
                    overallDiscountInput = text
                    val num = maxOf(0.0, text.toDoubleOrNull() ?: 0.0)
                    viewModel.setDiscount(draft.discountType, num)
                  },
                  placeholder = { Text("0") },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  modifier = Modifier
                    .width(96.dp)
                    .testTag("overall_discount_input"),
                  singleLine = true,
                  shape = RoundedCornerShape(8.dp)
                )
              }

              if (calculation.discountMinor > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  val label = if (draft.discountType == "PERCENTAGE") {
                    "Overall Discount (${formatCleanNumber(draft.discountValue)}%)"
                  } else {
                    "Overall Discount"
                  }
                  Text(label, color = SuccessGreen, fontSize = 13.sp)
                  Text(
                    "-${MoneyFormatter.formatPaise(calculation.discountMinor)}",
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                  )
                }
              }

              Spacer(modifier = Modifier.height(12.dp))
              HorizontalDivider(color = BorderLight)
              Spacer(modifier = Modifier.height(12.dp))

              // Payment Mode Selection Header
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Payment Mode (भुगतान):",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                val badgeText = when (draft.paymentMethod) {
                  "UDHAR" -> "Pay Later / Udhar"
                  "UPI" -> "Online UPI"
                  else -> "Cash Counter"
                }
                val badgeColor = when (draft.paymentMethod) {
                  "UDHAR" -> UdharColor
                  "UPI" -> UpiColor
                  else -> CashColor
                }
                Box(
                  modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                  Text(text = badgeText, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              // 3 Prominent Payment Cards: Cash, UPI, Udhar
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                PaymentModeCard(
                  title = "Cash (नकद)",
                  subtitle = "At Counter",
                  icon = Icons.Default.Payments,
                  isSelected = draft.paymentMethod == "CASH",
                  themeColor = CashColor,
                  onClick = { viewModel.setPaymentMethod("CASH") },
                  modifier = Modifier.weight(1f)
                )

                PaymentModeCard(
                  title = "UPI (ऑनलाइन)",
                  subtitle = "QR / GPay",
                  icon = Icons.Default.QrCodeScanner,
                  isSelected = draft.paymentMethod == "UPI",
                  themeColor = UpiColor,
                  onClick = { viewModel.setPaymentMethod("UPI") },
                  modifier = Modifier.weight(1f)
                )

                PaymentModeCard(
                  title = "Udhar (उधार)",
                  subtitle = "Pay Later",
                  icon = Icons.Default.PendingActions,
                  isSelected = draft.paymentMethod == "UDHAR",
                  themeColor = UdharColor,
                  onClick = { viewModel.setPaymentMethod("UDHAR") },
                  modifier = Modifier.weight(1f)
                )
              }

              Spacer(modifier = Modifier.height(12.dp))

              // Dynamic Payment Guidance & Details Container
              when (draft.paymentMethod) {
                "UDHAR" -> {
                  Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = UdharColor.copy(alpha = 0.12f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, UdharColor.copy(alpha = 0.5f))
                  ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                          imageVector = Icons.Default.PendingActions,
                          contentDescription = null,
                          tint = UdharColor,
                          modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                          text = "Full ${MoneyFormatter.formatPaise(calculation.grandTotalMinor)} marked as Udhar (Pay Later)",
                          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = UdharColor)
                        )
                      }
                      Spacer(modifier = Modifier.height(8.dp))
                      OutlinedTextField(
                        value = draft.walkInName,
                        onValueChange = { viewModel.setWalkInDetails(it, draft.walkInPhone) },
                        label = { Text("Customer Name / Note (Optional)", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Ramesh, Shop 4") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                      )
                    }
                  }
                }
                else -> {
                  Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                      containerColor = if (draft.paymentMethod == "UPI") UpiColor.copy(alpha = 0.08f) else CashColor.copy(alpha = 0.08f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                      1.dp,
                      (if (draft.paymentMethod == "UPI") UpiColor else CashColor).copy(alpha = 0.35f)
                    )
                  ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(
                            imageVector = if (draft.paymentMethod == "UPI") Icons.Default.QrCodeScanner else Icons.Default.Payments,
                            contentDescription = null,
                            tint = if (draft.paymentMethod == "UPI") UpiColor else CashColor,
                            modifier = Modifier.size(18.dp)
                          )
                          Spacer(modifier = Modifier.width(8.dp))
                          Column {
                            Text(
                              text = if (draft.paymentMethod == "UPI") "Paid via UPI" else "Paid in Cash",
                              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            if (draft.amountPaidMinor != calculation.grandTotalMinor) {
                              TextButton(
                                onClick = { viewModel.setAmountPaid(calculation.grandTotalMinor) },
                                modifier = Modifier.height(30.dp)
                              ) {
                                Text("Mark Full", fontSize = 11.sp, color = PrimaryBlue)
                              }
                            }
                          }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Text(
                            text = "Received (₹): ",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 12.sp)
                          )
                          OutlinedTextField(
                            value = amountPaidInput,
                            onValueChange = { text ->
                              amountPaidInput = text
                              val p = MoneyFormatter.parseRupeesToPaise(text)
                              viewModel.setAmountPaid(p)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(105.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                          )
                        }
                      }

                      if (draft.amountPaidMinor < calculation.grandTotalMinor) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                          text = "⚠️ Partial payment! Remaining ${MoneyFormatter.formatPaise(calculation.balanceDueMinor)} is marked as Udhar / Due.",
                          style = MaterialTheme.typography.bodySmall.copy(color = UdharColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(80.dp))
      }
    }
  }

  // Edit Line Item Bottom Sheet
  editingItemIndex?.let { index ->
    val targetItem = draft.items.getOrNull(index)
    if (targetItem != null) {
      EditLineItemSheet(
        itemIndex = index,
        initialItem = targetItem,
        onDismiss = { editingItemIndex = null },
        onSave = { desc, qty, unit, ratePaise, discType, discVal ->
          viewModel.updateItem(
            index = index,
            description = desc,
            quantity = qty,
            unitLabel = unit,
            rateMinor = ratePaise,
            discountType = discType,
            discountValue = discVal
          )
          itemsViewModel.saveOrUpdateFromBilling(
            name = desc,
            rateMinor = ratePaise
          )
          editingItemIndex = null
        }
      )
    }
  }

  // Bill Saved / Updated Success & WhatsApp Share Sheet
  if (showSuccessSheet && lastSavedInvoiceSummary != null) {
    ModalBottomSheet(
      onDismissRequest = { showSuccessSheet = false },
      sheetState = rememberModalBottomSheetState()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          modifier = Modifier
            .size(56.dp)
            .background(SuccessGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = if (savedReceiptWasEdit) "Bill Updated Successfully!" else "Bill Finalized Successfully!",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = if (savedReceiptWasEdit) {
            "Your edited bill changes have been saved and synchronized."
          } else {
            "The bill has been saved and synchronized with your account."
          },
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
          onClick = {
            val sendIntent = Intent().apply {
              action = Intent.ACTION_SEND
              putExtra(Intent.EXTRA_TEXT, lastSavedInvoiceSummary)
              type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Receipt via WhatsApp"))
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
        ) {
          Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Share Receipt on WhatsApp", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
          onClick = { showSuccessSheet = false },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text("Create Another Bill")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLineItemSheet(
  itemIndex: Int,
  initialItem: InvoiceItem,
  onDismiss: () -> Unit,
  onSave: (
    description: String,
    quantity: Double,
    unitLabel: String,
    rateMinor: Long,
    discountType: String,
    discountValue: Double
  ) -> Unit
) {
  var description by remember(itemIndex, initialItem) { mutableStateOf(initialItem.description) }
  var qtyText by remember(itemIndex, initialItem) { mutableStateOf(formatCleanNumber(initialItem.quantity).ifBlank { "1" }) }
  var unitText by remember(itemIndex, initialItem) { mutableStateOf(initialItem.unitLabel) }
  var rateText by remember(itemIndex, initialItem) { mutableStateOf(formatCleanPaiseInput(initialItem.rateMinor)) }
  var discType by remember(itemIndex, initialItem) { mutableStateOf(initialItem.discountType) }
  var discText by remember(itemIndex, initialItem) {
    val initialDiscVal = if (initialItem.discountValue > 0.0) {
      initialItem.discountValue
    } else {
      initialItem.discountMinor / 100.0
    }
    mutableStateOf(formatCleanNumber(initialDiscVal))
  }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  val parsedQty = maxOf(0.0, qtyText.toDoubleOrNull() ?: 0.0)
  val parsedRatePaise = MoneyFormatter.parseRupeesToPaise(rateText)
  val parsedDiscVal = maxOf(0.0, discText.toDoubleOrNull() ?: 0.0)
  val previewItem = InvoiceEngine.calculateLineItem(
    description = description,
    quantity = parsedQty,
    unitLabel = unitText.ifBlank { "pcs" },
    rateMinor = parsedRatePaise,
    discountType = discType,
    discountValue = parsedDiscVal,
    isGstMode = false
  )
  val grossPaise = (parsedQty * parsedRatePaise).toLong()

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
      Text(
        text = "Edit Item #${itemIndex + 1}",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
      )

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = description,
        onValueChange = { description = it; errorMsg = null },
        label = { Text("Item Description") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("edit_dialog_description"),
        singleLine = true,
        shape = RoundedCornerShape(10.dp)
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Quantity with quick stepper & direct edit
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        IconButton(
          onClick = {
            val current = qtyText.toDoubleOrNull() ?: 1.0
            val next = maxOf(1.0, current - 1.0)
            qtyText = formatCleanNumber(next).ifBlank { "1" }
          }
        ) {
          Icon(Icons.Default.Remove, contentDescription = "Decrease quantity", tint = PrimaryBlue)
        }

        OutlinedTextField(
          value = qtyText,
          onValueChange = { qtyText = it; errorMsg = null },
          label = { Text("Qty") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier
            .weight(1f)
            .testTag("edit_dialog_qty"),
          singleLine = true,
          shape = RoundedCornerShape(10.dp)
        )

        IconButton(
          onClick = {
            val current = qtyText.toDoubleOrNull() ?: 0.0
            val next = current + 1.0
            qtyText = formatCleanNumber(next).ifBlank { "1" }
          }
        ) {
          Icon(Icons.Default.Add, contentDescription = "Increase quantity", tint = PrimaryBlue)
        }

        OutlinedTextField(
          value = unitText,
          onValueChange = { unitText = it },
          label = { Text("Unit") },
          modifier = Modifier.weight(0.9f),
          singleLine = true,
          shape = RoundedCornerShape(10.dp)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedTextField(
        value = rateText,
        onValueChange = { rateText = it; errorMsg = null },
        label = { Text("Rate per unit (₹)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("edit_dialog_rate"),
        singleLine = true,
        shape = RoundedCornerShape(10.dp)
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Per-Item Discount Editor
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Item Disc:", fontSize = 13.sp, color = TextSecondary)
          Spacer(modifier = Modifier.width(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
              selected = discType == "FIXED",
              onClick = { discType = "FIXED" },
              label = { Text("₹ Flat", fontSize = 11.sp) }
            )
            FilterChip(
              selected = discType == "PERCENTAGE",
              onClick = { discType = "PERCENTAGE" },
              label = { Text("% Off", fontSize = 11.sp) }
            )
          }
        }

        OutlinedTextField(
          value = discText,
          onValueChange = { discText = it },
          placeholder = { Text("0") },
          label = { Text(if (discType == "PERCENTAGE") "Disc %" else "Disc ₹", fontSize = 11.sp) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier
            .width(110.dp)
            .testTag("edit_dialog_discount"),
          singleLine = true,
          shape = RoundedCornerShape(8.dp)
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Live Line Total Preview Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f))
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Gross Amount", fontSize = 12.sp, color = TextSecondary)
            Text(MoneyFormatter.formatPaise(grossPaise), fontSize = 12.sp)
          }
          if (previewItem.discountMinor > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Item Discount", fontSize = 12.sp, color = SuccessGreen)
              Text("-${MoneyFormatter.formatPaise(previewItem.discountMinor)}", fontSize = 12.sp, color = SuccessGreen)
            }
          }
          HorizontalDivider(color = BorderLight)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Net Item Total", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
              MoneyFormatter.formatPaise(previewItem.lineTotalMinor),
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = PrimaryBlue
            )
          }
        }
      }

      if (errorMsg != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(errorMsg!!, color = WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier
            .weight(1f)
            .height(48.dp),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text("Cancel")
        }
        Button(
          onClick = {
            if (description.isBlank()) {
              errorMsg = "Enter item description"
              return@Button
            }
            if (parsedQty <= 0.0) {
              errorMsg = "Quantity must be greater than 0"
              return@Button
            }
            if (parsedRatePaise <= 0L) {
              errorMsg = "Enter valid item rate"
              return@Button
            }
            onSave(
              description.trim(),
              parsedQty,
              unitText.trim().ifBlank { "pcs" },
              parsedRatePaise,
              discType,
              parsedDiscVal
            )
          },
          modifier = Modifier
            .weight(1.5f)
            .height(48.dp)
            .testTag("save_edited_item_button"),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
          Text("Update Item", fontWeight = FontWeight.SemiBold)
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}

private fun formatCleanNumber(value: Double): String {
  if (value == 0.0) return ""
  return if (value % 1.0 == 0.0) {
    value.toLong().toString()
  } else {
    value.toString()
  }
}

private fun formatCleanPaiseInput(paise: Long): String {
  if (paise <= 0L) return ""
  return if (paise % 100L == 0L) {
    (paise / 100L).toString()
  } else {
    (paise / 100.0).toString()
  }
}

fun buildReceiptShareText(draft: NewBillDraft, settings: BusinessSettings): String {
  val calc = draft.calculation
  val invoice = Invoice(
    invoiceNumber = draft.editingInvoiceNumber ?: "DRAFT",
    documentType = draft.documentType,
    customerName = draft.walkInName.trim().ifBlank { "Counter Customer" },
    billingMode = draft.billingMode,
    subtotalMinor = calc.subtotalMinor,
    discountType = draft.discountType,
    discountValue = draft.discountValue,
    discountMinor = calc.discountMinor,
    cgstMinor = calc.cgstMinor,
    sgstMinor = calc.sgstMinor,
    igstMinor = calc.igstMinor,
    grandTotalMinor = calc.grandTotalMinor,
    amountPaidMinor = draft.amountPaidMinor,
    balanceDueMinor = calc.balanceDueMinor,
    paymentMethod = draft.paymentMethod,
    status = if (calc.balanceDueMinor == 0L) "PAID" else if (draft.amountPaidMinor == 0L) "UNPAID" else "PARTIALLY_PAID",
    items = calc.items
  )
  return buildSavedInvoiceShareText(invoice, settings)
}

@Composable
fun PaymentModeCard(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isSelected: Boolean,
  themeColor: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val surfaceColor = MaterialTheme.colorScheme.surface
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface

  Card(
    modifier = modifier.clickable { onClick() },
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) themeColor.copy(alpha = 0.16f) else surfaceColor
    ),
    border = androidx.compose.foundation.BorderStroke(
      width = if (isSelected) 2.dp else 1.dp,
      color = if (isSelected) themeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp, horizontal = 4.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .background(
            color = if (isSelected) themeColor else themeColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(8.dp)
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = if (isSelected) SurfaceLight else themeColor,
          modifier = Modifier.size(20.dp)
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.Bold,
          color = if (isSelected) themeColor else onSurfaceColor
        ),
        maxLines = 1
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall.copy(
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        maxLines = 1
      )
    }
  }
}
