package com.example.spredrop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spredrop.model.*
import com.example.spredrop.ui.SpreDropViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    viewModel: SpreDropViewModel,
    modifier: Modifier = Modifier
) {
    val activeTransfers by viewModel.activeTransfers.collectAsState()
    val receivedFiles by viewModel.receivedFiles.collectAsState()
    val completedTransfers by viewModel.completedTransfers.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Active (${activeTransfers.size})", "Received (${receivedFiles.size})", "History")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("File Transfers", fontWeight = FontWeight.Bold)
                },
                actions = {
                    if (selectedTabIndex == 2 && completedTransfers.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = SpreTealPrimary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> ActiveTransfersTab(
                    activeTransfers = activeTransfers,
                    onAccept = { viewModel.acceptIncomingTransfer(it) },
                    onDecline = { viewModel.declineIncomingTransfer(it.transferId) },
                    onCancel = { viewModel.cancelTransfer(it.transferId) }
                )
                1 -> ReceivedFilesTab(
                    receivedFiles = receivedFiles,
                    onOpenFile = { if (it.localFilePath != null) viewModel.openFile(it.localFilePath) },
                    onShareFile = { if (it.localFilePath != null) viewModel.shareFile(it.localFilePath) },
                    onDeleteFile = { viewModel.deleteReceivedFile(it.transferId) }
                )
                2 -> TransferHistoryTab(
                    completedTransfers = completedTransfers,
                    onClearHistory = { viewModel.clearHistory() }
                )
            }
        }
    }
}

@Composable
fun ActiveTransfersTab(
    activeTransfers: List<TransferRecord>,
    onAccept: (TransferRecord) -> Unit,
    onDecline: (TransferRecord) -> Unit,
    onCancel: (TransferRecord) -> Unit
) {
    if (activeTransfers.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.SyncAlt,
                    contentDescription = null,
                    tint = SpreTealPrimary.copy(alpha = 0.6f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No active file transfers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Files currently being transferred via WebRTC DataChannel will appear here in real-time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(activeTransfers, key = { it.transferId }) { transfer ->
                ActiveTransferDetailCard(
                    transfer = transfer,
                    onAccept = { onAccept(transfer) },
                    onDecline = { onDecline(transfer) },
                    onCancel = { onCancel(transfer) }
                )
            }
        }
    }
}

@Composable
fun ActiveTransferDetailCard(
    transfer: TransferRecord,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit
) {
    val isIncoming = transfer.direction == TransferDirection.INCOMING
    val isPending = transfer.status == TransferStatus.PENDING

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isIncoming) SpreCyanAccent.copy(alpha = 0.15f) else SpreTealPrimary.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = getFileIcon(transfer.fileName),
                            contentDescription = null,
                            tint = if (isIncoming) SpreCyanAccent else SpreTealPrimary,
                            modifier = Modifier.size(24.dp)
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
                            text = if (isIncoming) "Incoming from ${transfer.senderDisplayName} (${transfer.senderSpreDropId})"
                            else "Sending to ${transfer.receiverDisplayName} (${transfer.receiverSpreDropId})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isPending) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isPending) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SpreErrorRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Decline")
                    }
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = SpreCyanAccent, contentColor = Color.Black),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Accept Transfer", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LinearProgressIndicator(
                    progress = { transfer.progress },
                    color = SpreTealPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Status: ${transfer.status.label}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "WebRTC DataChannel",
                        fontSize = 11.sp,
                        color = SpreCyanAccent
                    )
                }
            }
        }
    }
}

@Composable
fun ReceivedFilesTab(
    receivedFiles: List<TransferRecord>,
    onOpenFile: (TransferRecord) -> Unit,
    onShareFile: (TransferRecord) -> Unit,
    onDeleteFile: (TransferRecord) -> Unit
) {
    if (receivedFiles.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = SpreTealPrimary.copy(alpha = 0.6f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No received files yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Transferred photos, videos, and documents will be saved directly on this device with SHA-256 integrity verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(receivedFiles, key = { it.transferId }) { file ->
                ReceivedFileCard(
                    file = file,
                    onOpen = { onOpenFile(file) },
                    onShare = { onShareFile(file) },
                    onDelete = { onDeleteFile(file) }
                )
            }
        }
    }
}

@Composable
fun ReceivedFileCard(
    file: TransferRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpreCyanAccent.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = getFileIcon(file.fileName),
                        contentDescription = null,
                        tint = SpreCyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatFileSize(file.fileSize),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(text = "•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = file.senderDisplayName,
                            fontSize = 12.sp,
                            color = SpreTealPrimary
                        )
                    }
                    if (file.verifiedChecksum) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = SpreOnlineGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "SHA-256 verified",
                                fontSize = 10.sp,
                                color = SpreOnlineGreen
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Launch, contentDescription = "Open File", tint = SpreTealPrimary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share File", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete File", tint = SpreErrorRed.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun TransferHistoryTab(
    completedTransfers: List<TransferRecord>,
    onClearHistory: () -> Unit
) {
    if (completedTransfers.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Text(
                text = "No transfer history yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(completedTransfers, key = { it.transferId }) { transfer ->
                val isIncoming = transfer.direction == TransferDirection.INCOMING
                val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isIncoming) SpreCyanAccent.copy(alpha = 0.15f) else SpreTealPrimary.copy(alpha = 0.15f)
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = if (isIncoming) SpreCyanAccent else SpreTealPrimary,
                                    modifier = Modifier.size(18.dp)
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

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatFileSize(transfer.fileSize),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dateFormat.format(Date(transfer.timestamp)),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getFileIcon(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = fileName.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "heic" -> Icons.Default.Image
        "mp4", "mkv", "avi", "mov" -> Icons.Default.Movie
        "mp3", "wav", "flac", "m4a" -> Icons.Default.MusicNote
        "pdf" -> Icons.Default.PictureAsPdf
        "zip", "rar", "7z", "tar" -> Icons.Default.FolderZip
        "txt", "doc", "docx" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}
