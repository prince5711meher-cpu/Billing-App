package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.domain.InvoiceCalculationResult
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

private val BillShape16 = RoundedCornerShape(16.dp)
private val BillShape14 = RoundedCornerShape(14.dp)
private val BillShape12 = RoundedCornerShape(12.dp)
private val BillShape10 = RoundedCornerShape(10.dp)
private val BillShape8 = RoundedCornerShape(8.dp)
private val BillShape6 = RoundedCornerShape(6.dp)
private val BillShape4 = RoundedCornerShape(4.dp)

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
  val scrollState = rememberScrollState()

  val draft by viewModel.draft.collectAsStateWithLifecycle()
  val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
  val settings by settingsViewModel.settingsState.collectAsStateWithLifecycle()
  val savedItems by itemsViewModel.itemsState.collectAsStateWithLifecycle()

  val colorScheme = MaterialTheme.colorScheme
  val surfaceColor = colorScheme.surface
  val borderColor = colorScheme.outline
  val textPrimary = colorScheme.onSurface
  val textSecondary = colorScheme.onSurfaceVariant

  var clearSignal by remember { mutableIntStateOf(0) }
  var editingItemIndex by remember { mutableStateOf<Int?>(null) }
  var showSuccessSheet by remember { mutableStateOf(false) }
  var savedReceiptWasEdit by remember { mutableStateOf(false) }
  var lastSavedInvoiceSummary by remember { mutableStateOf<String?>(null) }

  val calculation = draft.calculation

  Column(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        if (draft.isEditing) {
          EditModeBannerCard(
            invoiceNumber = draft.editingInvoiceNumber,
            onCancelEdit = {
              viewModel.resetDraft()
              clearSignal++
              onCancelEdit()
            }
          )
        }

        BillHeaderCard(
          isEditing = draft.isEditing,
          documentType = draft.documentType,
          surfaceColor = surfaceColor,
          borderColor = borderColor,
          onSelectDocumentType = { viewModel.setDocumentType(it) }
        )

        ManualItemEntryCard(
          savedItems = savedItems,
          surfaceColor = surfaceColor,
          borderColor = borderColor,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          onValidationError = { msg ->
            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
          },
          onAddItem = { name, qty, unit, ratePaise, discType, discVal ->
            viewModel.addItem(
              description = name,
              quantity = qty,
              unitLabel = unit,
              rateMinor = ratePaise,
              discountType = discType,
              discountValue = discVal
            )
            itemsViewModel.saveOrUpdateFromBilling(
              name = name,
              rateMinor = ratePaise
            )
          }
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Bill Items (${draft.items.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = textPrimary
          )
          if (draft.items.isNotEmpty()) {
            Text(
              text = "Tap any item to edit",
              style = MaterialTheme.typography.labelSmall,
              color = PrimaryBlue
            )
          }
        }

        if (draft.items.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(surfaceColor, BillShape12)
              .border(1.dp, borderColor, BillShape12)
              .padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Default.Receipt,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(36.dp)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "No items added yet",
                style = MaterialTheme.typography.bodyMedium,
                color = textSecondary
              )
              Text(
                text = "Enter item description and rate above to start billing",
                style = MaterialTheme.typography.labelSmall,
                color = textSecondary
              )
            }
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            draft.items.forEachIndexed { index, item ->
              key(index) {
                BillLineItemCard(
                  index = index,
                  item = item,
                  surfaceColor = surfaceColor,
                  borderColor = borderColor,
                  textPrimary = textPrimary,
                  textSecondary = textSecondary,
                  onEditClick = { editingItemIndex = index },
                  onDeleteClick = { viewModel.removeItem(index) }
                )
              }
            }
          }
        }

        if (draft.items.isNotEmpty()) {
          BillSummaryAndPaymentCard(
            draft = draft,
            calculation = calculation,
            clearSignal = clearSignal,
            surfaceColor = surfaceColor,
            borderColor = borderColor,
            textSecondary = textSecondary,
            onSetDiscount = { type, value -> viewModel.setDiscount(type, value) },
            onSetPaymentMethod = { viewModel.setPaymentMethod(it) },
            onSetWalkInName = { viewModel.setWalkInDetails(it, draft.walkInPhone) },
            onSetAmountPaid = { viewModel.setAmountPaid(it) }
          )
        }
      }

      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(8.dp)
      )
    }

    BillStickyFooter(
      grandTotalMinor = calculation.grandTotalMinor,
      balanceDueMinor = calculation.balanceDueMinor,
      amountPaidMinor = draft.amountPaidMinor,
      paymentMethod = draft.paymentMethod,
      isEditing = draft.isEditing,
      documentType = draft.documentType,
      isSaving = isSaving,
      hasItems = draft.items.isNotEmpty(),
      onClearOrCancel = {
        val wasEditing = draft.isEditing
        viewModel.resetDraft()
        clearSignal++
        if (wasEditing) {
          onCancelEdit()
        }
      },
      onSaveBill = {
        val wasEdit = draft.isEditing
        viewModel.saveInvoice(
          invoicePrefix = settings.invoicePrefix,
          onSuccess = { invoiceId, savedInvoice ->
            clearSignal++
            savedReceiptWasEdit = wasEdit
            lastSavedInvoiceSummary = buildSavedInvoiceShareText(savedInvoice, settings)
            showSuccessSheet = true
            onBillSaved(invoiceId)
          },
          onError = { msg ->
            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
          }
        )
      }
    )
  }

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
            .background(SuccessGreen.copy(alpha = 0.15f), shape = BillShape16),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = if (savedReceiptWasEdit) "Bill Updated Successfully!" else "Bill Finalized Successfully!",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = if (savedReceiptWasEdit) {
            "Your edited bill changes have been saved and synchronized."
          } else {
            "The bill has been saved and synchronized with your account."
          },
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary
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
          shape = BillShape10,
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
          shape = BillShape10
        ) {
          Text("Create Another Bill")
        }
      }
    }
  }
}

@Composable
private fun BillStickyFooter(
  grandTotalMinor: Long,
  balanceDueMinor: Long,
  amountPaidMinor: Long,
  paymentMethod: String,
  isEditing: Boolean,
  documentType: String,
  isSaving: Boolean,
  hasItems: Boolean,
  onClearOrCancel: () -> Unit,
  onSaveBill: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 8.dp),
    shape = BillShape16,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, BorderLight),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
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
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            val (modeLabel, modeColor) = when (paymentMethod) {
              "UDHAR" -> Pair("Udhar (उधार)", UdharColor)
              "UPI" -> Pair("UPI (ऑनलाइन)", UpiColor)
              else -> Pair("Cash (नकद)", CashColor)
            }
            Box(
              modifier = Modifier
                .background(modeColor.copy(alpha = 0.15f), BillShape4)
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(text = modeLabel, color = modeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
          }
          Text(
            text = MoneyFormatter.formatPaise(grandTotalMinor),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue
          )
        }

        if (amountPaidMinor < grandTotalMinor || paymentMethod == "UDHAR") {
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "Balance Due (उधार)",
              style = MaterialTheme.typography.bodySmall,
              color = UdharColor,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = MoneyFormatter.formatPaise(balanceDueMinor),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = UdharColor
            )
          }
        } else {
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "Paid in Full",
              style = MaterialTheme.typography.bodySmall,
              color = SuccessGreen
            )
            Text(
              text = MoneyFormatter.formatPaise(amountPaidMinor),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = SuccessGreen
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = onClearOrCancel,
          modifier = Modifier
            .weight(1f)
            .height(48.dp),
          shape = BillShape10
        ) {
          Text(if (isEditing) "Cancel Edit" else "Clear")
        }

        Button(
          onClick = onSaveBill,
          enabled = !isSaving && hasItems,
          modifier = Modifier
            .weight(2f)
            .height(48.dp)
            .testTag("save_bill_button"),
          shape = BillShape10,
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
                isEditing -> "Update Bill"
                documentType == "QUOTATION" -> "Save Quotation"
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

@Composable
private fun EditModeBannerCard(
  invoiceNumber: String?,
  onCancelEdit: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(PrimaryBlue.copy(alpha = 0.12f), BillShape12)
      .border(1.dp, PrimaryBlue.copy(alpha = 0.5f), BillShape12)
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
          text = "Editing ${invoiceNumber ?: "Saved Bill"}",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = PrimaryBlue
        )
        Text(
          text = "Edit items, item discounts, overall discount, or payment and tap Update Bill.",
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
          fontSize = 11.sp
        )
      }
    }
    IconButton(onClick = onCancelEdit) {
      Icon(Icons.Default.Close, contentDescription = "Cancel Edit", tint = PrimaryBlue)
    }
  }
}

@Composable
private fun BillHeaderCard(
  isEditing: Boolean,
  documentType: String,
  surfaceColor: Color,
  borderColor: Color,
  onSelectDocumentType: (String) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(surfaceColor, BillShape14)
      .border(1.dp, borderColor, BillShape14)
      .padding(16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = if (isEditing) "Edit Counter Bill" else "New Billing Counter",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      FilterChip(
        selected = documentType == "INVOICE",
        onClick = { onSelectDocumentType("INVOICE") },
        label = { Text("Bill", fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
          selectedContainerColor = PrimaryBlue,
          selectedLabelColor = SurfaceLight
        )
      )
      FilterChip(
        selected = documentType == "QUOTATION",
        onClick = { onSelectDocumentType("QUOTATION") },
        label = { Text("Estimate", fontSize = 12.sp) }
      )
    }
  }
}

@Composable
private fun ManualItemEntryCard(
  savedItems: List<SavedItem>,
  surfaceColor: Color,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  onValidationError: (String) -> Unit,
  onAddItem: (
    name: String,
    qty: Double,
    unit: String,
    ratePaise: Long,
    discountType: String,
    discountVal: Double
  ) -> Unit
) {
  var itemDescription by remember { mutableStateOf("") }
  var itemQty by remember { mutableStateOf("1") }
  var itemUnit by remember { mutableStateOf("pcs") }
  var itemRate by remember { mutableStateOf("") }
  var itemDiscountType by remember { mutableStateOf("FIXED") }
  var itemDiscountValue by remember { mutableStateOf("") }
  val quickPickScrollState = rememberScrollState()

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
  val quickPicks = remember(savedItems) {
    savedItems.sortedByDescending { it.useCount }.take(6)
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(surfaceColor, BillShape14)
      .border(1.dp, borderColor, BillShape14)
      .padding(16.dp)
  ) {
    Text(
      text = "Add Item Line (Manual Entry)",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = textPrimary
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
      shape = BillShape10
    )

    if (matchingSuggestions.isNotEmpty()) {
      Spacer(modifier = Modifier.height(6.dp))
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(PrimaryBlue.copy(alpha = 0.06f), BillShape10)
          .border(1.dp, PrimaryBlue.copy(alpha = 0.25f), BillShape10)
          .padding(vertical = 4.dp)
          .testTag("item_suggestions_list")
      ) {
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
            Text(
              text = suggestion.name,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = textPrimary,
              modifier = Modifier.weight(1f)
            )
            Text(
              text = MoneyFormatter.formatPaise(suggestion.rateMinor),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = PrimaryBlue
            )
          }
          if (idx < matchingSuggestions.lastIndex) {
            HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
          }
        }
      }
    } else if (trimmedQuery.isEmpty() && quickPicks.isNotEmpty()) {
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(quickPickScrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        for (pick in quickPicks) {
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
        shape = BillShape10
      )

      OutlinedTextField(
        value = itemUnit,
        onValueChange = { itemUnit = it },
        label = { Text("Unit") },
        modifier = Modifier.weight(1f),
        singleLine = true,
        shape = BillShape10
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
        shape = BillShape10
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Item Disc:", fontSize = 12.sp, color = textSecondary)
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
        shape = BillShape8
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
      onClick = {
        val qty = itemQty.toDoubleOrNull() ?: 1.0
        val ratePaise = MoneyFormatter.parseRupeesToPaise(itemRate)
        val discVal = maxOf(0.0, itemDiscountValue.toDoubleOrNull() ?: 0.0)
        if (itemDescription.isBlank()) {
          onValidationError("Enter item description")
          return@Button
        }
        if (qty <= 0.0) {
          onValidationError("Quantity must be greater than 0")
          return@Button
        }
        if (ratePaise <= 0L) {
          onValidationError("Enter valid item rate")
          return@Button
        }

        val cleanName = itemDescription.trim()
        val cleanUnit = itemUnit.trim().ifBlank { "pcs" }
        onAddItem(cleanName, qty, cleanUnit, ratePaise, itemDiscountType, discVal)

        itemDescription = ""
        itemQty = "1"
        itemRate = ""
        itemDiscountValue = ""
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("add_item_button"),
      shape = BillShape10,
      colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
    ) {
      Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text("Add Line Item", fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
private fun BillLineItemCard(
  index: Int,
  item: InvoiceItem,
  surfaceColor: Color,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  onEditClick: () -> Unit,
  onDeleteClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(BillShape12)
      .background(surfaceColor, BillShape12)
      .border(1.dp, borderColor, BillShape12)
      .clickable(onClick = onEditClick)
      .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
      .testTag("bill_item_card_$index"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "${index + 1}. ${item.description}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = textPrimary
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = "${formatCleanNumber(item.quantity).ifBlank { "0" }} ${item.unitLabel} × ${MoneyFormatter.formatPaise(item.rateMinor)}",
        style = MaterialTheme.typography.bodySmall,
        color = textSecondary
      )
      if (item.discountMinor > 0) {
        val discLabel = if (item.discountType == "PERCENTAGE" && item.discountValue > 0.0) {
          "Disc ${formatCleanNumber(item.discountValue)}%: -${MoneyFormatter.formatPaise(item.discountMinor)}"
        } else {
          "Item Disc: -${MoneyFormatter.formatPaise(item.discountMinor)}"
        }
        Text(
          text = discLabel,
          style = MaterialTheme.typography.labelSmall,
          color = SuccessGreen,
          fontWeight = FontWeight.Medium
        )
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = MoneyFormatter.formatPaise(item.lineTotalMinor),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = textPrimary
      )
      IconButton(
        onClick = onEditClick,
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
        onClick = onDeleteClick,
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

@Composable
private fun BillSummaryAndPaymentCard(
  draft: NewBillDraft,
  calculation: InvoiceCalculationResult,
  clearSignal: Int,
  surfaceColor: Color,
  borderColor: Color,
  textSecondary: Color,
  onSetDiscount: (type: String, value: Double) -> Unit,
  onSetPaymentMethod: (String) -> Unit,
  onSetWalkInName: (String) -> Unit,
  onSetAmountPaid: (Long) -> Unit
) {
  var overallDiscountInput by remember(draft.editingInvoiceId, clearSignal) {
    mutableStateOf(formatCleanNumber(draft.discountValue))
  }
  var amountPaidInput by remember(draft.editingInvoiceId, clearSignal) {
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

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(surfaceColor, BillShape14)
      .border(1.dp, borderColor, BillShape14)
      .padding(16.dp)
  ) {
    Text(
      text = "Summary & Overall Discount",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(10.dp))

    if (calculation.itemDiscountTotalMinor > 0) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text("Items Gross", color = textSecondary, fontSize = 13.sp)
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
      Text("Subtotal", color = textSecondary)
      Text(
        MoneyFormatter.formatPaise(calculation.subtotalMinor),
        fontWeight = FontWeight.SemiBold
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Overall Disc:", color = textSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          FilterChip(
            selected = draft.discountType == "FIXED",
            onClick = { onSetDiscount("FIXED", overallDiscountInput.toDoubleOrNull() ?: 0.0) },
            label = { Text("₹ Flat", fontSize = 11.sp) }
          )
          FilterChip(
            selected = draft.discountType == "PERCENTAGE",
            onClick = { onSetDiscount("PERCENTAGE", overallDiscountInput.toDoubleOrNull() ?: 0.0) },
            label = { Text("% Off", fontSize = 11.sp) }
          )
        }
      }

      OutlinedTextField(
        value = overallDiscountInput,
        onValueChange = { text ->
          overallDiscountInput = text
          val num = maxOf(0.0, text.toDoubleOrNull() ?: 0.0)
          onSetDiscount(draft.discountType, num)
        },
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
          .width(96.dp)
          .testTag("overall_discount_input"),
        singleLine = true,
        shape = BillShape8
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
    HorizontalDivider(color = borderColor)
    Spacer(modifier = Modifier.height(12.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Payment Mode (भुगतान):",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
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
          .background(badgeColor.copy(alpha = 0.15f), BillShape6)
          .padding(horizontal = 8.dp, vertical = 3.dp)
      ) {
        Text(text = badgeText, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

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
        onClick = { onSetPaymentMethod("CASH") },
        modifier = Modifier.weight(1f)
      )

      PaymentModeCard(
        title = "UPI (ऑनलाइन)",
        subtitle = "QR / GPay",
        icon = Icons.Default.QrCodeScanner,
        isSelected = draft.paymentMethod == "UPI",
        themeColor = UpiColor,
        onClick = { onSetPaymentMethod("UPI") },
        modifier = Modifier.weight(1f)
      )

      PaymentModeCard(
        title = "Udhar (उधार)",
        subtitle = "Pay Later",
        icon = Icons.Default.PendingActions,
        isSelected = draft.paymentMethod == "UDHAR",
        themeColor = UdharColor,
        onClick = { onSetPaymentMethod("UDHAR") },
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    when (draft.paymentMethod) {
      "UDHAR" -> {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(UdharColor.copy(alpha = 0.12f), BillShape10)
            .border(1.dp, UdharColor.copy(alpha = 0.5f), BillShape10)
            .padding(12.dp)
        ) {
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
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Bold,
              color = UdharColor
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = draft.walkInName,
            onValueChange = onSetWalkInName,
            label = { Text("Customer Name / Note (Optional)", fontSize = 12.sp) },
            placeholder = { Text("e.g. Ramesh, Shop 4") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = BillShape8
          )
        }
      }
      else -> {
        val modeThemeColor = if (draft.paymentMethod == "UPI") UpiColor else CashColor
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(modeThemeColor.copy(alpha = 0.08f), BillShape10)
            .border(1.dp, modeThemeColor.copy(alpha = 0.35f), BillShape10)
            .padding(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (draft.paymentMethod == "UPI") Icons.Default.QrCodeScanner else Icons.Default.Payments,
                contentDescription = null,
                tint = modeThemeColor,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = if (draft.paymentMethod == "UPI") "Paid via UPI" else "Paid in Cash",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold
                )
                if (draft.amountPaidMinor != calculation.grandTotalMinor) {
                  TextButton(
                    onClick = { onSetAmountPaid(calculation.grandTotalMinor) },
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
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary,
                fontSize = 12.sp
              )
              OutlinedTextField(
                value = amountPaidInput,
                onValueChange = { text ->
                  amountPaidInput = text
                  val p = MoneyFormatter.parseRupeesToPaise(text)
                  onSetAmountPaid(p)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(105.dp),
                singleLine = true,
                shape = BillShape8
              )
            }
          }

          if (draft.amountPaidMinor < calculation.grandTotalMinor) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "⚠️ Partial payment! Remaining ${MoneyFormatter.formatPaise(calculation.balanceDueMinor)} is marked as Udhar / Due.",
              style = MaterialTheme.typography.bodySmall,
              color = UdharColor,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
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
  val previewItem = remember(description, parsedQty, unitText, parsedRatePaise, discType, parsedDiscVal) {
    InvoiceEngine.calculateLineItem(
      description = description,
      quantity = parsedQty,
      unitLabel = unitText.ifBlank { "pcs" },
      rateMinor = parsedRatePaise,
      discountType = discType,
      discountValue = parsedDiscVal,
      isGstMode = false
    )
  }
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
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
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
        shape = BillShape10
      )

      Spacer(modifier = Modifier.height(10.dp))

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
          shape = BillShape10
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
          shape = BillShape10
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
        shape = BillShape10
      )

      Spacer(modifier = Modifier.height(10.dp))

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
          shape = BillShape8
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(PrimaryBlue.copy(alpha = 0.08f), BillShape10)
          .border(1.dp, PrimaryBlue.copy(alpha = 0.3f), BillShape10)
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
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
          shape = BillShape10
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
          shape = BillShape10,
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
  icon: ImageVector,
  isSelected: Boolean,
  themeColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val colorScheme = MaterialTheme.colorScheme
  val bgColor = if (isSelected) themeColor.copy(alpha = 0.16f) else colorScheme.surface
  val borderStrokeColor = if (isSelected) themeColor else colorScheme.outline.copy(alpha = 0.35f)

  Column(
    modifier = modifier
      .clip(BillShape12)
      .background(bgColor, BillShape12)
      .border(if (isSelected) 2.dp else 1.dp, borderStrokeColor, BillShape12)
      .clickable(onClick = onClick)
      .padding(vertical = 12.dp, horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .background(
          color = if (isSelected) themeColor else themeColor.copy(alpha = 0.12f),
          shape = BillShape8
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
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = if (isSelected) themeColor else colorScheme.onSurface,
      maxLines = 1
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodySmall,
      fontSize = 10.sp,
      color = colorScheme.onSurfaceVariant,
      maxLines = 1
    )
  }
}
