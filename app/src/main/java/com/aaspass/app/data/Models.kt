package com.aaspass.app.data

import com.google.firebase.Timestamp
import java.util.Locale

// This release deliberately ships only fields that are backed by working Firestore writes.
data class UserProfile(
    val id: String = "",
    val displayName: String = "",
    val username: String = "",
    val discoverable: Boolean = false,
    val blockedUids: List<String> = emptyList()
)

data class Conversation(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
    val requestedBy: String = "",
    val status: String = "requested",
    val lastMessage: String = "",
    val lastAt: Timestamp? = null
) {
    fun peerUid(myUid: String): String = participants.firstOrNull { it != myUid }.orEmpty()
    fun peerName(myUid: String): String = labels[peerUid(myUid)]?.ifBlank { null }
        ?: peerUid(myUid).take(10)
}

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
    val replyToId: String = "",
    val editedAt: Timestamp? = null,
    val deletedAt: Timestamp? = null,
    val pending: Boolean = false,
    val kind: String = "text",
    val mediaPath: String = "",
    val fileName: String = "",
    val contentType: String = "",
    val fileSize: Long = 0L
) {
    val isDeleted: Boolean get() = deletedAt != null
}

object InputRules {
    private val handleRegex = Regex("[a-z0-9_]{3,20}")
    fun normalizedUsername(value: String): String = value.trim().lowercase(Locale.ROOT)
    fun validUsername(value: String): Boolean = handleRegex.matches(normalizedUsername(value))
    fun validDisplayName(value: String): Boolean = value.trim().length in 2..50
    fun validMessageId(value: String): Boolean = Regex("[A-Za-z0-9]{20}").matches(value)
    fun conversationId(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")
}
