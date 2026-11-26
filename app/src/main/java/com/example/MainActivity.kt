package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.audio.SpeechAudioEngine
import com.example.data.repository.LanguageAiRepository
import com.example.data.repository.LearningRepository
import com.example.firebase.configureAppCheck
import com.example.floating.FloatingAssistService
import com.example.ui.LingoFloatViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.FloatingMiniCardOverlay
import com.example.ui.screens.PhrasebookScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.LingoFloatTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_STUDIO = "studio"
const val ROUTE_PHRASEBOOK = "phrasebook"
const val ROUTE_PROGRESS = "progress"

data class NavItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val navItems = listOf(
    NavItem(
        route = ROUTE_STUDIO,
        labelRes = R.string.nav_studio,
        selectedIcon = Icons.Filled.AutoStories,
        unselectedIcon = Icons.Outlined.AutoStories,
        testTag = "nav_studio"
    ),
    NavItem(
        route = ROUTE_PHRASEBOOK,
        labelRes = R.string.nav_phrasebook,
        selectedIcon = Icons.Filled.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks,
        testTag = "nav_phrasebook"
    ),
    NavItem(
        route = ROUTE_PROGRESS,
        labelRes = R.string.nav_progress,
        selectedIcon = Icons.Filled.Insights,
        unselectedIcon = Icons.Outlined.Insights,
        testTag = "nav_progress"
    )
)

class MainActivity : ComponentActivity() {
    private var prefillTextState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        prefillTextState = intent?.getStringExtra(FloatingAssistService.EXTRA_PREFILL_TEXT)

        // STEP 2: Initialize UI
        setContent {
            LingoFloatTheme {
                AppContent(
                    prefillText = prefillTextState,
                    onConsumePrefill = { prefillTextState = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(FloatingAssistService.EXTRA_PREFILL_TEXT)?.let {
            if (it.isNotBlank()) prefillTextState = it
        }
    }
}

@Composable
fun AppContent(
    prefillText: String?,
    onConsumePrefill: () -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: LingoFloatViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        LingoFloatViewModel(
                            learningRepository = LearningRepository(db),
                            aiRepository = LanguageAiRepository(),
                            audioEngine = SpeechAudioEngine(app),
                            currentUserId = uid
                        )
                    }
                }
            )
            LingoFloatMainShell(
                viewModel = viewModel,
                userEmail = user.email,
                prefillText = prefillText,
                onConsumePrefill = onConsumePrefill
            )
        }
    }
}

@Composable
private fun LingoFloatMainShell(
    viewModel: LingoFloatViewModel,
    userEmail: String?,
    prefillText: String?,
    onConsumePrefill: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val audioMeter by viewModel.audioMeter.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val ttsEngineLabel by viewModel.ttsEngineLabel.collectAsStateWithLifecycle()
    val isFloatingBubbleRunning by FloatingAssistService.isRunning.collectAsStateWithLifecycle()
    val phrasesState by viewModel.phrasesState.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()

    LaunchedEffect(prefillText) {
        if (!prefillText.isNullOrBlank()) {
            viewModel.updateInputText(prefillText)
            onConsumePrefill()
        }
    }

    LaunchedEffect(studioState.statusBanner) {
        studioState.statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_STUDIO

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useRail) {
                        NavigationRail {
                            navItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(item.labelRes)) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_STUDIO,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(ROUTE_STUDIO) {
                            StudioScreen(
                                studioState = studioState,
                                audioMeter = audioMeter,
                                isSpeaking = isSpeaking,
                                ttsEngineLabel = ttsEngineLabel,
                                isFloatingBubbleRunning = isFloatingBubbleRunning,
                                viewModel = viewModel
                            )
                        }
                        composable(ROUTE_PHRASEBOOK) {
                            PhrasebookScreen(
                                phrasesState = phrasesState,
                                onPracticePhrase = { phrase ->
                                    viewModel.loadSavedPhraseIntoStudio(phrase)
                                    navigateToTab(ROUTE_STUDIO)
                                },
                                onPronouncePhrase = { text, lang ->
                                    viewModel.pronounceTextInLanguage(text, lang, 0.95f)
                                },
                                onDeletePhrase = viewModel::deleteSavedPhrase
                            )
                        }
                        composable(ROUTE_PROGRESS) {
                            ProgressScreen(
                                sessionsState = sessionsState,
                                userEmail = userEmail,
                                hasCloudApiKey = viewModel.hasCloudApiKey,
                                onDeleteSession = viewModel::deletePracticeSession,
                                onSignOut = {
                                    FloatingAssistService.stop(context)
                                    signOutUser(credentialManager, scope)
                                }
                            )
                        }
                    }
                }

                FloatingMiniCardOverlay(
                    visible = studioState.isInAppFloatingOpen,
                    studioState = studioState,
                    onInputChange = viewModel::updateFloatingMiniInput,
                    onTranslate = viewModel::translateFloatingMini,
                    onSpeak = viewModel::speakFloatingMini,
                    onSendToStudio = {
                        viewModel.sendFloatingMiniToStudio()
                        navigateToTab(ROUTE_STUDIO)
                    },
                    onClose = { viewModel.setInAppFloatingOpen(false) },
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}
