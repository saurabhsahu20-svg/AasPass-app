package com.aaspass.app.data

import java.io.File

/**
 * Keep temporary incoming attachments bounded in the app-private cache.
 * Never remove files opened recently: an external viewer may still be reading
 * them via FileProvider. Only our own aaspass_* files inside received/ qualify.
 * Best-effort cleanup: failed deletions are harmless and do not block a download.
 */
object AttachmentCachePolicy {
    const val MAX_BYTES = 80L * 1024L * 1024L
    const val MAX_AGE_MS = 48L * 60L * 60L * 1000L
    const val MIN_KEEP_AGE_MS = 15L * 60L * 1000L

    @Synchronized
    fun prune(directory: File, nowMs: Long = System.currentTimeMillis()) {
        val ownedFiles = directory.listFiles()
            ?.filter { it.isFile && !it.isSymbolicLink() && it.name.startsWith("aaspass_") }
            ?: return
        // Purge stale files, but keep a 15-minute safety window for active viewers.
        for (file in ownedFiles) {
            val age = (nowMs - file.lastModified()).coerceAtLeast(0L)
            if (age > MAX_AGE_MS) file.delete()
        }
        val remaining = ownedFiles.filter { it.exists() }.sortedBy { it.lastModified() }
        var total = remaining.sumOf { it.length() }
        for (file in remaining) {
            if (total <= MAX_BYTES) break
            val age = (nowMs - file.lastModified()).coerceAtLeast(0L)
            if (age <= MIN_KEEP_AGE_MS) continue
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    // Symbolic links should never be followed during cache cleanup.
    private fun File.isSymbolicLink(): Boolean = try {
        canonicalFile != absoluteFile
    } catch (_: Exception) { true }
}
