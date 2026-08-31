package com.example.spredrop.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spredrop.model.PrivacyMode
import com.example.spredrop.model.UserPresence
import com.example.spredrop.ui.SpreDropViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: SpreDropViewModel,
    onOnboardingFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })
    val focusManager = LocalFocusManager.current

    // Setup input fields initialized with default user profile values
    var hasInitialized by remember { mutableStateOf(false) }
    var tempDisplayName by remember { mutableStateOf("") }
    var tempSpreDropId by remember { mutableStateOf("") }
    var tempVisibility by remember { mutableStateOf(PrivacyMode.VISIBLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(userProfile) {
        val profile = userProfile
        if (profile != null && !hasInitialized) {
            tempDisplayName = profile.displayName
            tempSpreDropId = profile.spreDropId
            tempVisibility = profile.visibility
            hasInitialized = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SpreDarkSurface,
                        SpreDarkBg,
                        Color(0xFF080C14)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Logo/App Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    tint = SpreTealPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "SpreDrop",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = SpreTealPrimary,
                    letterSpacing = 1.sp
                )
            }

            // Slide Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                when (pageIndex) {
                    0 -> WelcomeSlide()
                    1 -> PrivacySlide()
                    2 -> ProfileSlide(
                        displayName = tempDisplayName,
                        onDisplayNameChange = { tempDisplayName = it },
                        spreDropId = tempSpreDropId,
                        onSpreDropIdChange = { tempSpreDropId = it },
                        visibility = tempVisibility,
                        onVisibilityChange = { tempVisibility = it },
                        errorMessage = errorMessage,
                        onClearError = { errorMessage = null }
                    )
                    3 -> ReadySlide(
                        displayName = tempDisplayName,
                        spreDropId = tempSpreDropId,
                        visibility = tempVisibility
                    )
                }
            }

            // Bottom controls: Page indicators, Back & Next/Finish buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Indicator dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { i ->
                        val selected = pagerState.currentPage == i
                        Box(
                            modifier = Modifier
                                .size(if (selected) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (selected) SpreTealPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )
                    }
                }

                // Control Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    if (pagerState.currentPage > 0) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        ) {
                            Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }

                    // Next / Finish button
                    val isLastPage = pagerState.currentPage == 3
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (pagerState.currentPage == 2) {
                                // Validate profile input on slide 2
                                if (tempDisplayName.isBlank()) {
                                    errorMessage = "Display name cannot be empty."
                                    return@Button
                                }
                                val cleanId = tempSpreDropId.trim()
                                if (cleanId.isBlank()) {
                                    errorMessage = "SpreDrop ID cannot be empty."
                                    return@Button
                                }
                                
                                isSaving = true
                                scope.launch {
                                    try {
                                        viewModel.updateIdentitySuspending(cleanId, tempDisplayName)
                                        viewModel.updatePrivacySuspending(tempVisibility)
                                        pagerState.animateScrollToPage(3)
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Could not update profile"
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            } else if (isLastPage) {
                                onOnboardingFinished()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLastPage) SpreCyanAccent else SpreTealPrimary,
                            contentColor = if (isLastPage) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 8.dp),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text(
                                text = if (isLastPage) "Get Started" else "Next",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeSlide() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .background(SpreTealPrimary.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.SwapCalls,
                contentDescription = null,
                tint = SpreTealPrimary,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Welcome to SpreDrop",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Secure, lightning-fast, and completely decentralized local P2P file transfers. Connect effortlessly with nearby users without burning cellular data.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun PrivacySlide() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .background(SpreCyanAccent.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = SpreCyanAccent,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Privacy First Specification",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "SpreDrop runs fully offline-first. Your files are streamed directly between devices with peer-to-peer WebRTC and Direct Wi-Fi. No clouds, no middle-men, completely confidential.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun ProfileSlide(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    spreDropId: String,
    onSpreDropIdChange: (String) -> Unit,
    visibility: PrivacyMode,
    onVisibilityChange: (PrivacyMode) -> Unit,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Profile Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Define your unique SpreDrop ID so nearby devices can see you on their radar screens.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = displayName,
            onValueChange = {
                onDisplayNameChange(it)
                onClearError()
            },
            label = { Text("Display Name") },
            placeholder = { Text("e.g. John Doe") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = SpreTealPrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = spreDropId,
            onValueChange = {
                onSpreDropIdChange(it)
                onClearError()
            },
            label = { Text("SpreDrop ID") },
            placeholder = { Text("e.g. @john") },
            singleLine = true,
            leadingIcon = { Text("@", color = SpreTealPrimary, fontWeight = FontWeight.Bold) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = SpreTealPrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        Text(
            text = "Discovery Visibility Mode",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(PrivacyMode.VISIBLE, PrivacyMode.FRIENDS_ONLY, PrivacyMode.INVISIBLE).forEach { mode ->
                val selected = visibility == mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) SpreTealPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .clickable { onVisibilityChange(mode) }
                ) {
                    Text(
                        text = when (mode) {
                            PrivacyMode.VISIBLE -> "Everyone"
                            PrivacyMode.FRIENDS_ONLY -> "Friends Only"
                            else -> "Invisible"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "💡 Note: Setting discovery to \"Everyone\" or \"Friends Only\" will automatically make you Available to others.",
            fontSize = 11.sp,
            color = SpreCyanAccent
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage,
                color = SpreErrorRed,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun ReadySlide(
    displayName: String,
    spreDropId: String,
    visibility: PrivacyMode
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .background(SpreOnlineGreen.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SpreOnlineGreen,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Setup Completed!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Welcome aboard, $displayName!\nYour ID: @${spreDropId.removePrefix("@")}\nDiscovery Status: ${visibility.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}
