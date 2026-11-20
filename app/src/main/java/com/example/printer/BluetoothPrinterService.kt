package com.example.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
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

interface PrinterTransport {
  val transportType: PrinterTransportType
  val connectionState: StateFlow<PrinterConnectionState>
  fun hasRequiredPermission(): Boolean
  fun hasScanPermission(): Boolean = false
  fun isBluetoothEnabled(): Boolean
  fun isBluetoothSupported(): Boolean
  suspend fun getPairedDevices(): PairedDevicesResult
  suspend fun scanNearbyBleDevices(durationMs: Long = 4500L): PairedDevicesResult =
    PairedDevicesResult.Success(emptyList())
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
  private var activeBleSession: BleGattSession? = null

  @Volatile
  private var activeDevice: BluetoothPrinterDevice? = null

  private var receiverRegistered = false

  private val bluetoothReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      when (intent?.action) {
        BluetoothAdapter.ACTION_STATE_CHANGED -> {
          val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
          if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
            closeAllActiveLinksSilently()
            _connectionState.value = PrinterConnectionState.Disconnected
          }
        }
        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
          val disconnectedDevice = getParcelableDevice(intent)
          val current = activeDevice
          if (current != null && disconnectedDevice?.address.equals(current.address, ignoreCase = true)) {
            closeAllActiveLinksSilently()
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

  override fun hasScanPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.BLUETOOTH_SCAN
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      hasRequiredPermission()
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

        val devType = runCatching { device.type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)
        val typeLabel = when (devType) {
          BluetoothDevice.DEVICE_TYPE_LE -> "Paired (BLE/SPP)"
          BluetoothDevice.DEVICE_TYPE_DUAL -> "Classic+BLE"
          BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic SPP"
          else -> "Bluetooth"
        }

        BluetoothPrinterDevice(
          name = displayName,
          address = device.address,
          isPosiflowBrand = isPosiflow,
          isLikelyPrinter = isPrinter,
          // Always start with Classic SPP for paired devices; BLE is used as fallback if RFCOMM fails
          transportType = PrinterTransportType.BLUETOOTH_CLASSIC_SPP,
          deviceTypeLabel = typeLabel
        )
      }.sortedWith(
        compareByDescending<BluetoothPrinterDevice> { it.isPosiflowBrand }
          .thenByDescending { it.isLikelyPrinter }
          .thenBy { it.name.lowercase() }
      )
      PairedDevicesResult.Success(mapped)
    } catch (_: SecurityException) {
      PairedDevicesResult.PermissionRequired
    } catch (e: Exception) {
      PairedDevicesResult.Error(e.message ?: "Unable to read paired Bluetooth devices")
    }
  }

  @SuppressLint("MissingPermission")
  override suspend fun scanNearbyBleDevices(durationMs: Long): PairedDevicesResult =
    withContext(Dispatchers.IO) {
      val adapter = bluetoothAdapter ?: return@withContext PairedDevicesResult.BluetoothUnsupported
      if (!hasRequiredPermission() || !hasScanPermission()) {
        return@withContext PairedDevicesResult.PermissionRequired
      }
      if (!adapter.isEnabled) return@withContext PairedDevicesResult.BluetoothDisabled

      val scanner = adapter.bluetoothLeScanner
        ?: return@withContext PairedDevicesResult.Error("BLE scanner unavailable on this device")

      val found = LinkedHashMap<String, BluetoothPrinterDevice>()
      val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
          val dev = result.device ?: return
          val addr = dev.address ?: return
          val rawName = runCatching { dev.name }.getOrNull()?.trim()
            ?: result.scanRecord?.deviceName?.trim().orEmpty()
          if (rawName.isBlank()) return
          val isPosiflow = isPosiflowPrinterName(rawName)
          val isPrinter = isPosiflow || isLikelyThermalPrinterName(rawName)
          found[addr] = BluetoothPrinterDevice(
            name = rawName,
            address = addr,
            isPosiflowBrand = isPosiflow,
            isLikelyPrinter = isPrinter,
            transportType = PrinterTransportType.BLE_GATT,
            deviceTypeLabel = "BLE"
          )
        }
      }

      return@withContext try {
        val settings = ScanSettings.Builder()
          .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
          .build()
        scanner.startScan(null, settings, callback)
        delay(durationMs.coerceIn(1500L, 8000L))
        runCatching { scanner.stopScan(callback) }
        val sorted = found.values.sortedWith(
          compareByDescending<BluetoothPrinterDevice> { it.isPosiflowBrand }
            .thenByDescending { it.isLikelyPrinter }
            .thenBy { it.name.lowercase() }
        )
        PairedDevicesResult.Success(sorted)
      } catch (_: SecurityException) {
        PairedDevicesResult.PermissionRequired
      } catch (e: Exception) {
        runCatching { scanner.stopScan(callback) }
        PairedDevicesResult.Error(e.message ?: "BLE scan failed")
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

      closeAllActiveLinksSilently()
      _connectionState.value = PrinterConnectionState.Connecting(device.name, device.address)

      withContext(Dispatchers.IO) {
        val btDevice = try {
          adapter.getRemoteDevice(device.address)
        } catch (_: Exception) {
          val err = PrinterConnectionState.Error(
            message = "Selected printer ${device.name} was not found",
            deviceName = device.name
          )
          _connectionState.value = err
          return@withContext err
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
          runCatching {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
          }
        }

        val devType = runCatching { btDevice.type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)
        val isBonded = runCatching { btDevice.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
        val shouldTryBleOnly = device.transportType == PrinterTransportType.BLE_GATT && !isBonded
        val supportsBleFallback = devType == BluetoothDevice.DEVICE_TYPE_LE ||
          devType == BluetoothDevice.DEVICE_TYPE_DUAL ||
          devType == BluetoothDevice.DEVICE_TYPE_UNKNOWN

        var lastErrorMsg: String? = null

        if (!shouldTryBleOnly) {
          val methods = listOf(
            ConnectMethod.SECURE_SPP,
            ConnectMethod.INSECURE_SPP,
            ConnectMethod.RFCOMM_CHANNEL_1
          )
          for (method in methods) {
            var pendingSocket: BluetoothSocket? = null
            try {
              val sppConnected = withTimeout(RFCOMM_ATTEMPT_TIMEOUT_MS) {
                connectRfcommAttempt(btDevice, method) { pendingSocket = it }
              }
              if (sppConnected != null && sppConnected.first.isConnected) {
                activeSocket = sppConnected.first
                activeDevice = device.copy(transportType = PrinterTransportType.BLUETOOTH_CLASSIC_SPP)
                val state = PrinterConnectionState.Connected(
                  deviceName = device.name,
                  deviceAddress = device.address,
                  protocolLabel = sppConnected.second
                )
                _connectionState.value = state
                return@withContext state
              }
            } catch (_: TimeoutCancellationException) {
              closeQuietly(pendingSocket)
              lastErrorMsg = "${method.label} timed out."
            } catch (_: SecurityException) {
              closeQuietly(pendingSocket)
              val err = PrinterConnectionState.Error(
                message = "Bluetooth Connect permission denied.",
                deviceName = device.name
              )
              _connectionState.value = err
              return@withContext err
            } catch (e: IOException) {
              closeQuietly(pendingSocket)
              lastErrorMsg = e.localizedMessage ?: "SPP link error"
            }
          }
        }

        // Try BLE GATT only when discovered as BLE-only or when Classic SPP/RFCOMM did not connect
        if (shouldTryBleOnly || supportsBleFallback) {
          val bleSession = BleGattSession(appContext, btDevice)
          val bleConnected = bleSession.connect(CONNECT_TIMEOUT_MS)
          if (bleConnected) {
            activeBleSession = bleSession
            activeDevice = device.copy(transportType = PrinterTransportType.BLE_GATT)
            val state = PrinterConnectionState.Connected(
              deviceName = device.name,
              deviceAddress = device.address,
              protocolLabel = "BLE GATT (${bleSession.characteristicShortName()})"
            )
            _connectionState.value = state
            return@withContext state
          } else {
            bleSession.close()
            if (shouldTryBleOnly) {
              lastErrorMsg = bleSession.lastError ?: "No writable BLE printer characteristic found"
            }
          }
        }

        val err = PrinterConnectionState.Error(
          message = "Could not connect to ${device.name}. ${lastErrorMsg ?: "Ensure the printer is powered ON, paper loaded, and in range."}",
          deviceName = device.name
        )
        _connectionState.value = err
        err
      }
    }

  private enum class ConnectMethod(val label: String) {
    SECURE_SPP("Bluetooth SPP"),
    INSECURE_SPP("Bluetooth SPP (Insecure)"),
    RFCOMM_CHANNEL_1("Bluetooth RFCOMM Ch 1")
  }

  @SuppressLint("MissingPermission")
  private fun connectRfcommAttempt(
    btDevice: BluetoothDevice,
    method: ConnectMethod,
    onSocketCreated: (BluetoothSocket?) -> Unit
  ): Pair<BluetoothSocket, String>? {
    var socket: BluetoothSocket? = null
    return try {
      socket = when (method) {
        ConnectMethod.SECURE_SPP -> btDevice.createRfcommSocketToServiceRecord(SPP_UUID)
        ConnectMethod.INSECURE_SPP -> btDevice.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
        ConnectMethod.RFCOMM_CHANNEL_1 -> {
          val createMethod = btDevice.javaClass.getMethod(
            "createRfcommSocket",
            Int::class.javaPrimitiveType
          )
          createMethod.invoke(btDevice, 1) as BluetoothSocket
        }
      }
      onSocketCreated(socket)
      socket.connect()
      socket to method.label
    } catch (_: Throwable) {
      closeQuietly(socket)
      onSocketCreated(null)
      null
    }
  }

  override suspend fun disconnect() {
    ioMutex.withLock {
      withContext(Dispatchers.IO) {
        closeAllActiveLinksSilently()
      }
      _connectionState.value = PrinterConnectionState.Disconnected
    }
  }

  override suspend fun writeReceiptBytes(data: ByteArray): TransportPrintResult =
    ioMutex.withLock {
      if (data.isEmpty()) {
        return@withLock TransportPrintResult.Failed("Receipt payload is empty")
      }

      if (!hasRequiredPermission()) {
        return@withLock TransportPrintResult.Failed(
          message = "Bluetooth Connect permission is required to print."
        )
      }

      val bleSession = activeBleSession
      val device = activeDevice
      if (bleSession != null && device != null) {
        return@withLock withContext(Dispatchers.IO) {
          val ok = bleSession.writeAll(data)
          if (ok) {
            TransportPrintResult.Sent(
              deviceName = device.name,
              bytesWritten = data.size,
              paperStatusWarning = null
            )
          } else {
            val errMsg = bleSession.lastError ?: "BLE write failed"
            closeAllActiveLinksSilently()
            _connectionState.value = PrinterConnectionState.Error(errMsg, device.name)
            TransportPrintResult.Failed("Printing failed: $errMsg", disconnected = true)
          }
        }
      }

      val socket = activeSocket
      if (socket == null || device == null || !socket.isConnected) {
        closeAllActiveLinksSilently()
        _connectionState.value = PrinterConnectionState.Disconnected
        return@withLock TransportPrintResult.Failed(
          message = "Printer is not connected. Connect your POSIFLOW printer first.",
          disconnected = true
        )
      }

      withContext(Dispatchers.IO) {
        try {
          val out = socket.outputStream
          val chunkSize = 256
          var offset = 0
          val paceEachChunk = data.size > 512
          while (offset < data.size) {
            val count = minOf(chunkSize, data.size - offset)
            out.write(data, offset, count)
            out.flush()
            offset += count
            if (paceEachChunk && offset < data.size) {
              delay(20L)
            }
          }
          // Allow Bluetooth SPP UART bridge time to drain buffered receipt lines
          delay(150L)

          TransportPrintResult.Sent(
            deviceName = device.name,
            bytesWritten = data.size,
            paperStatusWarning = null
          )
        } catch (e: IOException) {
          closeAllActiveLinksSilently()
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

  private fun closeAllActiveLinksSilently() {
    closeQuietly(activeSocket)
    activeSocket = null
    runCatching { activeBleSession?.close() }
    activeBleSession = null
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
    closeAllActiveLinksSilently()
    _connectionState.value = PrinterConnectionState.Disconnected
  }

  companion object {
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val RFCOMM_ATTEMPT_TIMEOUT_MS = 6_500L
    private const val CONNECT_TIMEOUT_MS = 10_000L

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

/**
 * Minimal, self-contained BLE GATT session supporting common ESC/POS BLE printer profiles
 * (18F0/2AF1, FFE0/FFE1, ISSC 49535343, Nordic UART 6E400001, or generic writable characteristic).
 */
@SuppressLint("MissingPermission")
private class BleGattSession(
  private val context: Context,
  private val device: BluetoothDevice
) {
  private var gatt: BluetoothGatt? = null
  private var writeCharacteristic: BluetoothGattCharacteristic? = null
  private var negotiatedMtuPayload = 20
  private var connectDeferred: CompletableDeferred<Boolean>? = null
  private var writeDeferred: CompletableDeferred<Boolean>? = null

  @Volatile
  var lastError: String? = null
    private set

  private val callback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
      if (status != BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
        lastError = "BLE connect failed (status $status)"
        connectDeferred?.complete(false)
        writeDeferred?.complete(false)
        return
      }
      when (newState) {
        BluetoothProfile.STATE_CONNECTED -> {
          runCatching { gatt.requestMtu(247) }
          gatt.discoverServices()
        }
        BluetoothProfile.STATE_DISCONNECTED -> {
          lastError = "BLE printer disconnected"
          connectDeferred?.complete(false)
          writeDeferred?.complete(false)
        }
      }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
      if (status == BluetoothGatt.GATT_SUCCESS && mtu > 23) {
        negotiatedMtuPayload = (mtu - 3).coerceIn(20, 240)
      }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
      if (status != BluetoothGatt.GATT_SUCCESS) {
        lastError = "BLE service discovery failed ($status)"
        connectDeferred?.complete(false)
        return
      }
      val char = findBestPrinterWriteCharacteristic(gatt)
      if (char != null) {
        writeCharacteristic = char
        connectDeferred?.complete(true)
      } else {
        lastError = "No writable BLE printer characteristic found"
        connectDeferred?.complete(false)
      }
    }

    override fun onCharacteristicWrite(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      status: Int
    ) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        writeDeferred?.complete(true)
      } else {
        lastError = "BLE write error ($status)"
        writeDeferred?.complete(false)
      }
    }
  }

  suspend fun connect(timeoutMs: Long): Boolean {
    val deferred = CompletableDeferred<Boolean>()
    connectDeferred = deferred
    gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    } else {
      @Suppress("DEPRECATION")
      device.connectGatt(context, false, callback)
    }
    return try {
      withTimeout(timeoutMs) { deferred.await() }
    } catch (_: TimeoutCancellationException) {
      lastError = "BLE GATT connection timed out"
      false
    }
  }

  suspend fun writeAll(data: ByteArray): Boolean {
    val activeGatt = gatt ?: return false
    val char = writeCharacteristic ?: return false

    val writeType = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
      BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    } else {
      BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    }

    val chunkMax = negotiatedMtuPayload.coerceIn(20, 180)
    var offset = 0
    while (offset < data.size) {
      val len = minOf(chunkMax, data.size - offset)
      val chunk = data.copyOfRange(offset, offset + len)
      val ack = CompletableDeferred<Boolean>()
      writeDeferred = ack

      val started = writeChunkCompat(activeGatt, char, chunk, writeType)
      if (!started) {
        lastError = "Failed to initiate BLE packet write"
        return false
      }
      val wrote = try {
        withTimeout(3000L) { ack.await() }
      } catch (_: TimeoutCancellationException) {
        lastError = "BLE write timed out"
        false
      }
      if (!wrote) return false

      offset += len
      if (offset < data.size) {
        delay(12L)
      }
    }
    delay(120L)
    return true
  }

  @Suppress("DEPRECATION")
  private fun writeChunkCompat(
    activeGatt: BluetoothGatt,
    char: BluetoothGattCharacteristic,
    chunk: ByteArray,
    writeType: Int
  ): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      activeGatt.writeCharacteristic(char, chunk, writeType) == BluetoothGatt.GATT_SUCCESS
    } else {
      char.writeType = writeType
      char.value = chunk
      activeGatt.writeCharacteristic(char)
    }
  }

  fun characteristicShortName(): String {
    val uuidStr = writeCharacteristic?.uuid?.toString()?.uppercase() ?: return "GATT"
    return uuidStr.substring(4, 8)
  }

  fun close() {
    runCatching { gatt?.disconnect() }
    runCatching { gatt?.close() }
    gatt = null
    writeCharacteristic = null
  }

  companion object {
    private val KNOWN_PRINTER_CHAR_UUIDS = listOf(
      "00002af1-0000-1000-8000-00805f9b34fb",
      "0000ffe1-0000-1000-8000-00805f9b34fb",
      "49535343-8841-43f4-a8d4-ecbe34729bb3",
      "6e400002-b5a3-f393-e0a9-e50e24dcca9e",
      "bef8d6c9-9c21-4c9e-b632-bd58c1009f9f"
    )

    private val IGNORED_SIG_SERVICE_UUIDS = setOf(
      "00001800-0000-1000-8000-00805f9b34fb",
      "00001801-0000-1000-8000-00805f9b34fb",
      "0000180a-0000-1000-8000-00805f9b34fb",
      "0000180f-0000-1000-8000-00805f9b34fb"
    )

    private fun findBestPrinterWriteCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
      val nonSystemServices = gatt.services.orEmpty().filter { svc ->
        !IGNORED_SIG_SERVICE_UUIDS.contains(svc.uuid.toString().lowercase())
      }
      val writable = nonSystemServices.flatMap { it.characteristics.orEmpty() }.filter { c ->
        (c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
          BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
      }
      for (preferred in KNOWN_PRINTER_CHAR_UUIDS) {
        writable.firstOrNull { it.uuid.toString().equals(preferred, ignoreCase = true) }?.let {
          return it
        }
      }
      return writable.firstOrNull()
    }
  }
}
