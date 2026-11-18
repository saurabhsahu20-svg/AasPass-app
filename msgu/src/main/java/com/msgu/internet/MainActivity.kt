package com.msgu.internet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

private val emerald = Color(0xFF087F5B)
private val lightBg = Color(0xFFF3FAF6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme(
                primary = Color(0xFF8DE3BF), secondary = Color(0xFFA9EBCF)
            ) else lightColorScheme(
                primary = emerald, onPrimary = Color.White,
                primaryContainer = Color(0xFFD1F4E0),
                background = lightBg, surface = Color.White
            )) { MsgUApplication() }
        }
    }
}

@Composable
private fun BrandTitle(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp)) {
            Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
                Text("💬", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MsgUApplication() {
    val context = LocalContext.current
    val gateway = remember { SupabaseGateway(context) }
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(
        ConfigRules.validUrl(gateway.config.url) && ConfigRules.validKey(gateway.config.publicKey)
    ) }
    var signed by remember { mutableStateOf(gateway.session != null) }
    var profile by remember { mutableStateOf<ChatProfile?>(null) }
    var checking by remember { mutableStateOf(signed) }
    var globalError by remember { mutableStateOf<String?>(null) }
    var openChat by remember { mutableStateOf<ChatItem?>(null) }
    LaunchedEffect(ready, signed) {
        if (!ready || !signed) {
            profile = null
            checking = false
        } else {
            checking = true
            try {
                gateway.ensureSession()
                profile = gateway.myProfile()
                globalError = null
            } catch (e: Exception) {
                globalError = e.localizedMessage ?: "Could not load profile"
            } finally { checking = false }
        }
    }
    when {
        !ready -> SetupScreen(gateway) { ready = true }
        !signed -> AuthScreen(gateway) { signed = true }
        checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        globalError != null -> Column(Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(globalError.orEmpty(), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { scope.launch {
                try { profile = gateway.myProfile(); globalError = null }
                catch (e: Exception) { globalError = e.localizedMessage }
            } }) { Text("Retry") }
            TextButton(onClick = { scope.launch {
                gateway.signOut(); signed = false; globalError = null
            } }) { Text("Sign out") }
        }
        profile == null -> ProfileSetup(gateway) { created -> profile = created }
        openChat != null -> ChatRoom(gateway, requireNotNull(openChat), onBack = { openChat = null })
        else -> Inbox(gateway, requireNotNull(profile), onChat = { openChat = it },
            onProfile = { profile = it }, onLogout = {
                scope.launch { gateway.signOut(); openChat = null; signed = false }
            }, onReset = {
                gateway.clearLocalSession()
                ready = false
                signed = false
            })
    }
}

@Composable
private fun SetupScreen(gateway: SupabaseGateway, onConnected: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(gateway.config.url) }
    var key by rememberSaveable { mutableStateOf(gateway.config.publicKey) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp),
        verticalArrangement = Arrangement.Center) {
        BrandTitle("MsgU", "Internet messaging • Firebase-free")
        Spacer(Modifier.height(32.dp))
        Text("Connect your Supabase project", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("MsgU needs an online database to connect two phones. Create a project, run the included SQL schema, then enter its public connection details below.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(url, { url = it.trim().take(200) }, Modifier.fillMaxWidth(),
            label = { Text("Project URL") },
            placeholder = { Text("https://YOUR-ID.supabase.co") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(key, { key = it.trim().take(1000) }, Modifier.fillMaxWidth(),
            label = { Text("Public / publishable key") },
            supportingText = { Text("NEVER enter a secret or service-role key") },
            singleLine = true)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            try { gateway.saveConfig(url,key); message = null; onConnected() }
            catch (e: Exception) { message = e.localizedMessage ?: "Invalid setup" }
        }, Modifier.fillMaxWidth().height(52.dp)) { Text("Continue to MsgU") }
        Spacer(Modifier.height(22.dp))
        Text("💎 The sticker preview is offline, but messages only work after connecting a real Supabase project.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        JellyStudio(Modifier.height(340.dp))
    }
}

@Composable
private fun AuthScreen(gateway: SupabaseGateway, onSignedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var signUp by remember { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp),
        verticalArrangement = Arrangement.Center) {
        BrandTitle("Welcome to MsgU", "Private chats • Friendly connections")
        Spacer(Modifier.height(32.dp))
        Text(if (signUp) "Create your account" else "Sign in",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(email, { email = it.take(250) }, Modifier.fillMaxWidth(),
            label = { Text("Email address") }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it.take(128) }, Modifier.fillMaxWidth(),
            label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(10.dp))
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(14.dp))
        Button(enabled = !busy && email.contains("@") && password.length >= 6,
            onClick = {
                busy = true; message = null
                scope.launch {
                    try {
                        if (signUp) {
                            val loggedIn = gateway.signUp(email,password)
                            if (loggedIn) onSignedIn()
                            else message = "Check your email to confirm your account, then sign in."
                        } else {
                            gateway.signIn(email,password)
                            onSignedIn()
                        }
                    } catch (e: Exception) { message = e.localizedMessage ?: "Could not sign in" }
                    finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(if (signUp) "Create account" else "Sign in to MsgU")
        }
        TextButton(onClick = { signUp = !signUp; message = null }) {
            Text(if (signUp) "Already registered? Sign in" else "New to MsgU? Create account")
        }
        Text("Supabase authentication uses email verification settings from your project.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileSetup(gateway: SupabaseGateway, onDone: (ChatProfile) -> Unit) {
    val scope = rememberCoroutineScope()
    var handle by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(25.dp), verticalArrangement = Arrangement.Center) {
        BrandTitle("Your profile", "Choose how friends find you")
        Spacer(Modifier.height(25.dp))
        OutlinedTextField(name, { name = it.take(50) }, Modifier.fillMaxWidth(),
            label = { Text("Display name") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(handle, { handle = it.lowercase().filter { c ->
            c.isLetterOrDigit() || c == '_'
        }.take(20) }, Modifier.fillMaxWidth(), label = { Text("Unique username") },
            prefix = { Text("@") }, singleLine = true,
            supportingText = { Text("3–20 lowercase English letters, numbers or underscore") })
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(18.dp))
        Button(enabled = !busy && name.trim().length >= 2 && ConfigRules.validUsername(handle),
            onClick = { scope.launch {
                busy = true
                try {
                    gateway.createProfile(handle,name)
                    onDone(requireNotNull(gateway.myProfile()))
                } catch (e: Exception) { error = e.localizedMessage }
                finally { busy = false }
            } }, Modifier.fillMaxWidth().height(52.dp)) { Text("Start using MsgU") }
        Text("Your username is discoverable by default so friends can send requests. You can change this in Profile.",
            modifier = Modifier.padding(top=15.dp),style=MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Inbox(
    gateway: SupabaseGateway, profile: ChatProfile,
    onChat: (ChatItem) -> Unit, onProfile: (ChatProfile) -> Unit,
    onLogout: () -> Unit, onReset: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var conversations by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var pendingRequest by remember { mutableStateOf<ChatItem?>(null) }
    var newUsername by remember { mutableStateOf("") }
    var discoverable by remember(profile.id) { mutableStateOf(profile.discoverable) }
    suspend fun refresh() {
        try {
            conversations = gateway.conversations()
            error = null
        } catch (e: Exception) { error = e.localizedMessage ?: "Unable to load chats" }
    }
    LaunchedEffect(profile.id) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { if (tab == 0) refresh(); delay(4500) }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("MsgU",fontWeight = FontWeight.ExtraBold,
            color=MaterialTheme.colorScheme.primary) }, actions = {
            if (tab == 0) {
                IconButton(onClick = { scope.launch { refresh() } }) { Icon(Icons.Default.Refresh,"Refresh chats") }
                IconButton(onClick = { showNew = true }) { Icon(Icons.Default.AddComment,"New chat") }
            }
        })
    }, bottomBar = {
        NavigationBar {
            listOf("Chats","Jelly","Profile").forEachIndexed { i,label ->
                NavigationBarItem(selected = tab == i,onClick = { tab = i },
                    icon = { Icon(when(i) {
                        0 -> Icons.Default.Chat
                        1 -> Icons.Default.Face
                        else -> Icons.Default.Person
                    },label) }, label = { Text(label) })
            }
        }
    }, floatingActionButton = {
        if (tab == 0) FloatingActionButton(onClick = { showNew = true }) {
            Icon(Icons.Default.Edit,"New conversation")
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> {
                    error?.let { Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Text("Sync issue: " + it,Modifier.padding(10.dp),
                            style=MaterialTheme.typography.bodySmall)
                    } }
                    if (conversations.isEmpty()) {
                        Column(Modifier.fillMaxSize().padding(25.dp),
                            horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.Center) {
                            Icon(Icons.Default.Forum,null,Modifier.size(68.dp),
                                tint=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(20.dp))
                            Text("No chats yet",style=MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(10.dp))
                            Text("Start by entering a friend's @username. They must accept your request.",
                                textAlign=TextAlign.Center)
                            Spacer(Modifier.height(15.dp))
                            Button(onClick={showNew=true}) { Text("Start a chat") }
                        }
                    } else LazyColumn(contentPadding=PaddingValues(vertical=8.dp)) {
                        items(conversations,key={it.id}) { chat ->
                            ListItem(
                                headlineContent = { Text(chat.peerName,fontWeight=FontWeight.SemiBold) },
                                supportingContent = {
                                    Text(when(chat.status) {
                                        "pending" -> if (chat.requestedBy == profile.id) "Request sent · awaiting approval" else "New message request · tap to respond"
                                        "declined" -> "Request declined"
                                        else -> chat.preview.ifBlank { "Tap to start messaging" }
                                    },maxLines=1,overflow=TextOverflow.Ellipsis)
                                },
                                leadingContent = {
                                    Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer) {
                                        Box(Modifier.size(53.dp),contentAlignment=Alignment.Center) {
                                            Text(chat.peerName.firstOrNull()?.uppercase() ?: "?",
                                                style=MaterialTheme.typography.titleLarge,
                                                color=MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                },
                                modifier=Modifier.clickable {
                                    if (chat.status=="accepted") onChat(chat)
                                    else pendingRequest=chat
                                }
                            )
                            HorizontalDivider(Modifier.padding(start=75.dp))
                        }
                    }
                }
                1 -> JellyStudio(Modifier.fillMaxSize().padding(10.dp))
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    BrandTitle(profile.name,"@"+profile.username)
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Allow username discovery",fontWeight=FontWeight.Medium)
                            Text("Friends can find you by username",style=MaterialTheme.typography.bodySmall)
                        }
                        Switch(discoverable,onCheckedChange={ enabled ->
                            scope.launch {
                                try {
                                    gateway.updateDiscovery(enabled)
                                    discoverable=enabled
                                    onProfile(profile.copy(discoverable=enabled))
                                } catch (e: Exception) { error=e.localizedMessage }
                            }
                        })
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("Chats use encrypted HTTPS transport and Supabase access controls. Messages are NOT end-to-end encrypted.",
                        style=MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(onClick=onLogout,Modifier.fillMaxWidth()) { Text("Sign out") }
                    TextButton(onClick=onReset,Modifier.fillMaxWidth()) { Text("Switch Supabase project") }
                    Spacer(Modifier.height(12.dp))
                    Text("Preview • Text messages only • No push notification or calling yet",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (showNew) AlertDialog(
        onDismissRequest={if(!busy)showNew=false},
        title={Text("Start a new chat")},
        text={Column{
            Text("Your friend must enable discovery and accept the request.")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(newUsername,{ newUsername=it.lowercase().take(20) },
                label={Text("Friend's @username")},singleLine=true)
        }},
        confirmButton={TextButton(enabled=!busy && ConfigRules.validUsername(newUsername),onClick={
            scope.launch {
                busy=true
                try {
                    gateway.requestChat(newUsername)
                    showNew=false;newUsername=""
                    refresh()
                } catch(e:Exception){error=e.localizedMessage}
                finally{busy=false}
            }
        }) {Text("Send request")}},
        dismissButton={TextButton(onClick={showNew=false}){Text("Cancel")}}
    )
    pendingRequest?.let { chat ->
        val incoming=chat.requestedBy!=profile.id
        AlertDialog(onDismissRequest={pendingRequest=null},
            title={Text(if(incoming)"Chat request from "+chat.peerName else "Chat request")},
            text={Text(if(chat.status=="declined") "This request was declined."
            else if(incoming)"Accept to let this person message you."
            else "Request sent; your friend needs to accept before messaging.")},
            confirmButton={
                if(incoming && chat.status=="pending") TextButton(enabled=!busy,onClick={
                    scope.launch {
                        busy=true
                        try {
                            gateway.respondToRequest(chat.id,true);pendingRequest=null;refresh()
                        } catch(e:Exception){error=e.localizedMessage}
                        finally{busy=false}
                    }
                }){Text("Accept")}
                else TextButton(onClick={pendingRequest=null}){Text("OK")}
            },
            dismissButton={
                if(incoming && chat.status=="pending") TextButton(enabled=!busy,onClick={
                    scope.launch {
                        busy=true
                        try {
                            gateway.respondToRequest(chat.id,false);pendingRequest=null;refresh()
                        }catch(e:Exception){error=e.localizedMessage}
                        finally{busy=false}
                    }
                }){Text("Decline")}
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoom(gateway: SupabaseGateway, chat: ChatItem, onBack: () -> Unit) {
    val scope=rememberCoroutineScope()
    val owner=LocalLifecycleOwner.current
    val me=gateway.session?.uid.orEmpty()
    var messages by remember(chat.id) { mutableStateOf<List<ChatText>>(emptyList()) }
    var draft by rememberSaveable(chat.id) { mutableStateOf("") }
    var sendNonce by remember(chat.id) { mutableStateOf(UUID.randomUUID().toString()) }
    var sending by remember(chat.id) { mutableStateOf(false) }
    var error by remember(chat.id) { mutableStateOf<String?>(null) }
    var emojiPicker by remember { mutableStateOf(false) }
    var showBlock by remember { mutableStateOf(false) }
    val state=rememberLazyListState()
    suspend fun refresh() {
        try {
            messages=gateway.messages(chat.id)
            error=null
        } catch(e:Exception) {error=e.localizedMessage ?: "Messages couldn't refresh"}
    }
    LaunchedEffect(chat.id) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val socket=gateway.openChatSocket(chat.id) { scope.launch { refresh() } }
            try {
                while(isActive) { refresh(); delay(3500) }
            } finally { socket?.close(1000,"Leaving chat") }
        }
    }
    LaunchedEffect(messages.lastOrNull()?.id) {
        if(messages.isNotEmpty()) state.scrollToItem(messages.lastIndex)
    }
    fun sendNow() {
        if(sending || draft.isBlank()) return
        val text=draft.trim()
        val nonce=sendNonce
        sending=true
        scope.launch {
            try {
                gateway.send(chat.id,text,nonce)
                draft=""
                sendNonce=UUID.randomUUID().toString()
                refresh()
            } catch(e:Exception) {
                // Preserve the exact draft and nonce for a safe retry.
                error="Message not sent: " + (e.localizedMessage ?: "Network error")
            } finally { sending=false }
        }
    }
    Scaffold(topBar={
        TopAppBar(title={
            Column {
                Text(chat.peerName,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text("Private text chat • not end-to-end encrypted",
                    style=MaterialTheme.typography.labelSmall)
            }
        },navigationIcon={IconButton(onClick=onBack){
            Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},
          actions={IconButton(onClick={showBlock=true}){Icon(Icons.Default.MoreVert,"Block options")}})
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            error?.let { Text(it,Modifier.padding(12.dp),
                color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodySmall) }
            if(messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                Text("Say hello! 👋",color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=state,
                contentPadding=PaddingValues(horizontal=14.dp,vertical=12.dp),
                verticalArrangement=Arrangement.spacedBy(7.dp)) {
                items(messages,key={it.id}){msg->
                    val mine=msg.author==me
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start) {
                        Surface(shape=RoundedCornerShape(
                            topStart=18.dp,topEnd=18.dp,
                            bottomStart=if(mine)18.dp else 4.dp,
                            bottomEnd=if(mine)4.dp else 18.dp),
                            color=if(mine)MaterialTheme.colorScheme.primaryContainer
                                  else MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.widthIn(max=290.dp).padding(horizontal=12.dp,vertical=9.dp)) {
                                Text(msg.body)
                                Spacer(Modifier.height(4.dp))
                                Text(msg.time.drop(11).take(5) + if(mine)" · Saved" else "",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier=Modifier.align(Alignment.End))
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=7.dp),
                verticalAlignment=Alignment.Bottom) {
                IconButton(onClick={emojiPicker=true}) {
                    Icon(Icons.Default.Face,"Insert emoji")
                }
                OutlinedTextField(draft,{ typed->
                    draft=typed.take(3000)
                    sendNonce=UUID.randomUUID().toString()
                },Modifier.weight(1f),enabled=!sending,
                    shape=RoundedCornerShape(24.dp),maxLines=5,
                    placeholder={Text("Message")},
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),
                    keyboardActions=KeyboardActions(onSend={sendNow()}))
                IconButton(enabled=!sending && draft.isNotBlank(),onClick={sendNow()}) {
                    if(sending) CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send,"Send",tint=MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if(emojiPicker) EmojiQuickPicker(onDismiss={emojiPicker=false},
        onPick={emoji->draft=(draft+emoji).take(3000);emojiPicker=false})
    if(showBlock) AlertDialog(onDismissRequest={showBlock=false},
        title={Text("Block this account?")},
        text={Text("You will no longer be able to send messages to each other in this chat.")},
        confirmButton={TextButton(onClick={
            scope.launch{
                try { gateway.block(chat.peerId);showBlock=false;onBack() }
                catch(e:Exception){error=e.localizedMessage;showBlock=false}
            }
        }){Text("Block")}},
        dismissButton={TextButton(onClick={showBlock=false}){Text("Cancel")}}
    )
}
