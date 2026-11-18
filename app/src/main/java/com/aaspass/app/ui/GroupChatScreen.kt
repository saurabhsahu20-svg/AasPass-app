package com.aaspass.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aaspass.app.data.AasPassGroup
import com.aaspass.app.data.AasPassGroupMessage
import com.aaspass.app.data.GroupRepository
import com.aaspass.app.data.TimelineMerge
import com.aaspass.app.data.MediaRepository
import com.aaspass.app.data.UserProfile
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    repository: GroupRepository,
    profile: UserProfile,
    groupId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepository() }
    var uploading by remember(groupId) { mutableStateOf(false) }
    var transferError by remember(groupId) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            uploading = true
            try { mediaRepository.upload(context, "group", groupId, uri) }
            catch (e: Exception) { transferError = e.localizedMessage ?: "Attachment failed" }
            finally { uploading = false }
        }
    }
    val groupFlow = remember(groupId) { repository.group(groupId) }
    val messageFlow = remember(groupId) { repository.messages(groupId) }
    var error by remember(groupId) { mutableStateOf<String?>(null) }
    var groupLoaded by remember(groupId) { mutableStateOf(false) }
    val group by remember(groupFlow) {
        groupFlow
            .onEach { groupLoaded = true; error = null }
            .catch { error = it.localizedMessage ?: "Group is unavailable"; groupLoaded = true; emit(null) }
    }.collectAsState(initial = null)
    val liveMessages by remember(messageFlow) {
        messageFlow.catch { error = it.localizedMessage ?: "Messages unavailable"; emit(emptyList()) }
    }.collectAsState(initial = emptyList())
    var olderMessages by remember(groupId) { mutableStateOf<List<AasPassGroupMessage>>(emptyList()) }
    var loadingOlder by remember(groupId) { mutableStateOf(false) }
    var hasOlder by remember(groupId) { mutableStateOf(true) }
    val messages = remember(liveMessages, olderMessages) {
        TimelineMerge.latestWins(liveMessages, olderMessages) { it.id }
            .sortedWith(compareBy<AasPassGroupMessage> {
                it.createdAt?.toDate()?.time ?: Long.MAX_VALUE
            }.thenBy { it.id })
    }
    val myUid = profile.id
    val isAdmin = group?.admins?.contains(myUid) == true
    var input by rememberSaveable(groupId) { mutableStateOf("") }
    var announcement by remember(groupId) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var inviteDialog by remember { mutableStateOf(false) }
    var membersDialog by remember { mutableStateOf(false) }
    var leaveConfirm by remember { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    // Auto-scroll for the first load and our own new messages. Do not yank the
    // list away when someone replies while the user is reading older history.
    var previousLastMessageId by remember(groupId) { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.lastOrNull()?.id) {
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        if (previousLastMessageId == last.id) return@LaunchedEffect
        val visible = scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalBefore = scroll.layoutInfo.totalItemsCount
        val wasNearBottom = totalBefore == 0 || visible >= totalBefore - 3
        if (previousLastMessageId == null || wasNearBottom || last.senderId == myUid) {
            scroll.animateScrollToItem(messages.lastIndex + (if (hasOlder) 1 else 0))
        }
        previousLastMessageId = last.id
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(group?.name ?: "AasPass group", maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text("${group?.members?.size ?: 0} members · ${if (group?.kind == "study") "Study" else "Group"}",
                        style = MaterialTheme.typography.labelSmall)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Group actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Members") }, onClick = {
                            menu = false; membersDialog = true
                        })
                        if (isAdmin) DropdownMenuItem(text = { Text("Invite member") }, onClick = {
                            menu = false; inviteDialog = true
                        })
                        if (group != null && group?.ownerId != myUid) {
                            DropdownMenuItem(text = { Text("Leave group") }, onClick = {
                                menu = false; leaveConfirm = true
                            })
                        }
                    }
                }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            transferError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
            if (!groupLoaded && group == null && error == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() }
            } else if (group == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    Text("Group unavailable or you are no longer a member")
                }
            } else {
                LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (hasOlder && messages.isNotEmpty()) item(key = "older") {
                        Box(Modifier.fillMaxWidth(), Alignment.Center) {
                            TextButton(enabled = !loadingOlder, onClick = {
                                val before = messages.firstNotNullOfOrNull { it.createdAt }
                                if (before != null) scope.launch {
                                    loadingOlder = true
                                    try {
                                        val page = repository.loadOlderMessages(groupId, before)
                                        olderMessages = TimelineMerge.latestWins(olderMessages, page) { it.id }
                                        if (page.size < 50) hasOlder = false
                                    } catch (e: Exception) { error = e.localizedMessage ?: "Older messages unavailable" }
                                    finally { loadingOlder = false }
                                }
                            }) { Text(if (loadingOlder) "Loading…" else "Load older messages") }
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        GroupMessageBubble(message, myUid, requireNotNull(group), onAttachment = {
                            scope.launch {
                                try { mediaRepository.open(context, message.mediaPath, message.contentType) }
                                catch (e: Exception) { transferError = e.localizedMessage ?: "Cannot open file" }
                            }
                        })
                    }
                }
                if (isAdmin) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = announcement, onCheckedChange = { announcement = it })
                        Text("Admin announcement", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (uploading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom) {
                    fun send() {
                        if (input.isBlank()) return
                        try {
                            repository.enqueueMessage(groupId, myUid, input, announcement && isAdmin) {
                                error = it
                            }
                            input = ""
                            announcement = false
                        } catch (e: Exception) { error = e.localizedMessage ?: "Send failed" }
                    }
                    IconButton(onClick = { picker.launch(MediaRepository.MIME_TYPES) }, enabled = !uploading) {
                        Icon(Icons.Default.AttachFile, "Attach study notes or media")
                    }
                    OutlinedTextField(input, { input = it.take(3000) },
                        modifier = Modifier.weight(1f), maxLines = 5,
                        placeholder = { Text("Message group") },
                        shape = RoundedCornerShape(22.dp))
                    IconButton(enabled = input.isNotBlank(), onClick = { send() }) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send group message",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (inviteDialog) {
        var username by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var dialogError by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { if (!busy) inviteDialog = false },
            title = { Text("Invite a member") },
            text = {
                Column {
                    Text("Enter an exact username. The person must have discovery enabled and accept your invitation.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(username, { username = it }, label = { Text("Username") },
                        singleLine = true)
                    dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && username.isNotBlank(), onClick = {
                    scope.launch {
                        busy = true
                        try {
                            repository.invite(groupId, username)
                            inviteDialog = false
                        } catch (e: Exception) { dialogError = e.localizedMessage ?: "Could not invite" }
                        finally { busy = false }
                    }
                }) { Text(if (busy) "Sending…" else "Invite") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { inviteDialog = false }) { Text("Cancel") } }
        )
    }
    if (membersDialog) {
        val activeGroup = group
        AlertDialog(onDismissRequest = { membersDialog = false },
            title = { Text("Members") },
            text = {
                Column {
                    activeGroup?.members.orEmpty().forEach { uid ->
                        val memberName = activeGroup?.memberNames?.get(uid)?.take(50) ?: "Member"
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(memberName + (if (uid == myUid) " (you)" else ""),
                                modifier = Modifier.weight(1f), maxLines = 1)
                            if (isAdmin && uid != myUid && uid != activeGroup?.ownerId) {
                                TextButton(onClick = {
                                    scope.launch {
                                        try { repository.removeMember(groupId, uid) }
                                        catch (e: Exception) { error = e.localizedMessage ?: "Unable to remove member" }
                                    }
                                }) { Text("Remove") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { membersDialog = false }) { Text("Done") } }
        )
    }
    if (leaveConfirm) {
        var leaving by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { if (!leaving) leaveConfirm = false },
            title = { Text("Leave this group?") },
            text = { Text("You will lose access to the group until a new invitation is approved.") },
            confirmButton = {
                TextButton(enabled = !leaving, onClick = {
                    scope.launch {
                        leaving = true
                        try { repository.leaveGroup(groupId); leaveConfirm = false; onBack() }
                        catch (e: Exception) { error = e.localizedMessage ?: "Unable to leave" }
                        finally { leaving = false }
                    }
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(enabled = !leaving, onClick = { leaveConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GroupMessageBubble(message: AasPassGroupMessage, myUid: String,
                               group: AasPassGroup, onAttachment: () -> Unit) {
    val mine = message.senderId == myUid
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(Modifier.fillMaxWidth(0.87f)
            .background(if (mine) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(16.dp))
            .padding(12.dp)) {
            Text(if (mine) "You" else group.memberNames[message.senderId] ?: "Former member",
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium)
            if (message.kind == "announcement") {
                Spacer(Modifier.height(4.dp))
                Text("📌 Announcement", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(4.dp))
            if (message.mediaPath.isNotBlank()) {
                Text("📎 ${message.fileName}", style = MaterialTheme.typography.bodyMedium)
                Text("${message.kind.uppercase()} · ${message.fileSize / 1024} KB",
                    style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = onAttachment) { Text("Open attachment") }
            } else {
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            val time = message.createdAt?.toDate()?.let {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(it)
            }.orEmpty()
            Text(listOf(time, if (mine) (if (message.pending) "Queued" else "Sent") else "")
                .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End))
        }
    }
}
