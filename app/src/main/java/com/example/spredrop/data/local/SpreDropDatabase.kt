package com.example.spredrop.data.local

import android.content.Context
import androidx.room.*
import com.example.spredrop.model.*
import kotlinx.coroutines.flow.Flow

class SpreDropTypeConverters {
    @TypeConverter
    fun fromUserPresence(value: UserPresence): String = value.name

    @TypeConverter
    fun toUserPresence(value: String): UserPresence = try {
        UserPresence.valueOf(value)
    } catch (e: Exception) {
        UserPresence.AVAILABLE
    }

    @TypeConverter
    fun fromPrivacyMode(value: PrivacyMode): String = value.name

    @TypeConverter
    fun toPrivacyMode(value: String): PrivacyMode = try {
        PrivacyMode.valueOf(value)
    } catch (e: Exception) {
        PrivacyMode.VISIBLE
    }

    @TypeConverter
    fun fromTransferDirection(value: TransferDirection): String = value.name

    @TypeConverter
    fun toTransferDirection(value: String): TransferDirection = try {
        TransferDirection.valueOf(value)
    } catch (e: Exception) {
        TransferDirection.INCOMING
    }

    @TypeConverter
    fun fromTransferStatus(value: TransferStatus): String = value.name

    @TypeConverter
    fun toTransferStatus(value: String): TransferStatus = try {
        TransferStatus.valueOf(value)
    } catch (e: Exception) {
        TransferStatus.COMPLETED
    }

    @TypeConverter
    fun fromFriendStatus(value: FriendStatus): String = value.name

    @TypeConverter
    fun toFriendStatus(value: String): FriendStatus = try {
        FriendStatus.valueOf(value)
    } catch (e: Exception) {
        FriendStatus.NONE
    }
}

@Dao
interface UserDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getUserProfileOnce(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfile)

    @Query("DELETE FROM user_profile")
    suspend fun deleteProfile()

    @Query("UPDATE user_profile SET availability = :presence, lastSeen = :time WHERE userId = :userId")
    suspend fun updatePresence(userId: String, presence: UserPresence, time: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET visibility = :privacy WHERE userId = :userId")
    suspend fun updatePrivacy(userId: String, privacy: PrivacyMode)

    @Query("UPDATE user_profile SET spreDropId = :spreDropId, displayName = :displayName WHERE userId = :userId")
    suspend fun updateIdentity(userId: String, spreDropId: String, displayName: String)

    @Query("UPDATE user_accounts SET spreDropId = :spreDropId, displayName = :displayName WHERE userId = :userId")
    suspend fun updateAccountIdentity(userId: String, spreDropId: String, displayName: String)

    // User Accounts Authentication
    @Query("SELECT * FROM user_accounts WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getAccountByEmail(email: String): UserAccount?

    @Query("SELECT * FROM user_accounts WHERE userId = :userId LIMIT 1")
    suspend fun getAccountById(userId: String): UserAccount?

    @Query("SELECT * FROM user_accounts ORDER BY createdAt DESC")
    suspend fun getAllAccounts(): List<UserAccount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: UserAccount)

    @Query("UPDATE user_accounts SET passwordHash = :newHash WHERE LOWER(email) = LOWER(:email)")
    suspend fun updatePasswordHash(email: String, newHash: String)
}

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends WHERE status = 'FRIENDS' ORDER BY availability ASC, displayName ASC")
    fun getFriends(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE status = 'FRIENDS'")
    suspend fun getFriendsOnce(): List<Friend>

    @Query("SELECT * FROM friends WHERE status = 'REQUEST_RECEIVED' ORDER BY lastSeen DESC")
    fun getIncomingFriendRequests(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE status = 'REQUEST_SENT' ORDER BY lastSeen DESC")
    fun getOutgoingFriendRequests(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE status = 'BLOCKED' ORDER BY displayName ASC")
    fun getBlockedUsers(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE userId = :userId LIMIT 1")
    suspend fun getFriendById(userId: String): Friend?

    @Query("SELECT * FROM friends WHERE spreDropId = :spreDropId LIMIT 1")
    suspend fun getFriendBySpreDropId(spreDropId: String): Friend?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFriend(friend: Friend)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(friends: List<Friend>)

    @Query("UPDATE friends SET status = :status WHERE userId = :userId")
    suspend fun updateFriendStatus(userId: String, status: FriendStatus)

    @Query("UPDATE friends SET availability = :presence, lastSeen = :time WHERE userId = :userId")
    suspend fun updateFriendPresence(userId: String, presence: UserPresence, time: Long)

    @Query("DELETE FROM friends WHERE userId = :userId")
    suspend fun deleteFriend(userId: String)
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferRecord>>

    @Query("SELECT * FROM transfers WHERE status IN ('CREATED', 'REQUESTED', 'ACCEPTED', 'CONNECTION_PENDING', 'CONNECTION_READY', 'TRANSFERRING') ORDER BY timestamp DESC")
    fun getActiveTransfers(): Flow<List<TransferRecord>>

    @Query("SELECT * FROM transfers WHERE status = 'COMPLETED' AND direction = 'INCOMING' ORDER BY timestamp DESC")
    fun getReceivedFiles(): Flow<List<TransferRecord>>

    @Query("SELECT * FROM transfers WHERE status = 'COMPLETED' ORDER BY timestamp DESC")
    fun getCompletedTransfers(): Flow<List<TransferRecord>>

    @Query("SELECT * FROM transfers WHERE transferId = :transferId LIMIT 1")
    fun getTransferFlow(transferId: String): Flow<TransferRecord?>

    @Query("SELECT * FROM transfers WHERE transferId = :transferId LIMIT 1")
    suspend fun getTransferById(transferId: String): TransferRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferRecord)

    @Update
    suspend fun updateTransfer(transfer: TransferRecord)

    @Query("UPDATE transfers SET status = :status, errorMessage = :error WHERE transferId = :transferId")
    suspend fun updateStatus(transferId: String, status: TransferStatus, error: String? = null)

    @Query("UPDATE transfers SET bytesTransferred = :bytes, chunksTransferred = :chunks, transferSpeedBytesPerSec = :speed WHERE transferId = :transferId")
    suspend fun updateProgress(transferId: String, bytes: Long, chunks: Int, speed: Long)

    @Query("DELETE FROM transfers WHERE transferId = :transferId")
    suspend fun deleteTransfer(transferId: String)

    @Query("DELETE FROM transfers WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldTransfers(cutoffTimestamp: Long)

    @Query("SELECT * FROM transfers") // Fallback clean up or selective prune if needed
    suspend fun getAllTransfersOnce(): List<TransferRecord>

    @Query("DELETE FROM transfers WHERE status NOT IN ('CREATED', 'REQUESTED', 'ACCEPTED', 'CONNECTION_PENDING', 'CONNECTION_READY', 'TRANSFERRING')")
    suspend fun clearCompletedHistory()
}

@Dao
interface DevLogDao {
    @Query("SELECT * FROM dev_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<DevLogEntry>>

    @Insert
    suspend fun insertLog(log: DevLogEntry)

    @Query("DELETE FROM dev_logs")
    suspend fun clearLogs()
}

@Database(
    entities = [UserProfile::class, Friend::class, TransferRecord::class, DevLogEntry::class, UserAccount::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(SpreDropTypeConverters::class)
abstract class SpreDropDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun friendDao(): FriendDao
    abstract fun transferDao(): TransferDao
    abstract fun devLogDao(): DevLogDao

    companion object {
        @Volatile
        private var INSTANCE: SpreDropDatabase? = null

        fun getInstance(context: Context): SpreDropDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpreDropDatabase::class.java,
                    "spredrop_p2p.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
