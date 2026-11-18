package com.aaspass.app.notifications

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import com.aaspass.app.navigation.NotificationDestination
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aaspass.app.MainActivity
import com.aaspass.app.R
import com.aaspass.app.data.AasPassRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AasPassMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            AasPassRepository().savePushToken(uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Data-only push: do not display a previous account's notifications after
        // offline logout or account switching (FCM tokens may survive briefly).
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (message.data["toUid"] != uid) return
        // Do not trust push payloads as URLs or routes. Firestore rules still
        // check permissions when opening a conversation, group, or call.
        val type = message.data["type"] ?: return
        val target = when (type) {
            "incoming_call" -> NotificationDestination.fromPayload(message.data["callId"], null, null)
            "new_message" -> NotificationDestination.fromPayload(null, message.data["chatId"], null)
            "group_message" -> NotificationDestination.fromPayload(null, null, message.data["groupId"])
            "group_invite" -> NotificationDestination.fromPayload(
                null, null, null, message.data["groupKind"] ?: "general")
            else -> return
        }
        if (target == null) return
        val incoming = type == "incoming_call"
        val targetId = target?.id ?: "invites"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // PendingIntent matching ignores extras: set distinct data per chat.
            data = Uri.parse("aaspass://notification/$type/$targetId")
            when (target?.kind) {
                "call" -> putExtra("callId", target.id)
                "chat" -> putExtra("chatId", target.id)
                "group" -> putExtra("groupId", target.id)
                "home" -> putExtra("inviteKind", if (target.id == "3") "study" else "general")
            }
        }
        val pi = PendingIntent.getActivity(this, (type + targetId).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notificationBody = when (type) {
            "incoming_call" -> "Incoming voice or video call"
            "group_invite" -> "You have a new group invitation"
            "group_message" -> "You have a new group message"
            else -> "You have a new message"
        }
        val notification = NotificationCompat.Builder(this, if (incoming) "calls" else "messages")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("AasPass")
            .setContentText(notificationBody)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(if (incoming) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (incoming) NotificationCompat.CATEGORY_CALL
                else NotificationCompat.CATEGORY_MESSAGE)
            .build()
        try {
            // One visible notification per thread, rather than a growing stack
            // of identical generic alerts for every individual message.
            NotificationManagerCompat.from(this).notify("$type:$targetId", 0, notification)
        } catch (_: SecurityException) {
            // The user may have denied POST_NOTIFICATIONS.
        }
    }
}
