package com.example.spredrop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spredrop.model.*
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unified SpreDrop Brand Logo used consistently throughout the application
 */
@Composable
fun SpreDropBrandLogo(
    sizeDp: Int = 40,
    showText: Boolean = false,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.90f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        val rotationWiggle by infiniteTransition.animateFloat(
            initialValue = -10f,
            targetValue = 10f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "wiggle"
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape((sizeDp * 0.28).dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF00E5FF), // Electric Cyan
                            Color(0xFF00B4D8), // Spre Teal Primary
                            Color(0xFF0077B6)  // Deep Oceanic Blue
                        )
                    )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape((sizeDp * 0.28).dp))
        ) {
            Canvas(
                modifier = Modifier
                    .size((sizeDp * 0.62f).dp)
                    .graphicsLayer(
                        scaleX = pulseScale,
                        scaleY = pulseScale,
                        rotationZ = rotationWiggle
                    )
            ) {
                val w = size.width
                val h = size.height
                
                val sPath = Path().apply {
                    // Top loop right start
                    moveTo(w * 0.78f, h * 0.24f)
                    // Top loop back to left
                    cubicTo(
                        w * 0.78f, h * 0.08f,
                        w * 0.22f, h * 0.08f,
                        w * 0.22f, h * 0.36f
                    )
                    // Flowing downward middle connection
                    cubicTo(
                        w * 0.22f, h * 0.58f,
                        w * 0.78f, h * 0.42f,
                        w * 0.78f, h * 0.64f
                    )
                    // Bottom loop to left-end
                    cubicTo(
                        w * 0.78f, h * 0.92f,
                        w * 0.22f, h * 0.92f,
                        w * 0.22f, h * 0.76f
                    )
                }
                
                // Outer subtle white glow
                drawPath(
                    path = sPath,
                    color = Color.White.copy(alpha = 0.25f),
                    style = Stroke(
                        width = w * 0.24f,
                        cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
                
                // Dynamic inner white stroke
                drawPath(
                    path = sPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFE0F7FA),
                            Color(0xFF80DEEA)
                        )
                    ),
                    style = Stroke(
                        width = w * 0.16f,
                        cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }

        if (showText) {
            Column {
                Text(
                    text = "SpreDrop",
                    style = if (sizeDp > 48) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.5).sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpreTealPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun PresenceStatusPill(
    presence: UserPresence,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val statusColor = when (presence) {
        UserPresence.ONLINE, UserPresence.AVAILABLE -> SpreOnlineGreen
        UserPresence.FRIENDS_ONLY -> SpreCyanAccent
        UserPresence.TRANSFERRING -> SpreTransferBlue
        UserPresence.CONNECTING -> SpreCyanAccent
        UserPresence.INVISIBLE, UserPresence.OFFLINE -> SpreOfflineGray
    }

    Surface(
        color = statusColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
                fontWeight = FontWeight.Bold
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
                UserPresence.FRIENDS_ONLY -> SpreCyanAccent
                UserPresence.TRANSFERRING -> SpreTransferBlue
                UserPresence.CONNECTING -> SpreCyanAccent
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

/**
 * Animated High-Tech Sweeping Radar Animation for peer discovery
 */
@Composable
fun AnimatedRadarScanner(
    isScanning: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_scanner")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_1"
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_2"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val maxRadius = minOf(centerX, centerY)

        // Concentric guide rings
        drawCircle(
            color = Color(0xFF00B4D8).copy(alpha = 0.12f),
            radius = maxRadius * 0.33f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF00B4D8).copy(alpha = 0.15f),
            radius = maxRadius * 0.66f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF00B4D8).copy(alpha = 0.2f),
            radius = maxRadius * 0.95f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx())
        )

        // Crosshairs
        drawLine(
            color = Color(0xFF00B4D8).copy(alpha = 0.18f),
            start = Offset(centerX - maxRadius, centerY),
            end = Offset(centerX + maxRadius, centerY),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color(0xFF00B4D8).copy(alpha = 0.18f),
            start = Offset(centerX, centerY - maxRadius),
            end = Offset(centerX, centerY + maxRadius),
            strokeWidth = 1.dp.toPx()
        )

        if (isScanning) {
            // Ripple wave 1
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = (1f - wave1) * 0.4f),
                radius = maxRadius * wave1,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.dp.toPx())
            )

            // Ripple wave 2
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = (1f - wave2) * 0.4f),
                radius = maxRadius * wave2,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.dp.toPx())
            )

            // Rotating sweep beam
            val radians = Math.toRadians(sweepAngle.toDouble())
            val beamEndX = centerX + (maxRadius * 0.95f * cos(radians)).toFloat()
            val beamEndY = centerY + (maxRadius * 0.95f * sin(radians)).toFloat()

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00E5FF), Color(0xFF00B4D8).copy(alpha = 0f)),
                    start = Offset(centerX, centerY),
                    end = Offset(beamEndX, beamEndY)
                ),
                start = Offset(centerX, centerY),
                end = Offset(beamEndX, beamEndY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Animated Laser Line Scanner for QR Camera Viewfinder
 */
@Composable
fun AnimatedCameraLaserScanner(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_scanner")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val y = size.height * laserPosition
        // Laser glow
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF00E5FF).copy(alpha = 0.35f),
                    Color(0xFF00E5FF),
                    Color(0xFF00E5FF).copy(alpha = 0.35f),
                    Color.Transparent
                )
            ),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Intense central core
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.8f),
                    Color.White,
                    Color.White.copy(alpha = 0.8f),
                    Color.Transparent
                )
            ),
            start = Offset(size.width * 0.15f, y),
            end = Offset(size.width * 0.85f, y),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * Animated Particle Flow for Active File Transfer
 */
@Composable
fun AnimatedTransferFlow(
    progressPercent: Float,
    speedString: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "transfer_flow")
    val flowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow_offset"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            val width = size.width
            val height = size.height
            val cy = height / 2f

            // Baseline track
            drawLine(
                color = Color(0xFF00B4D8).copy(alpha = 0.2f),
                start = Offset(16.dp.toPx(), cy),
                end = Offset(width - 16.dp.toPx(), cy),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Flowing packets/pulses
            val packetCount = 5
            for (i in 0 until packetCount) {
                val baseFrac = (i.toFloat() / packetCount + flowOffset) % 1.0f
                val x = 16.dp.toPx() + (width - 32.dp.toPx()) * baseFrac
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5FF), Color(0xFF0077B6).copy(alpha = 0f))
                    ),
                    radius = 8.dp.toPx(),
                    center = Offset(x, cy)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(x, cy)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Text(
                text = "${(progressPercent * 100).toInt()}% Transferred",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = SpreTealPrimary
            )
            Text(
                text = speedString,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = SpreOnlineGreen
            )
        }
    }
}

@Composable
fun SpreDropTopBar(
    userProfile: UserProfile?,
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
            // Unified SpreDrop Brand Logo
            SpreDropBrandLogo(
                sizeDp = 38,
                showText = true,
                subtitle = userProfile?.spreDropId ?: "@spredrop"
            )
        }
    }
}
