package com.aaspass.app

/** Client-side validation; only Supabase OTP verification authenticates the person. */
object MsgULoginRules {
    private val e164 = Regex("^\\+[1-9][0-9]{7,14}$")
    private val smsCode = Regex("^[0-9]{6}$")
    fun validPhone(number: String) = e164.matches(number.trim())
    fun validOtp(code: String) = smsCode.matches(code)
}
