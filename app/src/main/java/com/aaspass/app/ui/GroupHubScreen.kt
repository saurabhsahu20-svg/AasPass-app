package com.aaspass.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aaspass.app.data.AasPassGroup
import com.aaspass.app.data.AasPassGroupInvite
import com.aaspass.app.data.GroupRepository
import com.aaspass.app.data.GroupInputRules
import com.aaspass.app.data.UserProfile
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun GroupHubScreen(
    repository: GroupRepository,
    profile: UserProfile,
    kind: String,
    onOpenGroup: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val sourceGroups = remember(profile.id) { repository.groups(profile.id) }
    val sourceInvites = remember(profile.id) { repository.invites(profile.id) }
    var error by remember { mutableStateOf<String?>(null) }
    val groups by remember(sourceGroups) {
        sourceGroups.catch { error = it.localizedMessage ?: "Unable to load groups"; emit(emptyList()) }
    }.collectAsState(initial = emptyList())
    val invites by remember(sourceInvites) {
        sourceInvites.catch { error = it.localizedMessage ?: "Unable to load invites"; emit(emptyList()) }
    }.collectAsState(initial = emptyList())
    val shownGroups = groups.filter { it.kind == kind }
    val shownInvites = invites.filter { it.groupKind == kind }
    var creating by remember { mutableStateOf(false) }
    var busyInvite by remember { mutableStateOf<String?>(null) }
    val isStudy = kind == "study"

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(if (isStudy) "Study Hub" else "Your groups",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(if (isStudy) "Private study discussions and admin announcements"
                     else "Private, invitation-only group conversations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (shownInvites.isNotEmpty()) {
                item { Text("Invitations", style = MaterialTheme.typography.titleMedium) }
                items(shownInvites, key = { "invite_${it.id}" }) { invite ->
                    GroupInvitationCard(invite, busyInvite == invite.id,
                        onAccept = {
                            scope.launch {
                                busyInvite = invite.id
                                try {
                                    repository.respondToInvite(invite.id, true)
                                    onOpenGroup(invite.groupId)
                                } catch (e: Exception) { error = e.localizedMessage ?: "Invite could not be accepted" }
                                finally { busyInvite = null }
                            }
                        },
                        onDecline = {
                            scope.launch {
                                busyInvite = invite.id
                                try { repository.respondToInvite(invite.id, false) }
                                catch (e: Exception) { error = e.localizedMessage ?: "Unable to decline" }
                                finally { busyInvite = null }
                            }
                        })
                }
            }
            item { Text(if (isStudy) "My study groups" else "My groups",
                style = MaterialTheme.typography.titleMedium) }
            if (shownGroups.isEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(if (isStudy) Icons.Default.School else Icons.Default.Groups, null,
                                Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("No ${if (isStudy) "study groups" else "groups"} yet")
                            Text("Create one or accept a private invitation.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            items(shownGroups, key = { "group_${it.id}" }) { group ->
                GroupCard(group, onOpen = { onOpenGroup(group.id) })
            }
        }
        FloatingActionButton(onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Default.Add, "Create ${if (isStudy) "study group" else "group"}")
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var dialogError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) creating = false },
            title = { Text(if (isStudy) "Create study group" else "Create private group") },
            text = {
                Column {
                    Text("Only invited users can join. Group size is limited to 50 members.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text("Group name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && GroupInputRules.validGroupName(name), onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val groupId = repository.createGroup(name, kind)
                            creating = false
                            onOpenGroup(groupId)
                        } catch (e: Exception) { dialogError = e.localizedMessage ?: "Creation failed" }
                        finally { busy = false }
                    }
                }) { Text(if (busy) "Creating…" else "Create") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { creating = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GroupInvitationCard(
    invite: AasPassGroupInvite,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(invite.groupName, fontWeight = FontWeight.Bold)
            Text("You were invited to a private ${if (invite.groupKind == "study") "study group" else "group"}.",
                style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = onAccept, enabled = !busy) { Text("Accept") }
                TextButton(onClick = onDecline, enabled = !busy) { Text("Decline") }
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun GroupCard(group: AasPassGroup, onOpen: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Text(group.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(group.lastMessage.ifBlank { "${group.members.size} member(s) · Start a conversation" },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
