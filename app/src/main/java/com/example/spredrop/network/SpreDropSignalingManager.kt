package com.example.spredrop.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.spredrop.data.firebase.FirebaseDatabaseManager
import com.example.spredrop.data.local.DevLogDao
import com.example.spredrop.data.local.FriendDao
import com.example.spredrop.data.local.TransferDao
import com.example.spredrop.data.local.UserDao
import com.example.spredrop.model.*
import com.example.spredrop.service.TransferNotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Signaling & Discovery Manager.
 * Orchestrates presence heartbeats, Bluetooth Low Energy proximity advertising & scanning,
 * real Cloud Firestore online presence, and incoming transfer proposals.
 * Real data only: No simulated or fake peers.
 */
class SpreDropSignalingManager(
    private val context: Context,
    private val userDao: UserDao,
    private val friendDao: FriendDao,
    private val transferDao: TransferDao,
    private val devLogDao: DevLogDao,
    private val transferEngine: P2PTransferEngine,
    private val databaseManager: FirebaseDatabaseManager
) {
    companion object {
        private const val TAG = "SpreDropSignaling"
    }

    private val signalingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _isDiscovering = MutableStateFlow(true)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    // Internal maps to store real discoveries
    private val blePeersMap = mutableMapOf<String, PeerDevice>()
    private val cloudPeersMap = mutableMapOf<String, PeerDevice>()
    private val manualPeersMap = mutableMapOf<String, PeerDevice>()

    private var heartbeatJob: Job? = null
    private var cloudPeerJob: Job? = null
    private var cloudProposalJob: Job? = null
    private var cloudFriendRequestJob: Job? = null
    private var cloudAcceptedRequestJob: Job? = null

    private val bleManager = SpreDropBleManager(context) { blePeer ->
        handleBlePeerDiscovered(blePeer)
    }

    private var profileObserverJob: Job? = null

    init {
        setupNetworkMonitor()
        observeProfileChanges()
    }

    private fun observeProfileChanges() {
        profileObserverJob?.cancel()
        profileObserverJob = signalingScope.launch {
            userDao.getUserProfile().collect { profile ->
                if (profile != null) {
                    log("PROFILE", "Profile updated dynamically in local DB: ${profile.spreDropId} (${profile.displayName})")
                    startPresenceHeartbeat(profile)
                    startCloudListeners(profile)
                    startBleDiscovery(profile)
                }
            }
        }
    }

    private fun setupNetworkMonitor() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager != null) {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                    signalingScope.launch {
                        log("NETWORK", "Network connection active (Wi-Fi/Cellular). Reconnecting presence & signaling...")
                        val profile = userDao.getUserProfileOnce()
                        if (profile != null && profile.availability != UserPresence.INVISIBLE) {
                            userDao.updatePresence(profile.userId, UserPresence.AVAILABLE)
                            databaseManager.publishPeerPresence(
                                userId = profile.userId,
                                spreDropId = profile.spreDropId,
                                displayName = profile.displayName,
                                avatarColorHex = profile.avatarColorHex,
                                availability = UserPresence.AVAILABLE,
                                isOnline = true
                            )
                        }
                    }
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                    signalingScope.launch {
                        log("NETWORK", "Network lost. Switching presence to OFFLINE.")
                        val profile = userDao.getUserProfileOnce()
                        if (profile != null) {
                            userDao.updatePresence(profile.userId, UserPresence.OFFLINE)
                        }
                    }
                }
            })
        }
    }

    private fun startPresenceHeartbeat(profile: UserProfile) {
        heartbeatJob?.cancel()
        heartbeatJob = signalingScope.launch {
            while (isActive) {
                if (_isOnline.value) {
                    val isVisible = profile.visibility != PrivacyMode.INVISIBLE && profile.availability != UserPresence.INVISIBLE && profile.availability != UserPresence.OFFLINE
                    if (isVisible) {
                        userDao.updatePresence(profile.userId, profile.availability, System.currentTimeMillis())
                        databaseManager.publishPeerPresence(
                            userId = profile.userId,
                            spreDropId = profile.spreDropId,
                            displayName = profile.displayName,
                            avatarColorHex = profile.avatarColorHex,
                            availability = profile.availability,
                            isOnline = true
                        )
                        log("SIGNAL", "Presence heartbeat ACK for ${profile.spreDropId} [${profile.availability.name}]")
                    } else {
                        databaseManager.publishPeerPresence(
                            userId = profile.userId,
                            spreDropId = profile.spreDropId,
                            displayName = profile.displayName,
                            avatarColorHex = profile.avatarColorHex,
                            availability = profile.availability,
                            isOnline = false
                        )
                        bleManager.stopAdvertising()
                        log("SIGNAL", "Presence set to INVISIBLE/OFFLINE. Cleaned Firestore & stopped BLE advertising.")
                    }
                }
                // Prune stale BLE/Cloud peers (> 2 minutes without beacon)
                pruneStalePeers()
                delay(12000) // 12 second heartbeat
            }
        }
    }

    private fun startCloudListeners(profile: UserProfile) {
        // Listen to real online peers from Firestore
        cloudPeerJob?.cancel()
        cloudPeerJob = signalingScope.launch {
            databaseManager.observeOnlineCloudPeers(profile.userId).collect { peers ->
                synchronized(cloudPeersMap) {
                    cloudPeersMap.clear()
                    peers.forEach { peer ->
                        cloudPeersMap[peer.spreDropId.lowercase()] = peer
                    }
                }

                // Update online status of friends in local DB from online cloud peers list
                try {
                    val onlineSpreDropIds = peers.map { it.spreDropId.lowercase().trim() }.toSet()
                    val friendsList = friendDao.getFriendsOnce()
                    friendsList.forEach { friend ->
                        val isOnlineNow = onlineSpreDropIds.contains(friend.spreDropId.lowercase().trim())
                        val targetPresence = if (isOnlineNow) UserPresence.ONLINE else UserPresence.OFFLINE
                        if (friend.availability != targetPresence) {
                            friendDao.updateFriendPresence(friend.userId, targetPresence, System.currentTimeMillis())
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update friend statuses: ${e.message}")
                }

                combineAndPublishDiscoveredPeers()
            }
        }

        // Listen to real incoming transfer proposals from Firestore
        cloudProposalJob?.cancel()
        cloudProposalJob = signalingScope.launch {
            databaseManager.observeIncomingTransferProposals(profile.userId, profile.spreDropId).collect { proposals ->
                proposals.forEach { proposal ->
                    val existing = transferDao.getTransferById(proposal.transferId)
                    if (existing == null) {
                        transferDao.insertTransfer(proposal)
                        TransferNotificationHelper.showIncomingTransferNotification(context, proposal)
                        log("SIGNAL", "Real incoming transfer proposal from ${proposal.senderSpreDropId} for '${proposal.fileName}'")
                    }
                }
            }
        }

        // Listen to real incoming friend requests from Firestore
        cloudFriendRequestJob?.cancel()
        cloudFriendRequestJob = signalingScope.launch {
            databaseManager.observeIncomingCloudFriendRequests(profile.spreDropId).collect { requests ->
                requests.forEach { req ->
                    val existing = friendDao.getFriendById(req.fromUserId)
                    if (existing == null || existing.status == FriendStatus.NONE) {
                        val newFriend = Friend(
                            userId = req.fromUserId,
                            spreDropId = req.fromSpreDropId,
                            displayName = req.fromDisplayName,
                            avatarColorHex = req.fromAvatarColorHex,
                            status = FriendStatus.REQUEST_RECEIVED,
                            lastSeen = req.timestamp
                        )
                        friendDao.insertOrUpdateFriend(newFriend)
                        log("SIGNAL", "New friend request received from ${req.fromSpreDropId} (${req.fromDisplayName})")
                    }
                }
            }
        }

        // Listen to real accepted friend requests from Firestore
        cloudAcceptedRequestJob?.cancel()
        cloudAcceptedRequestJob = signalingScope.launch {
            databaseManager.observeAcceptedCloudFriendRequests(profile.spreDropId).collect { acceptedList ->
                acceptedList.forEach { req ->
                    val targetSpreDropId = req.toSpreDropId.lowercase().trim()
                    val existing = friendDao.getFriendBySpreDropId(targetSpreDropId)
                    if (existing != null && existing.status != FriendStatus.FRIENDS) {
                        friendDao.updateFriendStatus(existing.userId, FriendStatus.FRIENDS)
                        log("SIGNAL", "Friend request to $targetSpreDropId was ACCEPTED! You are now friends.")
                        TransferNotificationHelper.showFriendRequestNotification(
                            context,
                            existing.displayName,
                            existing.spreDropId
                        )
                    }
                }
            }
        }
    }

    private fun startBleDiscovery(profile: UserProfile) {
        val isVisible = profile.visibility != PrivacyMode.INVISIBLE && profile.availability != UserPresence.INVISIBLE && profile.availability != UserPresence.OFFLINE
        if (isVisible) {
            bleManager.startAdvertising(
                spreDropId = profile.spreDropId,
                displayName = profile.displayName,
                userId = profile.userId
            )
        } else {
            bleManager.stopAdvertising()
        }
        bleManager.startScanning()
        signalingScope.launch {
            log("DISCOVERY", "BLE Proximity Advertiser & Scanner initialized with SpreDrop Service UUID")
        }
    }


    private fun handleBlePeerDiscovered(peer: PeerDevice) {
        synchronized(blePeersMap) {
            blePeersMap[peer.spreDropId.lowercase()] = peer
        }
        combineAndPublishDiscoveredPeers()
        signalingScope.launch {
            log("BLE", "Proximity beacon detected: ${peer.spreDropId} (${peer.signalStrengthRssi} dBm)")
        }
    }

    private fun combineAndPublishDiscoveredPeers() {
        val selfProfile = kotlinx.coroutines.runBlocking {
            try {
                userDao.getUserProfileOnce()
            } catch (e: Exception) {
                null
            }
        }
        val selfSpreDropId = selfProfile?.spreDropId?.lowercase() ?: ""
        val selfUserId = selfProfile?.userId ?: ""

        val combined = mutableMapOf<String, PeerDevice>()

        // 1. BLE proximity peers (take priority for RSSI & connection type)
        synchronized(blePeersMap) {
            blePeersMap.forEach { (key, blePeer) ->
                if (blePeer.deviceId != selfUserId && blePeer.spreDropId.lowercase() != selfSpreDropId) {
                    val cloudEquivalent = synchronized(cloudPeersMap) { cloudPeersMap[key] }
                    if (cloudEquivalent != null) {
                        combined[key] = blePeer.copy(
                            supportedCapabilities = cloudEquivalent.supportedCapabilities,
                            ipAddress = cloudEquivalent.ipAddress
                        )
                    } else {
                        combined[key] = blePeer
                    }
                }
            }
        }

        // 2. Manually paired peers
        synchronized(manualPeersMap) {
            manualPeersMap.forEach { (key, peer) ->
                if (peer.deviceId != selfUserId && peer.spreDropId.lowercase() != selfSpreDropId) {
                    if (!combined.containsKey(key)) {
                        combined[key] = peer
                    }
                }
            }
        }

        _discoveredPeers.value = combined.values.sortedByDescending { it.signalStrengthRssi }
    }

    private fun pruneStalePeers() {
        val now = System.currentTimeMillis()
        var changed = false

        synchronized(blePeersMap) {
            val it = blePeersMap.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (now - entry.value.lastDiscovered > 60_000) {
                    it.remove()
                    changed = true
                }
            }
        }

        if (changed) {
            combineAndPublishDiscoveredPeers()
        }
    }

    /**
     * Add peer from QR scan or direct ID
     */
    fun addDiscoveredPeer(spreDropId: String, displayName: String, userId: String) {
        val cleanId = if (spreDropId.startsWith("@")) spreDropId else "@$spreDropId"
        val newPeer = PeerDevice(
            deviceId = userId,
            spreDropId = cleanId,
            displayName = displayName,
            avatarColorHex = getDeterministicColor(cleanId),
            availability = UserPresence.AVAILABLE,
            isFriend = false,
            connectionType = PeerConnectionType.DIRECT_P2P,
            signalStrengthRssi = -38,
            ipAddress = "Direct/P2P",
            lastDiscovered = System.currentTimeMillis()
        )
        synchronized(manualPeersMap) {
            manualPeersMap[cleanId.lowercase()] = newPeer
        }
        combineAndPublishDiscoveredPeers()
        signalingScope.launch {
            log("PAIR", "Paired with peer $cleanId via QR/Direct ID")
        }
    }

    fun acceptIncomingTransfer(transfer: TransferRecord) {
        signalingScope.launch {
            val profile = userDao.getUserProfileOnce() ?: return@launch
            transferDao.updateStatus(transfer.transferId, TransferStatus.ACCEPTED)
            databaseManager.updateProposalStatus(transfer.transferId, "ACCEPTED")
            log("WEBRTC", "Transfer accepted. Establishing DataChannel stream with ${transfer.senderSpreDropId}")
            delay(300)
            transferEngine.startIncomingTransfer(
                transferId = transfer.transferId,
                fileName = transfer.fileName,
                fileSize = transfer.fileSize,
                mimeType = transfer.mimeType,
                senderSpreDropId = transfer.senderSpreDropId,
                senderDisplayName = transfer.senderDisplayName,
                senderId = transfer.senderId,
                receiverProfile = profile
            )
        }
    }

    fun declineIncomingTransfer(transferId: String) {
        signalingScope.launch {
            transferDao.updateStatus(transferId, TransferStatus.DECLINED, "Declined by user")
            databaseManager.updateProposalStatus(transferId, "DECLINED")
            log("SIGNAL", "Transfer $transferId declined.")
        }
    }

    fun setDiscoveryActive(active: Boolean) {
        _isDiscovering.value = active
        if (active) {
            bleManager.startScanning()
        } else {
            bleManager.stopScanning()
        }
    }

    fun refreshDiscovery() {
        signalingScope.launch {
            log("DISCOVERY", "Actively scanning for nearby SpreDrop peers via BLE and Cloud Firestore...")
            bleManager.stopScanning()
            delay(200)
            bleManager.startScanning()

            combineAndPublishDiscoveredPeers()
            log("DISCOVERY", "Active scanning refreshed.")
        }
    }

    private fun getDeterministicColor(input: String): String {
        val colors = listOf("#00B4D8", "#06D6A0", "#6366F1", "#EC4899", "#F59E0B", "#8B5CF6", "#10B981", "#3B82F6")
        val index = (input.hashCode() and 0x7FFFFFFF) % colors.size
        return colors[index]
    }

    private suspend fun log(tag: String, message: String) {
        devLogDao.insertLog(DevLogEntry(tag = tag, message = message, level = tag))
    }
}
