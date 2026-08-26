package com.example.spredrop.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spredrop.model.*
import com.example.spredrop.ui.SpreDropViewModel
import com.example.spredrop.ui.components.UserAvatar
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: SpreDropViewModel,
    modifier: Modifier = Modifier
) {
    val friends by viewModel.friends.collectAsState()
    val incomingRequests by viewModel.incomingRequests.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var targetFriendForFile by remember { mutableStateOf<Friend?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && targetFriendForFile != null) {
            val peer = PeerDevice(
                deviceId = targetFriendForFile!!.userId,
                spreDropId = targetFriendForFile!!.spreDropId,
                displayName = targetFriendForFile!!.displayName,
                avatarColorHex = targetFriendForFile!!.avatarColorHex,
                availability = targetFriendForFile!!.availability,
                isFriend = true
            )
            viewModel.sendFileToPeer(uri, peer)
            targetFriendForFile = null
        }
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            onSendRequest = { id, name ->
                viewModel.sendFriendRequest(id, name)
                showAddFriendDialog = false
            },
            onDismiss = { showAddFriendDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends & Contacts", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = { showAddFriendDialog = true },
                        modifier = Modifier.testTag("add_friend_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Friend", tint = SpreTealPrimary)
                    }
                },
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
            // Search field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by @spreDropId or Name") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpreTealPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("friends_search_field")
                )
            }

            // Incoming Requests Section
            if (incomingRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "Friend Requests (${incomingRequests.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpreCyanAccent
                    )
                }

                items(incomingRequests, key = { it.userId }) { request ->
                    FriendRequestCard(
                        friend = request,
                        onAccept = { viewModel.acceptFriendRequest(request.userId) },
                        onReject = { viewModel.rejectFriendRequest(request.userId) }
                    )
                }
            }

            // Connected Friends List
            item {
                Text(
                    text = "Connected Friends (${friends.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            val filteredFriends = friends.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.spreDropId.contains(searchQuery, ignoreCase = true)
            }

            if (filteredFriends.isEmpty()) {
                item {
                    EmptyFriendsCard(onAddFriend = { showAddFriendDialog = true })
                }
            } else {
                items(filteredFriends, key = { it.userId }) { friend ->
                    FriendItemCard(
                        friend = friend,
                        onSendFile = {
                            targetFriendForFile = friend
                            filePickerLauncher.launch("*/*")
                        },
                        onRemove = { viewModel.removeFriend(friend.userId) },
                        onBlock = { viewModel.blockUser(friend.userId) }
                    )
                }
            }
        }
    }
}

@Composable
fun FriendRequestCard(
    friend: Friend,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, SpreCyanAccent.copy(alpha = 0.5f)),
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UserAvatar(
                    name = friend.displayName,
                    spreDropId = friend.spreDropId,
                    colorHex = friend.avatarColorHex,
                    sizeDp = 44
                )
                Column {
                    Text(
                        text = friend.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = friend.spreDropId,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpreTealPrimary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SpreErrorRed),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Decline", fontSize = 12.sp)
                }
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = SpreCyanAccent, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FriendItemCard(
    friend: Friend,
    onSendFile: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

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
                UserAvatar(
                    name = friend.displayName,
                    spreDropId = friend.spreDropId,
                    presence = friend.availability,
                    colorHex = friend.avatarColorHex,
                    sizeDp = 46
                )

                Column {
                    Text(
                        text = friend.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = friend.spreDropId,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpreTealPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${friend.availability.emoji} ${friend.availability.label} • ${friend.mutualFilesCount} transfers",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onSendFile,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpreTealPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send", fontSize = 12.sp)
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Remove Friend") },
                            onClick = {
                                showMenu = false
                                onRemove()
                            },
                            leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Block User", color = SpreErrorRed) },
                            onClick = {
                                showMenu = false
                                onBlock()
                            },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = SpreErrorRed) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyFriendsCard(onAddFriend: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.People,
                contentDescription = null,
                tint = SpreTealPrimary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No friends added yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add friends using their unique SpreDrop @ID or by scanning their QR code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onAddFriend,
                colors = ButtonDefaults.buttonColors(containerColor = SpreTealPrimary)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Friend")
            }
        }
    }
}

@Composable
fun AddFriendDialog(
    onSendRequest: (spreDropId: String, displayName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var spreDropId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add SpreDrop Friend", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the user's SpreDrop ID to establish a trusted P2P transfer connection.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = spreDropId,
                    onValueChange = { spreDropId = it },
                    label = { Text("SpreDrop ID (e.g. @username)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name (optional)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (spreDropId.isNotBlank()) {
                        onSendRequest(spreDropId.trim(), displayName.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpreTealPrimary),
                enabled = spreDropId.isNotBlank()
            ) {
                Text("Send Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
