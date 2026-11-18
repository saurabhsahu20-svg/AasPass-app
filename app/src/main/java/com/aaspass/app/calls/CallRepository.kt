package com.aaspass.app.calls

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

/** Firestore contains signaling only, never recorded media. Membership changes
 * and TURN credentials are authenticated by the Cloud Functions backend.
 */
data class AasPassCall(
    val id: String = "",
    val callerUid: String = "",
    val calleeUid: String = "",
    val callerName: String = "",
    val calleeName: String = "",
    val kind: String = "voice",
    val status: String = "ringing",
    val offer: String = "",
    val answer: String = "",
    val createdAt: Timestamp? = null,
    val expiresAt: Timestamp? = null
) {
    fun peerName(uid: String): String = if (uid == callerUid) calleeName else callerName
    fun isLive(): Boolean = status == "ringing" || status == "accepted"
}

class CallRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-south1")
) {
    val currentUid: String get() = requireNotNull(FirebaseAuth.getInstance().currentUser?.uid)

    private suspend fun invoke(name: String, payload: Map<String, Any>): Map<*, *> =
        functions.getHttpsCallable(name).call(payload).await().data as? Map<*, *>
            ?: error("Unexpected server response")

    suspend fun startCall(chatId: String, video: Boolean): String {
        val result = invoke("startCall", mapOf("chatId" to chatId,
            "kind" to if (video) "video" else "voice"))
        return result["callId"] as? String ?: error("Call could not be started")
    }

    suspend fun respond(callId: String, accept: Boolean) {
        invoke("answerCall", mapOf("callId" to callId, "accept" to accept))
    }

    suspend fun end(callId: String) {
        invoke("endCall", mapOf("callId" to callId))
    }

    suspend fun iceServers(callId: String): List<PeerConnection.IceServer> {
        val response = invoke("getCallIceServers", mapOf("callId" to callId))
        val records = response["iceServers"] as? List<*> ?: error("TURN server unavailable")
        val servers = records.flatMap { record ->
            val map = record as? Map<*, *> ?: return@flatMap emptyList()
            val username = map["username"] as? String ?: return@flatMap emptyList()
            val credential = map["credential"] as? String ?: return@flatMap emptyList()
            val urls = (map["urls"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            urls.map { url -> PeerConnection.IceServer.builder(url)
                .setUsername(username).setPassword(credential).createIceServer() }
        }
        require(servers.isNotEmpty()) { "TURN credentials are missing" }
        return servers
    }

    fun call(callId: String): Flow<AasPassCall?> = callbackFlow {
        val listener = db.collection("calls").document(callId).addSnapshotListener { snap, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            trySend(if (snap?.exists() == true) snap.toCall() else null)
        }
        awaitClose { listener.remove() }
    }

    fun callHistory(uid: String): Flow<List<AasPassCall>> = callbackFlow {
        val listener = db.collection("calls").whereArrayContains("participants", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(80).addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().map { it.toCall() }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L })
            }
        awaitClose { listener.remove() }
    }

    suspend fun publishOffer(callId: String, sdp: String) {
        require(sdp.length in 1..65536)
        db.collection("calls").document(callId).update("offer", sdp).await()
    }
    suspend fun publishAnswer(callId: String, sdp: String) {
        require(sdp.length in 1..65536)
        db.collection("calls").document(callId).update("answer", sdp).await()
    }
    suspend fun addCandidate(callId: String, candidate: IceCandidate) {
        db.collection("calls").document(callId).collection("candidates").add(mapOf(
            "senderUid" to currentUid,
            "sdp" to candidate.sdp.take(8192),
            "sdpMid" to (candidate.sdpMid ?: "").take(100),
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }
    fun observeRemoteCandidates(callId: String, onCandidate: (IceCandidate) -> Unit,
                                onError: (Throwable) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        val uid = currentUid
        val seen = mutableSetOf<String>()
        return db.collection("calls").document(callId).collection("candidates")
            .addSnapshotListener { snap, error ->
                if (error != null) { onError(error); return@addSnapshotListener }
                snap?.documentChanges.orEmpty().forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED && seen.add(change.document.id)) {
                        val d = change.document
                        val from = d.getString("senderUid")
                        val mid = d.getString("sdpMid")
                        val index = d.getLong("sdpMLineIndex")?.toInt()
                        val sdp = d.getString("sdp")
                        if (from != uid && mid != null && index != null && sdp != null)
                            onCandidate(IceCandidate(mid, index, sdp))
                    }
                }
            }
    }
}

private fun DocumentSnapshot.toCall() = AasPassCall(
    id = id,
    callerUid = getString("callerUid").orEmpty(), calleeUid = getString("calleeUid").orEmpty(),
    callerName = getString("callerName") ?: "AasPass user",
    calleeName = getString("calleeName") ?: "AasPass user",
    kind = getString("kind") ?: "voice", status = getString("status") ?: "ringing",
    offer = getString("offer").orEmpty(), answer = getString("answer").orEmpty(),
    createdAt = getTimestamp("createdAt"), expiresAt = getTimestamp("expiresAt")
)
