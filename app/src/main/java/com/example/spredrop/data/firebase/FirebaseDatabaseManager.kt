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

    internal val firestore: FirebaseFirestore? by lazy {
        try {
            val app = com.google.firebase.FirebaseApp.getInstance()
            val instance = FirebaseFirestore.getInstance(app)
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

    internal val isConfigured: Boolean by lazy {
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

    suspend fun isUsernameAvailable(spreDropId: String): Boolean {
        val fs = firestore
        if (fs == null || !isConfigured) return true
        val cleanId = spreDropId.removePrefix("@").lowercase().trim()
        if (cleanId.isEmpty()) return false
        return try {
            val doc = fs.collection("usernames").document(cleanId).get().await()
            !doc.exists()
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error checking username availability: ${e.message}")
            true
        }
    }

    suspend fun reserveUsername(spreDropId: String, userId: String): Result<Boolean> {
        val fs = firestore
        if (fs == null || !isConfigured) return Result.success(true)
        val cleanId = spreDropId.removePrefix("@").lowercase().trim()
        if (cleanId.isEmpty()) return Result.failure(IllegalArgumentException("Username cannot be empty"))
        return try {
            val docRef = fs.collection("usernames").document(cleanId)
            val success = fs.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val existingOwner = snapshot.getString("ownerUid")
                    existingOwner == userId
                } else {
                    transaction.set(docRef, mapOf("ownerUid" to userId))
                    true
                }
            }.await()
            Result.success(success)
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error reserving username: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        val fs = firestore
        if (fs == null || !isConfigured) return null
        return try {
            val snapshot = fs.collection("users").document(userId).get().await()
            if (snapshot != null && snapshot.exists()) {
                val spreDropId = snapshot.getString("spreDropId") ?: "@user"
                val displayName = snapshot.getString("displayName") ?: "SpreDrop User"
                val avatarColorHex = snapshot.getString("avatarColorHex") ?: "#00B4D8"
                val visibilityStr = snapshot.getString("visibility") ?: PrivacyMode.VISIBLE.name
                val availabilityStr = snapshot.getString("availability") ?: UserPresence.AVAILABLE.name
                val deviceModel = snapshot.getString("deviceModel") ?: "Android"

                UserProfile(
                    userId = userId,
                    spreDropId = spreDropId,
                    displayName = displayName,
                    avatarColorHex = avatarColorHex,
                    visibility = runCatching { PrivacyMode.valueOf(visibilityStr) }.getOrDefault(PrivacyMode.VISIBLE),
                    availability = runCatching { UserPresence.valueOf(availabilityStr) }.getOrDefault(UserPresence.AVAILABLE),
                    deviceModel = deviceModel,
                    lastSeen = snapshot.getLong("lastSeen") ?: System.currentTimeMillis()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error fetching user profile: ${e.message}")
            null
        }
    }

    suspend fun getUserByUsername(spreDropId: String): UserProfile? {
        val fs = firestore ?: return null
        val cleanId = spreDropId.removePrefix("@").lowercase().trim()
        return try {
            val doc = fs.collection("usernames").document(cleanId).get().await()
            if (doc != null && doc.exists()) {
                val ownerUid = doc.getString("ownerUid")
                if (ownerUid != null) {
                    getUserProfile(ownerUid)
                } else null
            } else null
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error in getUserByUsername: ${e.message}")
            null
        }
    }

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
    // CLOUD FRIEND REQUESTS & RELATIONSHIPS (REAL LIFE CYCLE)
    // -------------------------------------------------------------

    suspend fun sendCloudFriendRequest(
        fromProfile: UserProfile,
        targetProfile: UserProfile
    ): Result<Unit> {
        val fs = firestore
        if (fs == null || !isConfigured) return Result.success(Unit)
        return try {
            val requestId = "${fromProfile.userId}_${targetProfile.userId}"
            val requestDoc = mapOf(
                "requestId" to requestId,
                "senderUid" to fromProfile.userId,
                "senderSpreDropId" to fromProfile.spreDropId,
                "senderDisplayName" to fromProfile.displayName,
                "senderAvatarColorHex" to fromProfile.avatarColorHex,
                "receiverUid" to targetProfile.userId,
                "receiverSpreDropId" to targetProfile.spreDropId,
                "receiverDisplayName" to targetProfile.displayName,
                "status" to "PENDING",
                "createdAt" to System.currentTimeMillis(),
                "projectId" to "spredrop"
            )

            fs.collection("friendRequests")
                .document(requestId)
                .set(requestDoc, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error sending cloud friend request: ${e.message}")
            Result.failure(e)
        }
    }

    fun observeIncomingCloudFriendRequests(myUserId: String): Flow<List<CloudFriendRequest>> = callbackFlow {
        val fs = firestore
        if (fs == null || !isConfigured) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        var registration: ListenerRegistration? = null
        try {
            registration = fs.collection("friendRequests")
                .whereEqualTo("receiverUid", myUserId)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e("FirebaseDatabaseManager", "Friend requests listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        val requests = snapshots.documents.mapNotNull { doc ->
                            val id = doc.getString("requestId") ?: doc.id
                            val senderUid = doc.getString("senderUid") ?: return@mapNotNull null
                            val senderSpreDropId = doc.getString("senderSpreDropId") ?: "@user"
                            val senderDisplayName = doc.getString("senderDisplayName") ?: "User"
                            val senderAvatarHex = doc.getString("senderAvatarColorHex") ?: "#00B4D8"
                            val status = doc.getString("status") ?: "PENDING"
                            val ts = doc.getLong("createdAt") ?: System.currentTimeMillis()

                            CloudFriendRequest(
                                id = id,
                                fromUserId = senderUid,
                                fromSpreDropId = senderSpreDropId,
                                fromDisplayName = senderDisplayName,
                                fromAvatarColorHex = senderAvatarHex,
                                toSpreDropId = doc.getString("receiverSpreDropId") ?: "",
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

    fun observeAcceptedCloudFriendRequests(mySpreDropId: String): Flow<List<CloudFriendRequest>> = callbackFlow {
        val fs = firestore
        if (fs == null || !isConfigured) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        var registration: ListenerRegistration? = null
        try {
            registration = fs.collection("friendRequests")
                .whereEqualTo("senderSpreDropId", mySpreDropId)
                .whereEqualTo("status", "ACCEPTED")
                .addSnapshotListener { snapshots, error ->
                    if (error == null && snapshots != null) {
                        val requests = snapshots.documents.mapNotNull { doc ->
                            val id = doc.getString("requestId") ?: doc.id
                            val senderUid = doc.getString("senderUid") ?: ""
                            val senderSpreDropId = doc.getString("senderSpreDropId") ?: "@user"
                            val senderDisplayName = doc.getString("senderDisplayName") ?: "User"
                            val senderAvatarHex = doc.getString("senderAvatarColorHex") ?: "#00B4D8"
                            val receiverSpreDropId = doc.getString("receiverSpreDropId") ?: ""
                            val status = doc.getString("status") ?: "PENDING"
                            val ts = doc.getLong("createdAt") ?: System.currentTimeMillis()

                            CloudFriendRequest(
                                id = id,
                                fromUserId = senderUid,
                                fromSpreDropId = senderSpreDropId,
                                fromDisplayName = senderDisplayName,
                                fromAvatarColorHex = senderAvatarHex,
                                toSpreDropId = receiverSpreDropId,
                                status = status,
                                timestamp = ts
                            )
                        }
                        trySend(requests)
                    }
                }
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error in accepted requests listener: ${e.message}")
            trySend(emptyList())
        }
        awaitClose {
            registration?.remove()
        }
    }

    suspend fun acceptCloudFriendRequest(myUserId: String, requestId: String): Result<Unit> {
        val fs = firestore
        if (fs == null || !isConfigured) return Result.success(Unit)
        return try {
            val reqRef = fs.collection("friendRequests").document(requestId)
            val snapshot = reqRef.get().await()
            if (!snapshot.exists()) {
                return Result.failure(Exception("Friend request not found"))
            }
            val receiverUid = snapshot.getString("receiverUid")
            if (receiverUid != myUserId) {
                return Result.failure(Exception("Unauthorized: You are not the receiver of this request"))
            }

            // Update status of the request
            reqRef.update("status", "ACCEPTED").await()

            // Fetch both profiles to build friendship doc
            val senderUid = snapshot.getString("senderUid") ?: ""
            val senderProfile = getUserProfile(senderUid)
            val receiverProfile = getUserProfile(myUserId)

            if (senderProfile != null && receiverProfile != null) {
                val relationshipId = if (senderUid < myUserId) "${senderUid}_${myUserId}" else "${myUserId}_${senderUid}"
                val friendshipDoc = mapOf(
                    "relationshipId" to relationshipId,
                    "userA_uid" to senderProfile.userId,
                    "userA_spreDropId" to senderProfile.spreDropId,
                    "userA_displayName" to senderProfile.displayName,
                    "userA_avatarColorHex" to senderProfile.avatarColorHex,
                    "userB_uid" to receiverProfile.userId,
                    "userB_spreDropId" to receiverProfile.spreDropId,
                    "userB_displayName" to receiverProfile.displayName,
                    "userB_avatarColorHex" to receiverProfile.avatarColorHex,
                    "createdAt" to System.currentTimeMillis(),
                    "projectId" to "spredrop"
                )

                fs.collection("friendships")
                    .document(relationshipId)
                    .set(friendshipDoc, SetOptions.merge())
                    .await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error accepting friend request: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun rejectCloudFriendRequest(myUserId: String, requestId: String): Result<Unit> {
        val fs = firestore
        if (fs == null || !isConfigured) return Result.success(Unit)
        return try {
            val reqRef = fs.collection("friendRequests").document(requestId)
            val snapshot = reqRef.get().await()
            if (snapshot.exists()) {
                val receiverUid = snapshot.getString("receiverUid")
                if (receiverUid == myUserId) {
                    reqRef.update("status", "REJECTED").await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error rejecting friend request: ${e.message}")
            Result.failure(e)
        }
    }

    fun observeCloudFriendships(userId: String): Flow<List<Friend>> = callbackFlow {
        val fs = firestore
        if (fs == null || !isConfigured) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        var regA: ListenerRegistration? = null
        var regB: ListenerRegistration? = null
        val listA = mutableListOf<Friend>()
        val listB = mutableListOf<Friend>()

        fun sendCombined() {
            val combined = (listA + listB).distinctBy { it.userId }
            trySend(combined)
        }

        try {
            regA = fs.collection("friendships")
                .whereEqualTo("userA_uid", userId)
                .addSnapshotListener { snapshots, error ->
                    if (error == null && snapshots != null) {
                        listA.clear()
                        snapshots.documents.forEach { doc ->
                            val otherUid = doc.getString("userB_uid") ?: ""
                            val otherSpreDropId = doc.getString("userB_spreDropId") ?: ""
                            val otherDisplayName = doc.getString("userB_displayName") ?: ""
                            val otherAvatarHex = doc.getString("userB_avatarColorHex") ?: "#00B4D8"
                            if (otherUid.isNotEmpty()) {
                                listA.add(
                                    Friend(
                                        userId = otherUid,
                                        spreDropId = otherSpreDropId,
                                        displayName = otherDisplayName,
                                        avatarColorHex = otherAvatarHex,
                                        status = FriendStatus.FRIENDS,
                                        availability = UserPresence.OFFLINE
                                    )
                                )
                            }
                        }
                        sendCombined()
                    }
                }

            regB = fs.collection("friendships")
                .whereEqualTo("userB_uid", userId)
                .addSnapshotListener { snapshots, error ->
                    if (error == null && snapshots != null) {
                        listB.clear()
                        snapshots.documents.forEach { doc ->
                            val otherUid = doc.getString("userA_uid") ?: ""
                            val otherSpreDropId = doc.getString("userA_spreDropId") ?: ""
                            val otherDisplayName = doc.getString("userA_displayName") ?: ""
                            val otherAvatarHex = doc.getString("userA_avatarColorHex") ?: "#00B4D8"
                            if (otherUid.isNotEmpty()) {
                                listB.add(
                                    Friend(
                                        userId = otherUid,
                                        spreDropId = otherSpreDropId,
                                        displayName = otherDisplayName,
                                        avatarColorHex = otherAvatarHex,
                                        status = FriendStatus.FRIENDS,
                                        availability = UserPresence.OFFLINE
                                    )
                                )
                            }
                        }
                        sendCombined()
                    }
                }
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error in friendships listener: ${e.message}")
        }

        awaitClose {
            regA?.remove()
            regB?.remove()
        }
    }

    suspend fun removeFriendshipInCloud(userId: String, friendId: String) {
        val fs = firestore
        if (fs == null || !isConfigured) return
        try {
            val relationshipId = if (userId < friendId) "${userId}_${friendId}" else "${friendId}_${userId}"
            fs.collection("friendships").document(relationshipId).delete().await()
            val requestId1 = "${userId}_${friendId}"
            val requestId2 = "${friendId}_${userId}"
            fs.collection("friendRequests").document(requestId1).delete().await()
            fs.collection("friendRequests").document(requestId2).delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Error deleting friendship in cloud: ${e.message}")
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
                                status = TransferStatus.REQUESTED,
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

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = java.util.Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) return sAddr
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("FirebaseDatabaseManager", "Error getting local IP: ${ex.message}")
        }
        return ""
    }

    suspend fun updateProposalStatus(transferId: String, status: String): Result<Unit> {
        val fs = firestore
        if (fs == null || !isConfigured) return Result.success(Unit)
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status
            )
            if (status == "ACCEPTED") {
                val localIp = getLocalIpAddress()
                if (localIp.isNotEmpty()) {
                    updates["receiverIp"] = localIp
                }
            }
            fs.collection("transfer_proposals")
                .document(transferId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pruneOldCloudData(): Result<Unit> {
        val fs = firestore
        if (fs == null || !isConfigured) return Result.success(Unit)
        return try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            
            // Prune old transfers
            val oldTransfers = fs.collection("transfers")
                .whereLessThan("timestamp", thirtyDaysAgo)
                .get()
                .await()
            for (doc in oldTransfers.documents) {
                doc.reference.delete()
            }
            
            // Prune old friend requests
            val oldReqs = fs.collection("friendRequests")
                .whereLessThan("createdAt", thirtyDaysAgo)
                .get()
                .await()
            for (doc in oldReqs.documents) {
                doc.reference.delete()
            }

            // Prune old transfer proposals
            val oldProposals = fs.collection("transfer_proposals")
                .whereLessThan("timestamp", thirtyDaysAgo)
                .get()
                .await()
            for (doc in oldProposals.documents) {
                doc.reference.delete()
            }

            Log.i("FirebaseDatabaseManager", "Successfully pruned old cloud data (older than 30 days) from Firestore.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDatabaseManager", "Failed to prune old cloud data: ${e.message}")
            Result.failure(e)
        }
    }
}
