package com.example.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

sealed interface TransportPrintResult {
  data class Sent(
    val deviceName: String,
    val bytesWritten: Int,
    val paperStatusWarning: String? = null
  ) : TransportPrintResult

  data class Failed(val message: String, val disconnected: Boolean = false) : TransportPrintResult
}

sealed interface PairedDevicesResult {
  data class Success(val devices: List<BluetoothPrinterDevice>) : PairedDevicesResult
  data object PermissionRequired : PairedDevicesResult
  data object BluetoothDisabled : PairedDevicesResult
  data object BluetoothUnsupported : PairedDevicesResult
  data class Error(val message: String) : PairedDevicesResult
}

/**
 * Transport abstraction so Bluetooth Classic SPP printers work today and BLE GATT printers
 * can be plugged in cleanly without changing receipt formatting or billing UI code.
 */
interface PrinterTransport {
  val transportType: PrinterTransportType
  val connectionState: StateFlow<PrinterConnectionState>
  fun hasRequiredPermission(): Boolean
  fun isBluetoothEnabled(): Boolean
  fun isBluetoothSupported(): Boolean
  suspend fun getPairedDevices(): PairedDevicesResult
  suspend fun connect(device: BluetoothPrinterDevice): PrinterConnectionState
  suspend fun disconnect()
  suspend fun writeReceiptBytes(data: ByteArray): TransportPrintResult
  fun release()
}

class BluetoothClassicSppService(context: Context) : PrinterTransport {

  private val appContext = context.applicationContext
  private val ioMutex = Mutex()

  private val bluetoothAdapter: BluetoothAdapter? by lazy {
    val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    manager?.adapter
  }

  private val _connectionState = MutableStateFlow<PrinterConnectionState>(PrinterConnectionState.Disconnected)
  override val connectionState: StateFlow<PrinterConnectionState> = _connectionState.asStateFlow()

  override val transportType: PrinterTransportType = PrinterTransportType.BLUETOOTH_CLASSIC_SPP

  @Volatile
  private var activeSocket: BluetoothSocket? = null

  @Volatile
  private var activeDevice: BluetoothPrinterDevice? = null

  private var receiverRegistered = false

  private val bluetoothReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      when (intent?.action) {
        BluetoothAdapter.ACTION_STATE_CHANGED -> {
          val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
          if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
            closeSocketSilently()
            _connectionState.value = PrinterConnectionState.Disconnected
          }
        }
        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
          val disconnectedDevice = getParcelableDevice(intent)
          val current = activeDevice
          if (current != null && disconnectedDevice?.address.equals(current.address, ignoreCase = true)) {
            closeSocketSilently()
            _connectionState.value = PrinterConnectionState.Error(
              message = "${current.name} disconnected. Turn on printer and reconnect.",
              deviceName = current.name
            )
          }
        }
      }
    }
  }

  init {
    registerReceiverSafely()
  }

  @Suppress("DEPRECATION")
  private fun getParcelableDevice(intent: Intent): BluetoothDevice? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
      intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
  }

  private fun registerReceiverSafely() {
    if (receiverRegistered) return
    val filter = IntentFilter().apply {
      addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
      addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
    }
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        appContext.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
      } else {
        appContext.registerReceiver(bluetoothReceiver, filter)
      }
      receiverRegistered = true
    }
  }

  override fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

  override fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

  override fun hasRequiredPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.BLUETOOTH_CONNECT
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.BLUETOOTH
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  @SuppressLint("MissingPermission")
  override suspend fun getPairedDevices(): PairedDevicesResult = withContext(Dispatchers.IO) {
    val adapter = bluetoothAdapter ?: return@withContext PairedDevicesResult.BluetoothUnsupported
    if (!hasRequiredPermission()) return@withContext PairedDevicesResult.PermissionRequired
    if (!adapter.isEnabled) return@withContext PairedDevicesResult.BluetoothDisabled

    return@withContext try {
      val bonded = adapter.bondedDevices.orEmpty()
      val mapped = bonded.map { device ->
        val rawName = runCatching { device.name }.getOrNull()?.trim().orEmpty()
        val displayName = rawName.ifBlank { "Bluetooth Device (${device.address})" }
        val isPosiflow = isPosiflowPrinterName(rawName)
        val isImagingClass = runCatching {
          device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.IMAGING
        }.getOrDefault(false)
        val isPrinter = isPosiflow || isImagingClass || isLikelyThermalPrinterName(rawName)

        BluetoothPrinterDevice(
          name = displayName,
          address = device.address,
          isPosiflowBrand = isPosiflow,
          isLikelyPrinter = isPrinter,
          transportType = PrinterTransportType.BLUETOOTH_CLASSIC_SPP
        )
      }.sortedWith(
        compareByDescending<BluetoothPrinterDevice> { it.isPosiflowBrand }
          .thenByDescending { it.isLikelyPrinter }
          .thenBy { it.name.lowercase() }
      )
      PairedDevicesResult.Success(mapped)
    } catch (se: SecurityException) {
      PairedDevicesResult.PermissionRequired
    } catch (e: Exception) {
      PairedDevicesResult.Error(e.message ?: "Unable to read paired Bluetooth devices")
    }
  }

  @SuppressLint("MissingPermission")
  override suspend fun connect(device: BluetoothPrinterDevice): PrinterConnectionState =
    ioMutex.withLock {
      val adapter = bluetoothAdapter
        ?: return@withLock PrinterConnectionState.Error("Bluetooth hardware unavailable").also {
          _connectionState.value = it
        }

      if (!hasRequiredPermission()) {
        return@withLock PrinterConnectionState.Error("Bluetooth permission required").also {
          _connectionState.value = it
        }
      }

      if (!adapter.isEnabled) {
        return@withLock PrinterConnectionState.Error("Turn on Bluetooth to connect").also {
          _connectionState.value = it
        }
      }

      if (!BluetoothAdapter.checkBluetoothAddress(device.address)) {
        return@withLock PrinterConnectionState.Error("Invalid printer address").also {
          _connectionState.value = it
        }
      }

      closeSocketSilently()
      _connectionState.value = PrinterConnectionState.Connecting(device.name, device.address)

      withContext(Dispatchers.IO) {
        val btDevice = try {
          adapter.getRemoteDevice(device.address)
        } catch (e: Exception) {
          val err = PrinterConnectionState.Error(
            message = "Selected printer ${device.name} was not found",
            deviceName = device.name
          )
          _connectionState.value = err
          return@withContext err
        }

        // Legacy discovery cancel only below API 31 to avoid requiring BLUETOOTH_SCAN
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
          runCatching {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
          }
        }

        var candidateSocket: BluetoothSocket? = null
        try {
          withTimeout(CONNECT_TIMEOUT_MS) {
            candidateSocket = openAndConnectSocket(btDevice, secure = true)
              ?: openAndConnectSocket(btDevice, secure = false)
          }

          val connectedSocket = candidateSocket
          if (connectedSocket == null || !connectedSocket.isConnected) {
            closeQuietly(candidateSocket)
            val err = PrinterConnectionState.Error(
              message = "Could not connect to ${device.name}. Ensure the printer is powered on and nearby.",
              deviceName = device.name
            )
            _connectionState.value = err
            return@withContext err
          }

          activeSocket = connectedSocket
          activeDevice = device
          val connected = PrinterConnectionState.Connected(device.name, device.address)
          _connectionState.value = connected
          connected
        } catch (_: TimeoutCancellationException) {
          closeQuietly(candidateSocket)
          val err = PrinterConnectionState.Error(
            message = "Connection to ${device.name} timed out. Check printer power and range.",
            deviceName = device.name
          )
          _connectionState.value = err
          err
        } catch (_: SecurityException) {
          closeQuietly(candidateSocket)
          val err = PrinterConnectionState.Error(
            message = "Bluetooth Connect permission denied.",
            deviceName = device.name
          )
          _connectionState.value = err
          err
        } catch (e: IOException) {
          closeQuietly(candidateSocket)
          val err = PrinterConnectionState.Error(
            message = "Failed to connect to ${device.name}: ${e.localizedMessage ?: "SPP link error"}",
            deviceName = device.name
          )
          _connectionState.value = err
          err
        }
      }
    }

  @SuppressLint("MissingPermission")
  private fun openAndConnectSocket(btDevice: BluetoothDevice, secure: Boolean): BluetoothSocket? {
    var socket: BluetoothSocket? = null
    return try {
      socket = if (secure) {
        btDevice.createRfcommSocketToServiceRecord(SPP_UUID)
      } else {
        btDevice.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
      }
      socket.connect()
      socket
    } catch (_: IOException) {
      closeQuietly(socket)
      null
    }
  }

  override suspend fun disconnect() {
    ioMutex.withLock {
      withContext(Dispatchers.IO) {
        closeSocketSilently()
      }
      _connectionState.value = PrinterConnectionState.Disconnected
    }
  }

  override suspend fun writeReceiptBytes(data: ByteArray): TransportPrintResult =
    ioMutex.withLock {
      if (data.isEmpty()) {
        return@withLock TransportPrintResult.Failed("Receipt payload is empty")
      }

      val socket = activeSocket
      val device = activeDevice
      if (socket == null || device == null || !socket.isConnected) {
        closeSocketSilently()
        _connectionState.value = PrinterConnectionState.Disconnected
        return@withLock TransportPrintResult.Failed(
          message = "Printer is not connected. Connect your POSIFLOW printer first.",
          disconnected = true
        )
      }

      if (!hasRequiredPermission()) {
        return@withLock TransportPrintResult.Failed(
          message = "Bluetooth Connect permission is required to print."
        )
      }

      withContext(Dispatchers.IO) {
        try {
          val out = socket.outputStream
          val input = socket.inputStream

          // Drain stale bytes if any before writing
          runCatching {
            val stale = input.available()
            if (stale > 0) input.skip(stale.toLong())
          }

          var offset = 0
          val chunkSize = 1024
          val isLargeRaster = data.size > 2048
          while (offset < data.size) {
            val count = minOf(chunkSize, data.size - offset)
            out.write(data, offset, count)
            out.flush()
            offset += count
            if (isLargeRaster && offset < data.size) {
              delay(15L)
            }
          }

          // Optional non-blocking ESC/POS real-time paper sensor check (DLE EOT 4)
          val paperWarning = probePaperStatusNonBlocking(out, input)

          TransportPrintResult.Sent(
            deviceName = device.name,
            bytesWritten = data.size,
            paperStatusWarning = paperWarning
          )
        } catch (e: IOException) {
          closeSocketSilently()
          _connectionState.value = PrinterConnectionState.Error(
            message = "Connection lost during printing (${e.localizedMessage ?: "I/O error"}).",
            deviceName = device.name
          )
          TransportPrintResult.Failed(
            message = "Printing failed: Bluetooth connection lost (${e.localizedMessage ?: "check printer"}).",
            disconnected = true
          )
        } catch (e: Exception) {
          TransportPrintResult.Failed(
            message = "Unexpected printing error: ${e.localizedMessage ?: "unknown error"}"
          )
        }
      }
    }

  private suspend fun probePaperStatusNonBlocking(
    out: java.io.OutputStream,
    input: java.io.InputStream
  ): String? {
    return runCatching {
      out.write(byteArrayOf(0x10, 0x04, 0x04)) // DLE EOT 4 (paper sensor)
      out.flush()
      delay(90L)
      if (input.available() > 0) {
        val statusByte = input.read()
        // Standard ESC/POS DLE EOT 4: bits 5 & 6 (0x60) indicate paper end
        if (statusByte >= 0 && (statusByte and 0x60) == 0x60) {
          return@runCatching "Printer sensor reports out of paper."
        }
      }
      null
    }.getOrNull()
  }

  private fun closeSocketSilently() {
    closeQuietly(activeSocket)
    activeSocket = null
    activeDevice = null
  }

  private fun closeQuietly(socket: BluetoothSocket?) {
    runCatching { socket?.close() }
  }

  override fun release() {
    if (receiverRegistered) {
      runCatching { appContext.unregisterReceiver(bluetoothReceiver) }
      receiverRegistered = false
    }
    closeSocketSilently()
    _connectionState.value = PrinterConnectionState.Disconnected
  }

  companion object {
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val CONNECT_TIMEOUT_MS = 12_000L

    fun isPosiflowPrinterName(rawName: String?): Boolean {
      if (rawName.isNullOrBlank()) return false
      val upper = rawName.uppercase()
      return upper.contains("POSIFLOW") ||
        upper.contains("POSI-FLOW") ||
        upper.contains("POSI_FLOW")
    }

    fun isLikelyThermalPrinterName(rawName: String?): Boolean {
      if (rawName.isNullOrBlank()) return false
      val upper = rawName.uppercase()
      return isPosiflowPrinterName(upper) ||
        upper.contains("PRINTER") ||
        upper.contains("THERMAL") ||
        upper.startsWith("POS-") ||
        upper.startsWith("POS_") ||
        upper.startsWith("MPT") ||
        upper.startsWith("RPP") ||
        upper.startsWith("MTP") ||
        upper.startsWith("BTP") ||
        upper.contains("58MM") ||
        upper.contains("80MM")
    }
  }
}
