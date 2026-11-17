package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.domain.MoneyFormatter
import com.example.data.model.SavedItem
import com.example.ui.theme.BorderLight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(
  itemsViewModel: ItemsViewModel,
  onAddToBill: (SavedItem) -> Unit,
  onNavigateToNewBill: () -> Unit,
  onNavigateToSettings: () -> Unit
) {
  val savedItems by itemsViewModel.itemsState.collectAsState()
  val isSaving by itemsViewModel.isSaving.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  var searchQuery by remember { mutableStateOf("") }
  var showItemSheet by remember { mutableStateOf(false) }
  var itemBeingEdited by remember { mutableStateOf<SavedItem?>(null) }
  var itemPendingDelete by remember { mutableStateOf<SavedItem?>(null) }

  val filteredItems = remember(savedItems, searchQuery) {
    val q = searchQuery.trim()
    if (q.isEmpty()) {
      savedItems
    } else {
      savedItems.filter { it.name.contains(q, ignoreCase = true) }
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Saved Items",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
          )
          Text(
            text = "${savedItems.size} items • Auto-saved with name & price",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier.testTag("items_settings_button")
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Settings",
              tint = TextSecondary
            )
          }
          Spacer(modifier = Modifier.width(4.dp))
          Button(
            onClick = {
              itemBeingEdited = null
              showItemSheet = true
            },
            modifier = Modifier
              .height(48.dp)
              .testTag("add_saved_item_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("New Item", fontWeight = FontWeight.SemiBold)
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Search Bar
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("search_saved_items_input"),
        placeholder = { Text("Search saved items by name...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Close, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
      )

      Spacer(modifier = Modifier.height(12.dp))

      if (filteredItems.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, BorderLight)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Box(
                modifier = Modifier
                  .size(56.dp)
                  .background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Inventory2,
                  contentDescription = null,
                  tint = PrimaryBlue,
                  modifier = Modifier.size(28.dp)
                )
              }
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = if (savedItems.isEmpty()) "No Saved Items Yet" else "No Matching Items",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = if (savedItems.isEmpty()) {
                  "Whenever you add an item while creating a bill, its name and price are automatically saved here for 1-tap reuse."
                } else {
                  "Try searching with a different keyword or add a new item."
                },
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
              )
              if (savedItems.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                  onClick = {
                    itemBeingEdited = null
                    showItemSheet = true
                  },
                  modifier = Modifier.height(48.dp),
                  shape = RoundedCornerShape(10.dp)
                ) {
                  Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Add First Item")
                }
              }
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(filteredItems, key = { it.id }) { item ->
            SavedItemCard(
              item = item,
              onAddToBill = {
                onAddToBill(item)
                coroutineScope.launch {
                  val res = snackbarHostState.showSnackbar(
                    message = "Added ${item.name} to bill",
                    actionLabel = "View Bill",
                    duration = SnackbarDuration.Short
                  )
                  if (res == SnackbarResult.ActionPerformed) {
                    onNavigateToNewBill()
                  }
                }
              },
              onEdit = {
                itemBeingEdited = item
                showItemSheet = true
              },
              onDelete = {
                itemPendingDelete = item
              }
            )
          }

          item {
            Spacer(modifier = Modifier.height(24.dp))
          }
        }
      }
    }
  }

  if (showItemSheet) {
    EditSavedItemSheet(
      initialItem = itemBeingEdited,
      isSaving = isSaving,
      onDismiss = { showItemSheet = false },
      onSave = { name, ratePaise ->
        itemsViewModel.saveItem(
          existingItem = itemBeingEdited,
          name = name,
          rateMinor = ratePaise,
          onSuccess = {
            showItemSheet = false
            coroutineScope.launch {
              snackbarHostState.showSnackbar(
                if (itemBeingEdited != null) "Updated $name" else "Saved $name"
              )
            }
          },
          onError = { err ->
            coroutineScope.launch { snackbarHostState.showSnackbar(err) }
          }
        )
      }
    )
  }

  itemPendingDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { itemPendingDelete = null },
      title = { Text("Delete Saved Item?") },
      text = {
        Text("Remove \"${target.name}\" from your saved items list? Previously saved bills will not be affected.")
      },
      confirmButton = {
        Button(
          onClick = {
            val name = target.name
            itemsViewModel.deleteItem(
              itemId = target.id,
              onSuccess = {
                coroutineScope.launch { snackbarHostState.showSnackbar("Deleted $name") }
              },
              onError = { err ->
                coroutineScope.launch { snackbarHostState.showSnackbar(err) }
              }
            )
            itemPendingDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = WarningOrange)
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { itemPendingDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun SavedItemCard(
  item: SavedItem,
  onAddToBill: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("saved_item_card_${item.id}"),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, BorderLight),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = item.name,
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Billed ${item.useCount}×",
            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
          )
        }

        Text(
          text = MoneyFormatter.formatPaise(item.rateMinor),
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue
          )
        )
      }

      Spacer(modifier = Modifier.height(10.dp))
      HorizontalDivider(color = BorderLight.copy(alpha = 0.7f))
      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Button(
          onClick = onAddToBill,
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("add_saved_to_bill_${item.id}"),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add to Bill", fontWeight = FontWeight.SemiBold)
        }

        IconButton(
          onClick = onEdit,
          modifier = Modifier
            .size(48.dp)
            .testTag("edit_saved_item_${item.id}")
        ) {
          Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit ${item.name}",
            tint = PrimaryBlue
          )
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .size(48.dp)
            .testTag("delete_saved_item_${item.id}")
        ) {
          Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete ${item.name}",
            tint = WarningOrange
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSavedItemSheet(
  initialItem: SavedItem?,
  isSaving: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    rateMinor: Long
  ) -> Unit
) {
  var nameText by remember(initialItem) { mutableStateOf(initialItem?.name ?: "") }
  var rateText by remember(initialItem) {
    mutableStateOf(
      if (initialItem != null && initialItem.rateMinor > 0L) {
        if (initialItem.rateMinor % 100L == 0L) (initialItem.rateMinor / 100L).toString()
        else (initialItem.rateMinor / 100.0).toString()
      } else ""
    )
  }
  var errorMsg by remember { mutableStateOf<String?>(null) }

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
        text = if (initialItem == null) "Add Saved Item" else "Edit Saved Item",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
      )

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = nameText,
        onValueChange = { nameText = it; errorMsg = null },
        label = { Text("Item Name") },
        placeholder = { Text("e.g. Steel Thali, Rice 1kg") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("saved_item_name_input"),
        singleLine = true,
        shape = RoundedCornerShape(10.dp)
      )

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedTextField(
        value = rateText,
        onValueChange = { rateText = it; errorMsg = null },
        label = { Text("Price (₹)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("saved_item_rate_input"),
        singleLine = true,
        shape = RoundedCornerShape(10.dp)
      )

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
            val cleanName = nameText.trim()
            val ratePaise = MoneyFormatter.parseRupeesToPaise(rateText)
            if (cleanName.isBlank()) {
              errorMsg = "Enter item name"
              return@Button
            }
            if (ratePaise <= 0L) {
              errorMsg = "Enter valid price in ₹"
              return@Button
            }
            onSave(cleanName, ratePaise)
          },
          enabled = !isSaving,
          modifier = Modifier
            .weight(1.5f)
            .height(48.dp)
            .testTag("save_catalog_item_button"),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
          Text(if (initialItem == null) "Save Item" else "Update Item", fontWeight = FontWeight.SemiBold)
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}
