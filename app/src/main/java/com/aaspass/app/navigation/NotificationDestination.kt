package com.aaspass.app.navigation

/** A push may arrive long after sign-out or be malformed. Never navigate to
 * arbitrary strings supplied by a notification without checking the route.
 */
data class NotificationDestination(val kind: String, val id: String) {
    val route: String get() = if (kind == "home") "home?tab=$id" else "$kind/$id"

    companion object {
        private val firestoreId = Regex("[A-Za-z0-9]{20}")
        private val chatIdPattern = Regex("[A-Za-z0-9_-]{9,265}")

        fun fromPayload(callId: String?, chatId: String?, groupId: String?,
                        inviteKind: String? = null): NotificationDestination? = when {
            callId != null && firestoreId.matches(callId) -> NotificationDestination("call", callId)
            chatId != null && chatIdPattern.matches(chatId) -> NotificationDestination("chat", chatId)
            groupId != null && firestoreId.matches(groupId) -> NotificationDestination("group", groupId)
            inviteKind == "study" -> NotificationDestination("home", "3")
            inviteKind == "general" -> NotificationDestination("home", "2")
            else -> null
        }
    }
}
