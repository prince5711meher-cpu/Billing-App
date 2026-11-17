package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.domain.MoneyFormatter
import com.example.data.model.Invoice
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
  billingViewModel: BillingViewModel,
  settingsViewModel: SettingsViewModel,
  onNavigateToNewBill: () -> Unit,
  onNavigateToInvoices: () -> Unit,
  onNavigateToReports: () -> Unit,
  onNavigateToSettings: () -> Unit,
  onSelectInvoice: (Invoice) -> Unit,
  onEditInvoice: (Invoice) -> Unit
) {
  val invoices by billingViewModel.invoicesState.collectAsState()
  val payments by billingViewModel.paymentsState.collectAsState()
  val settings by settingsViewModel.settingsState.collectAsState()

  // Calculate today's sales and collections
  val todayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
  val todayStr = todayFormat.format(Date())

  val todayInvoices = invoices.filter { inv ->
    inv.createdAt != null && todayFormat.format(inv.createdAt.toDate()) == todayStr && inv.status != "CANCELLED"
  }

  val todayGrossSales = todayInvoices.sumOf { it.grandTotalMinor }
  val todayPaidCash = todayInvoices.filter { it.paymentMethod == "CASH" }.sumOf { it.amountPaidMinor }
  val todayPaidUpi = todayInvoices.filter { it.paymentMethod == "UPI" }.sumOf { it.amountPaidMinor }
  val todayUdhar = todayInvoices.sumOf { it.balanceDueMinor }
  val totalUdharDue = invoices.filter { it.status != "CANCELLED" }.sumOf { it.balanceDueMinor }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Top Greeting & Store Identity
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = settings.businessName.ifBlank { "Billing App" },
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
          )
          Text(
            text = "Counter Dashboard • ${SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date())}",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )
        }

        IconButton(
          onClick = onNavigateToSettings,
          modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PrimaryBlue.copy(alpha = 0.1f))
        ) {
          Icon(Icons.Default.Settings, contentDescription = "Settings", tint = PrimaryBlue)
        }
      }
    }

    // Hero Today's Sales Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
        ) {
          Text(
            text = "Today's Gross Sales",
            style = MaterialTheme.typography.bodyMedium.copy(color = SurfaceLight.copy(alpha = 0.85f))
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = MoneyFormatter.formatPaise(todayGrossSales),
            style = MaterialTheme.typography.displayLarge.copy(
              fontWeight = FontWeight.Bold,
              color = SurfaceLight
            )
          )

          Spacer(modifier = Modifier.height(16.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${todayInvoices.size} bills issued today",
              style = MaterialTheme.typography.bodySmall.copy(color = SurfaceLight.copy(alpha = 0.9f))
            )

            Button(
              onClick = onNavigateToNewBill,
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceLight,
                contentColor = PrimaryBlue
              )
            ) {
              Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("New Bill", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // Metric Highlights: Cash, UPI, Today's Udhar, Total Due
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          MetricTile(
            title = "Cash Sales (नकद)",
            value = MoneyFormatter.formatPaise(todayPaidCash),
            color = CashColor,
            modifier = Modifier.weight(1f)
          )
          MetricTile(
            title = "UPI Sales (ऑनलाइन)",
            value = MoneyFormatter.formatPaise(todayPaidUpi),
            color = UpiColor,
            modifier = Modifier.weight(1f)
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          MetricTile(
            title = "Today's Udhar (उधार)",
            value = MoneyFormatter.formatPaise(todayUdhar),
            color = UdharColor,
            modifier = Modifier.weight(1f)
          )
          MetricTile(
            title = "Total Pending Udhar",
            value = MoneyFormatter.formatPaise(totalUdharDue),
            color = WarningOrange,
            modifier = Modifier.weight(1f)
          )
        }
      }
    }

    // Quick Action Shortcuts (New Bill, Invoices, Reports, Settings)
    item {
      Text(
        text = "Quick Actions",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
      )
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        QuickActionButton(
          label = "New Bill",
          icon = Icons.Default.AddCircle,
          onClick = onNavigateToNewBill,
          modifier = Modifier.weight(1f)
        )
        QuickActionButton(
          label = "Bills",
          icon = Icons.Default.Receipt,
          onClick = onNavigateToInvoices,
          modifier = Modifier.weight(1f)
        )
        QuickActionButton(
          label = "Reports",
          icon = Icons.Default.Assessment,
          onClick = onNavigateToReports,
          modifier = Modifier.weight(1f)
        )
        QuickActionButton(
          label = "Settings",
          icon = Icons.Default.Settings,
          onClick = onNavigateToSettings,
          modifier = Modifier.weight(1f)
        )
      }
    }

    // Recent Invoices Section
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Recent Bills",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
          text = "View All (${invoices.size})",
          style = MaterialTheme.typography.labelSmall.copy(
            color = PrimaryBlue,
            fontWeight = FontWeight.SemiBold
          ),
          modifier = Modifier.clickable { onNavigateToInvoices() }
        )
      }
    }

    if (invoices.isEmpty()) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(Icons.Default.Receipt, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("No Invoices Yet", fontWeight = FontWeight.Bold)
            Text(
              "Tap 'New Bill' above to create your store's first retail invoice.",
              style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
              modifier = Modifier.padding(top = 4.dp)
            )
          }
        }
      }
    } else {
      items(invoices.take(5)) { invoice ->
        InvoiceListItem(
          invoice = invoice,
          onClick = { onSelectInvoice(invoice) },
          onEditClick = if (invoice.status != "CANCELLED") {
            { onEditInvoice(invoice) }
          } else null
        )
      }
    }

    item {
      Spacer(modifier = Modifier.height(40.dp))
    }
  }
}

@Composable
fun MetricTile(
  title: String,
  value: String,
  color: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          color = color,
          fontSize = 15.sp
        )
      )
    }
  }
}

@Composable
fun QuickActionButton(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.clickable { onClick() },
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp, horizontal = 4.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(icon, contentDescription = label, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.Medium,
          fontSize = 11.sp
        )
      )
    }
  }
}

@Composable
fun InvoiceListItem(
  invoice: Invoice,
  onClick: () -> Unit,
  onEditClick: (() -> Unit)? = null
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() },
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 14.dp, end = if (onEditClick != null) 6.dp else 14.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = invoice.invoiceNumber,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
          )
          Spacer(modifier = Modifier.width(8.dp))
          StatusBadge(status = invoice.status)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "${invoice.customerName} • ${invoice.items.size} items",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = MoneyFormatter.formatPaise(invoice.grandTotalMinor),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
          if (invoice.balanceDueMinor > 0) {
            Text(
              text = "Due: " + MoneyFormatter.formatPaise(invoice.balanceDueMinor),
              style = MaterialTheme.typography.labelSmall.copy(color = UdharColor, fontWeight = FontWeight.Bold)
            )
          }
        }
        if (onEditClick != null) {
          IconButton(onClick = onEditClick) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = "Edit ${invoice.invoiceNumber}",
              tint = PrimaryBlue,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
fun StatusBadge(status: String) {
  val (bgColor, textColor, label) = when (status.uppercase()) {
    "PAID" -> Triple(SuccessGreen.copy(alpha = 0.15f), SuccessGreen, "Paid")
    "PARTIALLY_PAID" -> Triple(WarningOrange.copy(alpha = 0.15f), WarningOrange, "Part Due")
    "UNPAID", "UDHAR" -> Triple(UdharColor.copy(alpha = 0.15f), UdharColor, "Udhar")
    "CANCELLED" -> Triple(TextSecondary.copy(alpha = 0.15f), TextSecondary, "Cancelled")
    "QUOTATION" -> Triple(PrimaryBlue.copy(alpha = 0.15f), PrimaryBlue, "Estimate")
    else -> Triple(BorderLight, TextSecondary, status)
  }

  Box(
    modifier = Modifier
      .background(bgColor, RoundedCornerShape(6.dp))
      .padding(horizontal = 6.dp, vertical = 2.dp)
  ) {
    Text(
      text = label,
      color = textColor,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

