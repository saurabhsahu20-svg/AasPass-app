package com.aaspass.app

/** Safe, actionable messages for Supabase Auth. Never display passwords or OTP values. */
object MsgUAuthErrors {
    fun validEmail(email: String): Boolean {
        val clean = email.trim()
        return clean.length in 3..254 && clean.indexOf('@') > 0 &&
            clean.substringAfter('@').contains('.') && !clean.contains(' ')
    }

    fun describe(status: Int, rawCode: String, rawMessage: String): String {
        val code = rawCode.trim().lowercase()
        val message = rawMessage.trim().lowercase()
        return when {
            code == "over_email_send_rate_limit" || (status == 429 && message.contains("email")) ->
                "Email confirmation limit reached. Retry later; the MsgU admin must enable Custom SMTP."
            code == "email_address_not_authorized" || message.contains("email address not authorized") ->
                "Supabase cannot email this address until Custom SMTP is configured for MsgU."
            code == "email_not_confirmed" || message.contains("email not confirmed") ->
                "Email not confirmed. Open the verification link, or use Resend confirmation."
            code == "email_provider_disabled" ->
                "Email login is not enabled for this Supabase project."
            code == "phone_provider_disabled" ->
                "Mobile OTP is not enabled yet; use Email Login until an SMS provider is configured."
            status == 429 ->
                "Too many verification requests. Please wait before trying again."
            status == 401 || status == 403 ->
                rawMessage.takeIf { it.isNotBlank() }?.take(180) ?: "Login failed or access denied."
            else -> rawMessage.takeIf { it.isNotBlank() }?.take(180) ?: "Server error " + status
        }
    }
}
