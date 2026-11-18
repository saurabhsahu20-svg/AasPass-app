package com.aaspass.app.navigation

import org.junit.Assert.*
import org.junit.Test

class NotificationDestinationTest {
    private val id = "Abc123Def456Ghi789Jk"

    @Test fun `valid call opens call route`() {
        val destination = NotificationDestination.fromPayload(id, null, null)
        assertEquals("call/$id", destination?.route)
    }

    @Test fun `direct message opens intended chat`() {
        assertEquals("chat/abc_DEF-123", NotificationDestination.fromPayload(null, "abc_DEF-123", null)?.route)
    }

    @Test fun `group message opens intended group`() {
        assertEquals("group/$id", NotificationDestination.fromPayload(null, null, id)?.route)
    }

    @Test fun `study invite opens Study tab`() {
        assertEquals("home?tab=3", NotificationDestination.fromPayload(null, null, null, "study")?.route)
        assertEquals("home?tab=2", NotificationDestination.fromPayload(null, null, null, "general")?.route)
    }

    @Test fun `malformed payload cannot redirect navigation`() {
        assertNull(NotificationDestination.fromPayload("../../etc", null, null))
        assertNull(NotificationDestination.fromPayload(null, "a/b", null))
        assertNull(NotificationDestination.fromPayload(null, null, "a%2Fb"))
        assertNull(NotificationDestination.fromPayload(null, null, null, "unknown"))
    }
}
