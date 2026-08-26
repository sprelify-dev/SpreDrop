package com.example.spredrop.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spredrop.model.*
import com.example.spredrop.ui.SpreDropViewModel
import com.example.spredrop.ui.components.*
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: SpreDropViewModel,
    onNavigateToQrPair: () -> Unit,
    onNavigateToTransfers: () -> Unit,
    onNavigateToFriends: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val activeTransfers by viewModel.activeTransfers.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var targetPeerForFilePick by remember { mutableStateOf<PeerDevice?>(null) }
    var showPresenceDialog by remember { mutableStateOf(false) }

    // System File/Media Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && targetPeerForFilePick != null) {
            viewModel.sendFileToPeer(uri, targetPeerForFilePick!!)
            targetPeerForFilePick = null
        }
    }

    if (showPresenceDialog) {
        PresenceSelectionDialog(
            currentPresence = userProfile?.availability ?: UserPresence.AVAILABLE,
            onSelectPresence = {
                viewModel.updatePresence(it)
                showPresenceDialog = false
            },
            onDismiss = { showPresenceDialog = false }
        )
    }

    Scaffold(
        topBar = {
            SpreDropTopBar(
                userProfile = userProfile,
                isOnline = isOnline,
                onPresenceClick = { showPresenceDialog = true },
                onSimulateTransfer = { viewModel.triggerSimulatedIncomingTransfer() }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (discoveredPeers.isNotEmpty()) {
                        targetPeerForFilePick = discoveredPeers.first()
                        filePickerLauncher.launch("*/*")
                    } else {
                        onNavigateToQrPair()
                    }
                },
                containerColor = SpreTealPrimary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Send, contentDescription = null) },
                text = { Text("Send File", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("fab_send_file")
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. User Hero Card
            item {
                UserHeroCard(
                    userProfile = userProfile,
                    isOnline = isOnline,
                    onPresenceClick = { showPresenceDialog = true },
                    onQrClick = onNavigateToQrPair
                )
            }

            // 2. Pending or Active Transfer Requests
            if (activeTransfers.isNotEmpty()) {
                item {
                    Text(
                        text = "Active Transfer Stream",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                items(activeTransfers, key = { it.transferId }) { transfer ->
                    ActiveTransferCard(
                        transfer = transfer,
                        onAccept = { viewModel.acceptIncomingTransfer(transfer) },
                        onDecline = { viewModel.declineIncomingTransfer(transfer.transferId) },
                        onCancel = { viewModel.cancelTransfer(transfer.transferId) },
                        onClick = onNavigateToTransfers
                    )
                }
            }

            // 3. Nearby & Network Discovered Peers (Radar section)
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Nearby SpreDrop Devices",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(SpreCyanAccent.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(SpreCyanAccent, CircleShape)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.refreshDiscovery() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Nearby Devices",
                            tint = SpreTealPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (discoveredPeers.isEmpty()) {
                item {
                    EmptyDiscoveryCard(onScanQr = onNavigateToQrPair)
                }
            } else {
                items(discoveredPeers, key = { it.deviceId }) { peer ->
                    DiscoveredPeerCard(
                        peer = peer,
                        onSendFile = {
                            targetPeerForFilePick = peer
                            filePickerLauncher.launch("*/*")
                        },
                        onAddFriend = {
                            viewModel.sendFriendRequest(peer.spreDropId, peer.displayName)
                        }
                    )
                }
            }

            // 4. Friends Quick Bar
            if (friends.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Friends",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(onClick = onNavigateToFriends) {
                            Text("View all (${friends.size})", color = SpreTealPrimary, fontSize = 13.sp)
                        }
                    }
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(friends, key = { it.userId }) { friend ->
                            FriendQuickAvatarItem(
                                friend = friend,
                                onClick = {
                                    val peer = PeerDevice(
                                        deviceId = friend.userId,
                                        spreDropId = friend.spreDropId,
                                        displayName = friend.displayName,
                                        avatarColorHex = friend.avatarColorHex,
                                        availability = friend.availability,
                                        isFriend = true
                                    )
                                    targetPeerForFilePick = peer
                                    filePickerLauncher.launch("*/*")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserHeroCard(
    userProfile: UserProfile?,
    isOnline: Boolean,
    onPresenceClick: () -> Unit,
    onQrClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserAvatar(
                        name = userProfile?.displayName ?: "User",
                        spreDropId = userProfile?.spreDropId ?: "@user",
                        presence = userProfile?.availability,
                        colorHex = userProfile?.avatarColorHex ?: "#00B4D8",
                        sizeDp = 52
                    )

                    Column {
                        Text(
                            text = userProfile?.displayName ?: "User",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = userProfile?.spreDropId ?: "@spredrop",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SpreTealPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "•",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = userProfile?.deviceModel ?: "Android",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onQrClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpreTealPrimary.copy(alpha = 0.12f))
                        .testTag("home_qr_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "My QR Code",
                        tint = SpreTealPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (isOnline) SpreOnlineGreen else SpreOfflineGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isOnline) "WebRTC P2P Ready" else "Offline (Local only)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onPresenceClick() }
                ) {
                    Text(
                        text = "Visibility:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = userProfile?.visibility?.label ?: "Visible",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpreCyanAccent
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveTransferCard(
    transfer: TransferRecord,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIncoming = transfer.direction == TransferDirection.INCOMING
    val isPending = transfer.status == TransferStatus.PENDING

    Surface(
        color = if (isPending) SpreDarkSurfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPending) SpreCyanAccent.copy(alpha = 0.6f) else SpreTealPrimary.copy(alpha = 0.3f)
        ),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isIncoming) SpreCyanAccent.copy(alpha = 0.15f) else SpreTealPrimary.copy(alpha = 0.15f)
                            )
                    ) {
                        Icon(
                            imageVector = if (isIncoming) Icons.Default.Download else Icons.Default.Upload,
                            contentDescription = null,
                            tint = if (isIncoming) SpreCyanAccent else SpreTealPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = transfer.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isIncoming) "From ${transfer.senderDisplayName} (${transfer.senderSpreDropId})"
                            else "To ${transfer.receiverDisplayName} (${transfer.receiverSpreDropId})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isPending) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Transfer",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isPending) {
                Text(
                    text = "wants to send you this file (${formatFileSize(transfer.fileSize)}). Do you want to accept?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SpreErrorRed),
                        modifier = Modifier.weight(1f).testTag("decline_transfer_button")
                    ) {
                        Text("Decline")
                    }
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = SpreCyanAccent, contentColor = Color.Black),
                        modifier = Modifier.weight(1f).testTag("accept_transfer_button")
                    ) {
                        Text("Accept", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                if (transfer.status == TransferStatus.TRANSFERRING) {
                    AnimatedTransferFlow(
                        progressPercent = transfer.progress,
                        speedString = transfer.formattedSpeed,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Live Progress bar and transfer stats
                LinearProgressIndicator(
                    progress = { transfer.progress },
                    color = SpreTealPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${(transfer.progress * 100).toInt()}% • Chunk ${transfer.chunksTransferred}/${transfer.totalChunks}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = transfer.formattedSpeed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpreTealPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun DiscoveredPeerCard(
    peer: PeerDevice,
    onSendFile: () -> Unit,
    onAddFriend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UserAvatar(
                    name = peer.displayName,
                    spreDropId = peer.spreDropId,
                    presence = peer.availability,
                    colorHex = peer.avatarColorHex,
                    sizeDp = 46
                )

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = peer.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (peer.isFriend) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Friend",
                                tint = SpreAwayYellow,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = peer.spreDropId,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpreTealPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = peer.connectionType.name.replace("_", " "),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "(${peer.signalStrengthRssi} dBm)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!peer.isFriend) {
                    IconButton(
                        onClick = onAddFriend,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Add Friend",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Button(
                    onClick = onSendFile,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpreTealPrimary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("send_file_to_${peer.spreDropId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun EmptyDiscoveryCard(onScanQr: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .padding(8.dp)
            ) {
                AnimatedRadarScanner(isScanning = true)
                SpreDropBrandLogo(sizeDp = 36)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Scanning for Nearby SpreDrop Devices...",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Devices on Wi-Fi, Bluetooth Low Energy, or online will appear automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onScanQr,
                colors = ButtonDefaults.buttonColors(containerColor = SpreTealPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Camera QR Scanner", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FriendQuickAvatarItem(friend: Friend, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable { onClick() }
    ) {
        UserAvatar(
            name = friend.displayName,
            spreDropId = friend.spreDropId,
            presence = friend.availability,
            colorHex = friend.avatarColorHex,
            sizeDp = 50
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = friend.displayName.split(" ").firstOrNull() ?: friend.spreDropId,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PresenceSelectionDialog(
    currentPresence: UserPresence,
    onSelectPresence: (UserPresence) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Discovery & Presence", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple(UserPresence.AVAILABLE, "Available", "Visible to everyone nearby and all friends 🟢"),
                    Triple(UserPresence.FRIENDS_ONLY, "Friends Only", "Only confirmed friends can discover you and send files 👥"),
                    Triple(UserPresence.INVISIBLE, "Invisible", "Stealth mode: Completely hidden from nearby radar 👻")
                ).forEach { (presence, title, desc) ->
                    val isSelected = currentPresence == presence
                    Surface(
                        color = if (isSelected) SpreTealPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(14.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, SpreTealPrimary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPresence(presence) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text(text = presence.emoji, fontSize = 24.sp)
                            Column {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = desc,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}

fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1.0f) String.format("%.1f MB", mb) else "${bytes / 1024} KB"
}
