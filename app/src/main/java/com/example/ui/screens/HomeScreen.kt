package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.domain.MoneyFormatter
import com.example.data.model.Invoice
import com.example.ui.theme.BrandCobaltAccent
import com.example.ui.theme.CashColor
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryNavyHero
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.UdharColor
import com.example.ui.theme.UpiColor
import com.example.ui.theme.WarningOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ShapeHeroCard = RoundedCornerShape(20.dp)
private val ShapeCard14 = RoundedCornerShape(14.dp)
private val ShapeCard12 = RoundedCornerShape(12.dp)
private val ShapeButton10 = RoundedCornerShape(10.dp)
private val ShapeBadge6 = RoundedCornerShape(6.dp)

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
  val invoices by billingViewModel.invoicesState.collectAsStateWithLifecycle()
  val settings by settingsViewModel.settingsState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()

  val colorScheme = MaterialTheme.colorScheme
  val surfaceColor = colorScheme.surface
  val borderColor = colorScheme.outline
  val textPrimary = colorScheme.onSurface
  val textSecondary = colorScheme.onSurfaceVariant

  val dashboardSummary = remember(invoices) {
    val todayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val headerFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    val now = Date()
    val todayStr = todayFormat.format(now)

    var gross = 0L
    var cash = 0L
    var upi = 0L
    var todayUdharSum = 0L
    var totalUdharSum = 0L
    var todayCount = 0

    for (inv in invoices) {
      if (inv.status == "CANCELLED") continue
      totalUdharSum += inv.balanceDueMinor
      val created = inv.createdAt
      if (created != null && todayFormat.format(created.toDate()) == todayStr) {
        todayCount++
        gross += inv.grandTotalMinor
        when (inv.paymentMethod) {
          "CASH" -> cash += inv.amountPaidMinor
          "UPI" -> upi += inv.amountPaidMinor
        }
        todayUdharSum += inv.balanceDueMinor
      }
    }

    val recentRows = invoices.take(5).map { inv ->
      InvoiceRowUi(
        invoice = inv,
        subtitle = "${inv.customerName} • ${inv.items.size} items",
        formattedTotal = MoneyFormatter.formatPaise(inv.grandTotalMinor),
        formattedDue = if (inv.balanceDueMinor > 0L) {
          "Due: ${MoneyFormatter.formatPaise(inv.balanceDueMinor)}"
        } else null
      )
    }

    HomeDashboardSummary(
      formattedHeaderDate = headerFormat.format(now),
      todayBillCount = todayCount,
      todayGrossSalesFormatted = MoneyFormatter.formatPaise(gross),
      todayPaidCashFormatted = MoneyFormatter.formatPaise(cash),
      todayPaidUpiFormatted = MoneyFormatter.formatPaise(upi),
      todayUdharFormatted = MoneyFormatter.formatPaise(todayUdharSum),
      totalUdharDueFormatted = MoneyFormatter.formatPaise(totalUdharSum),
      recentRows = recentRows
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Top Greeting & Store Identity
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = settings.businessName.ifBlank { "Meher" },
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = textPrimary
        )
        Text(
          text = "Counter Dashboard • ${dashboardSummary.formattedHeaderDate}",
          style = MaterialTheme.typography.bodySmall,
          color = textSecondary
        )
      }

      IconButton(
        onClick = onNavigateToSettings,
        modifier = Modifier
          .size(48.dp)
          .clip(ShapeCard12)
          .background(PrimaryBlue.copy(alpha = 0.1f))
      ) {
        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = PrimaryBlue)
      }
    }

    // Hero Today's Sales Card
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          brush = Brush.linearGradient(
            colors = listOf(PrimaryNavyHero, BrandCobaltAccent)
          ),
          shape = ShapeHeroCard
        )
        .padding(20.dp)
    ) {
      Text(
        text = "Today's Gross Sales",
        style = MaterialTheme.typography.bodyMedium,
        color = SurfaceLight.copy(alpha = 0.85f)
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = dashboardSummary.todayGrossSalesFormatted,
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Bold,
        color = SurfaceLight
      )

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${dashboardSummary.todayBillCount} bills issued today",
          style = MaterialTheme.typography.bodySmall,
          color = SurfaceLight.copy(alpha = 0.9f)
        )

        Button(
          onClick = onNavigateToNewBill,
          shape = ShapeButton10,
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

    // Metric Highlights: Cash, UPI, Today's Udhar, Total Due
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        MetricTile(
          title = "Cash Sales (नकद)",
          value = dashboardSummary.todayPaidCashFormatted,
          color = CashColor,
          surfaceColor = surfaceColor,
          borderColor = borderColor,
          textSecondary = textSecondary,
          modifier = Modifier.weight(1f)
        )
        MetricTile(
          title = "UPI Sales (ऑनलाइन)",
          value = dashboardSummary.todayPaidUpiFormatted,
          color = UpiColor,
          surfaceColor = surfaceColor,
          borderColor = borderColor,
          textSecondary = textSecondary,
          modifier = Modifier.weight(1f)
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        MetricTile(
          title = "Today's Udhar (उधार)",
          value = dashboardSummary.todayUdharFormatted,
          color = UdharColor,
          surfaceColor = surfaceColor,
          borderColor = borderColor,
          textSecondary = textSecondary,
          modifier = Modifier.weight(1f)
        )
        MetricTile(
          title = "Total Pending Udhar",
          value = dashboardSummary.totalUdharDueFormatted,
          color = WarningOrange,
          surfaceColor = surfaceColor,
          borderColor = borderColor,
          textSecondary = textSecondary,
          modifier = Modifier.weight(1f)
        )
      }
    }

    // Quick Action Shortcuts (New Bill, Bills, Reports, Settings)
    Column {
      Text(
        text = "Quick Actions",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = textPrimary
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
          surfaceColor = surfaceColor,
          borderColor = borderColor,
          textPrimary = textPrimary,
          modifier = Modifier.weight(1f)
        )
        QuickActionButton(
          label = "Bills",
          icon = Icons.Default.Receipt,
          onClick = onNavigateToInvoices,
          surfaceColor = surfaceColor,
          borderColor = borderColor,
          textPrimary = textPrimary,
          modifier = Modifier.weight(1f)
        )
        QuickActionButton(
          label = "Reports",
          icon = Icons.Default.Assessment,
          onClick = onNavigateToReports,
          surfaceColor = surfaceColor,
          borderColor = borderColor,
          textPrimary = textPrimary,
          modifier = Modifier.weight(1f)
        )
        QuickActionButton(
          label = "Settings",
          icon = Icons.Default.Settings,
          onClick = onNavigateToSettings,
          surfaceColor = surfaceColor,
          borderColor = borderColor,
          textPrimary = textPrimary,
          modifier = Modifier.weight(1f)
        )
      }
    }

    // Recent Invoices Section
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Recent Bills",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = textPrimary
      )
      Text(
        text = "View All (${invoices.size})",
        style = MaterialTheme.typography.labelSmall,
        color = PrimaryBlue,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
          .clip(ShapeBadge6)
          .clickable(onClick = onNavigateToInvoices)
          .padding(horizontal = 6.dp, vertical = 4.dp)
      )
    }

    if (dashboardSummary.recentRows.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(surfaceColor, ShapeCard14)
          .border(1.dp, borderColor, ShapeCard14)
          .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(Icons.Default.Receipt, contentDescription = null, tint = textSecondary, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text("No Invoices Yet", fontWeight = FontWeight.Bold, color = textPrimary)
        Text(
          "Tap 'New Bill' above to create your store's first retail invoice.",
          style = MaterialTheme.typography.bodySmall,
          color = textSecondary,
          modifier = Modifier.padding(top = 4.dp)
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in dashboardSummary.recentRows) {
          val inv = row.invoice
          InvoiceRowCard(
            row = row,
            surfaceColor = surfaceColor,
            borderColor = borderColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onClick = { onSelectInvoice(inv) },
            onEditClick = if (inv.status != "CANCELLED") {
              { onEditInvoice(inv) }
            } else null
          )
        }
      }
    }
  }
}

@Immutable
data class InvoiceRowUi(
  val invoice: Invoice,
  val subtitle: String,
  val formattedTotal: String,
  val formattedDue: String?
)

@Immutable
private data class HomeDashboardSummary(
  val formattedHeaderDate: String,
  val todayBillCount: Int,
  val todayGrossSalesFormatted: String,
  val todayPaidCashFormatted: String,
  val todayPaidUpiFormatted: String,
  val todayUdharFormatted: String,
  val totalUdharDueFormatted: String,
  val recentRows: List<InvoiceRowUi>
)

@Composable
fun MetricTile(
  title: String,
  value: String,
  color: Color,
  modifier: Modifier = Modifier,
  surfaceColor: Color = MaterialTheme.colorScheme.surface,
  borderColor: Color = MaterialTheme.colorScheme.outline,
  textSecondary: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
  Column(
    modifier = modifier
      .background(surfaceColor, ShapeCard14)
      .border(1.dp, borderColor, ShapeCard14)
      .padding(12.dp)
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelSmall,
      color = textSecondary,
      fontSize = 11.sp
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = color,
      fontSize = 15.sp
    )
  }
}

@Composable
fun QuickActionButton(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  surfaceColor: Color = MaterialTheme.colorScheme.surface,
  borderColor: Color = MaterialTheme.colorScheme.outline,
  textPrimary: Color = MaterialTheme.colorScheme.onSurface
) {
  Column(
    modifier = modifier
      .clip(ShapeCard12)
      .background(surfaceColor, ShapeCard12)
      .border(1.dp, borderColor, ShapeCard12)
      .clickable(onClick = onClick)
      .padding(vertical = 12.dp, horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(icon, contentDescription = label, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Medium,
      fontSize = 11.sp,
      color = textPrimary
    )
  }
}

@Composable
fun InvoiceListItem(
  invoice: Invoice,
  onClick: () -> Unit,
  onEditClick: (() -> Unit)? = null
) {
  val colorScheme = MaterialTheme.colorScheme
  val row = remember(invoice) {
    InvoiceRowUi(
      invoice = invoice,
      subtitle = "${invoice.customerName} • ${invoice.items.size} items",
      formattedTotal = MoneyFormatter.formatPaise(invoice.grandTotalMinor),
      formattedDue = if (invoice.balanceDueMinor > 0L) {
        "Due: ${MoneyFormatter.formatPaise(invoice.balanceDueMinor)}"
      } else null
    )
  }
  InvoiceRowCard(
    row = row,
    surfaceColor = colorScheme.surface,
    borderColor = colorScheme.outline,
    textPrimary = colorScheme.onSurface,
    textSecondary = colorScheme.onSurfaceVariant,
    onClick = onClick,
    onEditClick = onEditClick
  )
}

@Composable
fun InvoiceRowCard(
  row: InvoiceRowUi,
  surfaceColor: Color,
  borderColor: Color,
  textPrimary: Color,
  textSecondary: Color,
  onClick: () -> Unit,
  onEditClick: (() -> Unit)? = null
) {
  val invoice = row.invoice
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(ShapeCard12)
      .background(surfaceColor, ShapeCard12)
      .border(1.dp, borderColor, ShapeCard12)
      .clickable(onClick = onClick)
      .padding(start = 14.dp, end = if (onEditClick != null) 6.dp else 14.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = invoice.invoiceNumber,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = textPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        StatusBadge(status = invoice.status)
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = row.subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = textSecondary
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = row.formattedTotal,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = textPrimary
        )
        if (row.formattedDue != null) {
          Text(
            text = row.formattedDue,
            style = MaterialTheme.typography.labelSmall,
            color = UdharColor,
            fontWeight = FontWeight.Bold
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

@Composable
fun StatusBadge(status: String) {
  val neutralText = MaterialTheme.colorScheme.onSurfaceVariant
  val neutralBg = MaterialTheme.colorScheme.outline
  val paidBg = SuccessGreen.copy(alpha = 0.15f)
  val partPaidBg = WarningOrange.copy(alpha = 0.15f)
  val udharBg = UdharColor.copy(alpha = 0.15f)
  val estimateBg = PrimaryBlue.copy(alpha = 0.15f)
  val bgColor: Color
  val textColor: Color
  val label: String

  when (status.uppercase()) {
    "PAID" -> {
      bgColor = paidBg; textColor = SuccessGreen; label = "Paid"
    }
    "PARTIALLY_PAID" -> {
      bgColor = partPaidBg; textColor = WarningOrange; label = "Part Due"
    }
    "UNPAID", "UDHAR" -> {
      bgColor = udharBg; textColor = UdharColor; label = "Udhar"
    }
    "CANCELLED" -> {
      bgColor = neutralText.copy(alpha = 0.15f); textColor = neutralText; label = "Cancelled"
    }
    "QUOTATION" -> {
      bgColor = estimateBg; textColor = PrimaryBlue; label = "Estimate"
    }
    else -> {
      bgColor = neutralBg; textColor = neutralText; label = status
    }
  }

  Box(
    modifier = Modifier
      .background(bgColor, ShapeBadge6)
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
