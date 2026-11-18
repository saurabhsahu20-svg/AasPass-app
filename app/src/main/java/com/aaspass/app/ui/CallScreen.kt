package com.aaspass.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aaspass.app.calls.AasPassCall
import com.aaspass.app.calls.CallRepository
import com.aaspass.app.calls.RtcSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.webrtc.VideoTrack
import org.webrtc.SurfaceViewRenderer
import java.text.DateFormat

@Composable
fun CallsTab(repository: CallRepository, uid: String, onOpenCall: (String) -> Unit) {
    val flow = remember(uid) { repository.callHistory(uid) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    val calls by remember(flow) {
        flow.catch { error = it.localizedMessage ?: "Unable to load call history"; emit(emptyList()) }
    }.collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize()) {
        Text("Your calls", Modifier.padding(start = 18.dp, top = 18.dp, bottom = 4.dp),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Tap an incoming call to answer. Start new calls from an accepted chat.",
            Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.error) }
        if (calls.isEmpty() && error == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No calls yet") }
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp)) {
                items(calls, key = { it.id }) { call ->
                    val incoming = call.calleeUid == uid && call.status == "ringing" &&
                        (call.expiresAt?.toDate()?.time ?: 0L) > System.currentTimeMillis()
                    Surface(tonalElevation = if (incoming) 4.dp else 0.dp, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (call.kind == "video") Icons.Default.Videocam else Icons.Default.Call,
                                null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(call.peerName(uid), fontWeight = FontWeight.SemiBold)
                                Text("${call.kind.replaceFirstChar { it.uppercase() }} · ${call.status}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(call.createdAt?.toDate()?.let {
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it)
                                }.orEmpty(), style = MaterialTheme.typography.labelSmall)
                            }
                            if (incoming) Button(onClick = { onOpenCall(call.id) }) { Text("Answer") }
                            else if (call.isLive() && call.callerUid == uid)
                                TextButton(onClick = { onOpenCall(call.id) }) { Text("Open") }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(repository: CallRepository, callId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = remember { repository.currentUid }
    val flow = remember(callId) { repository.call(callId) }
    var error by remember(callId) { mutableStateOf<String?>(null) }
    val call by remember(flow) {
        flow.catch { error = it.localizedMessage ?: "Unable to load call"; emit(null) }
    }.collectAsState(initial = null)
    var session by remember(callId) { mutableStateOf<RtcSession?>(null) }
    var remoteVideo by remember(callId) { mutableStateOf<VideoTrack?>(null) }
    var rtcStatus by remember(callId) { mutableStateOf("Not connected") }
    var busy by remember(callId) { mutableStateOf(false) }
    var initiated by remember(callId) { mutableStateOf(false) }
    var answering by remember(callId) { mutableStateOf(false) }
    var remoteAnswerApplied by remember(callId) { mutableStateOf(false) }
    var muted by remember(callId) { mutableStateOf(false) }
    var cameraOff by remember(callId) { mutableStateOf(false) }
    var speaker by remember(callId) { mutableStateOf(true) }
    var permissionGranted by remember(callId) { mutableStateOf(false) }
    var acceptAfterPermission by remember(callId) { mutableStateOf(false) }

    val permissions = remember(call?.kind) {
        if (call?.kind == "video") arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result.values.all { it }
        if (permissionGranted && acceptAfterPermission) {
            acceptAfterPermission = false
            scope.launch {
                try { repository.respond(callId, true) }
                catch (e: Exception) { error = e.localizedMessage ?: "Cannot answer call" }
            }
        }
        if (!permissionGranted) error = "Microphone${if (call?.kind == "video") " and camera" else ""} permission required."
    }
    fun ensurePermissions(): Boolean {
        permissionGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (!permissionGranted) permissionLauncher.launch(permissions)
        return permissionGranted
    }

    // The screen owns the live camera/microphone session and releases it on exit.
    DisposableEffect(callId, session) {
        onDispose { session?.close() }
    }
    DisposableEffect(session, callId) {
        val registration = session?.let { live ->
            repository.observeRemoteCandidates(callId, onCandidate = { live.addRemoteIce(it) },
                onError = { throwable ->
                    scope.launch(Dispatchers.Main) { error = throwable.localizedMessage ?: "ICE signaling failed" }
                })
        }
        onDispose { registration?.remove() }
    }

    val active = call
    LaunchedEffect(active?.status, permissionGranted) {
        if (active == null || !active.isLive() || session != null || busy || !permissionGranted) return@LaunchedEffect
        if (active.callerUid != uid && active.status != "accepted") return@LaunchedEffect
        busy = true
        try {
            val servers = repository.iceServers(callId)
            val live = RtcSession(context.applicationContext, active.kind == "video", servers,
                onIceCandidateFound = { candidate ->
                    scope.launch {
                        try { repository.addCandidate(callId, candidate) }
                        catch (_: Exception) { /* Call may have ended while ICE was gathering. */ }
                    }
                },
                onConnectionChanged = { status ->
                    scope.launch(Dispatchers.Main) { rtcStatus = status }
                },
                onRemoteVideo = { track ->
                    scope.launch(Dispatchers.Main) { remoteVideo = track }
                })
            session = live
            rtcStatus = "Connecting securely…"
            if (active.callerUid == uid && active.offer.isBlank()) {
                repository.publishOffer(callId, live.offer())
                initiated = true
            }
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Unable to initialize calling"
            session?.close(); session = null
            try { repository.end(callId) } catch (_: Exception) { }
        } finally { busy = false }
    }
    LaunchedEffect(active?.offer, active?.status, session) {
        val live = session ?: return@LaunchedEffect
        val info = active ?: return@LaunchedEffect
        if (uid == info.calleeUid && info.status == "accepted" && info.offer.isNotBlank() &&
            info.answer.isBlank() && !answering) {
            answering = true
            try { repository.publishAnswer(callId, live.acceptOffer(info.offer)) }
            catch (e: Exception) { error = e.localizedMessage ?: "Cannot answer secure call" }
        }
    }
    LaunchedEffect(active?.answer, session) {
        val live = session ?: return@LaunchedEffect
        val info = active ?: return@LaunchedEffect
        if (uid == info.callerUid && info.answer.isNotBlank() && !remoteAnswerApplied) {
            remoteAnswerApplied = true
            try { live.receiveAnswer(info.answer) }
            catch (e: Exception) { error = e.localizedMessage ?: "Could not connect to receiver" }
        }
    }
    LaunchedEffect(active?.status) {
        if (active != null && !active.isLive()) {
            session?.close(); session = null
            rtcStatus = active.status.replaceFirstChar { it.uppercase() }
        }
    }
    fun hangUp() {
        scope.launch {
            try { if (active?.isLive() == true) repository.end(callId) }
            catch (e: Exception) { error = e.localizedMessage ?: "Could not end call" }
            finally { session?.close(); session = null; onBack() }
        }
    }
    BackHandler { hangUp() }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("AasPass ${if (active?.kind == "video") "Video" else "Voice"} Call") },
            navigationIcon = { IconButton(onClick = { hangUp() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Leave call")
            } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            if (active?.kind == "video" && session != null && active.status == "accepted") {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val live = requireNotNull(session)
                    val track = remoteVideo
                    if (track != null) {
                        key(live, track) {
                            AndroidView(factory = { ctx ->
                                SurfaceViewRenderer(ctx).also { view ->
                                    view.init(live.egl.eglBaseContext, null)
                                    view.setMirror(false)
                                    track.addSink(view)
                                }
                            }, modifier = Modifier.fillMaxSize(), onRelease = { view ->
                                track.removeSink(view); view.release()
                            })
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Waiting for video…") }
                    }
                    if (!cameraOff) {
                        AndroidView(factory = { ctx ->
                            SurfaceViewRenderer(ctx).also { view ->
                                view.init(live.egl.eglBaseContext, null)
                                view.setZOrderMediaOverlay(true)
                                view.setMirror(true)
                                live.attachLocalRenderer(view)
                            }
                        }, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                            .size(112.dp, 156.dp), onRelease = { view ->
                            live.detachLocalRenderer(view); view.release()
                        })
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(104.dp), Alignment.Center) {
                        Icon(if (active?.kind == "video") Icons.Default.Videocam else Icons.Default.Call,
                            null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Text(active?.peerName(uid) ?: "Connecting…", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(if (active?.status == "ringing") "Ringing" else rtcStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, Modifier.padding(12.dp), textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(18.dp))
            if (active != null && active.isLive() && !permissionGranted &&
                (uid == active.callerUid || active.status == "accepted")) {
                Button(onClick = { ensurePermissions() }) { Text("Enable microphone / camera") }
            }
            if (active != null && active.status == "ringing" && uid == active.calleeUid) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            try { repository.respond(callId, false) }
                            catch (e: Exception) { error = e.localizedMessage }
                        }
                    }) { Text("Decline") }
                    Button(onClick = {
                        scope.launch {
                            if (ensurePermissions()) {
                                try { repository.respond(callId, true) }
                                catch (e: Exception) { error = e.localizedMessage }
                            } else {
                                acceptAfterPermission = true
                            }
                        }
                    }) { Text("Accept") }
                }
            }
            if (session != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { muted = !muted; session?.setMuted(muted) }) {
                        Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, "Mute microphone")
                    }
                    IconButton(onClick = { speaker = !speaker; session?.setSpeaker(speaker) }) {
                        Icon(if (speaker) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "Speaker")
                    }
                    if (active?.kind == "video") {
                        IconButton(onClick = { cameraOff = !cameraOff; session?.setCameraEnabled(!cameraOff) }) {
                            Icon(if (cameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                "Turn camera on or off")
                        }
                        IconButton(onClick = { session?.switchCamera() }) {
                            Icon(Icons.Default.Cameraswitch, "Switch camera")
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (active?.isLive() == true) {
                Button(onClick = { hangUp() }, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.CallEnd, null)
                    Spacer(Modifier.width(8.dp)); Text("End call")
                }
            } else {
                Button(onClick = onBack) { Text("Done") }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
