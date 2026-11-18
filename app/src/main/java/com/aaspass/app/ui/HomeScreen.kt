package com.aaspass.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aaspass.app.calls.CallRepository
import com.aaspass.app.data.AasPassRepository
import com.aaspass.app.data.GroupRepository
import com.aaspass.app.data.Conversation
import com.aaspass.app.data.TimelineMerge
import com.aaspass.app.data.UserProfile
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.DateFormat

private val tabNames = listOf("Chats", "Calls", "Groups", "Study", "Settings")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: AasPassRepository,
    profile: UserProfile,
    groupRepository: GroupRepository,
    callRepository: CallRepository,
    initialTab: Int = 0,
    onOpenChat: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenCall: (String) -> Unit
) {
    var tab by rememberSaveable(profile.id, initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 4)) }
    var showNewChat by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val conversationFlow = remember(profile.id) { repository.conversations(profile.id) }
    var listError by remember { mutableStateOf<String?>(null) }
    var listLoaded by remember(profile.id) { mutableStateOf(false) }
    val chats by remember(conversationFlow) {
        conversationFlow
            .onEach { listLoaded = true; listError = null }
            .catch { listLoaded = true; listError = it.localizedMessage ?: "Unable to load chats"; emit(emptyList()) }
    }.collectAsState(initial = emptyList())
    var olderChats by remember(profile.id) { mutableStateOf<List<Conversation>>(emptyList()) }
    var olderBusy by remember(profile.id) { mutableStateOf(false) }
    var hasOlderChats by remember(profile.id) { mutableStateOf(true) }
    var olderError by remember(profile.id) { mutableStateOf<String?>(null) }
    val displayedChats = remember(chats, olderChats) {
        TimelineMerge.latestWins(chats, olderChats) { it.id }
            .sortedByDescending { it.lastAt?.toDate()?.time ?: 0L }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notificationPrefs = remember(context) {
        context.getSharedPreferences("aaspass_notification_onboarding", Context.MODE_PRIVATE)
    }
    var offerNotifications by remember(profile.id) {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED &&
            !notificationPrefs.getBoolean(profile.id, false))
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Android controls the final permission state. The Settings tab can retry. */ }
    fun dismissNotificationOffer() {
        notificationPrefs.edit().putBoolean(profile.id, true).apply()
        offerNotifications = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (search && tab == 0) {
                        OutlinedTextField(
                            query, { query = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search conversations") }, singleLine = true
                        )
                    } else {
                        Text("AasPass", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (tab == 0) {
                        IconButton(onClick = { search = !search; if (!search) query = "" }) {
                            Icon(if (search) Icons.Default.Close else Icons.Default.Search, "Search")
                        }
                        IconButton(onClick = { showNewChat = true }) {
                            Icon(Icons.Default.Edit, "Start conversation")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabNames.forEachIndexed { index, label ->
                    val icon = when (index) {
                        0 -> Icons.Default.Chat
                        1 -> Icons.Default.Call
                        2 -> Icons.Default.Groups
                        3 -> Icons.Default.School
                        else -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = tab == index, onClick = { tab = index },
                        icon = { Icon(icon, label) }, label = { Text(label) },
                        alwaysShowLabel = true
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = { showNewChat = true }) {
                    Icon(Icons.Default.AddComment, "New chat")
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> if (!listLoaded) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else ChatList(displayedChats, profile.id, query, listError,
                    hasOlderChats && displayedChats.isNotEmpty() && query.isBlank(), olderBusy,
                    olderError, onClick = onOpenChat,
                    onLoadMore = {
                        val oldest = displayedChats.lastOrNull()?.lastAt
                        if (oldest != null && !olderBusy) scope.launch {
                            olderBusy = true
                            olderError = null
                            try {
                                val page = repository.loadOlderConversations(profile.id, oldest)
                                olderChats = TimelineMerge.latestWins(olderChats, page) { it.id }
                                if (page.size < 75) hasOlderChats = false
                            } catch (e: Exception) {
                                olderError = e.localizedMessage ?: "Could not load older chats"
                            } finally { olderBusy = false }
                        }
                    })
                1 -> CallsTab(callRepository, profile.id, onOpenCall)
                2 -> GroupHubScreen(groupRepository, profile, "general", onOpenGroup)
                3 -> GroupHubScreen(groupRepository, profile, "study", onOpenGroup)
                else -> SettingsScreen(repository, profile)
            }
        }
    }
    if (offerNotifications) {
        AlertDialog(
            onDismissRequest = { dismissNotificationOffer() },
            title = { Text("Message & call alerts") },
            text = { Text("Allow notifications so AasPass can alert you about new chats, group invites and incoming calls. You can change this any time in Android Settings.") },
            confirmButton = { TextButton(onClick = {
                dismissNotificationOffer()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("Enable alerts") } },
            dismissButton = { TextButton(onClick = { dismissNotificationOffer() }) { Text("Not now") } }
        )
    }
    if (showNewChat) {
        NewChatDialog(repository, profile, onDismiss = { showNewChat = false },
            onCreated = { chatId -> showNewChat = false; onOpenChat(chatId) })
    }
}

@Composable
private fun ChatList(
    conversations: List<Conversation>, uid: String, search: String,
    error: String?, hasOlder: Boolean, olderBusy: Boolean, olderError: String?,
    onClick: (String) -> Unit, onLoadMore: () -> Unit
) {
    val filtered = conversations.filter { it.peerName(uid).contains(search, ignoreCase = true) }
    if (error != null) {
        Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    } else if (filtered.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Forum, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(if (search.isBlank()) "No conversations yet" else "No matching conversations",
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Start a chat using a discoverable AasPass username.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(filtered, key = { it.id }) { conversation ->
                Row(
                    Modifier.fillMaxWidth().clickable { onClick(conversation.id) }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(54.dp).clip(CircleShape)
                        .then(Modifier), Alignment.Center) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(Modifier.size(54.dp), Alignment.Center) {
                                Text(conversation.peerName(uid).take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(conversation.peerName(uid), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when (conversation.status) {
                                "requested" -> if (conversation.requestedBy == uid) "Request pending" else "New message request"
                                "rejected" -> "Request declined"
                                else -> conversation.lastMessage.ifBlank { "Start chatting" }
                            },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    conversation.lastAt?.toDate()?.let { timestamp ->
                        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(start = 84.dp), thickness = 0.5.dp)
            }
            if (olderError != null) item(key = "older_chats_error") {
                Text(olderError, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            }
            if (hasOlder) item(key = "load_more_chats") {
                Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                    TextButton(enabled = !olderBusy, onClick = onLoadMore) {
                        Text(if (olderBusy) "Loading…" else "Load older conversations")
                    }
                }
            }
        }
    }
}

@Composable
private fun NewChatDialog(
    repository: AasPassRepository, profile: UserProfile,
    onDismiss: () -> Unit, onCreated: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a private chat") },
        text = {
            Column {
                Text("Enter an exact username. The other person must enable discovery and accept your request.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(username, { username = it; error = null },
                    label = { Text("Username") }, singleLine = true)
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    busy = true
                    try { onCreated(repository.requestChat(profile, username)) }
                    catch (e: Exception) { error = e.localizedMessage ?: "Request failed" }
                    finally { busy = false }
                }
            }, enabled = !busy && username.isNotBlank()) { Text("Send request") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AvailabilityScreen(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
            Box(Modifier.size(86.dp), Alignment.Center) {
                Icon(icon, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}
