package com.example.spredrop.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spredrop.security.QrCodeGenerator
import com.example.spredrop.ui.SpreDropViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPairScreen(
    viewModel: SpreDropViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("My QR Code", "Scan QR")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Pairing & Connect", fontWeight = FontWeight.Bold) },
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
                0 -> MyQrCodeTab(
                    userProfile = userProfile,
                    onShareQr = { payload ->
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Connect with me on SpreDrop: $payload")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share SpreDrop ID"))
                    },
                    onCopyId = { id ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SpreDrop ID", id))
                    }
                )
                1 -> ScanQrCodeTab(
                    onCodeScanned = { payload ->
                        viewModel.handleScannedQr(payload)
                    }
                )
            }
        }
    }
}

@Composable
fun MyQrCodeTab(
    userProfile: com.example.spredrop.model.UserProfile?,
    onShareQr: (String) -> Unit,
    onCopyId: (String) -> Unit
) {
    val profile = userProfile ?: return
    val pairPayload = remember(profile.spreDropId, profile.userId, profile.displayName) {
        QrCodeGenerator.createPairPayload(profile.spreDropId, profile.userId, profile.displayName)
    }
    val qrImageBitmap = remember(pairPayload) {
        QrCodeGenerator.generateQrImageBitmap(pairPayload, 512)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Scan to Pair & Connect",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Show this QR code to any nearby device to initiate a direct WebRTC file transfer connection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        // QR Presentation Card
        Surface(
            color = SpreDarkSurface,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(
                2.dp,
                Brush.linearGradient(listOf(SpreTealPrimary, SpreCyanAccent))
            ),
            modifier = Modifier
                .width(300.dp)
                .wrapContentHeight()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp)
            ) {
                // Header in card
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpreTealPrimary)
                    ) {
                        Text("S", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Text(
                        text = "SpreDrop P2P Identity",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Crisp QR Matrix Bitmap
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SpreDarkBg)
                        .padding(8.dp)
                ) {
                    Image(
                        bitmap = qrImageBitmap,
                        contentDescription = "SpreDrop QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = profile.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = profile.spreDropId,
                    color = SpreTealPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = profile.deviceModel,
                    color = SpreDarkTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { onCopyId(profile.spreDropId) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("copy_id_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy ID")
            }

            Button(
                onClick = { onShareQr(pairPayload) },
                colors = ButtonDefaults.buttonColors(containerColor = SpreTealPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("share_qr_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Code")
            }
        }
    }
}

@Composable
fun ScanQrCodeTab(
    onCodeScanned: (String) -> Unit
) {
    var manualInput by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Camera & Code Scanner",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Point camera at a SpreDrop QR code, or type/paste a SpreDrop ID below to pair immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        // Viewfinder Scanner Mock Box with dynamic scanning laser
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SpreDarkBg)
                .border(2.dp, SpreTealPrimary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
        ) {
            // Viewfinder crosshair corners
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .border(2.dp, SpreCyanAccent, RoundedCornerShape(8.dp))
            )
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = SpreCyanAccent.copy(alpha = 0.7f),
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "Scanning for SpreDrop QR...",
                color = SpreDarkTextMuted,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick demo QR test triggers
        Text(
            text = "Quick Connect Presets",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { onCodeScanned("@priya") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("@priya", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { onCodeScanned("@alex") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("@alex", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { onCodeScanned("@elena") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("@elena", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Manual ID or Payload Input
        OutlinedTextField(
            value = manualInput,
            onValueChange = { manualInput = it },
            label = { Text("Paste SpreDrop URL or @ID") },
            placeholder = { Text("e.g. @rahul or spredrop://pair?...") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (manualInput.isNotBlank()) {
                    onCodeScanned(manualInput.trim())
                    manualInput = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = SpreCyanAccent, contentColor = Color.Black),
            enabled = manualInput.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pair_scanned_button")
        ) {
            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Pair Device Now", fontWeight = FontWeight.Bold)
        }
    }
}
