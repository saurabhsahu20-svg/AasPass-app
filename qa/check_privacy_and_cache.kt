import com.aaspass.app.data.AttachmentCachePolicy
import com.aaspass.app.data.ReadReceiptEligibility
import java.io.File
import java.nio.file.Files

fun main() {
    // Read receipts require opt-in, a foreground screen and a visible message.
    check(!ReadReceiptEligibility.canMarkRead(false, true, "m1", listOf("m1")))
    check(!ReadReceiptEligibility.canMarkRead(true, false, "m1", listOf("m1")))
    check(!ReadReceiptEligibility.canMarkRead(true, true, "m1", listOf("m0")))
    check(!ReadReceiptEligibility.canMarkRead(true, true, null, listOf("m1")))
    check(ReadReceiptEligibility.canMarkRead(true, true, "m1", listOf("m1")))

    val directory = Files.createTempDirectory("aaspass-cache-test").toFile()
    try {
        val now = System.currentTimeMillis()
        val stale = File(directory, "aaspass_stale.pdf").apply {
            writeText("old"); setLastModified(now - AttachmentCachePolicy.MAX_AGE_MS - 10000L)
        }
        val fresh = File(directory, "aaspass_fresh.pdf").apply { writeText("current") }
        val unrelated = File(directory, "my_photo.jpg").apply {
            writeText("keep"); setLastModified(now - AttachmentCachePolicy.MAX_AGE_MS - 10000L)
        }
        AttachmentCachePolicy.prune(directory, now)
        check(!stale.exists())
        check(fresh.exists())
        check(unrelated.exists())
    } finally { directory.deleteRecursively() }
    println("PASS: privacy and attachment cache (8 assertions)")
}
