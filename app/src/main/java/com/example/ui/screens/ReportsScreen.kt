package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.domain.MoneyFormatter
import com.example.data.model.BusinessSettings
import com.example.ui.theme.CashColor
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.UdharColor
import com.example.ui.theme.UpiColor
import com.example.ui.theme.WarningOrange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ReportShape16 = RoundedCornerShape(16.dp)
private val ReportShape14 = RoundedCornerShape(14.dp)
private val ReportShape12 = RoundedCornerShape(12.dp)
private val ReportShape10 = RoundedCornerShape(10.dp)

@Composable
fun ReportsScreen(
  billingViewModel: BillingViewModel,
  settingsViewModel: SettingsViewModel,
  onNavigateToSettings: () -> Unit = {}
) {
  val context = LocalContext.current
  val invoices by billingViewModel.invoicesState.collectAsStateWithLifecycle()
  val settings by settingsViewModel.settingsState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()
  val chipScrollState = rememberScrollState()

  val colorScheme = MaterialTheme.colorScheme
  val surfaceColor = colorScheme.surface
  val borderColor = colorScheme.outline
  val textPrimary = colorScheme.onSurface
  val textSecondary = colorScheme.onSurfaceVariant

  var selectedPeriod by remember { mutableStateOf("TODAY") }

  val reportMetrics = remember(invoices, selectedPeriod) {
    val now = Calendar.getInstance()
    val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
    val invCal = Calendar.getInstance()

    var gross = 0L
    var count = 0
    var cash = 0L
    var upi = 0L
    var udhar = 0L
    var totalReceivablesSum = 0L
    val productMap = HashMap<String, Pair<Double, Long>>()

    for (inv in invoices) {
      if (inv.status == "CANCELLED") continue
      totalReceivablesSum += inv.balanceDueMinor

      val created = inv.createdAt ?: continue
      invCal.time = created.toDate()
      val inPeriod = when (selectedPeriod) {
        "TODAY" -> {
          invCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            invCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        }
        "WEEK" -> invCal.after(weekAgo)
        "MONTH" -> {
          invCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            invCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
        }
        else -> true
      }

      if (inPeriod) {
        count++
        gross += inv.grandTotalMinor
        when (inv.paymentMethod) {
          "CASH" -> cash += inv.amountPaidMinor
          "UPI" -> upi += inv.amountPaidMinor
        }
        udhar += inv.balanceDueMinor
        for (item in inv.items) {
          val current = productMap[item.description]
          if (current == null) {
            productMap[item.description] = Pair(item.quantity, item.lineTotalMinor)
          } else {
            productMap[item.description] = Pair(
              current.first + item.quantity,
              current.second + item.lineTotalMinor
            )
          }
        }
      }
    }

    val avg = if (count > 0) gross / count else 0L
    val topProducts = productMap.entries
      .sortedByDescending { it.value.second }
      .take(8)
      .mapIndexed { idx, entry ->
        TopProductRowUi(
          title = "${idx + 1}. ${entry.key}",
          qtyText = "Qty Sold: ${entry.value.first}",
          formattedTotal = MoneyFormatter.formatPaise(entry.value.second)
        )
      }

    ReportsMetricsSummary(
      totalGrossSales = gross,
      totalInvoicesCount = count,
      avgBillValue = avg,
      cashCollections = cash,
      upiCollections = upi,
      udharCollections = udhar,
      totalReceivables = totalReceivablesSum,
      grossSalesFormatted = MoneyFormatter.formatPaise(gross),
      avgBillFormatted = MoneyFormatter.formatPaise(avg),
      receivablesFormatted = MoneyFormatter.formatPaise(totalReceivablesSum),
      cashFormatted = MoneyFormatter.formatPaise(cash),
      upiFormatted = MoneyFormatter.formatPaise(upi),
      udharFormatted = MoneyFormatter.formatPaise(udhar),
      topProducts = topProducts
    )
  }

  val periods = remember {
    listOf(
      "TODAY" to "Today",
      "WEEK" to "This Week",
      "MONTH" to "This Month",
      "ALL" to "All Time"
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "Business Reports",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = textPrimary
        )
        Text(
          text = "Sales, Collections & Item Summaries",
          style = MaterialTheme.typography.bodySmall,
          color = textSecondary
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onNavigateToSettings) {
          Icon(Icons.Default.Settings, contentDescription = "Settings", tint = textSecondary)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Button(
          onClick = {
            val reportSummary = buildReportShareText(
              period = selectedPeriod,
              sales = reportMetrics.totalGrossSales,
              billsCount = reportMetrics.totalInvoicesCount,
              cash = reportMetrics.cashCollections,
              upi = reportMetrics.upiCollections,
              udhar = reportMetrics.udharCollections,
              due = reportMetrics.totalReceivables,
              settings = settings
            )
            val sendIntent = Intent().apply {
              action = Intent.ACTION_SEND
              putExtra(Intent.EXTRA_TEXT, reportSummary)
              type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Sales Report"))
          },
          shape = ReportShape10,
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
          Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Share Report", fontSize = 12.sp)
        }
      }
    }

    // Period Filter Chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(chipScrollState),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      for ((key, label) in periods) {
        val selected = selectedPeriod == key
        FilterChip(
          selected = selected,
          onClick = { selectedPeriod = key },
          label = { Text(label, fontSize = 12.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryBlue,
            selectedLabelColor = SurfaceLight
          ),
          border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = borderColor
          )
        )
      }
    }

    // Main Sales KPI Card
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(surfaceColor, ReportShape16)
        .border(1.dp, borderColor, ReportShape16)
        .padding(18.dp)
    ) {
      Text("Gross Total Sales ($selectedPeriod)", fontSize = 12.sp, color = textSecondary)
      Text(
        text = reportMetrics.grossSalesFormatted,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = PrimaryBlue
      )

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(color = borderColor)
      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text("Total Bills", fontSize = 11.sp, color = textSecondary)
          Text("${reportMetrics.totalInvoicesCount}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimary)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("Avg. Bill Value", fontSize = 11.sp, color = textSecondary)
          Text(reportMetrics.avgBillFormatted, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimary)
        }
        Column(horizontalAlignment = Alignment.End) {
          Text("Customer Receivables", fontSize = 11.sp, color = WarningOrange)
          Text(reportMetrics.receivablesFormatted, fontWeight = FontWeight.Bold, color = WarningOrange, fontSize = 15.sp)
        }
      }
    }

    // Collections Breakdown
    Column {
      Text(
        text = "Collections by Payment Mode",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = textPrimary
      )
      Spacer(modifier = Modifier.height(8.dp))
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(surfaceColor, ReportShape14)
          .border(1.dp, borderColor, ReportShape14)
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Cash Collections (नकद)", color = textSecondary)
          Text(reportMetrics.cashFormatted, fontWeight = FontWeight.Bold, color = CashColor)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("UPI / QR Collections (ऑनलाइन)", color = textSecondary)
          Text(reportMetrics.upiFormatted, fontWeight = FontWeight.Bold, color = UpiColor)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Udhar / Credit Recorded (उधार)", color = textSecondary)
          Text(reportMetrics.udharFormatted, fontWeight = FontWeight.Bold, color = UdharColor)
        }
      }
    }

    // Top Selling Items Breakdown
    Column {
      Text(
        text = "Top Selling Items ($selectedPeriod)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = textPrimary
      )
      Spacer(modifier = Modifier.height(8.dp))
      if (reportMetrics.topProducts.isEmpty()) {
        Text(
          text = "No item sales in this period",
          color = textSecondary,
          modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor, ReportShape12)
            .border(1.dp, borderColor, ReportShape12)
            .padding(16.dp),
          fontSize = 13.sp
        )
      } else {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor, ReportShape14)
            .border(1.dp, borderColor, ReportShape14)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          reportMetrics.topProducts.forEachIndexed { i, product ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(product.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = textPrimary)
                Text(product.qtyText, fontSize = 11.sp, color = textSecondary)
              }
              Text(product.formattedTotal, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textPrimary)
            }
            if (i < reportMetrics.topProducts.lastIndex) {
              HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
            }
          }
        }
      }
    }
  }
}

@Immutable
private data class TopProductRowUi(
  val title: String,
  val qtyText: String,
  val formattedTotal: String
)

@Immutable
private data class ReportsMetricsSummary(
  val totalGrossSales: Long,
  val totalInvoicesCount: Int,
  val avgBillValue: Long,
  val cashCollections: Long,
  val upiCollections: Long,
  val udharCollections: Long,
  val totalReceivables: Long,
  val grossSalesFormatted: String,
  val avgBillFormatted: String,
  val receivablesFormatted: String,
  val cashFormatted: String,
  val upiFormatted: String,
  val udharFormatted: String,
  val topProducts: List<TopProductRowUi>
)

fun buildReportShareText(
  period: String,
  sales: Long,
  billsCount: Int,
  cash: Long,
  upi: Long,
  udhar: Long,
  due: Long,
  settings: BusinessSettings
): String {
  return """
    📊 *${settings.businessName} — Sales Summary*
    📅 Period: $period
    --------------------------------
    Gross Sales: *${MoneyFormatter.formatPaise(sales)}*
    Total Invoices: *$billsCount*
    --------------------------------
    💵 Cash Sales: ${MoneyFormatter.formatPaise(cash)}
    📱 UPI / QR Sales: ${MoneyFormatter.formatPaise(upi)}
    🔴 Udhar / Credit Bills: ${MoneyFormatter.formatPaise(udhar)}
    --------------------------------
    ⚠️ Total Customer Credit Receivables: *${MoneyFormatter.formatPaise(due)}*
    --------------------------------
    Generated: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())}
  """.trimIndent()
}
