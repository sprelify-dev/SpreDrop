package com.example.spredrop.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.spredrop.data.firebase.FirebaseDatabaseManager
import com.example.spredrop.data.local.SpreDropDatabase
import com.example.spredrop.network.SpreDropBleManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*

/**
 * Android Foreground Service for SpreDrop Proximity Discovery.
 * Keeps Bluetooth Low Energy Advertising & Scanning active in the background,
 * keeps Firestore presence live, and listens for incoming transfer proposals.
 */
class SpreDropBackgroundDiscoveryService : Service() {

    companion object {
        private const val TAG = "BackgroundDiscovery"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_DISCOVERY = "channel_spredrop_discovery"

        fun start(context: Context) {
            try {
                val intent = Intent(context, SpreDropBackgroundDiscoveryService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Service start skipped or not allowed: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SpreDropBackgroundDiscoveryService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Service stop warning: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var bleManager: SpreDropBleManager? = null
    private val databaseManager = FirebaseDatabaseManager()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            val hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                hasLocation
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val serviceType = if (hasBluetooth || hasLocation) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(NOTIFICATION_ID, createForegroundNotification(), serviceType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (hasBluetooth || hasLocation) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                }
                if (serviceType != 0) {
                    startForeground(NOTIFICATION_ID, createForegroundNotification(), serviceType)
                } else {
                    startForeground(NOTIFICATION_ID, createForegroundNotification())
                }
            } else {
                startForeground(NOTIFICATION_ID, createForegroundNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Foreground start failed, trying minimal fallback: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        createForegroundNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, createForegroundNotification())
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Fatal failed to start foreground service: ${ex.message}")
            }
        }
        startProximityEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        bleManager?.stopAdvertising()
        bleManager?.stopScanning()
        serviceScope.cancel()
        Log.i(TAG, "SpreDrop background proximity service destroyed")
    }

    private fun startProximityEngine() {
        serviceScope.launch {
            val db = SpreDropDatabase.getInstance(applicationContext)
            val userDao = db.userDao()
            val profile = userDao.getUserProfileOnce() ?: return@launch
            val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }

            bleManager = SpreDropBleManager(applicationContext) { discoveredPeer ->
                Log.d(TAG, "Background discovered nearby peer: ${discoveredPeer.spreDropId} (${discoveredPeer.signalStrengthRssi} dBm)")
            }

            // Start BLE scanning
            bleManager?.startScanning()

            // Periodic presence heartbeat to Firestore with dynamic visibility checks
            while (isActive) {
                val currentProfile = userDao.getUserProfileOnce()
                if (currentProfile != null) {
                    val isVisible = currentProfile.visibility != com.example.spredrop.model.PrivacyMode.INVISIBLE && 
                                    currentProfile.availability != com.example.spredrop.model.UserPresence.INVISIBLE && 
                                    currentProfile.availability != com.example.spredrop.model.UserPresence.OFFLINE
                    if (isVisible) {
                        bleManager?.startAdvertising(
                            spreDropId = currentProfile.spreDropId,
                            displayName = currentProfile.displayName,
                            userId = currentProfile.userId
                        )
                        if (authUser != null) {
                            databaseManager.publishPeerPresence(
                                userId = currentProfile.userId,
                                spreDropId = currentProfile.spreDropId,
                                displayName = currentProfile.displayName,
                                avatarColorHex = currentProfile.avatarColorHex,
                                availability = currentProfile.availability,
                                isOnline = true
                            )
                        }
                    } else {
                        bleManager?.stopAdvertising()
                        if (authUser != null) {
                            databaseManager.publishPeerPresence(
                                userId = currentProfile.userId,
                                spreDropId = currentProfile.spreDropId,
                                displayName = currentProfile.displayName,
                                avatarColorHex = currentProfile.avatarColorHex,
                                availability = currentProfile.availability,
                                isOnline = false
                            )
                        }
                    }
                }
                delay(15000) // 15s heartbeat
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_DISCOVERY,
                "SpreDrop Proximity Radar",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps nearby peer discovery and transfer listeners active"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_DISCOVERY)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("SpreDrop Proximity Active")
            .setContentText("Scanning for nearby SpreDrop peers • Ready to send & receive")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
