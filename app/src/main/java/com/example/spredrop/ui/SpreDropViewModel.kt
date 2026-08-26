package com.example.spredrop.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spredrop.data.SpreDropRepository
import com.example.spredrop.data.StorageStats
import com.example.spredrop.data.firebase.AuthState
import com.example.spredrop.data.firebase.FirestoreConnectionState
import com.example.spredrop.model.*
import com.example.spredrop.security.QrCodeGenerator
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SpreDropViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpreDropRepository(application)

    val userProfile: StateFlow<UserProfile?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val friends: StateFlow<List<Friend>> = repository.friends
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingRequests: StateFlow<List<Friend>> = repository.incomingFriendRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    val currentFirebaseUser: FirebaseUser?
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

    fun sendFriendRequest(targetSpreDropId: String, targetDisplayName: String = "") {
        viewModelScope.launch {
            repository.sendFriendRequest(targetSpreDropId, targetDisplayName)
            _userMessage.value = "Friend request sent to $targetSpreDropId"
        }
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
                _userMessage.value = "Signed out of Firebase"
            }
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            val result = repository.sendPasswordReset(email)
            if (result.isSuccess) {
                _userMessage.value = "Password reset email sent to $email"
            } else {
                _userMessage.value = "Reset error: ${result.exceptionOrNull()?.message}"
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

    fun clearAuthError() {
        repository.clearAuthError()
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
