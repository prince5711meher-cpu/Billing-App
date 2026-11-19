package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Invoice
import com.example.ui.printer.PrinterSettingsScreen
import com.example.ui.printer.PrinterViewModel
import com.example.ui.screens.BillingViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InvoicesScreen
import com.example.ui.screens.ItemsScreen
import com.example.ui.screens.ItemsViewModel
import com.example.ui.screens.NewBillScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SettingsViewModel
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.TextSecondary
import com.example.ui.update.UpdateViewModel

enum class NavDestination(val label: String, val icon: ImageVector) {
  HOME("Home", Icons.Default.Dashboard),
  INVOICES("Bills", Icons.Default.Receipt),
  NEW_BILL("New Bill", Icons.Default.Add),
  ITEMS("Items", Icons.Default.Inventory2),
  REPORTS("Reports", Icons.Default.Assessment),
  SETTINGS("Settings", Icons.Default.Settings),
  PRINTER_SETTINGS("Printer", Icons.Default.Print)
}

private val BottomNavDestinations = listOf(
  NavDestination.HOME,
  NavDestination.INVOICES,
  NavDestination.NEW_BILL,
  NavDestination.ITEMS,
  NavDestination.REPORTS
)

@Composable
fun AppNavigation(
  onSignOut: () -> Unit,
  billingViewModel: BillingViewModel = viewModel(),
  settingsViewModel: SettingsViewModel = viewModel(),
  itemsViewModel: ItemsViewModel = viewModel(),
  updateViewModel: UpdateViewModel = viewModel(),
  printerViewModel: PrinterViewModel = viewModel()
) {
  val stateHolder = rememberSaveableStateHolder()
  var currentDestination by remember { mutableStateOf(NavDestination.HOME) }
  var editReturnDestination by remember { mutableStateOf(NavDestination.INVOICES) }
  var printerReturnDestination by remember { mutableStateOf(NavDestination.SETTINGS) }
  var selectedInvoiceForDetail by remember { mutableStateOf<Invoice?>(null) }

  val startEditing: (Invoice, NavDestination) -> Unit = remember(billingViewModel) {
    { invoice, origin ->
      editReturnDestination = origin
      billingViewModel.startEditingInvoice(invoice)
      currentDestination = NavDestination.NEW_BILL
    }
  }

  val openPrinterSettings: (NavDestination) -> Unit = remember {
    { origin ->
      printerReturnDestination = origin
      currentDestination = NavDestination.PRINTER_SETTINGS
    }
  }

  // Handle system back navigation
  if (currentDestination != NavDestination.HOME) {
    BackHandler {
      currentDestination = if (currentDestination == NavDestination.PRINTER_SETTINGS) {
        printerReturnDestination
      } else {
        NavDestination.HOME
      }
    }
  }

  Scaffold(
    bottomBar = {
      NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
      ) {
        BottomNavDestinations.forEach { destination ->
          val isSelected = currentDestination == destination

          NavigationBarItem(
            selected = isSelected,
            onClick = { currentDestination = destination },
            icon = {
              if (destination == NavDestination.NEW_BILL) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .background(PrimaryBlue, shape = CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.label,
                    tint = SurfaceLight,
                    modifier = Modifier.size(24.dp)
                  )
                }
              } else {
                Icon(
                  imageVector = destination.icon,
                  contentDescription = destination.label
                )
              }
            },
            label = {
              Text(
                text = destination.label,
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  fontSize = 11.sp
                )
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = PrimaryBlue,
              selectedTextColor = PrimaryBlue,
              indicatorColor = PrimaryBlue.copy(alpha = 0.12f),
              unselectedIconColor = TextSecondary,
              unselectedTextColor = TextSecondary
            ),
            modifier = Modifier.testTag("nav_${destination.name.lowercase()}")
          )
        }
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      stateHolder.SaveableStateProvider(currentDestination.name) {
        when (currentDestination) {
          NavDestination.HOME -> {
            HomeScreen(
              billingViewModel = billingViewModel,
              settingsViewModel = settingsViewModel,
              onNavigateToNewBill = { currentDestination = NavDestination.NEW_BILL },
              onNavigateToInvoices = { currentDestination = NavDestination.INVOICES },
              onNavigateToReports = { currentDestination = NavDestination.REPORTS },
              onNavigateToSettings = { currentDestination = NavDestination.SETTINGS },
              onSelectInvoice = { invoice ->
                selectedInvoiceForDetail = invoice
                currentDestination = NavDestination.INVOICES
              },
              onEditInvoice = { invoice ->
                startEditing(invoice, NavDestination.HOME)
              }
            )
          }

          NavDestination.INVOICES -> {
            InvoicesScreen(
              viewModel = billingViewModel,
              settingsViewModel = settingsViewModel,
              printerViewModel = printerViewModel,
              selectedInvoice = selectedInvoiceForDetail,
              onClearSelectedInvoice = { selectedInvoiceForDetail = null },
              onEditInvoice = { invoice ->
                startEditing(invoice, NavDestination.INVOICES)
              },
              onOpenPrinterSettings = { openPrinterSettings(NavDestination.INVOICES) }
            )
          }

          NavDestination.NEW_BILL -> {
            NewBillScreen(
              viewModel = billingViewModel,
              settingsViewModel = settingsViewModel,
              itemsViewModel = itemsViewModel,
              printerViewModel = printerViewModel,
              onOpenPrinterSettings = { openPrinterSettings(NavDestination.NEW_BILL) },
              onBillSaved = {
                // Keep user on NewBillScreen for WhatsApp share or creating another bill
              },
              onCancelEdit = {
                currentDestination = editReturnDestination
              }
            )
          }

          NavDestination.ITEMS -> {
            ItemsScreen(
              itemsViewModel = itemsViewModel,
              onAddToBill = { savedItem ->
                billingViewModel.addItem(
                  description = savedItem.name,
                  quantity = 1.0,
                  unitLabel = "pcs",
                  rateMinor = savedItem.rateMinor
                )
              },
              onNavigateToNewBill = { currentDestination = NavDestination.NEW_BILL },
              onNavigateToSettings = { currentDestination = NavDestination.SETTINGS }
            )
          }

          NavDestination.REPORTS -> {
            ReportsScreen(
              billingViewModel = billingViewModel,
              settingsViewModel = settingsViewModel,
              onNavigateToSettings = { currentDestination = NavDestination.SETTINGS }
            )
          }

          NavDestination.SETTINGS -> {
            SettingsScreen(
              viewModel = settingsViewModel,
              updateViewModel = updateViewModel,
              printerViewModel = printerViewModel,
              onOpenPrinterSettings = { openPrinterSettings(NavDestination.SETTINGS) },
              onSignOut = onSignOut
            )
          }

          NavDestination.PRINTER_SETTINGS -> {
            PrinterSettingsScreen(
              viewModel = printerViewModel,
              onBack = { currentDestination = printerReturnDestination }
            )
          }
        }
      }
    }
  }
}
