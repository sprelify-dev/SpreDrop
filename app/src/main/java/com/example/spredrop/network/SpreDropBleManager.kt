package com.example.spredrop.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.spredrop.model.PeerConnectionType
import com.example.spredrop.model.PeerDevice
import com.example.spredrop.model.UserPresence
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * SpreDrop Bluetooth Low Energy (BLE) Proximity Discovery & Advertising Engine.
 * Advertises custom SpreDrop 16-bit/128-bit UUID and scans nearby devices even when
 * screen is locked or app is in background.
 */
class SpreDropBleManager(
    private val context: Context,
    private val onPeerDiscovered: (PeerDevice) -> Unit
) {
    companion object {
        private const val TAG = "SpreDropBleManager"
        // Custom 128-bit UUID reserved for SpreDrop peer discovery protocol
        val SPREDROP_SERVICE_UUID: UUID = UUID.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")
        val PARCEL_SPREDROP_UUID = ParcelUuid(SPREDROP_SERVICE_UUID)
    }

    private val bleScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanner: BluetoothLeScanner? = null

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _isAdvertising.value = true
            Log.i(TAG, "BLE Advertising started successfully for SpreDrop")
        }

        override fun onStartFailure(errorCode: Int) {
            _isAdvertising.value = false
            Log.w(TAG, "BLE Advertising failed with error code: $errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            Log.w(TAG, "BLE Scan failed with error code: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(spreDropId: String, displayName: String, userId: String) {
        stopAdvertising()
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth not enabled for BLE advertising")
            return
        }

        bleAdvertiser = adapter.bluetoothLeAdvertiser
        if (bleAdvertiser == null) {
            Log.w(TAG, "Device does not support BLE Advertising")
            return
        }

        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0) // indefinite
                .build()

            // Encode payload: Handle without '@', max 20 chars for BLE advertisement payload
            val cleanHandle = spreDropId.removePrefix("@").take(18)
            val payloadBytes = cleanHandle.toByteArray(StandardCharsets.UTF_8)

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(PARCEL_SPREDROP_UUID)
                .addServiceData(PARCEL_SPREDROP_UUID, payloadBytes)
                .build()

            bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing Bluetooth permissions to advertise: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE advertiser: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            _isAdvertising.value = false
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        stopScanning()
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter unavailable for scanning")
            return
        }

        bleScanner = adapter.bluetoothLeScanner
        if (bleScanner == null) {
            Log.w(TAG, "BluetoothLeScanner not available")
            return
        }

        try {
            val filter = ScanFilter.Builder()
                .setServiceUuid(PARCEL_SPREDROP_UUID)
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()

            bleScanner?.startScan(listOf(filter), settings, scanCallback)
            _isScanning.value = true
            Log.i(TAG, "Started BLE Proximity Scan for SpreDrop UUID")
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing Bluetooth permissions to scan: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scanner: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        try {
            bleScanner?.stopScan(scanCallback)
            _isScanning.value = false
        } catch (_: Exception) {}
    }

    private fun handleScanResult(result: ScanResult) {
        try {
            val record = result.scanRecord ?: return
            val serviceData = record.getServiceData(PARCEL_SPREDROP_UUID)
            val handleRaw = if (serviceData != null && serviceData.isNotEmpty()) {
                String(serviceData, StandardCharsets.UTF_8)
            } else {
                try {
                    result.device.name ?: "Nearby SpreDrop"
                } catch (_: SecurityException) {
                    "Nearby SpreDrop"
                }
            }

            val handle = if (handleRaw.startsWith("@")) handleRaw else "@$handleRaw"
            val macAddress = result.device.address ?: "00:00:00:00:00:00"
            val deviceId = "ble_${macAddress.replace(":", "").lowercase()}"

            val peer = PeerDevice(
                deviceId = deviceId,
                spreDropId = handle,
                displayName = handle.removePrefix("@").replaceFirstChar { it.uppercase() },
                avatarColorHex = getDeterministicColor(handle),
                availability = UserPresence.AVAILABLE,
                isFriend = false,
                connectionType = PeerConnectionType.NEARBY_BLE,
                signalStrengthRssi = result.rssi,
                ipAddress = "BLE:$macAddress",
                lastDiscovered = System.currentTimeMillis(),
                supportedCapabilities = listOf("BLE_ADVERTISE", "WEBRTC_DATACHANNEL", "SHA256_INTEGRITY")
            )

            onPeerDiscovered(peer)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing BLE scan result: ${e.message}")
        }
    }

    private fun getDeterministicColor(input: String): String {
        val colors = listOf("#00B4D8", "#06D6A0", "#6366F1", "#EC4899", "#F59E0B", "#8B5CF6", "#10B981", "#3B82F6")
        val index = (input.hashCode() and 0x7FFFFFFF) % colors.size
        return colors[index]
    }
}
