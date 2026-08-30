@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.spredrop.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import android.net.Uri
import android.provider.Settings
import com.example.spredrop.model.UserProfile
import com.example.spredrop.model.UserPresence
import com.example.spredrop.security.QrCodeGenerator
import com.example.spredrop.ui.SpreDropViewModel
import com.example.spredrop.ui.components.AnimatedCameraLaserScanner
import com.example.spredrop.ui.components.SpreDropBrandLogo
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPairScreen(
    viewModel: SpreDropViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("My QR Code", "Scan QR")

    var scannedPeerResult by remember { mutableStateOf<com.example.spredrop.security.ParsedPairData?>(null) }
    var showScanResultDialog by remember { mutableStateOf(false) }
    var targetPeerForFilePick by remember { mutableStateOf<com.example.spredrop.model.PeerDevice?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null && targetPeerForFilePick != null) {
            viewModel.sendFileToPeer(uri, targetPeerForFilePick!!)
            targetPeerForFilePick = null
        }
    }

    if (showScanResultDialog && scannedPeerResult != null) {
        val result = scannedPeerResult!!
        AlertDialog(
            onDismissRequest = { showScanResultDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = SpreTealPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SpreDrop Connect",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = "You successfully paired with:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(SpreTealPrimary, CircleShape)
                            ) {
                                Text(
                                    text = result.displayName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = result.displayName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = result.spreDropId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Initiate a high-speed direct peer-to-peer file transfer or add them as a friend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        targetPeerForFilePick = com.example.spredrop.model.PeerDevice(
                            deviceId = result.userId,
                            spreDropId = result.spreDropId,
                            displayName = result.displayName,
                            avatarColorHex = "#00B4D8",
                            availability = UserPresence.AVAILABLE,
                            isFriend = true,
                            connectionType = com.example.spredrop.model.PeerConnectionType.DIRECT_P2P,
                            signalStrengthRssi = -30,
                            ipAddress = "Direct/QR"
                        )
                        showScanResultDialog = false
                        filePickerLauncher.launch("*/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpreTealPrimary)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send File")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.sendFriendRequest(result.spreDropId, result.displayName)
                            showScanResultDialog = false
                        }
                    ) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Friend")
                    }
                    TextButton(
                        onClick = { showScanResultDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Pairing & Connect", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("qr_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Radar"
                            )
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
                    viewModel = viewModel,
                    onCodeScanned = { payload ->
                        val parsed = QrCodeGenerator.parsePairUri(payload)
                        if (parsed != null) {
                            scannedPeerResult = parsed
                            showScanResultDialog = true
                        }
                        viewModel.handleScannedQr(payload)
                    }
                )
            }
        }
    }
}

@Composable
fun MyQrCodeTab(
    userProfile: UserProfile?,
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
                // Header with unified brand logo
                SpreDropBrandLogo(
                    sizeDp = 30,
                    showText = true,
                    subtitle = "P2P Wireless Identity",
                    modifier = Modifier.padding(bottom = 14.dp)
                )

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

@OptIn(ExperimentalGetImage::class)
@Composable
fun ScanQrCodeTab(
    viewModel: SpreDropViewModel,
    onCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var manualInput by remember { mutableStateOf("") }
    val checkResult by viewModel.usernameCheckResult.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var permissionDeniedByUser by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionDeniedByUser = !granted
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Camera & QR Code Scanner",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Point your back camera at any SpreDrop QR code, or enter a SpreDrop ID below to pair immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        // Live Back Camera Viewfinder with animated laser scan line
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(2.dp, SpreTealPrimary, RoundedCornerShape(24.dp))
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                val scanner = BarcodeScanning.getClient()
                                var hasScanned = false

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null && !hasScanned) {
                                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                        scanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                     val rawValue = barcode.rawValue
                                                     if (!rawValue.isNullOrBlank() && !hasScanned) {
                                                         hasScanned = true
                                                         onCodeScanned(rawValue)
                                                         break
                                                     }
                                                }
                                            }
                                            .addOnFailureListener { exc ->
                                                Log.e("QrScanner", "Barcode scan failure", exc)
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (exc: Exception) {
                                Log.e("QrScanner", "Use case binding failed", exc)
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Viewfinder guide corners
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .border(2.5.dp, SpreCyanAccent, RoundedCornerShape(12.dp))
                )

                // High-tech laser sweep animation
                AnimatedCameraLaserScanner(modifier = Modifier.size(200.dp))

                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(SpreOnlineGreen, CircleShape))
                        Text(
                            text = "Back Camera Active",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(18.dp)
                ) {
                    Icon(
                        imageVector = if (permissionDeniedByUser) Icons.Default.CameraEnhance else Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = if (permissionDeniedByUser) MaterialTheme.colorScheme.error else SpreTealPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (permissionDeniedByUser) "Camera Access Blocked" else "Camera Permission Required",
                        color = if (permissionDeniedByUser) MaterialTheme.colorScheme.error else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (permissionDeniedByUser) {
                            "To respect your decision, we won't show system permission requests anymore. If you want to use the scanner, enable camera access in app settings."
                        } else {
                            "Enable camera access to scan QR codes instantly."
                        },
                        color = SpreDarkTextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    if (permissionDeniedByUser) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("QrScanner", "Could not open app settings", e)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("open_settings_permission_button")
                        ) {
                            Text("Open App Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = SpreTealPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("grant_camera_permission_button")
                        ) {
                            Text("Open Camera", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Manual SpreDrop ID or Payload Input
        Text(
            text = "Or Connect via SpreDrop ID",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = manualInput,
            onValueChange = { 
                manualInput = it 
                viewModel.clearUsernameCheck()
            },
            label = { Text("Enter SpreDrop @ID or QR text") },
            placeholder = { Text("e.g. @rahul or spredrop://pair?...") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Show live check feedback
        if (checkResult != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.Start)
            ) {
                when (checkResult) {
                    "checking" -> {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = SpreTealPrimary)
                        Text("Verifying user exists...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    "exists" -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SpreOnlineGreen, modifier = Modifier.size(16.dp))
                        Text("User exists - Friend request sent!", color = SpreOnlineGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    "not_found" -> {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Text("User does not exist!", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    "error" -> {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = SpreAwayYellow, modifier = Modifier.size(16.dp))
                        Text("Verification failed. Check network.", color = SpreAwayYellow, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (manualInput.isNotBlank()) {
                    val trimmed = manualInput.trim()
                    if (!trimmed.startsWith("spredrop://") && !trimmed.contains("?")) {
                        // It is a plain username / ID! Verify exists and send friend request
                        viewModel.checkAndSendFriendRequest(trimmed)
                    } else {
                        // It's a full pair QR URI
                        onCodeScanned(trimmed)
                        manualInput = ""
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = SpreTealPrimary, contentColor = Color.White),
            enabled = manualInput.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pair_scanned_button")
        ) {
            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Friend / Pair Now", fontWeight = FontWeight.Bold)
        }
    }
}
