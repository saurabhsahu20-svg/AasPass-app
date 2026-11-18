package com.aaspass.app.data

import org.junit.Assert.*
import org.junit.Test

class InputRulesTest {
    @Test fun normalizeAndValidateUsername() {
        assertEquals("student_09", InputRules.normalizedUsername(" Student_09 "))
        assertTrue(InputRules.validUsername("student_09"))
        assertFalse(InputRules.validUsername("xy"))
        assertFalse(InputRules.validUsername("name with spaces"))
    }
    @Test fun deterministicConversationId() {
        assertEquals(InputRules.conversationId("b", "a"), InputRules.conversationId("a", "b"))
    }
    @Test fun messageIdsAreFirestoreAutoIdsOnly() {
        assertTrue(InputRules.validMessageId("abcDEF0123456789abcd"))
        assertFalse(InputRules.validMessageId("../messages/other"))
        assertFalse(InputRules.validMessageId("short"))
    }
    @Test fun displayNames() {
        assertFalse(InputRules.validDisplayName(""))
        assertTrue(InputRules.validDisplayName("Study Group"))
    }
}
