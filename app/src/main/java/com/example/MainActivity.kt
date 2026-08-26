package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.spredrop.data.firebase.AuthState
import com.example.spredrop.service.SpreDropBackgroundDiscoveryService
import com.example.spredrop.service.TransferNotificationHelper
import com.example.spredrop.ui.SpreDropViewModel
import com.example.spredrop.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SpreCyanAccent
import com.example.ui.theme.SpreTealPrimary

sealed class Screen(val route: String, val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    data object Home : Screen("home", "Radar", Icons.Default.Radar, Icons.Outlined.Radar)
    data object Transfers : Screen("transfers", "Transfers", Icons.Default.SwapVert, Icons.Outlined.SwapVert)
    data object Friends : Screen("friends", "Friends", Icons.Default.People, Icons.Outlined.People)
    data object QrPair : Screen("qr", "Pair", Icons.Default.QrCodeScanner, Icons.Outlined.QrCode)
    data object Profile : Screen("profile", "Settings", Icons.Default.Person, Icons.Outlined.Person)
}

class MainActivity : ComponentActivity() {

    private val viewModel: SpreDropViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            TransferNotificationHelper.initNotificationChannels(this)
        } catch (_: Exception) {}

        setContent {
            MyApplicationTheme {
                val authState by viewModel.authState.collectAsState()
                val context = LocalContext.current

                // Request essential permissions on startup
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { _ -> }

                LaunchedEffect(Unit) {
                    val permissionsToRequest = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
                        }
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                        }
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.CAMERA)
                    }

                    if (permissionsToRequest.isNotEmpty()) {
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }
                }

                // Authentication Gate: Require Login / Sign Up first
                AnimatedContent(
                    targetState = authState is AuthState.Authenticated,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "AuthGateTransition"
                ) { isAuthenticated ->
                    if (isAuthenticated) {
                        LaunchedEffect(Unit) {
                            try {
                                SpreDropBackgroundDiscoveryService.start(context)
                            } catch (_: Exception) {}
                        }

                        SpreDropApp(
                            viewModel = viewModel,
                            initialDestination = intent?.getStringExtra("nav_destination") ?: Screen.Home.route
                        )
                    } else {
                        AuthScreen(
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpreDropApp(
    viewModel: SpreDropViewModel,
    initialDestination: String = Screen.Home.route
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage by viewModel.userMessage.collectAsState()
    val activeTransfers by viewModel.activeTransfers.collectAsState()
    val incomingRequests by viewModel.incomingRequests.collectAsState()

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    val screens = listOf(
        Screen.Home,
        Screen.Transfers,
        Screen.Friends,
        Screen.QrPair,
        Screen.Profile
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                screens.forEach { screen ->
                    val selected = currentRoute == screen.route
                    val badgeCount = when (screen) {
                        Screen.Transfers -> activeTransfers.size
                        Screen.Friends -> incomingRequests.size
                        else -> 0
                    }

                    NavigationBarItem(
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (badgeCount > 0) {
                                        Badge(
                                            containerColor = SpreCyanAccent,
                                            contentColor = androidx.compose.ui.graphics.Color.Black
                                        ) {
                                            Text(badgeCount.toString(), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title,
                                    tint = if (selected) SpreTealPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) SpreTealPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        selected = selected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = SpreTealPrimary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_item_${screen.route}")
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = initialDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToQrPair = { navController.navigate(Screen.QrPair.route) },
                    onNavigateToTransfers = { navController.navigate(Screen.Transfers.route) },
                    onNavigateToFriends = { navController.navigate(Screen.Friends.route) }
                )
            }
            composable(Screen.Transfers.route) {
                TransfersScreen(
                    viewModel = viewModel
                )
            }
            composable(Screen.Friends.route) {
                FriendsScreen(
                    viewModel = viewModel
                )
            }
            composable(Screen.QrPair.route) {
                QrPairScreen(
                    viewModel = viewModel
                )
            }
            composable(Screen.Profile.route) {
                ProfileSettingsScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
