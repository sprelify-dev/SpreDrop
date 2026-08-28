package com.example.spredrop.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.spredrop.model.TransferRecord

object TransferNotificationHelper {

    private const val CHANNEL_TRANSFERS = "channel_spredrop_transfers"
    private const val CHANNEL_FRIENDS = "channel_spredrop_friends"
    private const val CHANNEL_NAME_TRANSFERS = "SpreDrop File Transfers"
    private const val CHANNEL_NAME_FRIENDS = "SpreDrop Friend Alerts"

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val transferChannel = NotificationChannel(
                CHANNEL_TRANSFERS,
                CHANNEL_NAME_TRANSFERS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows live incoming transfer requests and active chunk progress"
                enableVibration(true)
            }

            val friendChannel = NotificationChannel(
                CHANNEL_FRIENDS,
                CHANNEL_NAME_FRIENDS,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for friend requests and pairing status"
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(transferChannel)
            manager?.createNotificationChannel(friendChannel)
        }
    }

    fun showIncomingTransferNotification(context: Context, transfer: TransferRecord) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("nav_destination", "transfers")
                putExtra("transfer_id", transfer.transferId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                transfer.transferId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val sizeMb = transfer.fileSize / (1024f * 1024f)
            val sizeStr = if (sizeMb >= 1f) String.format("%.1f MB", sizeMb) else "${transfer.fileSize / 1024} KB"

            val notification = NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Incoming SpreDrop Transfer")
                .setContentText("${transfer.senderDisplayName} (${transfer.senderSpreDropId}) wants to send ${transfer.fileName} ($sizeStr)")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "Direct WebRTC transfer request for '${transfer.fileName}' ($sizeStr) from ${transfer.senderDisplayName}."
                ))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(transfer.transferId.hashCode(), notification)
        } catch (e: Exception) {
            // Notifications may be restricted by Android permissions
        }
    }

    fun showTransferProgressNotification(context: Context, transfer: TransferRecord) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("nav_destination", "transfers")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                transfer.transferId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val progressPercent = (transfer.progress * 100).toInt()

            val notification = NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("${if (transfer.direction == com.example.spredrop.model.TransferDirection.INCOMING) "Receiving" else "Sending"} ${transfer.fileName}")
                .setContentText("$progressPercent% • ${transfer.formattedSpeed}")
                .setProgress(100, progressPercent, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(transfer.transferId.hashCode(), notification)
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun showTransferCompleteNotification(context: Context, transfer: TransferRecord) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("nav_destination", "transfers")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                transfer.transferId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isIncoming = transfer.direction == com.example.spredrop.model.TransferDirection.INCOMING
            val title = if (isIncoming) "File Received Successfully" else "File Sent Successfully"
            val text = "${transfer.fileName} transferred directly via SpreDrop WebRTC DataChannel (Checksum verified)"

            val notification = NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(transfer.transferId.hashCode(), notification)
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun showFriendRequestNotification(context: Context, friendName: String, spreDropId: String) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("nav_destination", "friends")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                spreDropId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_FRIENDS)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("New Friend Request")
                .setContentText("$friendName ($spreDropId) wants to connect on SpreDrop")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(spreDropId.hashCode(), notification)
        } catch (e: Exception) {
            // Ignored
        }
    }
}
