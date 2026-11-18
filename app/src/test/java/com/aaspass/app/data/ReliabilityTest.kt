package com.aaspass.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReliabilityTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun readReceiptsRequireVisibleForegroundAndOptIn() {
        assertFalse(ReadReceiptEligibility.canMarkRead(false, true, "m1", listOf("m1")))
        assertFalse(ReadReceiptEligibility.canMarkRead(true, false, "m1", listOf("m1")))
        assertFalse(ReadReceiptEligibility.canMarkRead(true, true, "m1", listOf("m2")))
        assertFalse(ReadReceiptEligibility.canMarkRead(true, true, null, listOf("m1")))
        assertTrue(ReadReceiptEligibility.canMarkRead(true, true, "m1", listOf("m1", "m2")))
    }

    @Test fun cachePrunesOldFilesButPreservesRecentAndOtherFiles() {
        val folder = temp.newFolder("received")
        val now = System.currentTimeMillis()
        val old = File(folder, "aaspass_old.pdf").apply {
            writeText("old"); setLastModified(now - AttachmentCachePolicy.MAX_AGE_MS - 1000)
        }
        val recent = File(folder, "aaspass_recent.pdf").apply { writeText("recent") }
        val unrelated = File(folder, "personal_file.txt").apply {
            writeText("untouched"); setLastModified(now - AttachmentCachePolicy.MAX_AGE_MS - 1000)
        }
        AttachmentCachePolicy.prune(folder, now)
        assertFalse(old.exists())
        assertTrue(recent.exists())
        assertTrue(unrelated.exists())
    }
}
