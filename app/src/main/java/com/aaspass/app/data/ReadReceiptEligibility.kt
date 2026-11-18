package com.aaspass.app.data

/** Do not mark a message read unless its actual bubble is visible in the foreground. */
object ReadReceiptEligibility {
    fun canMarkRead(
        enabled: Boolean,
        screenResumed: Boolean,
        newestPeerMessageId: String?,
        visibleMessageKeys: List<Any?>
    ): Boolean = enabled && screenResumed && newestPeerMessageId != null &&
        newestPeerMessageId in visibleMessageKeys
}
