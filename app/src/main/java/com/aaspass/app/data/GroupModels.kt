package com.aaspass.app.data

import com.google.firebase.Timestamp

data class AasPassGroup(
    val id: String = "",
    val name: String = "",
    val kind: String = "general",
    val ownerId: String = "",
    val members: List<String> = emptyList(),
    val admins: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastAt: Timestamp? = null
)

data class AasPassGroupInvite(
    val id: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val groupKind: String = "general",
    val fromUid: String = "",
    val toUid: String = "",
    val status: String = "pending"
)

data class AasPassGroupMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val kind: String = "text",
    val createdAt: Timestamp? = null,
    val pending: Boolean = false,
    val mediaPath: String = "",
    val fileName: String = "",
    val contentType: String = "",
    val fileSize: Long = 0L
)

object GroupInputRules {
    fun validGroupName(value: String): Boolean = value.trim().length in 3..60
    fun validGroupKind(value: String): Boolean = value == "general" || value == "study"
}
