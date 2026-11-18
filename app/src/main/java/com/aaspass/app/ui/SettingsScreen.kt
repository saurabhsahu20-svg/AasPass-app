package com.aaspass.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.aaspass.app.data.AasPassRepository
import com.aaspass.app.data.UserProfile
import com.aaspass.app.data.PrivacyPreferences
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repository: AasPassRepository, profile: UserProfile) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var shareReceipts by remember(profile.id) {
        mutableStateOf(PrivacyPreferences.shareReadReceipts(context, profile.id))
    }
    var error by remember { mutableStateOf<String?>(null) }
    var changingDiscovery by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Android owns the permission result; notification delivery also depends on OS settings. */ }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Text("My profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
        Text("@${profile.username}", color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { editName = true }) { Text("Edit display name") }
        Spacer(Modifier.height(24.dp))
        Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Allow username discovery")
                Text("Off by default. Enable it to receive new chat requests.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = profile.discoverable, enabled = !changingDiscovery, onCheckedChange = { enabled ->
                scope.launch {
                    changingDiscovery = true
                    try { repository.setDiscoverable(profile, enabled) }
                    catch (e: Exception) { error = e.localizedMessage ?: "Unable to update privacy" }
                    finally { changingDiscovery = false }
                }
            })
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Share read receipts")
                Text("Off by default on this device. When enabled, opening a chat lets the sender see Read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = shareReceipts, onCheckedChange = { enabled ->
                PrivacyPreferences.setShareReadReceipts(context, profile.id, enabled)
                shareReceipts = enabled
            })
        }
        Spacer(Modifier.height(12.dp))
        Text("Messages and attachments use Firebase security rules, but are not end-to-end encrypted. Calls use WebRTC secure media transport when configured. Actual device-delivery receipts are not available.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
        Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Enable message notifications")
            }
        } else {
            Text("System notifications available when Firebase Cloud Messaging is configured.",
                style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = {
            scope.launch {
                try { repository.logout() }
                catch (e: Exception) { error = e.localizedMessage ?: "Unable to sign out safely" }
            }
        }) { Text("Sign out") }
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = { deleteDialog = true }) {
            Text("Permanently delete my AasPass account", color = MaterialTheme.colorScheme.error)
        }
    }
    if (deleteDialog) {
        var password by remember { mutableStateOf("") }
        var confirmation by remember { mutableStateOf("") }
        var deleting by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteDialog = false },
            title = { Text("Delete AasPass account permanently?") },
            text = {
                Column {
                    Text("Your profile, your direct conversations (including the other person's copies), owned groups, and uploaded media will be deleted. Other people's safety reports may be retained. This cannot be undone.")
                    Spacer(Modifier.height(12.dp))
                    Text("Type DELETE to confirm")
                    OutlinedTextField(confirmation, { confirmation = it },
                        label = { Text("Confirmation") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(password, { password = it },
                        label = { Text("Current password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                TextButton(enabled = !deleting && confirmation == "DELETE" && password.isNotBlank(),
                    onClick = {
                        scope.launch {
                            deleting = true
                            try {
                                repository.deleteMyAccount(password)
                                password = ""
                                deleteDialog = false
                            } catch (e: Exception) {
                                error = e.localizedMessage ?: "Account deletion failed; try again or contact support"
                            } finally { deleting = false }
                        }
                    }) { Text(if (deleting) "Deleting…" else "Delete permanently") }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { deleteDialog = false }) { Text("Cancel") }
            }
        )
    }
    if (editName) {
        var editedName by remember { mutableStateOf(profile.displayName) }
        AlertDialog(
            onDismissRequest = { editName = false }, title = { Text("Display name") },
            text = { OutlinedTextField(editedName, { editedName = it.take(50) }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try { repository.changeDisplayName(editedName); editName = false }
                    catch (e: Exception) { error = e.localizedMessage }
                }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } }
        )
    }
}
