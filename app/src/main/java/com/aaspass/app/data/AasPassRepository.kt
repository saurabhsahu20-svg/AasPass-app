package com.aaspass.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class AasPassRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        val cleaned = email.trim()
        require(cleaned.contains('@') && cleaned.length <= 254) { "Enter a valid email address" }
        auth.sendPasswordResetEmail(cleaned).await()
    }

    /** User must re-enter their password. Backend rechecks recent auth_time and
     * removes their account; other participants lose direct-chat history.
     */
    suspend fun deleteMyAccount(password: String) {
        val user = requireNotNull(auth.currentUser) { "Sign in first" }
        val email = requireNotNull(user.email) { "Email authentication is required" }
        require(password.isNotBlank()) { "Enter your account password" }
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("deleteMyAccount")
            .call(mapOf("confirmation" to "DELETE MY AASPASS ACCOUNT")).await()
        auth.signOut()
    }

    // Revoke this device's push token before signing out, to prevent notifications
    // for the former account reaching the device after logout.
    suspend fun logout() {
        val uid = currentUid ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            val tokenId = tokenDocumentId(token)
            db.collection("pushTokens").document(uid).collection("devices")
                .document(tokenId).delete().await()
            FirebaseMessaging.getInstance().deleteToken().await()
        } catch (_: Exception) {
            // Sign-out must also work offline. All push messages carry a recipient UID
            // and are ignored locally when the authenticated user has changed.
        } finally { auth.signOut() }
    }

    fun observeProfile(uid: String, onUpdate: (UserProfile?) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            when {
                error != null -> onError(error.localizedMessage ?: "Unable to load profile")
                snapshot == null || !snapshot.exists() -> onUpdate(null)
                else -> onUpdate(snapshot.toProfile())
            }
        }

    suspend fun createProfile(displayName: String, usernameText: String) {
        val uid = requireNotNull(currentUid) { "Sign in first" }
        val name = displayName.trim()
        val handle = InputRules.normalizedUsername(usernameText)
        require(InputRules.validDisplayName(name)) { "Display name must be 2–50 characters" }
        require(InputRules.validUsername(handle)) { "Username: 3–20 lowercase letters, digits or _" }
        val userRef = db.collection("users").document(uid)
        val handleRef = db.collection("usernames").document(handle)
        db.runTransaction { tx ->
            if (tx.get(userRef).exists()) error("Profile already exists")
            if (tx.get(handleRef).exists()) error("Username is already taken")
            tx.set(userRef, mapOf(
                "displayName" to name, "username" to handle,
                "discoverable" to false, "blockedUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp()
            ))
            tx.set(handleRef, mapOf("uid" to uid, "discoverable" to false,
                "displayName" to name))
            true
        }.await()
        registerCurrentPushToken()
    }

    fun conversations(uid: String): Flow<List<Conversation>> = callbackFlow {
        val registration = db.collection("conversations")
            .whereArrayContains("participants", uid)
            .orderBy("lastAt", Query.Direction.DESCENDING)
            .limit(75)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.documents.orEmpty().map { doc -> doc.toConversation() })
            }
        awaitClose { registration.remove() }
    }

    /** Bound the realtime chat listener and fetch older conversations on demand.
     * A chat receiving a new message reappears in the latest realtime window.
     */
    suspend fun loadOlderConversations(uid: String, before: com.google.firebase.Timestamp,
                                       pageSize: Long = 75): List<Conversation> {
        require(uid == currentUid) { "Sign in to view chats" }
        require(pageSize in 1..75)
        return db.collection("conversations")
            .whereArrayContains("participants", uid)
            .orderBy("lastAt", Query.Direction.DESCENDING)
            .startAfter(before)
            .limit(pageSize).get().await().documents.map { it.toConversation() }
    }

    fun conversation(id: String, onUpdate: (Conversation?) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db.collection("conversations").document(id).addSnapshotListener { snapshot, error ->
            when {
                error != null -> onError(error.localizedMessage ?: "Unable to load chat")
                snapshot == null || !snapshot.exists() -> onUpdate(null)
                else -> onUpdate(snapshot.toConversation())
            }
        }

    fun messages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val registration = db.collection("conversations").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(100)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.documents.orEmpty().map { doc ->
                    ChatMessage(
                        id = doc.id,
                        senderId = doc.getString("senderId").orEmpty(),
                        text = doc.getString("text").orEmpty(),
                        createdAt = doc.getTimestamp("createdAt"),
                        replyToId = doc.getString("replyToId").orEmpty(),
                        editedAt = doc.getTimestamp("editedAt"),
                        deletedAt = doc.getTimestamp("deletedAt"),
                        pending = doc.metadata.hasPendingWrites(),
                        kind = doc.getString("kind") ?: "text",
                        mediaPath = doc.getString("mediaPath").orEmpty(),
                        fileName = doc.getString("fileName").orEmpty(),
                        contentType = doc.getString("contentType").orEmpty(),
                        fileSize = doc.getLong("fileSize") ?: 0L
                    )
                })
            }
        awaitClose { registration.remove() }
    }

    /** Paginate older messages without loading full conversation history into memory. */
    suspend fun loadOlderMessages(chatId: String, before: com.google.firebase.Timestamp,
                                  pageSize: Long = 50): List<ChatMessage> {
        require(pageSize in 1..100)
        return db.collection("conversations").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(before).limit(pageSize).get().await().documents.map { doc ->
                ChatMessage(
                    id = doc.id, senderId = doc.getString("senderId").orEmpty(),
                    text = doc.getString("text").orEmpty(), createdAt = doc.getTimestamp("createdAt"),
                    replyToId = doc.getString("replyToId").orEmpty(),
                    editedAt = doc.getTimestamp("editedAt"), deletedAt = doc.getTimestamp("deletedAt"),
                    pending = doc.metadata.hasPendingWrites(),
                    kind = doc.getString("kind") ?: "text", mediaPath = doc.getString("mediaPath").orEmpty(),
                    fileName = doc.getString("fileName").orEmpty(),
                    contentType = doc.getString("contentType").orEmpty(),
                    fileSize = doc.getLong("fileSize") ?: 0L
                )
            }.asReversed()
    }

    /** Exact username lookup; only an explicitly discoverable account can be found. */
    suspend fun requestChat(myProfile: UserProfile, rawUsername: String): String {
        val uid = requireNotNull(currentUid)
        val handle = InputRules.normalizedUsername(rawUsername)
        require(InputRules.validUsername(handle)) { "Enter a valid username" }
        val hit = db.collection("usernames").document(handle).get().await()
        val recipientUid = hit.getString("uid") ?: error("User not found or not discoverable")
        require(recipientUid != uid) { "You cannot message yourself" }
        require(hit.getBoolean("discoverable") == true) { "User is not discoverable" }
        // The owner's private users document is never exposed to other users.
        val peerName = hit.getString("displayName")
            ?.takeIf { InputRules.validDisplayName(it) }
            ?: error("This account needs to refresh its public profile")
        val chatId = InputRules.conversationId(uid, recipientUid)
        val conversationRef = db.collection("conversations").document(chatId)
        db.runTransaction { tx ->
            if (!tx.get(conversationRef).exists()) {
                tx.set(conversationRef, mapOf(
                    "participants" to listOf(uid, recipientUid).sorted(),
                    "labels" to mapOf(uid to myProfile.displayName, recipientUid to peerName),
                    "requestedBy" to uid, "status" to "requested", "type" to "direct",
                    "lastMessage" to "", "lastAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp()
                ))
            }
            true
        }.await()
        return chatId
    }

    suspend fun respondToRequest(chatId: String, accept: Boolean) {
        db.collection("conversations").document(chatId)
            .update("status", if (accept) "accepted" else "rejected").await()
    }

    fun enqueueMessage(chatId: String, text: String, replyToId: String = "", onError: (String) -> Unit) {
        val uid = requireNotNull(currentUid)
        val cleaned = text.trim()
        require(cleaned.length in 1..3000) { "Message must be 1–3000 characters" }
        require(replyToId.isEmpty() || InputRules.validMessageId(replyToId)) { "Invalid reply message" }
        db.collection("conversations").document(chatId).collection("messages").add(
            mapOf("senderId" to uid, "text" to cleaned, "replyToId" to replyToId,
                "createdAt" to FieldValue.serverTimestamp())
        ).addOnFailureListener { error ->
            onError(error.localizedMessage ?: "Message was not saved")
        }
    }

    suspend fun editMessage(chatId: String, messageId: String, newText: String) {
        require(InputRules.validMessageId(messageId)) { "Invalid message" }
        val cleaned = newText.trim()
        require(cleaned.length in 1..3000) { "Message must be 1–3000 characters" }
        db.collection("conversations").document(chatId).collection("messages")
            .document(messageId).update(mapOf(
                "text" to cleaned, "editedAt" to FieldValue.serverTimestamp()
            )).await()
    }

    suspend fun deleteMessageForEveryone(chatId: String, messageId: String) {
        require(InputRules.validMessageId(messageId)) { "Invalid message" }
        // Soft deletion keeps reply references valid, but removes the displayed text.
        db.collection("conversations").document(chatId).collection("messages")
            .document(messageId).update(mapOf(
                "text" to "", "deletedAt" to FieldValue.serverTimestamp()
            )).await()
    }

    fun observePeerReadAt(chatId: String, peerUid: String): Flow<com.google.firebase.Timestamp?> = callbackFlow {
        val registration = db.collection("conversations").document(chatId)
            .collection("reads").document(peerUid).addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.getTimestamp("readAt"))
            }
        awaitClose { registration.remove() }
    }

    suspend fun markChatRead(chatId: String) {
        val uid = requireNotNull(currentUid)
        db.collection("conversations").document(chatId).collection("reads")
            .document(uid).set(mapOf("readAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun setDiscoverable(profile: UserProfile, enabled: Boolean) {
        val uid = requireNotNull(currentUid)
        val batch = db.batch()
        batch.update(db.collection("users").document(uid), "discoverable", enabled)
        batch.update(db.collection("usernames").document(profile.username), "discoverable", enabled)
        batch.commit().await()
    }

    suspend fun changeDisplayName(name: String) {
        require(InputRules.validDisplayName(name)) { "Display name must be 2–50 characters" }
        val uid = requireNotNull(currentUid)
        val userRef = db.collection("users").document(uid)
        val handle = userRef.get().await().getString("username") ?: error("Missing username")
        val batch = db.batch()
        batch.update(userRef, "displayName", name.trim())
        batch.update(db.collection("usernames").document(handle), "displayName", name.trim())
        batch.commit().await()
    }

    suspend fun blockUser(otherUid: String) {
        val uid = requireNotNull(currentUid)
        require(otherUid != uid)
        db.collection("users").document(uid)
            .update("blockedUids", FieldValue.arrayUnion(otherUid)).await()
    }

    suspend fun reportUser(chatId: String, peerUid: String, reason: String) {
        val uid = requireNotNull(currentUid)
        require(reason.isNotBlank() && reason.length <= 300)
        db.collection("reports").add(mapOf(
            "reporterUid" to uid, "reportedUid" to peerUid,
            "conversationId" to chatId, "reason" to reason.trim(),
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    fun registerCurrentPushToken() {
        val uid = currentUid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            savePushToken(uid, token)
        }
    }

    fun savePushToken(uid: String, token: String) {
        if (auth.currentUser?.uid != uid || token.isBlank()) return
        val tokenId = tokenDocumentId(token)
        db.collection("pushTokens").document(uid).collection("devices")
            .document(tokenId).set(mapOf("token" to token,
                "updatedAt" to FieldValue.serverTimestamp()))
    }
    private fun tokenDocumentId(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}

private fun DocumentSnapshot.toProfile(): UserProfile = UserProfile(
    id = id, displayName = getString("displayName").orEmpty(),
    username = getString("username").orEmpty(),
    discoverable = getBoolean("discoverable") ?: false,
    blockedUids = (get("blockedUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
)

private fun DocumentSnapshot.toConversation(): Conversation = Conversation(
    id = id,
    participants = (get("participants") as? List<*>)?.filterIsInstance<String>().orEmpty(),
    labels = (get("labels") as? Map<*, *>)?.mapNotNull { (key, value) ->
        if (key is String && value is String) key to value else null
    }?.toMap().orEmpty(),
    requestedBy = getString("requestedBy").orEmpty(),
    status = getString("status") ?: "requested",
    lastMessage = getString("lastMessage").orEmpty(),
    lastAt = getTimestamp("lastAt")
)
