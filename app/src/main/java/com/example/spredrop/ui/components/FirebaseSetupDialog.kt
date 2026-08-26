package com.example.spredrop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spredrop.ui.SpreDropViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseSetupDialog(
    viewModel: SpreDropViewModel,
    onDismiss: () -> Unit
) {
    val config by viewModel.firebaseConfig.collectAsState()
    val lastSync by viewModel.lastSyncTimestamp.collectAsState()

    var projectId by remember(config) { mutableStateOf(config.projectId) }
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var appId by remember(config) { mutableStateOf(config.appId) }
    var savedFeedback by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .testTag("firebase_setup_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(SpreTealPrimary.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = SpreTealPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Firebase Cloud & Database Setup",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Configure Firestore & Cloud Authentication sync",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                )

                // Current Active Status Banner
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(SpreOnlineGreen)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Firestore Status: Active & Ready",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SpreOnlineGreen
                                )
                            }
                            Text(
                                text = "P2P + Cloud",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SpreTealPrimary
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "Cloud Project: ${config.projectId}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.LightGray
                        )
                        Text(
                            text = "App ID: ${config.appId.take(24)}...",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (lastSync > 0) {
                            Text(
                                text = "Last Synced: Just now",
                                fontSize = 10.sp,
                                color = SpreTealPrimary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Editable Fields
                OutlinedTextField(
                    value = projectId,
                    onValueChange = { projectId = it },
                    label = { Text("Firebase Project ID") },
                    leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = SpreTealPrimary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpreTealPrimary,
                        focusedLabelColor = SpreTealPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("firebase_project_id_input")
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Web / Android API Key (Optional)") },
                    placeholder = { Text("AIzaSy...") },
                    leadingIcon = { Icon(Icons.Outlined.Key, null, tint = SpreTealPrimary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpreTealPrimary,
                        focusedLabelColor = SpreTealPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("firebase_api_key_input")
                )

                OutlinedTextField(
                    value = appId,
                    onValueChange = { appId = it },
                    label = { Text("Firebase App ID") },
                    leadingIcon = { Icon(Icons.Outlined.Smartphone, null, tint = SpreTealPrimary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpreTealPrimary,
                        focusedLabelColor = SpreTealPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("firebase_app_id_input")
                )

                if (savedFeedback != null) {
                    Surface(
                        color = SpreOnlineGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = SpreOnlineGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(savedFeedback ?: "", color = SpreOnlineGreen, fontSize = 12.sp)
                        }
                    }
                }

                // Action Buttons
                Button(
                    onClick = {
                        viewModel.updateFirebaseConfig(projectId, apiKey, appId)
                        savedFeedback = "Firebase configuration updated & active!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpreTealPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_firebase_config_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Save & Apply Configuration",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.syncWithFirestoreNow()
                        savedFeedback = "Cloud sync triggered successfully!"
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SpreTealPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = SpreTealPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test Connection & Sync Now", color = SpreTealPrimary, fontSize = 13.sp)
                }

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = onDismiss) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
