package com.example.spredrop.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Presence states defined in the SpreDrop specification
 */
enum class UserPresence(val label: String, val emoji: String) {
    AVAILABLE("Available (Everyone)", "🟢"),
    FRIENDS_ONLY("Friends Only", "👥"),
    INVISIBLE("Invisible (Hidden)", "👻"),
    ONLINE("Online", "🟢"),
    OFFLINE("Offline", "⚫"),
    TRANSFERRING("Transferring", "⚡"),
    CONNECTING("Connecting", "🔄")
}

/**
 * Privacy modes defined in the SpreDrop specification
 */
enum class PrivacyMode(val label: String, val description: String) {
    VISIBLE("Visible to Everyone", "Discoverable by all nearby SpreDrop users"),
    FRIENDS_ONLY("Friends Only", "Only confirmed friends can discover and send files"),
    INVISIBLE("Invisible", "Hidden from public discovery; completely private"),
    BLOCKED("Blocked", "Restricted access")
}

/**
 * Transfer direction
 */
enum class TransferDirection {
    INCOMING,
    OUTGOING
}

/**
 * Transfer state machine
 */
enum class TransferStatus(val label: String) {
    PENDING("Waiting for acceptance"),
    ACCEPTED("Accepted, connecting..."),
    NEGOTIATING_WEBRTC("Negotiating WebRTC..."),
    TRANSFERRING("Transferring"),
    VERIFYING("Verifying file integrity..."),
    COMPLETED("Completed"),
    DECLINED("Declined"),
    FAILED("Transfer Failed"),
    CANCELLED("Cancelled")
}

/**
 * Friend connection status
 */
enum class FriendStatus {
    NONE,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    FRIENDS,
    BLOCKED
}

/**
 * Peer connection transport type
 */
enum class PeerConnectionType {
    LAN_WEBRTC,
    NEARBY_BLE,
    SIGNALING_SERVER,
    DIRECT_P2P
}

/**
 * User Profile Entity
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val userId: String = "user_spredrop_local",
    val spreDropId: String = "@rahul",
    val displayName: String = "Rahul Sharma",
    val profilePhotoUri: String? = null,
    val avatarColorHex: String = "#00B4D8",
    val visibility: PrivacyMode = PrivacyMode.VISIBLE,
    val availability: UserPresence = UserPresence.AVAILABLE,
    val deviceModel: String = "Android Device",
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * User Account Credential Entity for Registered Users
 */
@Entity(tableName = "user_accounts")
data class UserAccount(
    @PrimaryKey val email: String,
    val passwordHash: String,
    val userId: String,
    val spreDropId: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Clean Unified Authenticated User Representation
 */
data class AuthenticatedAccount(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null
)

/**
 * Friend Entity
 */
@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey val userId: String,
    val spreDropId: String,
    val displayName: String,
    val profilePhotoUri: String? = null,
    val avatarColorHex: String = "#06D6A0",
    val status: FriendStatus = FriendStatus.FRIENDS,
    val availability: UserPresence = UserPresence.AVAILABLE,
    val lastSeen: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val mutualFilesCount: Int = 0
)

/**
 * Discovered Peer Model (In-memory + Nearby presence)
 */
data class PeerDevice(
    val deviceId: String,
    val spreDropId: String,
    val displayName: String,
    val avatarColorHex: String = "#00B4D8",
    val availability: UserPresence = UserPresence.AVAILABLE,
    val isFriend: Boolean = false,
    val connectionType: PeerConnectionType = PeerConnectionType.LAN_WEBRTC,
    val signalStrengthRssi: Int = -55,
    val ipAddress: String = "192.168.1.100",
    val lastDiscovered: Long = System.currentTimeMillis(),
    val supportedCapabilities: List<String> = listOf("WEBRTC_DATACHANNEL", "CHUNK_STREAM", "SHA256_INTEGRITY")
)

/**
 * Transfer Record Entity
 */
@Entity(tableName = "transfers")
data class TransferRecord(
    @PrimaryKey val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val senderId: String,
    val senderSpreDropId: String,
    val senderDisplayName: String,
    val receiverId: String,
    val receiverSpreDropId: String,
    val receiverDisplayName: String,
    val direction: TransferDirection,
    val status: TransferStatus,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val transferSpeedBytesPerSec: Long = 0L,
    val chunkSize: Int = 64 * 1024, // 64 KB chunks
    val totalChunks: Int = 1,
    val chunksTransferred: Int = 0,
    val localFilePath: String? = null,
    val sha256Checksum: String = "",
    val verifiedChecksum: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedSpeed: String
        get() {
            val mbps = transferSpeedBytesPerSec / (1024f * 1024f)
            return if (mbps >= 1.0f) {
                String.format("%.1f MB/s", mbps)
            } else {
                val kbps = transferSpeedBytesPerSec / 1024f
                String.format("%.0f KB/s", kbps)
            }
        }
}

/**
 * Dev / WebRTC Log Entity for inspection & diagnostics
 */
@Entity(tableName = "dev_logs")
data class DevLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val level: String = "INFO" // INFO, WEBRTC, SIGNAL, ERROR, CHUNK
)
