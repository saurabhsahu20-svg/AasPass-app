package com.aaspass.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private data class MChat(val id: String, val peer: String, val status: String, val incoming: Boolean)
private data class MMessage(val id: String, val text: String, val sender: String)
private class MBackend(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("msgu_supabase", Context.MODE_PRIVATE)
    private var url = prefs.getString("url", "").orEmpty()
    private var key = prefs.getString("key", "").orEmpty()
    private var token = prefs.getString("token", "").orEmpty()
    private var refresh = prefs.getString("refresh", "").orEmpty()
    private var expires = prefs.getLong("expires", 0)
    var uid: String = prefs.getString("uid", "").orEmpty()
        private set
    val configured get() = url.isNotEmpty() && key.isNotEmpty()
    val authenticated get() = uid.isNotEmpty() && refresh.isNotEmpty()
    fun setup(projectUrl: String, publishKey: String) {
        val clean = projectUrl.trim().trimEnd('/')
        val pk = publishKey.trim()
        require(Regex("https://[a-z0-9-]+\\.supabase\\.co", RegexOption.IGNORE_CASE).matches(clean)) {
            "Use your Supabase https://project.supabase.co URL"
        }
        require(pk.startsWith("sb_publishable_") || pk.startsWith("eyJ")) {
            "Use a publishable or legacy anon key, never a secret key"
        }
        if (url != clean || key != pk) signOut()
        url = clean; key = pk
        prefs.edit().putString("url", url).putString("key", key).apply()
    }
    fun signOut() {
        token = ""; refresh = ""; uid = ""; expires = 0L
        prefs.edit().remove("token").remove("refresh").remove("uid").remove("expires").apply()
    }
    private suspend fun request(method: String, path: String, body: JSONObject? = null, signed: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val conn = URL(url + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 12000
                conn.readTimeout = 16000
                conn.setRequestProperty("apikey", key)
                conn.setRequestProperty("Accept", "application/json")
                if (signed) conn.setRequestProperty("Authorization", "Bearer " + token)
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = conn.responseCode
                val value = (if (status in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val message = runCatching { JSONObject(value).let {
                        it.optString("msg").ifBlank { it.optString("message").ifBlank {
                            it.optString("error_description").ifBlank { it.optString("error") }
                        } }
                    } }.getOrDefault("")
                    error(if (status == 401 || status == 403) "Permission denied or session expired"
                          else message.ifBlank { "Network/server error " + status }.take(180))
                }
                value
            } finally { conn.disconnect() }
        }
    private fun saveSession(json: JSONObject) {
        token = json.optString("access_token")
        refresh = json.optString("refresh_token", refresh)
        uid = json.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() } ?: uid
        expires = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000L
        prefs.edit().putString("token", token).putString("refresh", refresh)
            .putString("uid", uid).putLong("expires", expires).apply()
    }
    suspend fun authenticate(email: String, pass: String, create: Boolean, name: String): Boolean {
        require(!create || pass.length >= 8) { "Password must be at least 8 characters" }
        val path = if (create) "/auth/v1/signup" else "/auth/v1/token?grant_type=password"
        val body = JSONObject().put("email", email.trim()).put("password", pass)
        if (create) body.put("data", JSONObject().put("display_name", name.trim().take(50)))
        saveSession(JSONObject(request("POST", path, body)))
        return authenticated
    }
    private suspend fun authenticatedRequest(method: String, path: String, body: JSONObject? = null): String {
        check(authenticated) { "Please sign in" }
        if (System.currentTimeMillis() >= expires - 90000) {
            val json = JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", refresh)))
            saveSession(json)
        }
        return request(method, "/rest/v1/" + path, body, true)
    }
    suspend fun profile(): JSONObject =
        JSONArray(authenticatedRequest("GET", "msgu_profiles?select=id,username,display_name,discoverable&id=eq." + uid))
            .optJSONObject(0) ?: error("Profile missing: run the Supabase setup SQL")
    suspend fun saveProfile(name: String, username: String, searchable: Boolean) {
        require(name.trim().length in 2..50) { "Display name must be 2–50 characters" }
        require(Regex("[a-z0-9_]{3,20}").matches(username)) { "Username must be 3–20 lowercase letters, numbers or _" }
        authenticatedRequest("PATCH", "msgu_profiles?id=eq." + uid, JSONObject()
            .put("display_name", name.trim()).put("username", username).put("discoverable", searchable))
    }
    suspend fun startChat(handle: String): String {
        val username = handle.trim().lowercase(Locale.ROOT)
        require(Regex("[a-z0-9_]{3,20}").matches(username)) { "Enter an exact username" }
        val id = authenticatedRequest("POST", "rpc/msgu_start_chat", JSONObject().put("handle", username))
            .trim().trim('"')
        require(Regex("[0-9a-f-]{36}").matches(id)) { "Unable to create chat request" }
        return id
    }
    suspend fun chats(): List<MChat> {
        val arr = JSONArray(authenticatedRequest("GET", "msgu_chats?select=id,user_a,user_b,status,requested_by&or=(user_a.eq."
            + uid + ",user_b.eq." + uid + ")&order=created_at.desc&limit=100"))
        return (0 until arr.length()).mapNotNull { i ->
            val row = arr.optJSONObject(i) ?: return@mapNotNull null
            val a = row.optString("user_a"); val b = row.optString("user_b")
            MChat(row.optString("id"), (if (a == uid) b else a).take(12),
                row.optString("status"), row.optString("requested_by") != uid)
        }
    }
    suspend fun messages(chatId: String): List<MMessage> {
        val arr = JSONArray(authenticatedRequest("GET",
            "msgu_messages?select=id,body,sender_id&chat_id=eq." + chatId + "&order=created_at.desc&limit=100"))
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let {
            MMessage(it.optString("id"), it.optString("body"), it.optString("sender_id"))
        } }.asReversed()
    }
    suspend fun send(chatId: String, text: String) {
        val clean = text.trim()
        require(clean.length in 1..3000) { "Message must contain 1–3000 characters" }
        authenticatedRequest("POST", "msgu_messages",
            JSONObject().put("chat_id", chatId).put("sender_id", uid).put("body", clean))
    }
    suspend fun decide(chatId: String, accept: Boolean) {
        authenticatedRequest("PATCH", "msgu_chats?id=eq." + chatId,
            JSONObject().put("status", if (accept) "accepted" else "rejected"))
    }
}

@Composable
fun MsgUCloudScreen() {
    val context = LocalContext.current
    val api = remember { MBackend(context.applicationContext) }
    var page by remember { mutableStateOf(if (!api.configured) 0 else if (api.authenticated) 2 else 1) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var chats by remember { mutableStateOf<List<MChat>>(emptyList()) }
    var chat by remember { mutableStateOf<MChat?>(null) }
    var messages by remember { mutableStateOf<List<MMessage>>(emptyList()) }
    var profileDialog by remember { mutableStateOf(false) }
    var newDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var searchable by remember { mutableStateOf(false) }
    fun runJob(block: suspend () -> Unit) {
        if (busy) return
        busy = true; errorText = ""
        scope.launch {
            try { block() }
            catch (c: CancellationException) { throw c }
            catch (e: Exception) { errorText = e.message ?: "Unable to connect" }
            finally { busy = false }
        }
    }
    LaunchedEffect(page) {
        if (page == 2) {
            try {
                val p = api.profile()
                name = p.optString("display_name")
                username = p.optString("username")
                searchable = p.optBoolean("discoverable")
            } catch (e: Exception) { errorText = e.message ?: "Profile unavailable" }
        }
    }
    LaunchedEffect(page, chat?.id, chat?.status) {
        if (page != 2) return@LaunchedEffect
        while (isActive) {
            try {
                if (chat == null) chats = api.chats()
                else if (chat?.status == "accepted") messages = api.messages(chat!!.id)
            } catch (c: CancellationException) { throw c }
            catch (e: Exception) { errorText = e.message ?: "Sync failed" }
            delay(if (chat == null) 5000L else 2500L)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (chat != null) TextButton(onClick = { chat = null; messages = emptyList() }) { Text("‹") }
                Column(Modifier.weight(1f)) {
                    Text("MsgU", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Connect • Chat • Share", style = MaterialTheme.typography.labelSmall)
                }
                if (page == 2 && chat == null) {
                    TextButton(onClick = { profileDialog = true }) { Text("Profile") }
                    TextButton(onClick = { api.signOut(); page = 1 }) { Text("Logout") }
                }
            }
        }
        if (errorText.isNotEmpty()) Text(errorText,
            Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.error)
        if (page == 0) {
            var url by rememberSaveable { mutableStateOf("") }
            var key by rememberSaveable { mutableStateOf("") }
            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center) {
                Text("Internet Chat Setup · No Firebase", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text("Create a Supabase project, run the included SQL, then enter its public project connection details.")
                OutlinedTextField(url, { url = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Project URL (https://...supabase.co)") })
                OutlinedTextField(key, { key = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Publishable API key") })
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    try { api.setup(url, key); page = 1; errorText = "" }
                    catch (e: Exception) { errorText = e.message.orEmpty() }
                }) { Text("Connect MsgU") }
                Text("Never enter a secret or service_role key.", style = MaterialTheme.typography.bodySmall)
            }
        } else if (page == 1) {
            var email by rememberSaveable { mutableStateOf("") }
            var pass by rememberSaveable { mutableStateOf("") }
            var nickname by rememberSaveable { mutableStateOf("") }
            var signup by rememberSaveable { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center) {
                Text(if (signup) "Create MsgU account" else "Welcome to MsgU",
                    style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Email") })
                OutlinedTextField(pass, { pass = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Password") })
                if (signup) OutlinedTextField(nickname, { nickname = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Display name") })
                Spacer(Modifier.height(12.dp))
                Button(enabled = !busy && email.isNotBlank() && pass.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(), onClick = { runJob {
                        if (api.authenticate(email, pass, signup, nickname)) page = 2
                        else errorText = "Check your inbox to confirm your email, then sign in."
                    } }) { Text(if (busy) "Connecting…" else if (signup) "Sign up" else "Sign in") }
                TextButton(onClick = { signup = !signup }) {
                    Text(if (signup) "Already have an account?" else "Create account")
                }
                TextButton(onClick = { page = 0 }) { Text("Change backend") }
            }
        } else if (chat == null) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Your chats", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Button(onClick = { newDialog = true }) { Text("+ New chat") }
            }
            LazyColumn {
                items(chats, key = { it.id }) { item ->
                    ElevatedCard(onClick = { chat = item; messages = emptyList() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Contact " + item.peer, fontWeight = FontWeight.Bold)
                            Text(when (item.status) {
                                "accepted" -> "Open conversation"
                                "requested" -> if (item.incoming) "Chat request received" else "Waiting for acceptance"
                                else -> "Request declined"
                            }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            val active = chat!!
            if (active.status == "requested" && active.incoming) {
                Column(Modifier.padding(18.dp)) {
                    Text("Someone wants to chat with you.")
                    Button(enabled = !busy, onClick = { runJob {
                        api.decide(active.id, true); chat = active.copy(status = "accepted")
                    } }) { Text("Accept chat request") }
                    TextButton(enabled = !busy, onClick = { runJob {
                        api.decide(active.id, false); chat = null
                    } }) { Text("Decline") }
                }
            } else if (active.status != "accepted") {
                Text(if (active.status == "requested") "Waiting for the recipient to accept"
                    else "Chat request was declined", Modifier.padding(20.dp))
            } else {
                val scroll = rememberLazyListState()
                LaunchedEffect(messages.lastOrNull()?.id) {
                    if (messages.isNotEmpty()) scroll.scrollToItem(messages.lastIndex)
                }
                LazyColumn(Modifier.weight(1f), state = scroll,
                    contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages, key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement =
                            if (item.sender == api.uid) Arrangement.End else Arrangement.Start) {
                            Surface(shape = RoundedCornerShape(16.dp),
                                color = if (item.sender == api.uid) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant) {
                                Text(item.text, Modifier.widthIn(max = 290.dp).padding(12.dp))
                            }
                        }
                    }
                }
                var draft by rememberSaveable(active.id) { mutableStateOf("") }
                var sending by remember { mutableStateOf(false) }
                fun send() {
                    if (sending || draft.isBlank()) return
                    val text = draft.trim()
                    sending = true
                    scope.launch {
                        try { api.send(active.id, text); draft = ""; messages = api.messages(active.id); errorText = "" }
                        catch (e: Exception) { errorText = "Not sent: " + (e.message ?: "please retry") }
                        finally { sending = false }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(draft, { draft = it.take(3000) }, Modifier.weight(1f),
                        placeholder = { Text("Message") }, maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }))
                    Button(enabled = !sending && draft.isNotBlank(), onClick = { send() }) { Text("Send") }
                }
            }
        }
    }
    if (newDialog) {
        var contact by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { newDialog = false }, title = { Text("New MsgU chat") },
            text = { OutlinedTextField(contact, { contact = it.lowercase(Locale.ROOT) },
                label = { Text("Exact username") }) },
            confirmButton = { TextButton(enabled = !busy && contact.isNotBlank(), onClick = { runJob {
                val id = api.startChat(contact)
                chats = api.chats()
                chat = chats.firstOrNull { it.id == id }
                newDialog = false
            } }) { Text("Send chat request") } },
            dismissButton = { TextButton(onClick = { newDialog = false }) { Text("Cancel") } })
    }
    if (profileDialog) {
        AlertDialog(onDismissRequest = { profileDialog = false }, title = { Text("MsgU profile") },
            text = { Column {
                OutlinedTextField(name, { name = it }, label = { Text("Display name") })
                OutlinedTextField(username, { username = it.lowercase(Locale.ROOT) }, label = { Text("Username") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(searchable, { searchable = it })
                    Text("Allow username discovery")
                }
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = { runJob {
                api.saveProfile(name, username, searchable)
                profileDialog = false
            } }) { Text("Save profile") } },
            dismissButton = { TextButton(onClick = { profileDialog = false }) { Text("Cancel") } })
    }
}
