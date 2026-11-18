package com.aaspass.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MsgUAuthErrorsTest {
    @Test fun explainsEmailSendQuota() {
        assertTrue(MsgUAuthErrors.describe(429, "over_email_send_rate_limit", "")
            .contains("Custom SMTP"))
    }
    @Test fun explainsEmailNotConfirmedWithoutPretendingToVerify() {
        assertTrue(MsgUAuthErrors.describe(400, "email_not_confirmed", "Email not confirmed")
            .contains("Resend"))
    }
    @Test fun explainsProviderRestrictions() {
        assertTrue(MsgUAuthErrors.describe(400, "email_address_not_authorized", "")
            .contains("Custom SMTP"))
        assertTrue(MsgUAuthErrors.describe(400, "phone_provider_disabled", "")
            .contains("Mobile OTP"))
    }
    @Test fun validatesEmailInput() {
        assertTrue(MsgUAuthErrors.validEmail("student@example.com"))
        assertFalse(MsgUAuthErrors.validEmail("not-an-email"))
        assertFalse(MsgUAuthErrors.validEmail("a@b"))
    }
    @Test fun keepsUnexpectedServerErrorsShort() {
        assertEquals("Server error 500", MsgUAuthErrors.describe(500, "", ""))
        assertTrue(MsgUAuthErrors.describe(503, "", "x".repeat(300)).length <= 180)
    }
}
