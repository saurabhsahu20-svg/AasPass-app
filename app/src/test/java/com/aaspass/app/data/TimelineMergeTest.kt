package com.aaspass.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineMergeTest {
    private data class Item(val id: String, val text: String)

    @Test fun latestWindowWinsOverOlderCachedMessage() {
        val old = listOf(Item("1", "old content"), Item("2", "original"))
        val live = listOf(Item("2", "edited"), Item("3", "new"))
        val combined = TimelineMerge.latestWins(live, old) { it.id }
        assertEquals(listOf("2", "3", "1"), combined.map { it.id })
        assertEquals("edited", combined.first().text)
    }

    @Test fun deletedMessageMustNotBeResurrectedByOldPage() {
        val live = listOf(Item("abc", ""))
        val historical = listOf(Item("abc", "private text"))
        assertEquals("", TimelineMerge.latestWins(live, historical) { it.id }[0].text)
    }
}
