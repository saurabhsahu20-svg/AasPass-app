package com.aaspass.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

/** File content is uploaded to a scoped Storage location. Only a server function
 * may publish the message after checking object metadata and membership.
 * No publicly shareable download URLs are created.
 */
class MediaRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-south1")
) {
    companion object {
        const val MAX_BYTES = 20L * 1024L * 1024L
        val MIME_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "video/mp4",
            "application/pdf", "audio/mp4", "audio/aac", "audio/mpeg")
    }

    suspend fun upload(context: Context, scope: String, parentId: String, uri: Uri,
                       explicitMime: String? = null, explicitName: String? = null) {
        require(scope == "direct" || scope == "group") { "Invalid chat type" }
        val uid = requireNotNull(FirebaseAuth.getInstance().currentUser?.uid) { "Sign in first" }
        val resolver = context.contentResolver
        val mime = explicitMime ?: resolver.getType(uri) ?: error("Unknown file type")
        require(mime in MIME_TYPES) { "Only photos, MP4 videos, PDF and audio files are supported" }
        var length: Long? = null
        var displayName: String? = null
        if (explicitName == null) {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) length = cursor.getLong(sizeIndex)
                }
            }
        }
        val title = (explicitName ?: displayName ?: "attachment")
            .replace(Regex("[\\x00-\\x1f\\x7f/\\\\<>:\"|?*]"), "_").take(120).trim()
        require(title.isNotBlank()) { "Invalid file name" }
        if (length != null) require(length!! in 1..MAX_BYTES) { "File must be 20 MB or less" }
        val assetId = db.collection("attachmentIds").document().id // Local ID only; no collection write.
        val ref = storage.reference.child("media/$scope/$parentId/$uid/$assetId")
        val metadata = StorageMetadata.Builder().setContentType(mime).build()
        ref.putFile(uri, metadata).await() // Wait for successful upload before publishing.
        try {
            functions.getHttpsCallable(if (scope == "direct") "publishDirectAttachment"
                else "publishGroupAttachment")
                .call(mapOf("parentId" to parentId, "assetId" to assetId, "fileName" to title)).await()
        } catch (error: Exception) {
            // Clean up an unfinished upload. Storage deletion is server-only by default;
            // retain the orphan for secure server cleanup if this fails.
            throw IllegalStateException("Upload completed, but sending failed. Please retry later.", error)
        }
    }

    suspend fun open(context: Context, mediaPath: String, mime: String) {
        require(mediaPath.startsWith("media/direct/") || mediaPath.startsWith("media/group/")) {
            "Invalid attachment path"
        }
        require(mime in MIME_TYPES) { "File type is not supported" }
        val extension = when (mime) {
            "application/pdf" -> ".pdf"
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "video/mp4" -> ".mp4"
            "audio/mpeg" -> ".mp3"
            "audio/aac" -> ".aac"
            else -> ".m4a"
        }
        val directory = File(context.cacheDir, "received").apply { mkdirs() }
        AttachmentCachePolicy.prune(directory)
        // No arbitrary filename or path enters local filesystem.
        val tmp = File.createTempFile("aaspass_", extension, directory)
        try {
            storage.getReference(mediaPath).getFile(tmp).await()
            val sharedUri = FileProvider.getUriForFile(context,
                "${context.packageName}.fileprovider", tmp)
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(sharedUri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(view, "Open attachment")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: Exception) {
            tmp.delete()
            throw error
        }
    }
}
