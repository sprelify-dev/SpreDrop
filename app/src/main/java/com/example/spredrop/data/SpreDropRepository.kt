package com.example.spredrop.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.spredrop.data.firebase.AuthState
import com.example.spredrop.data.firebase.FirebaseAuthManager
import com.example.spredrop.data.firebase.FirebaseConfig
import com.example.spredrop.data.firebase.FirebaseDatabaseManager
import com.example.spredrop.data.firebase.FirestoreConnectionState
import com.example.spredrop.data.local.SpreDropDatabase
import com.example.spredrop.model.*
import com.example.spredrop.network.P2PTransferEngine
import com.example.spredrop.network.SpreDropSignalingManager
import com.example.spredrop.network.SpreDropWifiP2pManager
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class StorageStats(
    val usedBytesBySpreDrop: Long = 0L,
    val availableDeviceBytes: Long = 0L,
    val totalDeviceBytes: Long = 0L,
    val totalFilesReceived: Int = 0
)

class SpreDropRepository(private val context: Context) {

    private val db = SpreDropDatabase.getInstance(context)
    private val userDao = db.userDao()
    private val friendDao = db.friendDao()
    private val transferDao = db.transferDao()
    private val devLogDao = db.devLogDao()

    val authManager = FirebaseAuthManager(context)
    val databaseManager = FirebaseDatabaseManager()
    val transferEngine = P2PTransferEngine(context, transferDao, devLogDao, databaseManager)
    val wifiP2pManager = SpreDropWifiP2pManager(context)

    val signalingManager = SpreDropSignalingManager(
        context = context,
        userDao = userDao,
        friendDao = friendDao,
        transferDao = transferDao,
        devLogDao = devLogDao,
        transferEngine = transferEngine,
        databaseManager = databaseManager
    )

    private val repoScope = CoroutineScope(Dispatchers.IO)

    val authState: StateFlow<AuthState> = authManager.authState
    val currentFirebaseUser: AuthenticatedAccount?
        get() = authManager.currentUser
    val firestoreConnectionState: StateFlow<FirestoreConnectionState> = databaseManager.connectionState
    val lastSyncTimestamp: StateFlow<Long> = databaseManager.lastSyncTimestamp

    val userProfile: Flow<UserProfile?> = userDao.getUserProfile()
    val friends: Flow<List<Friend>> = friendDao.getFriends()
    val incomingFriendRequests: Flow<List<Friend>> = friendDao.getIncomingFriendRequests()
    val outgoingFriendRequests: Flow<List<Friend>> = friendDao.getOutgoingFriendRequests()
    val blockedUsers: Flow<List<Friend>> = friendDao.getBlockedUsers()

    val allTransfers: Flow<List<TransferRecord>> = transferDao.getAllTransfers()
    val activeTransfers: Flow<List<TransferRecord>> = transferDao.getActiveTransfers()
    val receivedFiles: Flow<List<TransferRecord>> = transferDao.getReceivedFiles()
    val completedTransfers: Flow<List<TransferRecord>> = transferDao.getCompletedTransfers()

    val discoveredPeers: StateFlow<List<PeerDevice>> = signalingManager.discoveredPeers
    val isOnline: StateFlow<Boolean> = signalingManager.isOnline
    val isDiscovering: StateFlow<Boolean> = signalingManager.isDiscovering

    val devLogs: Flow<List<DevLogEntry>> = devLogDao.getRecentLogs()

    init {
        repoScope.launch {
            setupAuthSync()
            autoPruneOldData()
        }
    }

    private suspend fun autoPruneOldData() {
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
            transferDao.pruneOldTransfers(thirtyDaysAgo)
            databaseManager.pruneOldCloudData()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private suspend fun setupAuthSync() {
        authManager.authState.collect { state ->
            if (state is AuthState.Authenticated) {
                val user = state.user
                val current = userDao.getUserProfileOnce()
                val savedAccount = userDao.getAccountById(user.uid)

                // Try to get profile from Firestore first as source of truth
                val cloudProfile = databaseManager.getUserProfile(user.uid)

                val realDisplayName = when {
                    cloudProfile != null -> cloudProfile.displayName
                    !current?.displayName.isNullOrBlank() -> current!!.displayName
                    !savedAccount?.displayName.isNullOrBlank() -> savedAccount!!.displayName
                    !user.displayName.isNullOrBlank() -> user.displayName
                    else -> "SpreDrop User"
                }

                val realHandle = when {
                    cloudProfile != null -> cloudProfile.spreDropId
                    !current?.spreDropId.isNullOrBlank() -> current!!.spreDropId
                    !savedAccount?.spreDropId.isNullOrBlank() -> savedAccount!!.spreDropId
                    !user.email.isNullOrBlank() -> "@" + user.email.substringBefore("@").replace(".", "_")
                    else -> "@" + user.uid.take(6)
                }

                val realVisibility = cloudProfile?.visibility ?: current?.visibility ?: PrivacyMode.VISIBLE
                val realAvailability = cloudProfile?.availability ?: current?.availability ?: UserPresence.AVAILABLE
                val realAvatarHex = cloudProfile?.avatarColorHex ?: current?.avatarColorHex ?: "#00B4D8"

                val updated = UserProfile(
                    userId = user.uid,
                    spreDropId = realHandle,
                    displayName = realDisplayName,
                    profilePhotoUri = user.photoUrl ?: cloudProfile?.profilePhotoUri ?: current?.profilePhotoUri,
                    avatarColorHex = realAvatarHex,
                    visibility = realVisibility,
                    availability = realAvailability,
                    deviceModel = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}",
                    createdAt = cloudProfile?.createdAt ?: current?.createdAt ?: System.currentTimeMillis(),
                    lastSeen = System.currentTimeMillis()
                )
                userDao.insertOrUpdateProfile(updated)

                // Sync local account
                if (savedAccount == null && !user.email.isNullOrBlank()) {
                    userDao.insertAccount(
                        UserAccount(
                            email = user.email.lowercase(),
                            passwordHash = "", // OAuth or external sign in
                            userId = user.uid,
                            spreDropId = realHandle,
                            displayName = realDisplayName
                        )
                    )
                }

                databaseManager.uploadUserProfile(updated)
                databaseManager.reserveUsername(realHandle, user.uid)

                databaseManager.publishPeerPresence(
                    userId = updated.userId,
                    spreDropId = updated.spreDropId,
                    displayName = updated.displayName,
                    avatarColorHex = updated.avatarColorHex,
                    availability = updated.availability,
                    isOnline = true
                )
            }
        }
    }

    suspend fun updatePresence(presence: UserPresence) {
        val profile = userDao.getUserProfileOnce() ?: return
        userDao.updatePresence(profile.userId, presence)
        databaseManager.publishPeerPresence(
            userId = profile.userId,
            spreDropId = profile.spreDropId,
            displayName = profile.displayName,
            avatarColorHex = profile.avatarColorHex,
            availability = presence,
            isOnline = presence != UserPresence.OFFLINE && presence != UserPresence.INVISIBLE
        )
    }

    suspend fun updatePrivacy(privacy: PrivacyMode) {
        val profile = userDao.getUserProfileOnce() ?: return
        userDao.updatePrivacy(profile.userId, privacy)
        val updated = profile.copy(visibility = privacy)
        databaseManager.uploadUserProfile(updated)
    }

    suspend fun updateProfileIdentity(spreDropId: String, displayName: String) {
        val profile = userDao.getUserProfileOnce() ?: return
        val cleanId = (if (spreDropId.startsWith("@")) spreDropId else "@$spreDropId").lowercase().trim()
        
        val isSameUser = cleanId == profile.spreDropId.lowercase()
        if (!isSameUser) {
            val available = databaseManager.isUsernameAvailable(cleanId)
            if (!available) {
                throw IllegalArgumentException("The SpreDrop ID $cleanId is already taken.")
            }
            val reservation = databaseManager.reserveUsername(cleanId, profile.userId)
            if (reservation.isFailure || reservation.getOrDefault(false) == false) {
                throw IllegalArgumentException("Could not reserve SpreDrop ID $cleanId. It might be taken.")
            }
        }

        userDao.updateIdentity(profile.userId, cleanId, displayName)
        userDao.updateAccountIdentity(profile.userId, cleanId, displayName)
        authManager.updateActiveSession(displayName, cleanId)
        val updated = profile.copy(spreDropId = cleanId, displayName = displayName)
        databaseManager.uploadUserProfile(updated)
        databaseManager.publishPeerPresence(
            userId = updated.userId,
            spreDropId = updated.spreDropId,
            displayName = updated.displayName,
            avatarColorHex = updated.avatarColorHex,
            availability = updated.availability,
            isOnline = true
        )
    }

    suspend fun syncWithFirestoreNow(): Result<Unit> {
        val profile = userDao.getUserProfileOnce() ?: return Result.failure(Exception("No profile found"))
        val uploadResult = databaseManager.uploadUserProfile(profile)
        // Also sync recent transfers to Firestore
        val completed = transferDao.getCompletedTransfers()
        repoScope.launch {
            completed.collect { transfers ->
                transfers.take(10).forEach { transfer ->
                    databaseManager.logTransferToCloud(transfer)
                }
            }
        }
        return uploadResult
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<AuthenticatedAccount> {
        val result = authManager.signInWithEmail(email, pass)
        if (result.isSuccess) {
            syncWithFirestoreNow()
        }
        return result
    }

    suspend fun signUpWithEmail(email: String, pass: String, displayName: String, spreDropId: String): Result<AuthenticatedAccount> {
        val cleanId = (if (spreDropId.startsWith("@")) spreDropId else "@$spreDropId").lowercase().trim()
        val available = databaseManager.isUsernameAvailable(cleanId)
        if (!available) {
            return Result.failure(IllegalArgumentException("The SpreDrop ID $cleanId is already taken. Please choose a different handle."))
        }
        val result = authManager.signUpWithEmail(email, pass, displayName, cleanId)
        if (result.isSuccess) {
            val user = result.getOrNull()
            if (user != null) {
                databaseManager.reserveUsername(cleanId, user.uid)
            }
            syncWithFirestoreNow()
        }
        return result
    }

    suspend fun signInWithGoogle(webClientId: String? = null): Result<AuthenticatedAccount> {
        val result = authManager.signInWithGoogle(webClientId)
        if (result.isSuccess) {
            syncWithFirestoreNow()
        }
        return result
    }

    suspend fun signOut(): Result<Unit> {
        return authManager.signOut()
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return authManager.sendPasswordReset(email)
    }

    suspend fun resetPasswordWithNew(email: String, newPass: String): Result<Unit> {
        return authManager.resetPasswordWithNew(email, newPass)
    }

    fun getFirebaseConfig(): FirebaseConfig {
        return authManager.getFirebaseConfig()
    }

    suspend fun updateFirebaseConfig(projectId: String, apiKey: String, appId: String): Result<Unit> {
        val result = authManager.updateFirebaseConfig(projectId, apiKey, appId)
        if (result.isSuccess) {
            syncWithFirestoreNow()
        }
        return result
    }

    fun clearAuthError() {
        authManager.clearError()
    }

    suspend fun sendFriendRequest(targetSpreDropId: String, targetDisplayName: String) {
        val cleanId = (if (targetSpreDropId.startsWith("@")) targetSpreDropId else "@$targetSpreDropId").lowercase().trim()
        val profile = userDao.getUserProfileOnce()
        if (profile != null) {
            databaseManager.sendCloudFriendRequest(profile, cleanId)
        }

        val existing = friendDao.getFriendBySpreDropId(cleanId)
        if (existing != null) {
            if (existing.status == FriendStatus.NONE || existing.status == FriendStatus.REQUEST_RECEIVED) {
                friendDao.updateFriendStatus(existing.userId, FriendStatus.FRIENDS)
            }
        } else {
            val newFriend = Friend(
                userId = "usr_${cleanId.removePrefix("@")}_${UUID.randomUUID().toString().take(6)}",
                spreDropId = cleanId,
                displayName = targetDisplayName.ifBlank { cleanId.removePrefix("@").replaceFirstChar { it.uppercase() } },
                status = FriendStatus.REQUEST_SENT,
                availability = UserPresence.AVAILABLE
            )
            friendDao.insertOrUpdateFriend(newFriend)
        }
    }

    suspend fun acceptFriendRequest(friendId: String) {
        friendDao.updateFriendStatus(friendId, FriendStatus.FRIENDS)
        val profile = userDao.getUserProfileOnce()
        if (profile != null) {
            val requestId = "req_${friendId}_${profile.spreDropId.replace("@", "")}"
            databaseManager.updateCloudFriendRequestStatus(requestId, "ACCEPTED")
            val friend = friendDao.getFriendById(friendId)
            if (friend != null) {
                val reverseRequestId = "req_${profile.userId}_${friend.spreDropId.replace("@", "")}"
                databaseManager.updateCloudFriendRequestStatus(reverseRequestId, "ACCEPTED")
            }
        }
    }

    suspend fun rejectFriendRequest(friendId: String) {
        friendDao.deleteFriend(friendId)
        val profile = userDao.getUserProfileOnce()
        if (profile != null) {
            val requestId = "req_${friendId}_${profile.spreDropId.replace("@", "")}"
            databaseManager.updateCloudFriendRequestStatus(requestId, "REJECTED")
        }
    }

    suspend fun removeFriend(friendId: String) {
        friendDao.deleteFriend(friendId)
    }

    suspend fun blockUser(friendId: String) {
        friendDao.updateFriendStatus(friendId, FriendStatus.BLOCKED)
    }

    suspend fun unblockUser(friendId: String) {
        friendDao.updateFriendStatus(friendId, FriendStatus.NONE)
    }

    fun startFileTransferToPeer(uri: Uri, receiver: PeerDevice) {
        repoScope.launch {
            val profile = userDao.getUserProfileOnce() ?: return@launch
            val fileName = getFileNameFromUri(uri)
            val fileSize = getFileSizeFromUri(uri)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val transferId = "tx_${UUID.randomUUID().toString().take(8)}"

            val record = TransferRecord(
                transferId = transferId,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                senderId = profile.userId,
                senderSpreDropId = profile.spreDropId,
                senderDisplayName = profile.displayName,
                receiverId = receiver.deviceId,
                receiverSpreDropId = receiver.spreDropId,
                receiverDisplayName = receiver.displayName,
                direction = TransferDirection.OUTGOING,
                status = TransferStatus.NEGOTIATING_WEBRTC,
                totalBytes = fileSize,
                chunkSize = 64 * 1024,
                totalChunks = ((fileSize + 64 * 1024 - 1) / (64 * 1024)).toInt().coerceAtLeast(1),
                timestamp = System.currentTimeMillis()
            )

            // Insert outgoing transfer locally
            transferDao.insertTransfer(record)

            val transportType = if (receiver.connectionType == PeerConnectionType.NEARBY_BLE) "BLE & Cloud" else "Cloud Relay"
            devLogDao.insertLog(
                DevLogEntry(
                    tag = "TRANSFER",
                    message = "Initiating transfer for '$fileName' (${formatFileSize(fileSize)}) to ${receiver.displayName} (${receiver.spreDropId}) via $transportType.",
                    level = "INFO"
                )
            )

            // Send proposal via Firestore signaling to the actual recipient
            databaseManager.sendTransferProposal(record)

            // Start outgoing transfer process waiting for the real recipient's acceptance
            transferEngine.startOutgoingTransfer(
                transferId = transferId,
                uri = uri,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                receiver = receiver,
                senderProfile = profile
            )
        }
    }

    fun cancelTransfer(transferId: String) {
        transferEngine.cancelTransfer(transferId)
    }

    fun acceptIncomingTransfer(transfer: TransferRecord) {
        signalingManager.acceptIncomingTransfer(transfer)
    }

    fun declineIncomingTransfer(transferId: String) {
        signalingManager.declineIncomingTransfer(transferId)
    }

    suspend fun clearHistory() {
        transferDao.clearCompletedHistory()
    }

    suspend fun clearDevLogs() {
        devLogDao.clearLogs()
    }

    fun getStorageStats(): StorageStats {
        return try {
            val receivedDir = transferEngine.receivedFilesDir
            val files = receivedDir.listFiles() ?: emptyArray()
            val usedBytes = files.sumOf { it.length() }
            val stat = StatFs(Environment.getDataDirectory().path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            StorageStats(
                usedBytesBySpreDrop = usedBytes,
                availableDeviceBytes = availableBytes,
                totalDeviceBytes = totalBytes,
                totalFilesReceived = files.size
            )
        } catch (e: Exception) {
            StorageStats()
        }
    }

    suspend fun deleteReceivedFile(transferId: String) {
        val transfer = transferDao.getTransferById(transferId)
        if (transfer?.localFilePath != null) {
            try {
                File(transfer.localFilePath).delete()
            } catch (e: Exception) {
                // Ignore
            }
        }
        transferDao.deleteTransfer(transferId)
    }

    fun openFile(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val extension = MimeTypeMap.getFileExtensionFromUrl(file.name)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // No app to handle or restricted
        }
    }

    fun shareFile(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val extension = MimeTypeMap.getFileExtensionFromUrl(file.name)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        var size = 0L
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index != -1) {
                    size = it.getLong(index)
                }
            }
        }
        return if (size > 0) size else 1024L
    }

    private fun formatFileSize(bytes: Long): String {
        val mb = bytes / (1024f * 1024f)
        return if (mb >= 1.0f) String.format("%.1f MB", mb) else "${bytes / 1024} KB"
    }
}
