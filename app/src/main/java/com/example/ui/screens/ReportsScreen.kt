package com.example.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(
  billingViewModel: BillingViewModel,
  settingsViewModel: SettingsViewModel,
  onNavigateToSettings: () -> Unit = {}
) {
  val context = LocalContext.current
  val invoices by billingViewModel.invoicesState.collectAsState()
  val settings by settingsViewModel.settingsState.collectAsState()

  var selectedPeriod by remember { mutableStateOf("TODAY") }

  // Filter invoices by selected date period
  val now = Calendar.getInstance()
  val filteredInvoices = invoices.filter { inv ->
    if (inv.status == "CANCELLED" || inv.createdAt == null) return@filter false
    val invCal = Calendar.getInstance().apply { time = inv.createdAt.toDate() }
    when (selectedPeriod) {
      "TODAY" -> {
        invCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
          invCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
      }
      "WEEK" -> {
        val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        invCal.after(weekAgo)
      }
      "MONTH" -> {
        invCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
          invCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
      }
      else -> true // ALL
    }
  }

  val totalGrossSales = filteredInvoices.sumOf { it.grandTotalMinor }
  val totalInvoicesCount = filteredInvoices.size
  val avgBillValue = if (totalInvoicesCount > 0) totalGrossSales / totalInvoicesCount else 0L

  val cashCollections = filteredInvoices.filter { it.paymentMethod == "CASH" }.sumOf { it.amountPaidMinor }
  val upiCollections = filteredInvoices.filter { it.paymentMethod == "UPI" }.sumOf { it.amountPaidMinor }
  val udharCollections = filteredInvoices.sumOf { it.balanceDueMinor }
  val totalReceivables = invoices.filter { it.status != "CANCELLED" }.sumOf { it.balanceDueMinor }

  // Product description summary
  val productMap = mutableMapOf<String, Pair<Double, Long>>()
  filteredInvoices.forEach { inv ->
    inv.items.forEach { item ->
      val current = productMap[item.description] ?: Pair(0.0, 0L)
      productMap[item.description] = Pair(
        current.first + item.quantity,
        current.second + item.lineTotalMinor
      )
    }
  }
  val topProducts = productMap.toList().sortedByDescending { it.second.second }.take(8)

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Business Reports",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            text = "Sales, Collections & Item Summaries",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onNavigateToSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
          }
          Spacer(modifier = Modifier.width(4.dp))
          Button(
            onClick = {
              val reportSummary = buildReportShareText(
                period = selectedPeriod,
                sales = totalGrossSales,
                billsCount = totalInvoicesCount,
                cash = cashCollections,
                upi = upiCollections,
                udhar = udharCollections,
                due = totalReceivables,
                settings = settings
              )
              val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, reportSummary)
                type = "text/plain"
              }
              context.startActivity(Intent.createChooser(sendIntent, "Share Sales Report"))
            },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Share Report", fontSize = 12.sp)
          }
        }
      }
    }

    // Period Filter Chips
    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val periods = listOf(
          "TODAY" to "Today",
          "WEEK" to "This Week",
          "MONTH" to "This Month",
          "ALL" to "All Time"
        )
        items(periods) { (key, label) ->
          FilterChip(
            selected = selectedPeriod == key,
            onClick = { selectedPeriod = key },
            label = { Text(label, fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = PrimaryBlue,
              selectedLabelColor = SurfaceLight
            )
          )
        }
      }
    }

    // Main Sales KPI Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text("Gross Total Sales ($selectedPeriod)", fontSize = 12.sp, color = TextSecondary)
          Text(
            text = MoneyFormatter.formatPaise(totalGrossSales),
            style = MaterialTheme.typography.headlineMedium.copy(
              fontWeight = FontWeight.Bold,
              color = PrimaryBlue
            )
          )

          Spacer(modifier = Modifier.height(14.dp))
          HorizontalDivider(color = BorderLight)
          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text("Total Bills", fontSize = 11.sp, color = TextSecondary)
              Text("$totalInvoicesCount", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Avg. Bill Value", fontSize = 11.sp, color = TextSecondary)
              Text(MoneyFormatter.formatPaise(avgBillValue), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
              Text("Customer Receivables", fontSize = 11.sp, color = WarningOrange)
              Text(MoneyFormatter.formatPaise(totalReceivables), fontWeight = FontWeight.Bold, color = WarningOrange, fontSize = 15.sp)
            }
          }
        }
      }
    }

    // Collections Breakdown
    item {
      Text("Collections by Payment Mode", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
      Spacer(modifier = Modifier.height(8.dp))
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Cash Collections (नकद)", color = TextSecondary)
            Text(MoneyFormatter.formatPaise(cashCollections), fontWeight = FontWeight.Bold, color = CashColor)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("UPI / QR Collections (ऑनलाइन)", color = TextSecondary)
            Text(MoneyFormatter.formatPaise(upiCollections), fontWeight = FontWeight.Bold, color = UpiColor)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Udhar / Credit Recorded (उधार)", color = TextSecondary)
            Text(MoneyFormatter.formatPaise(udharCollections), fontWeight = FontWeight.Bold, color = UdharColor)
          }
        }
      }
    }

    // Top Selling Items Breakdown
    item {
      Text("Top Selling Items ($selectedPeriod)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
      Spacer(modifier = Modifier.height(8.dp))
      if (topProducts.isEmpty()) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
        ) {
          Text(
            text = "No item sales in this period",
            color = TextSecondary,
            modifier = Modifier.padding(16.dp),
            fontSize = 13.sp
          )
        }
      } else {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            topProducts.forEachIndexed { i, (desc, data) ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("${i + 1}. $desc", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                  Text("Qty Sold: ${data.first}", fontSize = 11.sp, color = TextSecondary)
                }
                Text(MoneyFormatter.formatPaise(data.second), fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
              if (i < topProducts.size - 1) HorizontalDivider(color = BorderLight.copy(alpha = 0.5f))
            }
          }
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(30.dp))
    }
  }
}

fun buildReportShareText(
  period: String,
  sales: Long,
  billsCount: Int,
  cash: Long,
  upi: Long,
  udhar: Long,
  due: Long,
  settings: com.example.data.model.BusinessSettings
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
