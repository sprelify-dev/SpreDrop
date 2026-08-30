package com.example.spredrop.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spredrop.data.SpreDropRepository
import com.example.spredrop.data.StorageStats
import com.example.spredrop.data.firebase.AuthState
import com.example.spredrop.data.firebase.FirebaseConfig
import com.example.spredrop.data.firebase.FirestoreConnectionState
import com.example.spredrop.model.*
import com.example.spredrop.security.QrCodeGenerator
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SpreDropViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpreDropRepository(application)

    // Wi-Fi P2P direct connectivity fields
    val isWifiP2pEnabled: StateFlow<Boolean> = repository.wifiP2pManager.isP2pEnabled
    val wifiP2pPeers: StateFlow<List<WifiP2pDevice>> = repository.wifiP2pManager.peersList
    val wifiP2pConnectionInfo: StateFlow<WifiP2pInfo?> = repository.wifiP2pManager.connectionInfo
    val wifiP2pThisDevice: StateFlow<WifiP2pDevice?> = repository.wifiP2pManager.thisDevice

    val userProfile: StateFlow<UserProfile?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val friends: StateFlow<List<Friend>> = repository.friends
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingRequests: StateFlow<List<Friend>> = repository.incomingFriendRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outgoingRequests: StateFlow<List<Friend>> = repository.outgoingFriendRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _usernameCheckResult = MutableStateFlow<String?>(null)
    val usernameCheckResult: StateFlow<String?> = _usernameCheckResult.asStateFlow()

    val activeTransfers: StateFlow<List<TransferRecord>> = repository.activeTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val receivedFiles: StateFlow<List<TransferRecord>> = repository.receivedFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTransfers: StateFlow<List<TransferRecord>> = repository.completedTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discoveredPeers: StateFlow<List<PeerDevice>> = repository.discoveredPeers

    val isOnline: StateFlow<Boolean> = repository.isOnline
    val isDiscovering: StateFlow<Boolean> = repository.isDiscovering

    val devLogs: StateFlow<List<DevLogEntry>> = repository.devLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Firebase Auth & Firestore State
    val authState: StateFlow<AuthState> = repository.authState
    val currentFirebaseUser: AuthenticatedAccount?
        get() = repository.currentFirebaseUser
    val firestoreConnectionState: StateFlow<FirestoreConnectionState> = repository.firestoreConnectionState
    val lastSyncTimestamp: StateFlow<Long> = repository.lastSyncTimestamp

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    // Selected peer for pending outgoing file transfer
    private val _selectedPeerForTransfer = MutableStateFlow<PeerDevice?>(null)
    val selectedPeerForTransfer: StateFlow<PeerDevice?> = _selectedPeerForTransfer.asStateFlow()

    // Snackbar / Toast message events
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        refreshStorageStats()
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun selectPeerForTransfer(peer: PeerDevice?) {
        _selectedPeerForTransfer.value = peer
    }

    fun sendFileToSelectedPeer(uri: Uri) {
        val peer = _selectedPeerForTransfer.value ?: return
        repository.startFileTransferToPeer(uri, peer)
        _selectedPeerForTransfer.value = null
        _userMessage.value = "Sending file to ${peer.displayName} (${peer.spreDropId})"
    }

    fun sendFileToPeer(uri: Uri, peer: PeerDevice) {
        repository.startFileTransferToPeer(uri, peer)
        _userMessage.value = "Sending file to ${peer.displayName} (${peer.spreDropId})"
    }

    fun cancelTransfer(transferId: String) {
        repository.cancelTransfer(transferId)
        _userMessage.value = "Transfer cancelled"
    }

    fun acceptIncomingTransfer(transfer: TransferRecord) {
        repository.acceptIncomingTransfer(transfer)
        _userMessage.value = "Transfer accepted. Receiving chunks..."
    }

    fun declineIncomingTransfer(transferId: String) {
        repository.declineIncomingTransfer(transferId)
        _userMessage.value = "Transfer declined"
    }

    fun updatePresence(presence: UserPresence) {
        viewModelScope.launch {
            repository.updatePresence(presence)
            _userMessage.value = "Presence set to ${presence.label}"
        }
    }

    fun updatePrivacy(privacy: PrivacyMode) {
        viewModelScope.launch {
            repository.updatePrivacy(privacy)
            _userMessage.value = "Privacy mode updated: ${privacy.label}"
        }
    }

    fun updateIdentity(spreDropId: String, displayName: String) {
        viewModelScope.launch {
            repository.updateProfileIdentity(spreDropId, displayName)
            _userMessage.value = "Profile identity updated to $spreDropId"
        }
    }

    fun registerWifiP2p() {
        repository.wifiP2pManager.register()
    }

    fun unregisterWifiP2p() {
        repository.wifiP2pManager.unregister()
    }

    fun startWifiP2pDiscovery() {
        repository.wifiP2pManager.startDiscovery(
            onSuccess = { _userMessage.value = "Wi-Fi Direct scan started successfully" },
            onFailure = { reason -> _userMessage.value = "Failed to start Wi-Fi Direct scan: error $reason" }
        )
    }

    fun stopWifiP2pDiscovery() {
        repository.wifiP2pManager.stopDiscovery(
            onSuccess = { _userMessage.value = "Wi-Fi Direct scan stopped" },
            onFailure = { reason -> _userMessage.value = "Failed to stop Wi-Fi Direct scan: error $reason" }
        )
    }

    fun connectToWifiP2pPeer(deviceAddress: String) {
        _userMessage.value = "Connecting to Wi-Fi Direct peer..."
        repository.wifiP2pManager.connectToPeer(
            deviceAddress = deviceAddress,
            onSuccess = { _userMessage.value = "Wi-Fi Direct connection negotiation started" },
            onFailure = { reason -> _userMessage.value = "Connection request failed: error $reason" }
        )
    }

    fun disconnectWifiP2p() {
        repository.wifiP2pManager.disconnect(
            onSuccess = { _userMessage.value = "Disconnected from Wi-Fi Direct group" },
            onFailure = { reason -> _userMessage.value = "Failed to disconnect: error $reason" }
        )
    }

    fun startP2pSocketServer(onFileReceived: (String, Long, java.io.File) -> Unit) {
        repository.wifiP2pManager.startP2pSocketServer(
            onFileReceived = { fileName, fileSize, file ->
                _userMessage.value = "Direct P2P File Received: $fileName (${fileSize} bytes)"
                onFileReceived(fileName, fileSize, file)
            },
            onError = { err ->
                _userMessage.value = "Local server error: $err"
            }
        )
    }

    fun stopP2pSocketServer() {
        repository.wifiP2pManager.stopP2pSocketServer()
    }

    fun sendFileDirectly(uri: android.net.Uri, host: String, onProgress: (Long) -> Unit, onSuccess: () -> Unit) {
        _userMessage.value = "Streaming file directly via P2P local highway..."
        repository.wifiP2pManager.sendFileDirectly(
            uri = uri,
            host = host,
            onProgress = onProgress,
            onSuccess = {
                _userMessage.value = "Direct P2P transfer completed successfully!"
                onSuccess()
            },
            onFailure = { err ->
                _userMessage.value = "Direct transfer failed: $err"
            }
        )
    }

    fun sendFriendRequest(targetSpreDropId: String, targetDisplayName: String = "") {
        viewModelScope.launch {
            try {
                repository.sendFriendRequest(targetSpreDropId, targetDisplayName)
                _userMessage.value = "Friend request sent to $targetSpreDropId"
            } catch (e: Exception) {
                _userMessage.value = e.message ?: "Failed to send friend request"
            }
        }
    }

    fun checkAndSendFriendRequest(username: String) {
        viewModelScope.launch {
            _usernameCheckResult.value = "checking"
            try {
                val clean = (if (username.startsWith("@")) username else "@$username").trim()
                val profile = repository.getUserByUsername(clean)
                if (profile != null) {
                    repository.sendFriendRequest(profile.spreDropId, profile.displayName)
                    _usernameCheckResult.value = "exists"
                    _userMessage.value = "User found! Friend request sent to ${profile.displayName}."
                } else {
                    _usernameCheckResult.value = "not_found"
                    _userMessage.value = "User '$clean' does not exist."
                }
            } catch (e: Exception) {
                _usernameCheckResult.value = "error"
                _userMessage.value = e.message ?: "Failed to verify username"
            }
        }
    }

    fun clearUsernameCheck() {
        _usernameCheckResult.value = null
    }

    fun acceptFriendRequest(friendId: String) {
        viewModelScope.launch {
            repository.acceptFriendRequest(friendId)
            _userMessage.value = "Friend connected"
        }
    }

    fun rejectFriendRequest(friendId: String) {
        viewModelScope.launch {
            repository.rejectFriendRequest(friendId)
            _userMessage.value = "Friend request declined"
        }
    }

    fun removeFriend(friendId: String) {
        viewModelScope.launch {
            repository.removeFriend(friendId)
            _userMessage.value = "Friend removed"
        }
    }

    fun blockUser(friendId: String) {
        viewModelScope.launch {
            repository.blockUser(friendId)
            _userMessage.value = "User blocked"
        }
    }

    fun handleScannedQr(rawQrPayload: String) {
        val parsed = QrCodeGenerator.parsePairUri(rawQrPayload)
        if (parsed != null) {
            repository.signalingManager.addDiscoveredPeer(
                spreDropId = parsed.spreDropId,
                displayName = parsed.displayName,
                userId = parsed.userId
            )
            viewModelScope.launch {
                repository.sendFriendRequest(parsed.spreDropId, parsed.displayName)
            }
            _userMessage.value = "Paired with ${parsed.displayName} (${parsed.spreDropId})"
        } else {
            _userMessage.value = "Invalid SpreDrop QR code payload"
        }
    }

    fun triggerSimulatedIncomingTransfer() {
        repository.signalingManager.refreshDiscovery()
        _userMessage.value = "Scanning for nearby BLE & cloud SpreDrop devices..."
    }

    fun refreshDiscovery() {
        repository.signalingManager.refreshDiscovery()
    }

    fun openFile(filePath: String) {
        repository.openFile(filePath)
    }

    fun shareFile(filePath: String) {
        repository.shareFile(filePath)
    }

    fun deleteReceivedFile(transferId: String) {
        viewModelScope.launch {
            repository.deleteReceivedFile(transferId)
            refreshStorageStats()
            _userMessage.value = "File removed from device"
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _userMessage.value = "Transfer history cleared"
        }
    }

    fun clearDevLogs() {
        viewModelScope.launch {
            repository.clearDevLogs()
            _userMessage.value = "Diagnostic logs cleared"
        }
    }

    fun signInWithEmail(email: String, pass: String) {
        viewModelScope.launch {
            val result = repository.signInWithEmail(email, pass)
            if (result.isSuccess) {
                _userMessage.value = "Welcome back, ${result.getOrNull()?.email ?: "User"}!"
            } else {
                _userMessage.value = "Sign-in error: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun signUpWithEmail(email: String, pass: String, displayName: String, spreDropId: String) {
        viewModelScope.launch {
            val result = repository.signUpWithEmail(email, pass, displayName, spreDropId)
            if (result.isSuccess) {
                _userMessage.value = "Account created successfully for $spreDropId!"
            } else {
                _userMessage.value = "Registration error: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun signInWithGoogle(webClientId: String? = null) {
        viewModelScope.launch {
            val result = repository.signInWithGoogle(webClientId)
            if (result.isSuccess) {
                _userMessage.value = "Signed in with Google successfully!"
            } else {
                _userMessage.value = "Google sign-in error: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val result = repository.signOut()
            if (result.isSuccess) {
                _userMessage.value = "Signed out successfully. Please log in to continue."
            }
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            val result = repository.sendPasswordReset(email)
            if (result.isSuccess) {
                _userMessage.value = "Password reset instructions sent for $email"
            } else {
                _userMessage.value = "Reset error: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun resetPasswordWithNew(email: String, newPass: String) {
        viewModelScope.launch {
            val result = repository.resetPasswordWithNew(email, newPass)
            if (result.isSuccess) {
                _userMessage.value = "Password updated successfully. You can now log in."
            } else {
                _userMessage.value = "Password update error: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun syncWithFirestoreNow() {
        viewModelScope.launch {
            val result = repository.syncWithFirestoreNow()
            if (result.isSuccess) {
                _userMessage.value = "Cloud Firestore synchronized successfully"
            } else {
                _userMessage.value = "Cloud sync note: ${result.exceptionOrNull()?.message ?: "Completed"}"
            }
        }
    }

    private val _firebaseConfig = MutableStateFlow<FirebaseConfig>(repository.getFirebaseConfig())
    val firebaseConfig: StateFlow<FirebaseConfig> = _firebaseConfig.asStateFlow()

    fun refreshFirebaseConfig() {
        _firebaseConfig.value = repository.getFirebaseConfig()
    }

    fun updateFirebaseConfig(projectId: String, apiKey: String, appId: String) {
        viewModelScope.launch {
            val result = repository.updateFirebaseConfig(projectId, apiKey, appId)
            if (result.isSuccess) {
                _firebaseConfig.value = repository.getFirebaseConfig()
                _userMessage.value = "Firebase Cloud & Database configuration saved"
            } else {
                _userMessage.value = "Config error: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearAuthError() {
        repository.clearAuthError()
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    init {
        val prefs = getApplication<Application>().getSharedPreferences("spredrop_auth_prefs", Context.MODE_PRIVATE)
        _isOnboardingCompleted.value = prefs.getBoolean("onboarding_completed", false)
    }

    fun completeOnboarding() {
        val prefs = getApplication<Application>().getSharedPreferences("spredrop_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        _isOnboardingCompleted.value = true
    }

    fun resetOnboarding() {
        val prefs = getApplication<Application>().getSharedPreferences("spredrop_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", false).apply()
        _isOnboardingCompleted.value = false
    }

    suspend fun updatePrivacySuspending(privacy: PrivacyMode) {
        repository.updatePrivacy(privacy)
        if (privacy == PrivacyMode.VISIBLE || privacy == PrivacyMode.FRIENDS_ONLY) {
            repository.updatePresence(UserPresence.AVAILABLE)
        }
    }

    suspend fun updateIdentitySuspending(spreDropId: String, displayName: String) {
        repository.updateProfileIdentity(spreDropId, displayName)
    }

    private val _localHardwareStatus = MutableStateFlow<HardwareRequirements?>(null)
    val localHardwareStatus: StateFlow<HardwareRequirements?> = _localHardwareStatus.asStateFlow()

    fun checkAndSetHardwareStatus(context: Context): Boolean {
        val reqs = checkHardwareRequirements(context)
        _localHardwareStatus.value = reqs
        return reqs.isAllOk
    }

    fun clearHardwareStatus() {
        _localHardwareStatus.value = null
    }

    private fun checkHardwareRequirements(context: Context): HardwareRequirements {
        val ctx = context.applicationContext
        
        val wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val isWlanOn = wifiManager?.isWifiEnabled == true
        
        val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val isBluetoothOn = bluetoothManager?.adapter?.isEnabled == true
        
        val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val isLocationOn = locationManager?.let {
            it.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) || 
            it.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } ?: false
        
        var isHotspotOff = true
        try {
            wifiManager?.let { wm ->
                val method = wm.javaClass.getDeclaredMethod("getWifiApState")
                val apState = method.invoke(wm) as Int
                // 12 = WIFI_AP_STATE_ENABLING, 13 = WIFI_AP_STATE_ENABLED
                if (apState == 12 || apState == 13) {
                    isHotspotOff = false
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        
        return HardwareRequirements(isWlanOn, isBluetoothOn, isLocationOn, isHotspotOff)
    }
}

