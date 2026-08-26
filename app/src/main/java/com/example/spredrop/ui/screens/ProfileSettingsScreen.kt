package com.example.spredrop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spredrop.data.firebase.AuthState
import com.example.spredrop.data.firebase.FirestoreConnectionState
import com.example.spredrop.model.*
import com.example.spredrop.ui.SpreDropViewModel
import com.example.spredrop.ui.components.FirebaseAuthDialog
import com.example.spredrop.ui.components.UserAvatar
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    viewModel: SpreDropViewModel,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()
    val devLogs by viewModel.devLogs.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val firestoreState by viewModel.firestoreConnectionState.collectAsState()
    val lastSyncTime by viewModel.lastSyncTimestamp.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showFirebaseSetupDialog by remember { mutableStateOf(false) }

    if (showFirebaseSetupDialog) {
        com.example.spredrop.ui.components.FirebaseSetupDialog(
            viewModel = viewModel,
            onDismiss = { showFirebaseSetupDialog = false }
        )
    }

    if (showAuthDialog) {
        FirebaseAuthDialog(
            viewModel = viewModel,
            onDismiss = { showAuthDialog = false }
        )
    }

    if (showEditProfileDialog && userProfile != null) {
        EditProfileDialog(
            currentDisplayName = userProfile!!.displayName,
            currentSpreDropId = userProfile!!.spreDropId,
            onSave = { newId, newName ->
                viewModel.updateIdentity(newId, newName)
                showEditProfileDialog = false
            },
            onDismiss = { showEditProfileDialog = false }
        )
    }

    if (showLogsDialog) {
        DevLogsDialog(
            logs = devLogs,
            onClear = { viewModel.clearDevLogs() },
            onDismiss = { showLogsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Profile Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                UserAvatar(
                                    name = userProfile?.displayName ?: "Rahul",
                                    spreDropId = userProfile?.spreDropId ?: "@rahul",
                                    presence = userProfile?.availability,
                                    sizeDp = 58
                                )

                                Column {
                                    Text(
                                        text = userProfile?.displayName ?: "Rahul Sharma",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = userProfile?.spreDropId ?: "@rahul",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SpreTealPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = userProfile?.deviceModel ?: "Android",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showEditProfileDialog = true },
                                modifier = Modifier.testTag("edit_profile_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = SpreTealPrimary)
                            }
                        }
                    }
                }
            }

            // Firebase Authentication & Cloud Database Section
            item {
                Text(
                    text = "Firebase Cloud & Database",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(SpreTealPrimary.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        tint = SpreTealPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Project: spredrop",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "ID: spredrop • #947368133167",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Connection Badge
                            Surface(
                                color = SpreTealPrimary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(SpreTealPrimary)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Firestore Active",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SpreTealPrimary
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Auth User Info
                        val firebaseUser = (authState as? AuthState.Authenticated)?.user
                        if (firebaseUser != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(
                                        text = firebaseUser.email ?: firebaseUser.displayName ?: "Authenticated User",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "UID: ${firebaseUser.uid.take(12)}...",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TextButton(
                                    onClick = { viewModel.signOut() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Sign Out", fontSize = 13.sp)
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text("Not signed in to Cloud Auth", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Sign in to sync friends & profile across devices", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Buttons row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { showAuthDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = SpreTealPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("manage_firebase_auth_button")
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF0F172A))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (firebaseUser != null) "Manage Auth" else "Sign In / Register",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            OutlinedButton(
                                onClick = { showFirebaseSetupDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SpreTealPrimary.copy(alpha = 0.6f)),
                                modifier = Modifier.weight(1f).testTag("configure_firebase_button")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = SpreTealPrimary)
                                Spacer(Modifier.width(6.dp))
                                Text("Configure Cloud", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Privacy & Discovery Section
            item {
                Text(
                    text = "Privacy & Discovery Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            PrivacyMode.VISIBLE to ("Visible to Everyone" to "You appear in nearby radar and friend discovery"),
                            PrivacyMode.FRIENDS_ONLY to ("Friends Only" to "Only confirmed friends can discover you"),
                            PrivacyMode.INVISIBLE to ("Invisible" to "Hidden from all public discovery feeds")
                        ).forEach { (mode, pair) ->
                            val (title, subtitle) = pair
                            val isSelected = userProfile?.visibility == mode

                            Surface(
                                color = if (isSelected) SpreTealPrimary.copy(alpha = 0.12f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, SpreTealPrimary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updatePrivacy(mode) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) SpreTealPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = subtitle,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.updatePrivacy(mode) },
                                        colors = RadioButtonDefaults.colors(selectedColor = SpreTealPrimary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Storage Section
            item {
                Text(
                    text = "Storage & Cache",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Received Files Space", fontSize = 14.sp)
                            Text(
                                text = formatFileSize(storageStats.usedBytesBySpreDrop),
                                fontWeight = FontWeight.Bold,
                                color = SpreTealPrimary
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Total Files Saved", fontSize = 14.sp)
                            Text(
                                text = "${storageStats.totalFilesReceived} files",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Available Device Storage", fontSize = 14.sp)
                            Text(
                                text = formatFileSize(storageStats.availableDeviceBytes),
                                fontWeight = FontWeight.Bold,
                                color = SpreCyanAccent
                            )
                        }
                    }
                }
            }

            // Developer & Diagnostics Section
            item {
                Text(
                    text = "Diagnostics & WebRTC Telemetry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("WebRTC & Signaling Logs", fontWeight = FontWeight.SemiBold)
                                Text("${devLogs.size} recent events recorded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = { showLogsDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = SpreTealDark),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View Logs")
                            }
                        }
                    }
                }
            }

            // Architecture Privacy Guarantee Card
            item {
                Surface(
                    color = SpreDarkSurfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SpreCyanAccent.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = SpreCyanAccent, modifier = Modifier.size(20.dp))
                            Text(
                                text = "SpreDrop Privacy Notice",
                                fontWeight = FontWeight.Bold,
                                color = SpreCyanAccent,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "SpreDrop transfers files directly between peer devices using WebRTC DataChannels with SHA-256 chunk verification. SpreDrop does not permanently store your transferred files on cloud servers.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    currentDisplayName: String,
    currentSpreDropId: String,
    onSave: (spreDropId: String, displayName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var displayName by remember { mutableStateOf(currentDisplayName) }
    var spreDropId by remember { mutableStateOf(currentSpreDropId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit SpreDrop Identity", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = spreDropId,
                    onValueChange = { spreDropId = it },
                    label = { Text("SpreDrop ID") },
                    placeholder = { Text("@username") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(spreDropId.trim(), displayName.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = SpreTealPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DevLogsDialog(
    logs: List<DevLogEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Signaling & Transfer Logs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        text = {
            Surface(
                color = SpreDarkBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("No logs recorded yet", color = SpreDarkTextMuted, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(logs, key = { it.id }) { log ->
                            val tagColor = when (log.level) {
                                "WEBRTC" -> SpreCyanAccent
                                "SIGNAL" -> SpreTealPrimary
                                "ERROR" -> SpreErrorRed
                                "INTEGRITY" -> SpreAwayYellow
                                else -> SpreDarkTextMuted
                            }
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateFormat.format(Date(log.timestamp)),
                                        color = SpreDarkTextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "[${log.tag}]",
                                        color = tagColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = log.message,
                                    color = SpreDarkText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
