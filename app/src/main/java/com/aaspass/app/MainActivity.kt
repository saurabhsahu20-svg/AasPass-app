package com.aaspass.app

import android.os.Bundle
import android.content.Intent
import com.aaspass.app.navigation.NotificationDestination
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aaspass.app.calls.CallRepository
import com.aaspass.app.data.GroupRepository
import com.aaspass.app.data.AasPassRepository
import com.aaspass.app.data.UserProfile
import com.aaspass.app.ui.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private var notificationTarget by mutableStateOf<NotificationDestination?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationTarget = destinationFromIntent(intent)
        setContent { AasPassTheme {
            Root(notificationTarget) { notificationTarget = null }
        } }
    }
    private fun destinationFromIntent(intent: Intent?): NotificationDestination? =
        NotificationDestination.fromPayload(
            intent?.getStringExtra("callId"),
            intent?.getStringExtra("chatId"),
            intent?.getStringExtra("groupId"),
            intent?.getStringExtra("inviteKind")
        )

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTarget = destinationFromIntent(intent)
    }
}

@Composable
private fun Root(pendingTarget: NotificationDestination?, onTargetConsumed: () -> Unit) {
    val context = LocalContext.current
    val configured = remember {
        FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
    }
    if (!configured) {
        MsgUCloudScreen()
        return
    }
    val auth = remember { FirebaseAuth.getInstance() }
    val repository = remember { AasPassRepository(auth) }
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val signedInUid = uid
    if (signedInUid == null) AuthScreen(repository)
    else ProfileGate(signedInUid, repository, pendingTarget, onTargetConsumed)
}

@Composable
private fun ProfileGate(uid: String, repository: AasPassRepository,
                        pendingTarget: NotificationDestination?, onTargetConsumed: () -> Unit) {
    LaunchedEffect(uid) { repository.registerCurrentPushToken() }
    var profile by remember(uid) { mutableStateOf<UserProfile?>(null) }
    var loading by remember(uid) { mutableStateOf(true) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    DisposableEffect(uid) {
        val registration = repository.observeProfile(uid,
            onUpdate = { profile = it; loading = false; error = null },
            onError = { error = it; loading = false }
        )
        onDispose { registration.remove() }
    }
    when {
        loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Unable to load account: $error", textAlign = TextAlign.Center)
        }
        profile == null -> ProfileSetupScreen(repository)
        else -> AasPassNavigation(repository, requireNotNull(profile), pendingTarget, onTargetConsumed)
    }
}

@Composable
private fun AasPassNavigation(repository: AasPassRepository, profile: UserProfile,
                              pendingTarget: NotificationDestination?, onTargetConsumed: () -> Unit) {
    val nav = rememberNavController()
    val groups = remember { GroupRepository() }
    val calls = remember { CallRepository() }
    LaunchedEffect(pendingTarget, profile.id) {
        pendingTarget?.let { destination ->
            nav.navigate(destination.route) { launchSingleTop = true }
            onTargetConsumed()
        }
    }
    NavHost(navController = nav, startDestination = "home?tab=0") {
        composable("home?tab={tab}", arguments = listOf(navArgument("tab") {
            type = NavType.IntType; defaultValue = 0
        })) { entry ->
            HomeScreen(repository, profile, groups, calls,
                initialTab = entry.arguments?.getInt("tab") ?: 0,
                onOpenChat = { id -> nav.navigate("chat/$id") },
                onOpenGroup = { id -> nav.navigate("group/$id") },
                onOpenCall = { id -> nav.navigate("call/$id") })
        }
        composable(
            route = "group/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { entry ->
            GroupChatScreen(groups, profile, entry.arguments?.getString("groupId").orEmpty(),
                onBack = { nav.popBackStack() })
        }
        composable(
            route = "chat/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { entry ->
            ChatScreen(
                repository = repository, currentProfile = profile,
                chatId = entry.arguments?.getString("chatId").orEmpty(),
                callRepository = calls,
                onOpenCall = { id -> nav.navigate("call/$id") },
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            route = "call/{callId}",
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) { entry ->
            CallScreen(calls, entry.arguments?.getString("callId").orEmpty(),
                onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun SetupRequired() {
    var showSetup by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("MsgU", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("💎 Crystal Jelly Studio · Offline Preview", color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = { showSetup = !showSetup }) {
            Text(if (showSetup) "Hide chat setup" else "Enable real chat · Firebase setup")
        }
        if (showSetup) {
            Text("Real messaging needs your own app/google-services.json, Email/Password Auth and deployed Firestore rules. This build does not simulate messages.",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        JellyGallery(Modifier.weight(1f))
    }
}
