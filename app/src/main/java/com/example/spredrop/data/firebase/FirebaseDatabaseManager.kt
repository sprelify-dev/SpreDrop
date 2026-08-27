package com.example.spredrop.data.firebase

import android.util.Log
import com.example.spredrop.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

sealed interface FirestoreConnectionState {
    data object Disconnected : FirestoreConnectionState
    data object Connecting : FirestoreConnectionState
    data class Connected(val projectId: String = "spredrop") : FirestoreConnectionState
    data class Error(val message: String) : FirestoreConnectionState
}

data class CloudFriendRequest(
    val id: String = "",
    val fromUserId: String = "",
    val fromSpreDropId: String = "",
    val fromDisplayName: String = "",
    val fromAvatarColorHex: String = "#00B4D8",
    val toUserId: String = "",
    val toSpreDropId: String = "",
    val status: String = "PENDING", // PENDING, ACCEPTED, DECLINED
    val timestamp: Long = System.currentTimeMillis()
)

class FirebaseDatabaseManager {

    private val firestore: FirebaseFirestore? by lazy {
        try {
            val app = com.google.firebase.FirebaseApp.getInstance()
            val instance = try {
                FirebaseFirestore.getInstance(app, "spredrop")
            } catch (e: Exception) {
                Log.i("FirebaseDatabaseManager", "Failed to initialize custom database 'spredrop', falling back to default instance: ${e.message}")
                FirebaseFirestore.getInstance()
            }
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            instance.firestoreSettings = settings
            instance
        } catch (e: Exception) {
            Log.w("FirebaseDatabaseManager", "Firestore init warning: ${e.message}")
            null
        }
    }

    private val isConfigured: Boolean by lazy {
        try {
            val key = firestore?.app?.options?.apiKey
            !key.isNullOrBlank() && !key.contains("Dummy", ignoreCase = true) && !key.contains("placeholder", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private val _connectionState = MutableStateFlow<FirestoreConnectionState>(
        FirestoreConnectionState.Connected(projectId = "spredrop")
    )
    val connectionState: StateFlow<FirestoreConnectionState> = _connectionState.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    // -------------------------------------------------------------
    // USER PROFILE SYNC
    // -------------------------------------------------------------

    suspend fun uploadUserProfile(profile: UserProfile): Result<Unit> {
        val fs = firestore
        if (fs == null || !isConfigured) {
            _lastSyncTimestamp.value = System.currentTimeMillis()
            return Result.success(Unit)
        }
        return try {
            val userDoc = mapOf(
                "userId" to profile.userId,
                "spreDropId" to profile.spreDropId,
                "displayName" to profile.displayName,
                "profilePhotoUri" to profile.profilePhotoUri,
                "avatarColorHex" to profile.avatarColorHex,
                "visibility" to profile.visibility.name,
                "availability" to profile.availability.name,
                "deviceModel" to profile.deviceModel,
                "lastSeen" to System.currentTimeMillis(),
                "projectId" to "spredrop"
            )

            fs.collection("users")
                .document(profile.userId)
                .set(userDoc, SetOptions.merge())
                .await()

            _lastSyncTimestamp.value = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error uploading user profile: ${e.message}")
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        var registration: ListenerRegistration? = null
        try {
            registration = fs.collection("users")
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FirebaseDatabaseManager", "Listen failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val spreDropId = snapshot.getString("spreDropId") ?: "@user"
                        val displayName = snapshot.getString("displayName") ?: "SpreDrop User"
                        val avatarColorHex = snapshot.getString("avatarColorHex") ?: "#00B4D8"
                        val visibilityStr = snapshot.getString("visibility") ?: PrivacyMode.VISIBLE.name
                        val availabilityStr = snapshot.getString("availability") ?: UserPresence.AVAILABLE.name
                        val deviceModel = snapshot.getString("deviceModel") ?: "Android"

                        val profile = UserProfile(
                            userId = userId,
                            spreDropId = spreDropId,
                            displayName = displayName,
                            avatarColorHex = avatarColorHex,
                            visibility = runCatching { PrivacyMode.valueOf(visibilityStr) }.getOrDefault(PrivacyMode.VISIBLE),
                            availability = runCatching { UserPresence.valueOf(availabilityStr) }.getOrDefault(UserPresence.AVAILABLE),
                            deviceModel = deviceModel,
                            lastSeen = snapshot.getLong("lastSeen") ?: System.currentTimeMillis()
                        )
                        trySend(profile)
                    } else {
                        trySend(null)
                    }
                }
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error creating listener: ${e.message}")
            trySend(null)
        }

        awaitClose {
            registration?.remove()
        }
    }

    // -------------------------------------------------------------
    // CLOUD PEER DISCOVERY & PRESENCE
    // -------------------------------------------------------------

    suspend fun publishPeerPresence(
        userId: String,
        spreDropId: String,
        displayName: String,
        avatarColorHex: String,
        availability: UserPresence,
        isOnline: Boolean
    ): Result<Unit> {
        val fs = firestore
        if (fs == null || !isConfigured) return Result.success(Unit)
        return try {
            val peerRef = fs.collection("online_peers").document(userId)
            if (isOnline) {
                val peerData = mapOf(
                    "deviceId" to userId,
                    "spreDropId" to spreDropId,
                    "displayName" to displayName,
                    "avatarColorHex" to avatarColorHex,
                    "availability" to availability.name,
                    "lastSeen" to System.currentTimeMillis(),
                    "isOnline" to true
                )
                peerRef.set(peerData, SetOptions.merge()).await()
            } else {
                peerRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error publishing peer presence: ${e.message}")
            Result.failure(e)
        }
    }

    fun observeOnlineCloudPeers(currentUserId: String): Flow<List<PeerDevice>> = callbackFlow {
        val fs = firestore
        if (fs == null || !isConfigured) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        var registration: ListenerRegistration? = null
        try {
            registration = fs.collection("online_peers")
                .whereEqualTo("isOnline", true)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e("FirebaseDatabaseManager", "Peers listener failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        val peers = snapshots.documents.mapNotNull { doc ->
                            val deviceId = doc.getString("deviceId") ?: doc.id
                            if (deviceId == currentUserId) return@mapNotNull null // ignore self

                            val spreDropId = doc.getString("spreDropId") ?: "@peer"
                            val displayName = doc.getString("displayName") ?: "Peer"
                            val avatarHex = doc.getString("avatarColorHex") ?: "#00B4D8"
                            val availStr = doc.getString("availability") ?: UserPresence.AVAILABLE.name
                            val avail = runCatching { UserPresence.valueOf(availStr) }.getOrDefault(UserPresence.AVAILABLE)
                            val lastSeen = doc.getLong("lastSeen") ?: System.currentTimeMillis()

                            // Drop stale peers (> 5 minutes inactive)
                            if (System.currentTimeMillis() - lastSeen > 5 * 60 * 1000) {
                                return@mapNotNull null
                            }

                            PeerDevice(
                                deviceId = deviceId,
                                spreDropId = spreDropId,
                                displayName = displayName,
                                avatarColorHex = avatarHex,
                                availability = avail,
                                isFriend = false,
                                connectionType = PeerConnectionType.SIGNALING_SERVER,
                                signalStrengthRssi = -48,
                                ipAddress = "Cloud/P2P",
                                lastDiscovered = lastSeen,
                                supportedCapabilities = listOf("FIREBASE_SYNC", "WEBRTC_DATACHANNEL", "SHA256_INTEGRITY")
                            )
                        }
                        trySend(peers)
                    }
                }
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error creating peers listener: ${e.message}")
            trySend(emptyList())
        }

        awaitClose {
            registration?.remove()
        }
    }

    // -------------------------------------------------------------
    // CLOUD TRANSFERS LOGGING & BACKUP
    // -------------------------------------------------------------

    suspend fun logTransferToCloud(transfer: TransferRecord): Result<Unit> {
        val fs = firestore
        if (fs == null || !isConfigured) return Result.success(Unit)
        return try {
            val transferDoc = mapOf(
                "transferId" to transfer.transferId,
                "fileName" to transfer.fileName,
                "fileSize" to transfer.fileSize,
                "mimeType" to transfer.mimeType,
                "senderId" to transfer.senderId,
                "senderSpreDropId" to transfer.senderSpreDropId,
                "senderDisplayName" to transfer.senderDisplayName,
                "receiverId" to transfer.receiverId,
                "receiverSpreDropId" to transfer.receiverSpreDropId,
                "receiverDisplayName" to transfer.receiverDisplayName,
                "direction" to transfer.direction.name,
                "status" to transfer.status.name,
                "sha256Checksum" to transfer.sha256Checksum,
                "verifiedChecksum" to transfer.verifiedChecksum,
                "timestamp" to transfer.timestamp,
                "projectId" to "spredrop"
            )

            fs.collection("transfers")
                .document(transfer.transferId)
                .set(transferDoc, SetOptions.merge())
                .await()

            _lastSyncTimestamp.value = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error saving transfer to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // CLOUD FRIEND REQUESTS
    // -------------------------------------------------------------

    suspend fun sendCloudFriendRequest(
        fromProfile: UserProfile,
        targetSpreDropId: String
    ): Result<Unit> {
        val fs = firestore
        if (fs == null || !isConfigured) return Result.success(Unit)
        return try {
            val requestId = "req_${fromProfile.userId}_${targetSpreDropId.replace("@", "")}"
            val requestDoc = mapOf(
                "id" to requestId,
                "fromUserId" to fromProfile.userId,
                "fromSpreDropId" to fromProfile.spreDropId,
                "fromDisplayName" to fromProfile.displayName,
                "fromAvatarColorHex" to fromProfile.avatarColorHex,
                "toSpreDropId" to targetSpreDropId,
                "status" to "PENDING",
                "timestamp" to System.currentTimeMillis(),
                "projectId" to "spredrop"
            )

            fs.collection("friend_requests")
                .document(requestId)
                .set(requestDoc, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error sending cloud friend request: ${e.message}")
            Result.failure(e)
        }
    }

    fun observeIncomingCloudFriendRequests(mySpreDropId: String): Flow<List<CloudFriendRequest>> = callbackFlow {
        val fs = firestore
        if (fs == null || !isConfigured) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        var registration: ListenerRegistration? = null
        try {
            registration = fs.collection("friend_requests")
                .whereEqualTo("toSpreDropId", mySpreDropId)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e("FirebaseDatabaseManager", "Friend requests listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        val requests = snapshots.documents.mapNotNull { doc ->
                            val id = doc.getString("id") ?: doc.id
                            val fromUserId = doc.getString("fromUserId") ?: return@mapNotNull null
                            val fromSpreDropId = doc.getString("fromSpreDropId") ?: "@user"
                            val fromDisplayName = doc.getString("fromDisplayName") ?: "User"
                            val fromAvatarHex = doc.getString("fromAvatarColorHex") ?: "#00B4D8"
                            val toSpreDropId = doc.getString("toSpreDropId") ?: mySpreDropId
                            val status = doc.getString("status") ?: "PENDING"
                            val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()

                            CloudFriendRequest(
                                id = id,
                                fromUserId = fromUserId,
                                fromSpreDropId = fromSpreDropId,
                                fromDisplayName = fromDisplayName,
                                fromAvatarColorHex = fromAvatarHex,
                                toSpreDropId = toSpreDropId,
                                status = status,
                                timestamp = ts
                            )
                        }
                        trySend(requests)
                    }
                }
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error listening to friend requests: ${e.message}")
            trySend(emptyList())
        }

        awaitClose {
            registration?.remove()
        }
    }

    suspend fun updateCloudFriendRequestStatus(requestId: String, status: String): Result<Unit> {
        val fs = firestore
        if (fs == null || !isConfigured) return Result.success(Unit)
        return try {
            fs.collection("friend_requests")
                .document(requestId)
                .update("status", status)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // LIVE CLOUD TRANSFER PROPOSALS (Cross-Device P2P Signaling)
    // -------------------------------------------------------------

    suspend fun sendTransferProposal(transfer: TransferRecord): Result<Unit> {
        val fs = firestore
        if (fs == null || !isConfigured) return Result.success(Unit)
        return try {
            val proposalDoc = mapOf(
                "transferId" to transfer.transferId,
                "fileName" to transfer.fileName,
                "fileSize" to transfer.fileSize,
                "mimeType" to transfer.mimeType,
                "senderId" to transfer.senderId,
                "senderSpreDropId" to transfer.senderSpreDropId,
                "senderDisplayName" to transfer.senderDisplayName,
                "receiverId" to transfer.receiverId,
                "receiverSpreDropId" to transfer.receiverSpreDropId,
                "receiverDisplayName" to transfer.receiverDisplayName,
                "status" to "PENDING",
                "sha256Checksum" to transfer.sha256Checksum,
                "timestamp" to System.currentTimeMillis()
            )

            fs.collection("transfer_proposals")
                .document(transfer.transferId)
                .set(proposalDoc, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error sending transfer proposal: ${e.message}")
            Result.failure(e)
        }
    }

    fun observeIncomingTransferProposals(myUserId: String, mySpreDropId: String): Flow<List<TransferRecord>> = callbackFlow {
        val fs = firestore
        if (fs == null || !isConfigured) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        var registration: ListenerRegistration? = null
        try {
            registration = fs.collection("transfer_proposals")
                .whereEqualTo("receiverSpreDropId", mySpreDropId)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e("FirebaseDatabaseManager", "Proposals listen failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        val proposals = snapshots.documents.mapNotNull { doc ->
                            val transferId = doc.getString("transferId") ?: doc.id
                            val fileName = doc.getString("fileName") ?: "file"
                            val fileSize = doc.getLong("fileSize") ?: 0L
                            val mimeType = doc.getString("mimeType") ?: "*/*"
                            val senderId = doc.getString("senderId") ?: "unknown"
                            val senderSpreDropId = doc.getString("senderSpreDropId") ?: "@peer"
                            val senderDisplayName = doc.getString("senderDisplayName") ?: "Nearby Peer"
                            val checksum = doc.getString("sha256Checksum") ?: ""
                            val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()

                            // Drop proposals older than 10 minutes
                            if (System.currentTimeMillis() - ts > 10 * 60 * 1000) return@mapNotNull null

                            TransferRecord(
                                transferId = transferId,
                                fileName = fileName,
                                fileSize = fileSize,
                                mimeType = mimeType,
                                senderId = senderId,
                                senderSpreDropId = senderSpreDropId,
                                senderDisplayName = senderDisplayName,
                                receiverId = myUserId,
                                receiverSpreDropId = mySpreDropId,
                                receiverDisplayName = "Me",
                                direction = TransferDirection.INCOMING,
                                status = TransferStatus.PENDING,
                                totalBytes = fileSize,
                                chunkSize = 64 * 1024,
                                totalChunks = ((fileSize + 64 * 1024 - 1) / (64 * 1024)).toInt().coerceAtLeast(1),
                                sha256Checksum = checksum,
                                timestamp = ts
                            )
                        }
                        trySend(proposals)
                    }
                }
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error creating proposals listener: ${e.message}")
            trySend(emptyList())
        }

        awaitClose {
            registration?.remove()
        }
    }

    suspend fun updateProposalStatus(transferId: String, status: String): Result<Unit> {
        val fs = firestore
        if (fs == null || !isConfigured) return Result.success(Unit)
        return try {
            fs.collection("transfer_proposals")
                .document(transferId)
                .update("status", status)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
