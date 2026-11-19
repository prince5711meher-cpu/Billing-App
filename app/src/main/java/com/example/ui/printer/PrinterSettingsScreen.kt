package com.example.ui.printer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.printer.BluetoothPrinterDevice
import com.example.printer.PairedDevicesResult
import com.example.printer.PaperSizePreset
import com.example.printer.PrintDensity
import com.example.printer.PrintJobState
import com.example.printer.PrintRenderMode
import com.example.printer.PrinterConnectionState
import com.example.printer.PrinterMaxWidthOption
import com.example.printer.PrinterSettings
import com.example.printer.ReceiptAlignment
import com.example.printer.ReceiptFontSize
import com.example.printer.ReceiptLayoutSpec
import com.example.printer.WidthValidationResult
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.WarningOrange
import kotlin.math.roundToInt

private val PrinterCardShape = RoundedCornerShape(14.dp)
private val PrinterButtonShape = RoundedCornerShape(10.dp)
private val PrinterChipShape = RoundedCornerShape(8.dp)

@Composable
fun PrinterSettingsScreen(
  viewModel: PrinterViewModel,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val scrollState = rememberScrollState()

  val printerSettings by viewModel.printerSettings.collectAsStateWithLifecycle()
  val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
  val printJobState by viewModel.printJobState.collectAsStateWithLifecycle()
  val pairedResult by viewModel.pairedDevicesResult.collectAsStateWithLifecycle()
  val layoutSpec by viewModel.layoutSpec.collectAsStateWithLifecycle()
  val hasPermission by viewModel.hasBluetoothPermission.collectAsStateWithLifecycle()

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    viewModel.onPermissionResult(granted)
  }

  LaunchedEffect(Unit) {
    viewModel.refreshBluetoothState()
  }

  val requestBluetoothPermission = remember(permissionLauncher, context) {
    {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
      } else {
        viewModel.refreshBluetoothState()
      }
    }
  }

  val openBluetoothSettings = remember(context) {
    {
      runCatching {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
      }
      Unit
    }
  }

  val openAppPermissionSettings = remember(context) {
    {
      runCatching {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
      }
      Unit
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Top Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.testTag("printer_settings_back_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back to Settings"
        )
      }
      Spacer(modifier = Modifier.width(4.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Thermal Printer (POSIFLOW)",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "Bluetooth SPP connection, paper width, and receipt preferences",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // Section 1: Bluetooth Connection (kept strictly separate from paper width settings)
    BluetoothConnectionCard(
      hasPermission = hasPermission,
      pairedResult = pairedResult,
      printerSettings = printerSettings,
      connectionState = connectionState,
      onRequestPermission = requestBluetoothPermission,
      onOpenAppSettings = openAppPermissionSettings,
      onOpenBluetoothSettings = openBluetoothSettings,
      onRefreshDevices = { viewModel.refreshBluetoothState() },
      onSelectDevice = { device -> viewModel.selectPrinter(device) },
      onConnectDevice = { device -> viewModel.connectToDevice(device) },
      onConnectRemembered = { viewModel.connectRememberedPrinter() },
      onDisconnect = { viewModel.disconnectPrinter() }
    )

    // Section 2: Receipt Paper Size & Printable Area Configuration
    ReceiptConfigurationCard(
      printerSettings = printerSettings,
      layoutSpec = layoutSpec,
      onSelectPreset = { viewModel.setPaperSizePreset(it) },
      onApplyCustomWidth = { viewModel.setCustomWidthMm(it) },
      onSelectMaxPrinterWidth = { viewModel.setPrinterMaxWidthOption(it) }
    )

    // Section 3: Printing Preferences
    PrintingPreferencesCard(
      printerSettings = printerSettings,
      onSelectRenderMode = { viewModel.setRenderMode(it) },
      onSelectDensity = { viewModel.setPrintDensity(it) },
      onSelectAlignment = { viewModel.setTextAlignment(it) },
      onSelectFontSize = { viewModel.setFontSize(it) },
      onToggleBoldText = { viewModel.setBoldText(it) },
      onToggleAutoPrint = { viewModel.setAutoPrintAfterBill(it) }
    )

    // Section 4: Test Printing
    PrinterTestCard(
      connectionState = connectionState,
      printerSettings = printerSettings,
      printJobState = printJobState,
      onPrintTest = { viewModel.printTestReceipt() },
      onRetry = { viewModel.retryLastFailedPrint() }
    )
  }
}

@Composable
private fun BluetoothConnectionCard(
  hasPermission: Boolean,
  pairedResult: PairedDevicesResult,
  printerSettings: PrinterSettings,
  connectionState: PrinterConnectionState,
  onRequestPermission: () -> Unit,
  onOpenAppSettings: () -> Unit,
  onOpenBluetoothSettings: () -> Unit,
  onRefreshDevices: () -> Unit,
  onSelectDevice: (BluetoothPrinterDevice) -> Unit,
  onConnectDevice: (BluetoothPrinterDevice) -> Unit,
  onConnectRemembered: () -> Unit,
  onDisconnect: () -> Unit
) {
  val colorScheme = MaterialTheme.colorScheme
  val isConnected = connectionState is PrinterConnectionState.Connected
  val isConnecting = connectionState is PrinterConnectionState.Connecting

  val (statusLabel, statusColor, statusIcon) = when (connectionState) {
    is PrinterConnectionState.Connected -> Triple(
      "Connected to ${connectionState.deviceName}",
      SuccessGreen,
      Icons.Default.BluetoothConnected
    )
    is PrinterConnectionState.Connecting -> Triple(
      "Connecting to ${connectionState.deviceName}...",
      PrimaryBlue,
      Icons.Default.Bluetooth
    )
    is PrinterConnectionState.Error -> Triple(
      connectionState.message,
      WarningOrange,
      Icons.Default.BluetoothDisabled
    )
    PrinterConnectionState.Disconnected -> Triple(
      "Disconnected",
      colorScheme.onSurfaceVariant,
      Icons.Default.BluetoothDisabled
    )
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(colorScheme.surface, PrinterCardShape)
      .border(1.dp, colorScheme.outline, PrinterCardShape)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Bluetooth Connection (SPP)",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "UUID: 00001101-0000-1000-8000-00805F9B34FB",
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.onSurfaceVariant
        )
      }

      IconButton(
        onClick = onRefreshDevices,
        modifier = Modifier.testTag("refresh_printers_button")
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Refresh paired Bluetooth printers"
        )
      }
    }

    // Connection status summary banner
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(statusColor.copy(alpha = 0.1f), PrinterButtonShape)
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = statusIcon,
        contentDescription = null,
        tint = statusColor,
        modifier = Modifier.size(22.dp)
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = statusLabel,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = statusColor
        )
        val remembered = printerSettings.rememberedPrinterName
        if (remembered.isNotBlank()) {
          Text(
            text = "Selected: $remembered (${printerSettings.rememberedPrinterAddress})",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant
          )
        } else {
          Text(
            text = "Select a paired thermal printer below",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // Connect / Disconnect controls for remembered printer
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = onConnectRemembered,
        enabled = !isConnected && !isConnecting && printerSettings.rememberedPrinterAddress.isNotBlank() && hasPermission,
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("printer_connect_button"),
        shape = PrinterButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
      ) {
        if (isConnecting) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = SurfaceLight,
            strokeWidth = 2.dp
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Connecting...")
        } else {
          Text("Connect", fontWeight = FontWeight.SemiBold)
        }
      }

      OutlinedButton(
        onClick = onDisconnect,
        enabled = isConnected || isConnecting,
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("printer_disconnect_button"),
        shape = PrinterButtonShape
      ) {
        Text("Disconnect", fontWeight = FontWeight.SemiBold)
      }
    }

    HorizontalDivider(color = colorScheme.outline)

    when {
      !hasPermission || pairedResult is PairedDevicesResult.PermissionRequired -> {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(WarningOrange.copy(alpha = 0.1f), PrinterButtonShape)
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "Bluetooth permission is required to view paired printers and connect to POSIFLOW hardware.",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = onRequestPermission,
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("grant_bluetooth_permission_button"),
              shape = PrinterButtonShape,
              colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
              Text("Allow Bluetooth")
            }
            OutlinedButton(
              onClick = onOpenAppSettings,
              modifier = Modifier.height(48.dp),
              shape = PrinterButtonShape
            ) {
              Text("App Settings")
            }
          }
        }
      }

      pairedResult is PairedDevicesResult.BluetoothUnsupported -> {
        Text(
          text = "Bluetooth hardware is not available on this device.",
          style = MaterialTheme.typography.bodySmall,
          color = WarningOrange
        )
      }

      pairedResult is PairedDevicesResult.BluetoothDisabled -> {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(WarningOrange.copy(alpha = 0.1f), PrinterButtonShape)
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "Bluetooth is turned off. Turn on Bluetooth in system settings, then tap Refresh.",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface
          )
          OutlinedButton(
            onClick = onOpenBluetoothSettings,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp),
            shape = PrinterButtonShape
          ) {
            Text("Open Bluetooth Settings")
          }
        }
      }

      pairedResult is PairedDevicesResult.Error -> {
        Text(
          text = pairedResult.message,
          style = MaterialTheme.typography.bodySmall,
          color = WarningOrange
        )
      }

      pairedResult is PairedDevicesResult.Success -> {
        val devices = pairedResult.devices
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Paired Bluetooth Devices (${devices.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
          )
          OutlinedButton(
            onClick = onOpenBluetoothSettings,
            modifier = Modifier.height(40.dp),
            shape = PrinterChipShape
          ) {
            Text("Pair New", fontSize = 12.sp)
          }
        }

        if (devices.isEmpty()) {
          Text(
            text = "No paired Bluetooth devices found. Pair your POSIFLOW printer in Android Bluetooth settings first, then tap Refresh.",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant
          )
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            devices.forEach { device ->
              val isSelected = device.address.equals(printerSettings.rememberedPrinterAddress, ignoreCase = true)
              val isDeviceConnected = connectionState is PrinterConnectionState.Connected &&
                connectionState.deviceAddress.equals(device.address, ignoreCase = true)

              PairedPrinterRow(
                device = device,
                isSelected = isSelected,
                isConnected = isDeviceConnected,
                isConnecting = connectionState is PrinterConnectionState.Connecting &&
                  connectionState.deviceAddress.equals(device.address, ignoreCase = true),
                onSelect = { onSelectDevice(device) },
                onConnect = { onConnectDevice(device) }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PairedPrinterRow(
  device: BluetoothPrinterDevice,
  isSelected: Boolean,
  isConnected: Boolean,
  isConnecting: Boolean,
  onSelect: () -> Unit,
  onConnect: () -> Unit
) {
  val colorScheme = MaterialTheme.colorScheme
  val borderColor = when {
    isConnected -> SuccessGreen
    isSelected -> PrimaryBlue
    else -> colorScheme.outline
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(PrinterButtonShape)
      .background(
        if (isSelected) PrimaryBlue.copy(alpha = 0.06f) else colorScheme.surfaceVariant.copy(alpha = 0.3f),
        PrinterButtonShape
      )
      .border(1.dp, borderColor, PrinterButtonShape)
      .clickable(onClick = onSelect)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = device.name,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = colorScheme.onSurface
        )
        if (device.isPosiflowBrand) {
          Box(
            modifier = Modifier
              .background(PrimaryBlue.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = "POSIFLOW",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = PrimaryBlue
            )
          }
        } else if (device.isLikelyPrinter) {
          Box(
            modifier = Modifier
              .background(SuccessGreen.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = "Printer",
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              color = SuccessGreen
            )
          }
        }
      }
      Text(
        text = device.address,
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.onSurfaceVariant
      )
    }

    if (isConnected) {
      Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = "Connected",
        tint = SuccessGreen,
        modifier = Modifier.size(22.dp)
      )
    } else {
      OutlinedButton(
        onClick = onConnect,
        enabled = !isConnecting,
        modifier = Modifier.height(48.dp),
        shape = PrinterChipShape
      ) {
        Text(if (isConnecting) "..." else "Connect", fontSize = 12.sp)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiptConfigurationCard(
  printerSettings: PrinterSettings,
  layoutSpec: ReceiptLayoutSpec,
  onSelectPreset: (PaperSizePreset) -> Unit,
  onApplyCustomWidth: (Float) -> Unit,
  onSelectMaxPrinterWidth: (PrinterMaxWidthOption) -> Unit
) {
  val colorScheme = MaterialTheme.colorScheme
  var customInput by remember(printerSettings.customWidthMm) {
    val rounded = (printerSettings.customWidthMm * 10f).roundToInt() / 10f
    mutableStateOf(if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString())
  }
  var validationError by remember { mutableStateOf<String?>(null) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(colorScheme.surface, PrinterCardShape)
      .border(1.dp, colorScheme.outline, PrinterCardShape)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      text = "Receipt Paper Size & Layout",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = "Select receipt layout width independently of Bluetooth connection. Default is 3 inches (76.2mm).",
      style = MaterialTheme.typography.bodySmall,
      color = colorScheme.onSurfaceVariant
    )

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      PaperSizePreset.entries.forEach { preset ->
        FilterChip(
          selected = printerSettings.paperSizePreset == preset,
          onClick = { onSelectPreset(preset) },
          label = { Text(preset.label, fontSize = 12.sp) },
          modifier = Modifier.testTag("paper_preset_${preset.name.lowercase()}"),
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryBlue,
            selectedLabelColor = SurfaceLight
          )
        )
      }
    }

    if (printerSettings.paperSizePreset == PaperSizePreset.CUSTOM) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(colorScheme.surfaceVariant.copy(alpha = 0.35f), PrinterButtonShape)
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "Custom Width (40mm – 108mm)",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = customInput,
            onValueChange = { next ->
              customInput = next
              when (val res = PrinterSettings.validateCustomWidth(next)) {
                is WidthValidationResult.Valid -> {
                  validationError = null
                  onApplyCustomWidth(res.widthMm)
                }
                is WidthValidationResult.Invalid -> {
                  validationError = res.error
                }
              }
            },
            label = { Text("Width (mm)") },
            isError = validationError != null,
            supportingText = validationError?.let { err -> { Text(err) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("custom_width_input")
          )
        }

        Slider(
          value = printerSettings.customWidthMm.coerceIn(
            PrinterSettings.MIN_CUSTOM_WIDTH_MM,
            PrinterSettings.MAX_CUSTOM_WIDTH_MM
          ),
          onValueChange = { sliderValue ->
            val stepped = ((sliderValue * 2f).roundToInt() / 2f)
              .coerceIn(PrinterSettings.MIN_CUSTOM_WIDTH_MM, PrinterSettings.MAX_CUSTOM_WIDTH_MM)
            validationError = null
            customInput = if (stepped % 1f == 0f) stepped.toInt().toString() else stepped.toString()
            onApplyCustomWidth(stepped)
          },
          valueRange = PrinterSettings.MIN_CUSTOM_WIDTH_MM..PrinterSettings.MAX_CUSTOM_WIDTH_MM,
          modifier = Modifier.testTag("custom_width_slider")
        )
      }
    }

    HorizontalDivider(color = colorScheme.outline)

    Text(
      text = "Printer Hardware Printable Area",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold
    )
    Text(
      text = "A paper roll's width is wider than its thermal print head's printable area. Specify your POSIFLOW model's max printable width if known:",
      style = MaterialTheme.typography.bodySmall,
      color = colorScheme.onSurfaceVariant
    )

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      PrinterMaxWidthOption.entries.forEach { option ->
        FilterChip(
          selected = printerSettings.printerMaxWidthOption == option,
          onClick = { onSelectMaxPrinterWidth(option) },
          label = { Text(option.label, fontSize = 11.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryBlue,
            selectedLabelColor = SurfaceLight
          )
        )
      }
    }

    if (layoutSpec.widthWarning != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(WarningOrange.copy(alpha = 0.12f), PrinterButtonShape)
          .padding(10.dp)
          .testTag("paper_width_warning_banner"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = WarningOrange,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = layoutSpec.widthWarning,
          style = MaterialTheme.typography.bodySmall,
          color = WarningOrange
        )
      }
    }

    Text(
      text = "Active Layout: ${printerSettings.selectedPaperWidthLabel} • Printable ${layoutSpec.effectivePrintableWidthMm.toInt()}mm (${layoutSpec.dotsPerLine} dots, ${layoutSpec.columnsPerLine} cols/line)",
      style = MaterialTheme.typography.labelMedium,
      color = PrimaryBlue,
      fontWeight = FontWeight.SemiBold
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrintingPreferencesCard(
  printerSettings: PrinterSettings,
  onSelectRenderMode: (PrintRenderMode) -> Unit,
  onSelectDensity: (PrintDensity) -> Unit,
  onSelectAlignment: (ReceiptAlignment) -> Unit,
  onSelectFontSize: (ReceiptFontSize) -> Unit,
  onToggleBoldText: (Boolean) -> Unit,
  onToggleAutoPrint: (Boolean) -> Unit
) {
  val colorScheme = MaterialTheme.colorScheme

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(colorScheme.surface, PrinterCardShape)
      .border(1.dp, colorScheme.outline, PrinterCardShape)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      text = "Printing Preferences",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold
    )

    // Render Mode (ESC/POS Text vs Raster Image)
    Text(
      text = "Printing Protocol Mode",
      style = MaterialTheme.typography.labelMedium,
      color = colorScheme.onSurfaceVariant
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      PrintRenderMode.entries.forEach { mode ->
        FilterChip(
          selected = printerSettings.renderMode == mode,
          onClick = { onSelectRenderMode(mode) },
          label = { Text(mode.label, fontSize = 12.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryBlue,
            selectedLabelColor = SurfaceLight
          )
        )
      }
    }
    Text(
      text = printerSettings.renderMode.subtitle,
      style = MaterialTheme.typography.labelSmall,
      color = colorScheme.onSurfaceVariant
    )

    // Print Density
    Text(
      text = "Print Density (if supported by printer firmware)",
      style = MaterialTheme.typography.labelMedium,
      color = colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      PrintDensity.entries.forEach { density ->
        FilterChip(
          selected = printerSettings.printDensity == density,
          onClick = { onSelectDensity(density) },
          label = { Text(density.label, fontSize = 12.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryBlue,
            selectedLabelColor = SurfaceLight
          )
        )
      }
    }

    // Header / Footer Text Alignment
    Text(
      text = "Header & Footer Alignment",
      style = MaterialTheme.typography.labelMedium,
      color = colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ReceiptAlignment.entries.forEach { align ->
        FilterChip(
          selected = printerSettings.textAlignment == align,
          onClick = { onSelectAlignment(align) },
          label = { Text(align.label, fontSize = 12.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryBlue,
            selectedLabelColor = SurfaceLight
          )
        )
      }
    }

    // Font Size
    Text(
      text = "Receipt Font Size",
      style = MaterialTheme.typography.labelMedium,
      color = colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ReceiptFontSize.entries.forEach { size ->
        FilterChip(
          selected = printerSettings.fontSize == size,
          onClick = { onSelectFontSize(size) },
          label = { Text(size.label, fontSize = 12.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryBlue,
            selectedLabelColor = SurfaceLight
          )
        )
      }
    }

    HorizontalDivider(color = colorScheme.outline)

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Bold Receipt Text", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text("Darker, heavier text weight across all receipt lines", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
      }
      Switch(
        checked = printerSettings.boldText,
        onCheckedChange = onToggleBoldText
      )
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Auto-Print After Bill Generation", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text("Automatically send bill to connected printer on save", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
      }
      Switch(
        checked = printerSettings.autoPrintAfterBill,
        onCheckedChange = onToggleAutoPrint,
        modifier = Modifier.testTag("auto_print_switch")
      )
    }
  }
}

@Composable
private fun PrinterTestCard(
  connectionState: PrinterConnectionState,
  printerSettings: PrinterSettings,
  printJobState: PrintJobState,
  onPrintTest: () -> Unit,
  onRetry: () -> Unit
) {
  val colorScheme = MaterialTheme.colorScheme
  val isConnected = connectionState is PrinterConnectionState.Connected

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(colorScheme.surface, PrinterCardShape)
      .border(1.dp, colorScheme.outline, PrinterCardShape)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text(
      text = "Test Receipt",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = "Prints MEHER BASAN BHANDAR • POSIFLOW • Bluetooth • ${printerSettings.selectedPaperWidthLabel} verification receipt.",
      style = MaterialTheme.typography.bodySmall,
      color = colorScheme.onSurfaceVariant
    )

    if (printJobState.errorMessage != null) {
      Text(
        text = printJobState.errorMessage,
        style = MaterialTheme.typography.bodySmall,
        color = WarningOrange
      )
    } else if (printJobState.statusMessage != null) {
      Text(
        text = printJobState.statusMessage,
        style = MaterialTheme.typography.bodySmall,
        color = SuccessGreen
      )
    }

    if (printJobState.paperStatusWarning != null && printJobState.errorMessage == null) {
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
        onClick = onPrintTest,
        enabled = !printJobState.isPrinting,
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("print_test_receipt_button"),
        shape = PrinterButtonShape,
        colors = ButtonDefaults.buttonColors(
          containerColor = if (isConnected) PrimaryBlue else colorScheme.secondary
        )
      ) {
        if (printJobState.isPrinting) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = SurfaceLight,
            strokeWidth = 2.dp
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Sending Test Receipt...")
        } else {
          Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Print Test Receipt", fontWeight = FontWeight.SemiBold)
        }
      }

      if (printJobState.errorMessage != null && !printJobState.isPrinting) {
        OutlinedButton(
          onClick = onRetry,
          modifier = Modifier.height(48.dp),
          shape = PrinterButtonShape
        ) {
          Text("Retry")
        }
      }
    }
  }
}
