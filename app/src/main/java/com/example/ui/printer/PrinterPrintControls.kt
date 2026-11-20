package com.example.ui.printer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.printer.PrintJobState
import com.example.printer.PrinterConnectionState
import com.example.printer.PrinterSettings
import com.example.printer.ReceiptLayoutSpec
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.WarningOrange

private val PrintSectionShape = RoundedCornerShape(12.dp)
private val PrintButtonShape = RoundedCornerShape(10.dp)

@Composable
fun ReceiptPrintActionSection(
  jobKey: String,
  connectionState: PrinterConnectionState,
  printJobState: PrintJobState,
  printerSettings: PrinterSettings,
  layoutSpec: ReceiptLayoutSpec,
  onPrintBill: () -> Unit,
  onRetryPrint: () -> Unit,
  onOpenPrinterSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val colorScheme = MaterialTheme.colorScheme
  val isConnected = connectionState is PrinterConnectionState.Connected
  val isConnecting = connectionState is PrinterConnectionState.Connecting
  val hasRememberedPrinter = printerSettings.rememberedPrinterAddress.isNotBlank()
  val isPrintingOrConnecting = printJobState.isPrinting || isConnecting
  val alreadyPrintedThis = printJobState.isSuccessFor(jobKey)
  val connectedName = (connectionState as? PrinterConnectionState.Connected)?.deviceName
    ?: printerSettings.rememberedPrinterName.ifBlank { "POSIFLOW Printer" }

  val effectiveErrorMessage = printJobState.errorMessage
    ?: (connectionState as? PrinterConnectionState.Error)?.message

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(colorScheme.surfaceVariant.copy(alpha = 0.45f), PrintSectionShape)
      .border(1.dp, colorScheme.outline, PrintSectionShape)
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Thermal Receipt Print",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = colorScheme.onSurface
        )
        Text(
          text = when {
            isConnected -> "Connected: $connectedName (${(connectionState as PrinterConnectionState.Connected).protocolLabel}) • ${printerSettings.selectedPaperWidthLabel}"
            isConnecting -> "Connecting to $connectedName..."
            hasRememberedPrinter ->
              "Ready to auto-connect: $connectedName • ${printerSettings.selectedPaperWidthLabel}"
            else -> "No Bluetooth printer selected"
          },
          style = MaterialTheme.typography.bodySmall,
          color = if (isConnected) SuccessGreen else colorScheme.onSurfaceVariant,
          fontSize = 11.sp
        )
      }

      OutlinedButton(
        onClick = onOpenPrinterSettings,
        shape = PrintButtonShape,
        modifier = Modifier
          .height(48.dp)
          .testTag("open_printer_settings_shortcut")
      ) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = "Printer Settings",
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Setup", fontSize = 12.sp)
      }
    }

    if (layoutSpec.widthWarning != null) {
      Text(
        text = layoutSpec.widthWarning,
        style = MaterialTheme.typography.labelSmall,
        color = WarningOrange
      )
    }

    if (effectiveErrorMessage != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(WarningOrange.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
          .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.ErrorOutline,
          contentDescription = null,
          tint = WarningOrange,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = effectiveErrorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = WarningOrange,
          modifier = Modifier.weight(1f)
        )
      }
    } else if (alreadyPrintedThis && printJobState.statusMessage != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(SuccessGreen.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
          .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = null,
          tint = SuccessGreen,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = printJobState.statusMessage,
          style = MaterialTheme.typography.bodySmall,
          color = SuccessGreen,
          modifier = Modifier.weight(1f)
        )
      }
    }

    if (printJobState.paperStatusWarning != null && effectiveErrorMessage == null) {
      Text(
        text = printJobState.paperStatusWarning,
        style = MaterialTheme.typography.labelSmall,
        color = WarningOrange
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = {
          if (isConnected || hasRememberedPrinter) {
            onPrintBill()
          } else {
            onOpenPrinterSettings()
          }
        },
        enabled = !isPrintingOrConnecting,
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("print_bill_button"),
        shape = PrintButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
      ) {
        if (isPrintingOrConnecting) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = SurfaceLight,
            strokeWidth = 2.dp
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (isConnecting) "Connecting..." else "Printing...",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
        } else {
          Icon(
            imageVector = if (isConnected || hasRememberedPrinter) Icons.Default.Print else Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = when {
              !isConnected && !hasRememberedPrinter -> "Select Printer to Print"
              !isConnected && hasRememberedPrinter -> "Connect & Print Bill"
              alreadyPrintedThis -> "Print Again"
              else -> "Print Bill"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      if (effectiveErrorMessage != null && hasRememberedPrinter && !isPrintingOrConnecting) {
        OutlinedButton(
          onClick = onRetryPrint,
          border = BorderStroke(1.dp, WarningOrange),
          shape = PrintButtonShape,
          modifier = Modifier
            .height(48.dp)
            .testTag("retry_print_button")
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Retry Print",
            tint = WarningOrange,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Retry", color = WarningOrange, fontSize = 13.sp)
        }
      }
    }
  }
}
