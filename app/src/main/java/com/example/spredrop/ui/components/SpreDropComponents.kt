package com.example.spredrop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spredrop.model.*
import com.example.ui.theme.*

@Composable
fun PresenceStatusPill(
    presence: UserPresence,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val statusColor = when (presence) {
        UserPresence.ONLINE, UserPresence.AVAILABLE -> SpreOnlineGreen
        UserPresence.AWAY -> SpreAwayYellow
        UserPresence.TRANSFERRING -> SpreTransferBlue
        UserPresence.CONNECTING -> SpreAwayYellow
        UserPresence.INVISIBLE, UserPresence.OFFLINE -> SpreOfflineGray
    }

    Surface(
        color = statusColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Text(
                text = presence.label,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Change Presence",
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun UserAvatar(
    name: String,
    spreDropId: String,
    presence: UserPresence? = null,
    colorHex: String = "#00B4D8",
    sizeDp: Int = 44,
    modifier: Modifier = Modifier
) {
    val avatarColor = try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        SpreTealPrimary
    }

    val initials = name.split(" ").filter { it.isNotBlank() }.map { it.first() }.take(2).joinToString("")
        .ifBlank { spreDropId.removePrefix("@").take(1).uppercase() }

    Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = modifier.size(sizeDp.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(avatarColor, avatarColor.copy(alpha = 0.7f))
                    )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
        ) {
            Text(
                text = initials.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (sizeDp * 0.38).sp
            )
        }

        if (presence != null) {
            val statusColor = when (presence) {
                UserPresence.ONLINE, UserPresence.AVAILABLE -> SpreOnlineGreen
                UserPresence.AWAY -> SpreAwayYellow
                UserPresence.TRANSFERRING -> SpreTransferBlue
                UserPresence.CONNECTING -> SpreAwayYellow
                UserPresence.INVISIBLE, UserPresence.OFFLINE -> SpreOfflineGray
            }
            Box(
                modifier = Modifier
                    .size((sizeDp * 0.3).dp.coerceAtLeast(10.dp))
                    .clip(CircleShape)
                    .background(statusColor)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun RadarPulseEffect(
    isScanning: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_alpha"
    )

    if (isScanning) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(SpreTealPrimary.copy(alpha = alpha))
        )
    }
}

@Composable
fun SpreDropTopBar(
    userProfile: UserProfile?,
    isOnline: Boolean,
    onPresenceClick: () -> Unit,
    onSimulateTransfer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Brand Logo & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(SpreTealPrimary, SpreTealDark)
                            )
                        )
                ) {
                    Text(
                        text = "S",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "SpreDrop",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (isOnline) SpreOnlineGreen else SpreOfflineGray, CircleShape)
                        )
                    }
                    Text(
                        text = userProfile?.spreDropId ?: "@spredrop",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpreTealPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Right Action Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Presence Pill
                userProfile?.let {
                    PresenceStatusPill(
                        presence = it.availability,
                        onClick = onPresenceClick,
                        modifier = Modifier.testTag("presence_pill_button")
                    )
                }

                // Radar Scan / Refresh Quick Action
                IconButton(
                    onClick = onSimulateTransfer,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("refresh_radar_discovery_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Scan Nearby SpreDrop Devices",
                        tint = SpreCyanAccent
                    )
                }
            }
        }
    }
}
