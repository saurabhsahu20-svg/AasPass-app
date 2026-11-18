package com.aaspass.app.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Group membership changes are ALWAYS authorized by callable Cloud Functions.
 * Direct Firestore writes are only used for messages, and protected by Firestore rules.
 */
class GroupRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-south1")
) {
    private suspend fun call(name: String, params: Map<String, Any>): Map<*, *> {
        val result = functions.getHttpsCallable(name).call(params).await().data
        return result as? Map<*, *> ?: error("Unexpected server response")
    }

    suspend fun createGroup(name: String, kind: String): String {
        require(GroupInputRules.validGroupName(name) && GroupInputRules.validGroupKind(kind)) {
            "Enter a group name (3–60 characters)"
        }
        return call("createGroup", mapOf("name" to name.trim(), "kind" to kind))["groupId"]
            as? String ?: error("Group creation was not confirmed")
    }

    suspend fun invite(groupId: String, username: String) {
        val handle = InputRules.normalizedUsername(username)
        require(InputRules.validUsername(handle)) { "Enter a valid username" }
        call("inviteToGroup", mapOf("groupId" to groupId, "username" to handle))
    }

    suspend fun respondToInvite(inviteId: String, accept: Boolean) {
        call("respondToGroupInvite", mapOf("inviteId" to inviteId, "accept" to accept))
    }

    suspend fun leaveGroup(groupId: String) {
        call("leaveGroup", mapOf("groupId" to groupId))
    }

    suspend fun removeMember(groupId: String, targetUid: String) {
        call("removeGroupMember", mapOf("groupId" to groupId, "targetUid" to targetUid))
    }

    fun groups(uid: String): Flow<List<AasPassGroup>> = callbackFlow {
        val listener = db.collection("groups").whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.documents.orEmpty().map { it.toGroup() }
                    .sortedByDescending { it.lastAt?.toDate()?.time ?: 0L })
            }
        awaitClose { listener.remove() }
    }

    fun invites(uid: String): Flow<List<AasPassGroupInvite>> = callbackFlow {
        val listener = db.collection("groupInvites").whereEqualTo("toUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.documents.orEmpty().map { it.toInvite() }
                    .filter { it.status == "pending" })
            }
        awaitClose { listener.remove() }
    }

    fun group(groupId: String): Flow<AasPassGroup?> = callbackFlow {
        val listener = db.collection("groups").document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(if (snapshot?.exists() == true) snapshot.toGroup() else null)
            }
        awaitClose { listener.remove() }
    }

    fun messages(groupId: String): Flow<List<AasPassGroupMessage>> = callbackFlow {
        val listener = db.collection("groups").document(groupId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(100)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.documents.orEmpty().map { doc ->
                    AasPassGroupMessage(
                        id = doc.id, senderId = doc.getString("senderId").orEmpty(),
                        text = doc.getString("text").orEmpty(), kind = doc.getString("kind") ?: "text",
                        createdAt = doc.getTimestamp("createdAt"), pending = doc.metadata.hasPendingWrites(),
                        mediaPath = doc.getString("mediaPath").orEmpty(),
                        fileName = doc.getString("fileName").orEmpty(),
                        contentType = doc.getString("contentType").orEmpty(),
                        fileSize = doc.getLong("fileSize") ?: 0L
                    )
                })
            }
        awaitClose { listener.remove() }
    }

    suspend fun loadOlderMessages(groupId: String, before: com.google.firebase.Timestamp,
                                  pageSize: Long = 50): List<AasPassGroupMessage> {
        require(pageSize in 1..100)
        return db.collection("groups").document(groupId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(before).limit(pageSize).get().await().documents.map { doc ->
                AasPassGroupMessage(
                    id = doc.id, senderId = doc.getString("senderId").orEmpty(),
                    text = doc.getString("text").orEmpty(), kind = doc.getString("kind") ?: "text",
                    createdAt = doc.getTimestamp("createdAt"), pending = doc.metadata.hasPendingWrites(),
                    mediaPath = doc.getString("mediaPath").orEmpty(), fileName = doc.getString("fileName").orEmpty(),
                    contentType = doc.getString("contentType").orEmpty(), fileSize = doc.getLong("fileSize") ?: 0L
                )
            }.asReversed()
    }

    fun enqueueMessage(groupId: String, uid: String, text: String,
                       announcement: Boolean, onError: (String) -> Unit) {
        val cleaned = text.trim()
        require(cleaned.length in 1..3000) { "Message must be 1–3000 characters" }
        db.collection("groups").document(groupId).collection("messages").add(mapOf(
            "senderId" to uid, "text" to cleaned,
            "kind" to if (announcement) "announcement" else "text",
            "createdAt" to FieldValue.serverTimestamp()
        )).addOnFailureListener { onError(it.localizedMessage ?: "Group message failed") }
    }
}

private fun DocumentSnapshot.toGroup() = AasPassGroup(
    id = id,
    name = getString("name").orEmpty(), kind = getString("kind") ?: "general",
    ownerId = getString("ownerId").orEmpty(),
    members = (get("members") as? List<*>)?.filterIsInstance<String>().orEmpty(),
    admins = (get("admins") as? List<*>)?.filterIsInstance<String>().orEmpty(),
    memberNames = (get("memberNames") as? Map<*, *>)?.entries?.mapNotNull { (k, v) ->
        if (k is String && v is String) k to v else null
    }?.toMap().orEmpty(),
    lastMessage = getString("lastMessage").orEmpty(), lastAt = getTimestamp("lastAt")
)

private fun DocumentSnapshot.toInvite() = AasPassGroupInvite(
    id = id, groupId = getString("groupId").orEmpty(),
    groupName = getString("groupName").orEmpty(),
    groupKind = getString("groupKind") ?: "general",
    fromUid = getString("fromUid").orEmpty(), toUid = getString("toUid").orEmpty(),
    status = getString("status") ?: "pending"
)
