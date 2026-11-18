package com.aaspass.app.data

import org.junit.Assert.*
import org.junit.Test

class GroupInputRulesTest {
    @Test fun validatesNames() {
        assertTrue(GroupInputRules.validGroupName("Science Group"))
        assertTrue(GroupInputRules.validGroupName("Math"))
        assertFalse(GroupInputRules.validGroupName("A"))
        assertFalse(GroupInputRules.validGroupName("x".repeat(61)))
    }

    @Test fun groupsUseOnlyKnownTypes() {
        assertTrue(GroupInputRules.validGroupKind("study"))
        assertTrue(GroupInputRules.validGroupKind("general"))
        assertFalse(GroupInputRules.validGroupKind("public"))
    }
}
