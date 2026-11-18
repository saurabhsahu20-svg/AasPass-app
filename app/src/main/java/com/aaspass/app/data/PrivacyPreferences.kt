package com.aaspass.app.data

import android.content.Context

/** Device-local, account-scoped opt-in. The default is OFF for a new install/account. */
object PrivacyPreferences {
    private const val PREFS = "aaspass_device_privacy"
    private fun key(uid: String) = "share_read_receipts_$uid"

    fun shareReadReceipts(context: Context, uid: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key(uid), false)

    fun setShareReadReceipts(context: Context, uid: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(key(uid), enabled).apply()
    }
}
