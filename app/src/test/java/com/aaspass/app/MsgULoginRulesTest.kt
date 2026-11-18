package com.aaspass.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MsgULoginRulesTest {
    @Test fun supportsCountryCodes() {
        assertTrue(MsgULoginRules.validPhone("+919876543210"))
        assertTrue(MsgULoginRules.validPhone("+14155551234"))
    }
    @Test fun rejectsMalformedPhoneNumbers() {
        assertFalse(MsgULoginRules.validPhone("9876543210"))
        assertFalse(MsgULoginRules.validPhone("+910"))
        assertFalse(MsgULoginRules.validPhone("+91 9876543210"))
    }
    @Test fun otpRequiresSixDigits() {
        assertTrue(MsgULoginRules.validOtp("123456"))
        assertFalse(MsgULoginRules.validOtp("12345"))
        assertFalse(MsgULoginRules.validOtp("12a456"))
    }
}
