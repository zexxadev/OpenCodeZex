package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.FirestoreConfigRepository
import com.example.data.firebase.GoogleAuthHelper
import com.example.data.firebase.authStateFlow
import com.example.data.model.SessionRunStatus
import com.example.data.preferences.ServerProfileStore
import com.example.ui.navigation.FilesRoute
import com.example.ui.navigation.ServersRoute
import com.example.ui.navigation.SessionsRoute
import com.example.ui.navigation.WorkspaceRoute
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SessionsScreen
import com.example.ui.screens.SetupWizardScreen
import com.example.ui.screens.SplashPhase
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StartupSessionCoordinator
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.LocalOpenCodeColors
import com.example.ui.theme.OpenCodeTheme
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.OpenCodeViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private sealed interface StartupStage {
    data object Splash : StartupStage
    data object Auth : StartupStage
    class Authenticated(val user: com.google.firebase.auth.FirebaseUser) : StartupStage {
        override fun equals(other: Any?): Boolean =
            other is Authenticated && other.user.uid == user.uid

        override fun hashCode(): Int = user.uid.hashCode()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (FirebaseApp.getApps(applicationContext).isEmpty()) {
            FirebaseApp.initializeApp(applicationContext)
        }
        setContent {
            val context = LocalContext.current
            val application = context.applicationContext as Application
            val coroutineScope = rememberCoroutineScope()

            val preAuthProfileStore = remember {
                ServerProfileStore(application, Json { ignoreUnknownKeys = true; isLenient = true })
            }
            val localThemePreference by preAuthProfileStore.themePreferenceFlow
                .collectAsStateWithLifecycle(initialValue = ThemePreference.DARK)

            val currentUser by remember { Firebase.auth.authStateFlow() }
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            var isSplashFinished by rememberSaveable { mutableStateOf(false) }
            var splashPhase by remember { mutableStateOf(SplashPhase.INITIALIZING_FIREBASE) }
            var isSigningIn by remember { mutableStateOf(false) }
            var authErrorMessage by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                if (!isSplashFinished) {
                    StartupSessionCoordinator.initializeAndCheckSession(context) { phase ->
                        splashPhase = phase
                    }
                    isSplashFinished = true
                }
            }

            val startupStage: StartupStage = when {
                !isSplashFinished -> StartupStage.Splash
                currentUser == null -> StartupStage.Auth
                else -> StartupStage.Authenticated(currentUser!!)
            }

            Crossfade(
                targetState = startupStage,
                animationSpec = tween(durationMillis = 260),
                label = "startup_stage_crossfade"
            ) { stage ->
                when (stage) {
                    StartupStage.Splash -> {
                        OpenCodeTheme(themePreference = localThemePreference) {
                            SplashScreen(phase = splashPhase)
                        }
                    }

                    StartupStage.Auth -> {
                        OpenCodeTheme(themePreference = localThemePreference) {
                            AuthScreen(
                                isSigningIn = isSigningIn,
                                errorMessage = authErrorMessage,
                                themePreference = localThemePreference,
                                onThemeChange = { pref ->
                                    coroutineScope.launch {
                                        preAuthProfileStore.setThemePreference(pref)
                                    }
                                },
                                onGoogleSignInClick = {
                                    if (!isSigningIn) {
                                        isSigningIn = true
                                        authErrorMessage = null
                                        coroutineScope.launch {
                                            val result = GoogleAuthHelper.signInWithGoogle(context)
                                            isSigningIn = false
                                            result.onFailure { err ->
                                                authErrorMessage = err.localizedMessage
                                                    ?: "Gagal masuk dengan akun Google. Silakan coba lagi."
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    is StartupStage.Authenticated -> {
                        val signedInUser = stage.user
                        key(signedInUser.uid) {
                            val cloudRepository = remember(signedInUser.uid) {
                                FirestoreConfigRepository(application)
                            }
                            LaunchedEffect(signedInUser.uid) {
                                cloudRepository.upsertUserProfile(signedInUser)
                            }

                            val viewModel: OpenCodeViewModel = viewModel(
                                key = "opencode_vm_${signedInUser.uid}",
                                factory = OpenCodeViewModel.provideFactory(
                                    application = application,
                                    userId = signedInUser.uid,
                                    email = signedInUser.email,
                                    displayName = signedInUser.displayName,
                                    cloudSyncRepository = cloudRepository
                                )
                            )
                            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                            OpenCodeTheme(themePreference = uiState.themePreference) {
                                when {
                                    uiState.hasCompletedSetup == null -> {
                                        SplashScreen(phase = SplashPhase.SESSION_FOUND)
                                    }
                                    uiState.shouldShowSetupWizard -> {
                                        SetupWizardScreen(
                                            state = uiState,
                                            canDismissWithBack = uiState.showSetupWizardManual,
                                            snackbarEvents = viewModel.snackbarEvents,
                                            onRefreshTermux = viewModel::refreshTermuxStatus,
                                            onSaveProfile = { profile ->
                                                viewModel.saveServerProfile(profile, setActive = true)
                                            },
                                            onTestConnection = { viewModel.connectToActiveServer() },
                                            onSaveProviderApiKey = viewModel::saveProviderApiKey,
                                            onSaveProviderAccount = { viewModel.saveProviderAccount(it) },
                                            onRefreshModels = { viewModel.refreshProviderModels(it) },
                                            onFinishSetup = viewModel::completeSetupWizard
                                        )
                                    }
                                    else -> {
                                        OpenCodeAppShell(
                                            viewModel = viewModel,
                                            onSignOut = {
                                                coroutineScope.launch {
                                                    GoogleAuthHelper.signOut(context)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TopNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val routeObj: Any
)

@Composable
private fun OpenCodeAppShell(
    viewModel: OpenCodeViewModel,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val activeBusyCount = uiState.sessionStatuses.values.count { it.status != SessionRunStatus.IDLE }

    val navItems = remember(uiState.activeSessionId) {
        listOf(
            TopNavItem(
                label = "Sesi",
                selectedIcon = Icons.Rounded.Terminal,
                unselectedIcon = Icons.Outlined.Terminal,
                testTag = "nav_sessions",
                routeObj = SessionsRoute
            ),
            TopNavItem(
                label = "Chat",
                selectedIcon = Icons.Rounded.Forum,
                unselectedIcon = Icons.Outlined.Forum,
                testTag = "nav_workspace",
                routeObj = WorkspaceRoute(uiState.activeSessionId)
            ),
            TopNavItem(
                label = "File",
                selectedIcon = Icons.Rounded.Folder,
                unselectedIcon = Icons.Outlined.Folder,
                testTag = "nav_files",
                routeObj = FilesRoute
            ),
            TopNavItem(
                label = "Server",
                selectedIcon = Icons.Rounded.Dns,
                unselectedIcon = Icons.Outlined.Dns,
                testTag = "nav_servers",
                routeObj = ServersRoute
            )
        )
    }

    fun navigateTopLevel(target: Any) {
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun isRouteSelected(item: TopNavItem): Boolean {
        return when (item.routeObj) {
            is SessionsRoute -> currentDestination?.hasRoute<SessionsRoute>() == true
            is WorkspaceRoute -> currentDestination?.hasRoute<WorkspaceRoute>() == true
            is FilesRoute -> currentDestination?.hasRoute<FilesRoute>() == true
            is ServersRoute -> currentDestination?.hasRoute<ServersRoute>() == true
            else -> false
        }
    }

    val semanticColors = LocalOpenCodeColors.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (semanticColors.isGlass) Color.Transparent else MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        navItems.forEach { item ->
                            val selected = isRouteSelected(item)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.routeObj) },
                                icon = {
                                    NavItemIcon(
                                        item = item,
                                        selected = selected,
                                        badgeCount = if (item.routeObj is SessionsRoute) activeBusyCount else 0
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        navItems.forEach { item ->
                            val selected = isRouteSelected(item)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.routeObj) },
                                icon = {
                                    NavItemIcon(
                                        item = item,
                                        selected = selected,
                                        badgeCount = if (item.routeObj is SessionsRoute) activeBusyCount else 0
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = SessionsRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<SessionsRoute> {
                        SessionsScreen(
                            state = uiState,
                            onRefresh = { viewModel.refreshSessions() },
                            onReconnect = { viewModel.connectToActiveServer() },
                            onOpenServers = { navigateTopLevel(ServersRoute) },
                            onThemeChange = viewModel::setThemePreference,
                            onSearchChange = viewModel::updateSessionSearch,
                            onCreateSession = { title ->
                                viewModel.createSession(title) { created ->
                                    navigateTopLevel(WorkspaceRoute(created.id))
                                }
                            },
                            onOpenSession = { sessionId ->
                                viewModel.openSession(sessionId)
                                navigateTopLevel(WorkspaceRoute(sessionId))
                            },
                            onRenameSession = viewModel::renameSession,
                            onForkSession = { sessionId ->
                                viewModel.forkSession(sessionId) { forked ->
                                    navigateTopLevel(WorkspaceRoute(forked.id))
                                }
                            },
                            onDeleteSession = viewModel::deleteSession
                        )
                    }

                    composable<WorkspaceRoute> {
                        WorkspaceScreen(
                            state = uiState,
                            onBackToSessions = { navigateTopLevel(SessionsRoute) },
                            onCreateNewChat = {
                                viewModel.createSession(title = null)
                            },
                            onRefreshSession = {
                                uiState.activeSessionId?.let { id ->
                                    viewModel.refreshMessages(id)
                                    viewModel.refreshSessionDiff(id)
                                }
                            },
                            onReconnectSse = viewModel::reconnectSse,
                            onComposerTextChange = viewModel::updateComposerText,
                            onSelectFileMention = viewModel::insertFileMention,
                            onSelectComposerMode = viewModel::setComposerMode,
                            onSelectAgent = viewModel::selectAgent,
                            onSelectModel = viewModel::selectModel,
                            onSendPrompt = viewModel::sendCurrentInput,
                            onAbortSession = viewModel::abortActiveSession,
                            onReplyPermission = viewModel::replyPermission,
                            onRefreshModels = { viewModel.refreshProviderModels(null) },
                            onRetryResponse = viewModel::retryFailedResponse,
                            onDismissFailedPrompt = viewModel::dismissFailedPrompt
                        )
                    }

                    composable<FilesRoute> {
                        FilesScreen(
                            state = uiState,
                            onLoadDirectory = viewModel::loadDirectory,
                            onNavigateUp = viewModel::navigateUpDirectory,
                            onSearchModeChange = viewModel::setFileSearchMode,
                            onSearchQueryChange = viewModel::updateFileSearchQuery,
                            onExecuteSearch = viewModel::executeFileSearch,
                            onOpenFile = viewModel::openFilePreview,
                            onCloseFilePreview = viewModel::closeFilePreview,
                            onInsertMentionToPrompt = { path ->
                                viewModel.appendFileToPrompt(path)
                                if (uiState.activeSessionId != null) {
                                    navigateTopLevel(WorkspaceRoute(uiState.activeSessionId))
                                }
                            },
                            onOpenServers = { navigateTopLevel(ServersRoute) },
                            onCreateLocalFile = viewModel::createLocalWorkspaceFile,
                            onImportLocalFile = viewModel::importFileToLocalWorkspace
                        )
                    }

                    composable<ServersRoute> {
                        ServersScreen(
                            state = uiState,
                            onTestConnection = { viewModel.connectToActiveServer() },
                            onActivateProfile = viewModel::activateServerProfile,
                            onSaveProfile = { profile ->
                                viewModel.saveServerProfile(profile, setActive = true)
                            },
                            onDeleteProfile = viewModel::deleteServerProfile,
                            onThemeChange = viewModel::setThemePreference,
                            onOpenSetupWizard = viewModel::openSetupWizard,
                            onSaveProviderApiKey = viewModel::saveProviderApiKey,
                            onSaveProviderAccount = { viewModel.saveProviderAccount(it) },
                            onDeleteCustomProvider = viewModel::deleteCustomProvider,
                            onRefreshModels = { viewModel.refreshProviderModels(it) },
                            onSelectModel = viewModel::selectModel,
                            onSyncCloud = viewModel::syncNowToCloud,
                            onSignOut = onSignOut
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItemIcon(
    item: TopNavItem,
    selected: Boolean,
    badgeCount: Int
) {
    val icon = if (selected) item.selectedIcon else item.unselectedIcon
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(badgeCount.toString()) }
            }
        ) {
            Icon(imageVector = icon, contentDescription = item.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = item.label)
    }
}
