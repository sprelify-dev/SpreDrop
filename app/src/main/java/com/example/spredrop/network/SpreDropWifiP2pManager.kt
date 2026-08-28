package com.example.spredrop.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SpreDrop high-speed, direct, internet-free Wi-Fi Direct (P2P) connection manager.
 * Sets up an isolated local network between discovered peers for seamless streaming.
 */
class SpreDropWifiP2pManager(private val context: Context) {
    companion object {
        private const val TAG = "SpreDropWifiP2p"
    }

    private val wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private val _isP2pEnabled = MutableStateFlow(false)
    val isP2pEnabled: StateFlow<Boolean> = _isP2pEnabled.asStateFlow()

    private val _peersList = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peersList: StateFlow<List<WifiP2pDevice>> = _peersList.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _thisDevice = MutableStateFlow<WifiP2pDevice?>(null)
    val thisDevice: StateFlow<WifiP2pDevice?> = _thisDevice.asStateFlow()

    private var receiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    init {
        wifiP2pManager?.let { manager ->
            channel = manager.initialize(context, context.mainLooper, null)
        }
    }

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    fun register() {
        if (isReceiverRegistered) return
        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        _isP2pEnabled.value = isEnabled
                        Log.d(TAG, "P2P state changed: enabled = $isEnabled")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        Log.d(TAG, "P2P peers list changed. Requesting list...")
                        requestPeers()
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        @Suppress("DEPRECATION")
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                        Log.d(TAG, "P2P connection changed: connected = ${networkInfo?.isConnected}")
                        if (networkInfo?.isConnected == true) {
                            requestConnectionInfo()
                        } else {
                            _connectionInfo.value = null
                        }
                    }
                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        @Suppress("DEPRECATION")
                        val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                        _thisDevice.value = device
                        Log.d(TAG, "This device changed: name = ${device?.deviceName}, address = ${device?.deviceAddress}")
                    }
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            intentFilter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isReceiverRegistered = true
        Log.d(TAG, "Wi-Fi Direct BroadcastReceiver registered")
    }

    fun unregister() {
        if (!isReceiverRegistered) return
        receiver?.let {
            context.unregisterReceiver(it)
        }
        receiver = null
        isReceiverRegistered = false
        Log.d(TAG, "Wi-Fi Direct BroadcastReceiver unregistered")
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        val manager = wifiP2pManager ?: return onFailure(-1)
        val chan = channel ?: return onFailure(-1)

        manager.discoverPeers(chan, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "P2P discovery initiated successfully")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "P2P discovery initiation failed: reason = $reason")
                onFailure(reason)
            }
        })
    }

    fun stopDiscovery(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        val manager = wifiP2pManager ?: return onFailure(-1)
        val chan = channel ?: return onFailure(-1)

        manager.stopPeerDiscovery(chan, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "P2P discovery stopped")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "P2P stop discovery failed: reason = $reason")
                onFailure(reason)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String, onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        val manager = wifiP2pManager ?: return onFailure(-1)
        val chan = channel ?: return onFailure(-1)

        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            this.wps.setup = WpsInfo.PBC
        }

        Log.i(TAG, "Connecting to peer: $deviceAddress")
        manager.connect(chan, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "P2P connect negotiation initiated successfully")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "P2P connect negotiation failed: reason = $reason")
                onFailure(reason)
            }
        })
    }

    fun disconnect(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        val manager = wifiP2pManager ?: return onFailure(-1)
        val chan = channel ?: return onFailure(-1)

        manager.removeGroup(chan, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "P2P group removed / disconnected successfully")
                _connectionInfo.value = null
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "P2P group removal failed: reason = $reason")
                onFailure(reason)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        val manager = wifiP2pManager ?: return
        val chan = channel ?: return

        manager.requestPeers(chan) { peerList ->
            val list = peerList?.deviceList?.toList() ?: emptyList()
            _peersList.value = list
            Log.d(TAG, "Discovered ${list.size} Wi-Fi Direct peers")
        }
    }

    private fun requestConnectionInfo() {
        val manager = wifiP2pManager ?: return
        val chan = channel ?: return

        manager.requestConnectionInfo(chan) { info ->
            _connectionInfo.value = info
            if (info != null && info.groupFormed) {
                Log.i(TAG, "P2P group formed. Is Group Owner: ${info.isGroupOwner}, GO Address: ${info.groupOwnerAddress?.hostAddress}")
            }
        }
    }

    private var serverSocket: java.net.ServerSocket? = null
    private var isServerRunning = false

    fun startP2pSocketServer(onFileReceived: (String, Long, java.io.File) -> Unit, onError: (String) -> Unit) {
        if (isServerRunning) return
        isServerRunning = true
        Thread {
            try {
                val server = java.net.ServerSocket(8988)
                serverSocket = server
                Log.i(TAG, "P2P Socket Server started on port 8988")
                while (isServerRunning) {
                    val client = try {
                        server.accept()
                    } catch (e: Exception) {
                        break
                    }
                    Log.i(TAG, "P2P client connected: ${client.inetAddress.hostAddress}")
                    
                    try {
                        val input = client.getInputStream()
                        val dataInput = java.io.DataInputStream(input)
                        
                        val fileName = dataInput.readUTF()
                        val fileSize = dataInput.readLong()
                        
                        val destDir = java.io.File(context.getExternalFilesDir(null), "SpreDrop_Received")
                        if (!destDir.exists()) destDir.mkdirs()
                        val destFile = java.io.File(destDir, "p2p_$fileName")
                        
                        java.io.FileOutputStream(destFile).use { fos ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var totalRead = 0L
                            while (totalRead < fileSize) {
                                val remain = (fileSize - totalRead).coerceAtMost(buffer.size.toLong()).toInt()
                                bytesRead = dataInput.read(buffer, 0, remain)
                                if (bytesRead == -1) break
                                fos.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                            }
                        }
                        onFileReceived(fileName, fileSize, destFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling client connection: ${e.message}")
                    } finally {
                        try {
                            client.close()
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Socket Server Error: ${e.message}")
                onError(e.localizedMessage ?: "Unknown socket error")
            } finally {
                isServerRunning = false
            }
        }.start()
    }

    fun stopP2pSocketServer() {
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        Log.i(TAG, "P2P Socket Server stopped")
    }

    fun sendFileDirectly(uri: android.net.Uri, host: String, onProgress: (Long) -> Unit, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        Thread {
            try {
                var fileName = "p2p_file"
                var fileSize = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) fileName = cursor.getString(nameIdx)
                        val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                    }
                }

                val socket = java.net.Socket()
                socket.bind(null)
                Log.i(TAG, "Connecting to host: $host on port 8988")
                socket.connect(java.net.InetSocketAddress(host, 8988), 15000)
                
                val output = socket.getOutputStream()
                val dataOutput = java.io.DataOutputStream(output)
                
                dataOutput.writeUTF(fileName)
                dataOutput.writeLong(fileSize)
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalSent = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        dataOutput.write(buffer, 0, bytesRead)
                        totalSent += bytesRead
                        onProgress(totalSent)
                    }
                }
                dataOutput.flush()
                socket.close()
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send direct P2P socket stream: ${e.message}")
                onFailure(e.localizedMessage ?: "Unknown streaming error")
            }
        }.start()
    }
}
