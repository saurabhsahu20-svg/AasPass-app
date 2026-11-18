package com.aaspass.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aaspass.app.calls.CallRepository
import com.aaspass.app.data.AasPassRepository
import com.aaspass.app.data.Conversation
import com.aaspass.app.data.UserProfile
import com.aaspass.app.data.ChatMessage
import com.aaspass.app.data.TimelineMerge
import com.aaspass.app.data.MediaRepository
import com.aaspass.app.data.PrivacyPreferences
import com.aaspass.app.data.ReadReceiptEligibility
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: AasPassRepository, currentProfile: UserProfile,
    chatId: String,
    callRepository: CallRepository,
    onOpenCall: (String) -> Unit,
    onBack: () -> Unit
) {
    var chat by remember(chatId) { mutableStateOf<Conversation?>(null) }
    var loaded by remember(chatId) { mutableStateOf(false) }
    var error by remember(chatId) { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var reportDialog by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var callBusy by remember(chatId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(chatId) {
        val registration = repository.conversation(chatId,
            onUpdate = { chat = it; loaded = true }, onError = { error = it; loaded = true })
        onDispose { registration.remove() }
    }
    val peerId = chat?.peerUid(currentProfile.id).orEmpty()
    val blocked = peerId in currentProfile.blockedUids
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(chat?.peerName(currentProfile.id) ?: "Conversation",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Private conversation · E2EE not enabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                } },
                actions = {
                    if (chat?.status == "accepted" && !blocked) {
                        IconButton(enabled = !callBusy, onClick = {
                            scope.launch {
                                callBusy = true
                                try { onOpenCall(callRepository.startCall(chatId, video = false)) }
                                catch (e: Exception) { error = e.localizedMessage ?: "Call could not start" }
                                finally { callBusy = false }
                            }
                        }) { Icon(Icons.Default.Call, "Voice call") }
                        IconButton(enabled = !callBusy, onClick = {
                            scope.launch {
                                callBusy = true
                                try { onOpenCall(callRepository.startCall(chatId, video = true)) }
                                catch (e: Exception) { error = e.localizedMessage ?: "Video call could not start" }
                                finally { callBusy = false }
                            }
                        }) { Icon(Icons.Default.Videocam, "Video call") }
                    }
                    IconButton(onClick = { menuExpanded = true }, enabled = peerId.isNotBlank()) {
                        Icon(Icons.Default.MoreVert, "Conversation options")
                    }
                    DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Report user") }, onClick = {
                            menuExpanded = false; reportDialog = true
                        })
                        DropdownMenuItem(text = { Text("Block user") }, onClick = {
                            menuExpanded = false; confirmBlock = true
                        }, enabled = !blocked)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val activeChat = chat
            if (error != null && activeChat != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(error.orEmpty(), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { error = null }) { Text("Dismiss") }
                    }
                }
            }
            when {
                !loaded -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                activeChat == null -> CenterText(error ?: "This conversation is unavailable.")
                blocked -> CenterText("You have blocked this user. New messages are disabled.")
                activeChat.status == "rejected" -> CenterText("This message request was declined.")
                activeChat.status == "requested" -> {
                    if (activeChat.requestedBy == currentProfile.id) {
                        CenterText("Message request sent. You can chat after the other person accepts.")
                    } else {
                        Column(
                            Modifier.fillMaxSize().padding(26.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("New conversation request", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Text("Accept to allow messages from ${activeChat.peerName(currentProfile.id)}.",
                                textAlign = TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { scope.launch {
                                try { repository.respondToRequest(chatId, true) }
                                catch (e: Exception) { error = e.localizedMessage }
                            } }) { Text("Accept request") }
                            TextButton(onClick = { scope.launch {
                                try { repository.respondToRequest(chatId, false) }
                                catch (e: Exception) { error = e.localizedMessage }
                            } }) { Text("Decline") }
                        }
                    }
                }
                else -> MessageThread(repository, currentProfile.id, chatId, peerId)
            }
        }
    }
    if (confirmBlock) AlertDialog(
        onDismissRequest = { confirmBlock = false },
        title = { Text("Block this user?") },
        text = { Text("They will not be able to send new messages to you. This does not delete earlier messages.") },
        confirmButton = { TextButton(onClick = { scope.launch {
            try { repository.blockUser(peerId); confirmBlock = false }
            catch (e: Exception) { error = e.localizedMessage }
        } }) { Text("Block") } },
        dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } }
    )
    if (reportDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reportDialog = false }, title = { Text("Report user") },
            text = { OutlinedTextField(reason, { reason = it.take(300) },
                label = { Text("Reason") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(enabled = reason.isNotBlank(), onClick = { scope.launch {
                try { repository.reportUser(chatId, peerId, reason); reportDialog = false }
                catch (e: Exception) { error = e.localizedMessage }
            } }) { Text("Submit report") } },
            dismissButton = { TextButton(onClick = { reportDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CenterText(text: String) = Box(
    Modifier.fillMaxSize().padding(32.dp), Alignment.Center
) { Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun MessageThread(repository: AasPassRepository, uid: String, chatId: String, peerUid: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepository() }
    var error by remember(chatId) { mutableStateOf<String?>(null) }
    var uploading by remember(chatId) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            uploading = true
            try { mediaRepository.upload(context, "direct", chatId, uri) }
            catch (e: Exception) { error = e.localizedMessage ?: "Attachment could not be sent" }
            finally { uploading = false }
        }
    }
    val shareReadReceipts = remember(uid) { PrivacyPreferences.shareReadReceipts(context, uid) }
    // Realtime snapshots are complete lists; keep only the freshest update during bursts.
    val messageFlow = remember(chatId) { repository.messages(chatId).conflate() }
    val liveMessages by remember(messageFlow) {
        messageFlow.catch { error = it.localizedMessage ?: "Could not load messages" }
    }.collectAsState(initial = emptyList())
    var olderMessages by remember(chatId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var olderBusy by remember(chatId) { mutableStateOf(false) }
    var hasOlder by remember(chatId) { mutableStateOf(true) }
    val messages = remember(liveMessages, olderMessages) {
        TimelineMerge.latestWins(liveMessages, olderMessages) { it.id }
            .sortedWith(compareBy<ChatMessage> { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                .thenBy { it.id })
    }
    // Constant-time reply preview lookup even when long history has been loaded.
    val messageById = remember(messages) { messages.associateBy { it.id } }
    val peerReadFlow = remember(chatId, peerUid) { repository.observePeerReadAt(chatId, peerUid) }
    val peerReadAt by remember(peerReadFlow) {
        peerReadFlow.catch { emit(null) }
    }.collectAsState(initial = null)
    var input by rememberSaveable(chatId) { mutableStateOf("") }
    var replyTo by remember(chatId) { mutableStateOf<ChatMessage?>(null) }
    // Keep each failed outgoing draft available for explicit restoration, not an automatic resend.
    val failedSends = remember(chatId) { mutableStateListOf<Pair<String, ChatMessage?>>() }
    var actionsFor by remember(chatId) { mutableStateOf<ChatMessage?>(null) }
    var editFor by remember(chatId) { mutableStateOf<ChatMessage?>(null) }
    var deletionFor by remember(chatId) { mutableStateOf<ChatMessage?>(null) }
    val scrollState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var screenResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            screenResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val mostRecentPeerMessage = messages.lastOrNull { it.senderId == peerUid && !it.pending }?.id
    // A live Firestore listener can fire while the phone is locked, or while
    // the user is reading older history. Neither situation means "read".
    LaunchedEffect(chatId, mostRecentPeerMessage, shareReadReceipts, screenResumed) {
        if (shareReadReceipts && screenResumed && mostRecentPeerMessage != null) {
            snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.map { it.key } }
                .filter { visibleKeys -> ReadReceiptEligibility.canMarkRead(
                    enabled = shareReadReceipts,
                    screenResumed = screenResumed,
                    newestPeerMessageId = mostRecentPeerMessage,
                    visibleMessageKeys = visibleKeys
                ) }.first()
            try { repository.markChatRead(chatId) }
            catch (e: Exception) { error = e.localizedMessage ?: "Read receipt not saved" }
        }
    }
    // Auto-scroll for the first load and our own new messages. Do not yank the
    // list away when someone replies while the user is reading older history.
    var previousLastMessageId by remember(chatId) { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.lastOrNull()?.id) {
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        if (previousLastMessageId == last.id) return@LaunchedEffect
        val visible = scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalBefore = scrollState.layoutInfo.totalItemsCount
        val wasNearBottom = totalBefore == 0 || visible >= totalBefore - 3
        val firstLoad = previousLastMessageId == null
        previousLastMessageId = last.id
        if (firstLoad || wasNearBottom || last.senderId == uid) {
            val destination = messages.lastIndex + (if (hasOlder) 1 else 0)
            // Avoid a long, expensive animation when a chat with many messages first opens.
            if (firstLoad || destination - visible > 5) {
                scrollState.scrollToItem(destination)
            } else {
                scrollState.animateScrollToItem(destination)
            }
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        }
        LazyColumn(
            state = scrollState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasOlder && messages.isNotEmpty()) {
                item(key = "load_older") {
                    Box(Modifier.fillMaxWidth(), Alignment.Center) {
                        TextButton(enabled = !olderBusy, onClick = {
                            val oldest = messages.firstNotNullOfOrNull { it.createdAt }
                            if (oldest != null) scope.launch {
                                olderBusy = true
                                try {
                                    val batch = repository.loadOlderMessages(chatId, oldest)
                                    olderMessages = TimelineMerge.latestWins(olderMessages, batch) { it.id }
                                    if (batch.size < 50) hasOlder = false
                                } catch (e: Exception) { error = e.localizedMessage ?: "Older messages unavailable" }
                                finally { olderBusy = false }
                            }
                        }) { Text(if (olderBusy) "Loading…" else "Load older messages") }
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                val referenced = messageById[message.replyToId]
                val replyPreview = when {
                    message.replyToId.isBlank() -> null
                    referenced == null -> "Earlier message"
                    referenced.isDeleted -> "Message deleted"
                    referenced.mediaPath.isNotBlank() -> "Attachment: ${referenced.fileName}"
                    else -> referenced.text.take(96)
                }
                MessageBubble(
                    message = message,
                    mine = message.senderId == uid,
                    replyPreview = replyPreview,
                    isRead = message.senderId == uid && !message.pending &&
                        message.createdAt != null && peerReadAt != null &&
                        peerReadAt!! >= message.createdAt,
                    onClick = { actionsFor = message },
                    onAttachment = {
                        scope.launch {
                            try { mediaRepository.open(context, message.mediaPath, message.contentType) }
                            catch (e: Exception) { error = e.localizedMessage ?: "Cannot open attachment" }
                        }
                    }
                )
            }
        }
        replyTo?.let { target ->
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Replying to ${if (target.senderId == uid) "yourself" else "message"}",
                            style = MaterialTheme.typography.labelMedium)
                        Text(if (target.isDeleted) "Message deleted" else if (target.mediaPath.isNotBlank())
                            "Attachment: ${target.fileName}" else target.text.take(130),
                            maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { replyTo = null }) { Text("Cancel") }
                }
            }
        }
        HorizontalDivider()
        if (failedSends.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (failedSends.size == 1) "Message not sent" else "${failedSends.size} messages not sent",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Restore your draft to retry when the issue is resolved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    TextButton(enabled = input.isBlank(), onClick = {
                        val failed = failedSends.removeAt(0)
                        input = failed.first
                        replyTo = failed.second
                        error = null
                    }) { Text("Restore") }
                }
            }
        }
        if (uploading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            fun send() {
                val messageText = input.trim()
                if (messageText.isEmpty()) return
                try {
                    // Enqueue into Firestore's offline cache without blocking the composer.
                    val originalReply = replyTo
                    repository.enqueueMessage(chatId, messageText, originalReply?.id.orEmpty()) {
                        // Do not silently lose a message that Firestore rejected.
                        failedSends.add(messageText to originalReply)
                        error = it
                    }
                    error = null
                    input = ""
                    replyTo = null
                    // Keep the keyboard open for fast consecutive messages.
                } catch (e: Exception) {
                    error = e.localizedMessage ?: "Message could not be queued"
                }
            }
            IconButton(onClick = { picker.launch(MediaRepository.MIME_TYPES) }, enabled = !uploading) {
                Icon(Icons.Default.AttachFile, "Attach a photo, video, PDF or audio file")
            }
            OutlinedTextField(
                value = input, onValueChange = { input = it.take(3000) },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                placeholder = { Text("Message") }, maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() })
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { send() }, enabled = input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    val selected = actionsFor
    if (selected != null) {
        val mine = selected.senderId == uid
        val canEdit = mine && selected.mediaPath.isBlank() && !selected.isDeleted && !selected.pending &&
            selected.createdAt != null &&
            System.currentTimeMillis() - selected.createdAt.toDate().time in 0..(15 * 60 * 1000L)
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            title = { Text("Message options") },
            text = {
                Column {
                    if (!selected.isDeleted) {
                        TextButton(onClick = { replyTo = selected; actionsFor = null }) {
                            Text("Reply")
                        }
                    }
                    if (canEdit) {
                        TextButton(onClick = { editFor = selected; actionsFor = null }) {
                            Text("Edit (within 15 minutes)")
                        }
                    }
                    if (mine && selected.mediaPath.isBlank() && !selected.isDeleted && !selected.pending) {
                        TextButton(onClick = { deletionFor = selected; actionsFor = null }) {
                            Text("Delete for everyone")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionsFor = null }) { Text("Close") } }
        )
    }
    editFor?.let { target ->
        var editedText by remember(target.id) { mutableStateOf(target.text) }
        var saving by remember(target.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!saving) editFor = null },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(editedText, { editedText = it.take(3000) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Message") })
            },
            confirmButton = {
                TextButton(enabled = !saving && editedText.isNotBlank(), onClick = {
                    scope.launch {
                        saving = true
                        try { repository.editMessage(chatId, target.id, editedText); editFor = null }
                        catch (e: Exception) { error = e.localizedMessage ?: "Edit failed" }
                        finally { saving = false }
                    }
                }) { Text(if (saving) "Saving…" else "Save") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { editFor = null }) { Text("Cancel") } }
        )
    }
    deletionFor?.let { target ->
        var deleting by remember(target.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!deleting) deletionFor = null },
            title = { Text("Delete this message?") },
            text = { Text("The text will be removed for both participants. This cannot be undone.") },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    scope.launch {
                        deleting = true
                        try { repository.deleteMessageForEveryone(chatId, target.id); deletionFor = null }
                        catch (e: Exception) { error = e.localizedMessage ?: "Delete failed" }
                        finally { deleting = false }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(enabled = !deleting, onClick = { deletionFor = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage, mine: Boolean,
    replyPreview: String?, isRead: Boolean,
    onClick: () -> Unit,
    onAttachment: () -> Unit
) {
    val bubbleMaxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.83f
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier.widthIn(max = bubbleMaxWidth)
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (mine) 18.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 18.dp)
                ).clickable(onClick = onClick)
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            replyPreview?.let {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) {
                    Text(it, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth().padding(8.dp), maxLines = 2)
                }
                Spacer(Modifier.height(5.dp))
            }
            if (message.isDeleted) {
                Text("This message was deleted", style = MaterialTheme.typography.bodyLarge)
            } else if (message.mediaPath.isNotBlank()) {
                Text("📎 ${message.fileName}", style = MaterialTheme.typography.bodyLarge)
                Text("${message.kind.uppercase()} · ${message.fileSize / 1024} KB",
                    style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = onAttachment) { Text("Open attachment") }
            } else {
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(3.dp))
            val timestamp = message.createdAt?.toDate()?.let {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(it)
            }.orEmpty()
            val status = if (mine) when {
                message.pending -> "Queued"
                isRead -> "Read"
                else -> "Sent"
            } else ""
            val edited = if (message.editedAt != null && !message.isDeleted) "Edited" else ""
            Text(listOf(timestamp, edited, status).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
