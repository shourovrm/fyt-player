package com.fyiplayer.app.download

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * Best-effort COPY of an already-finished download into a user-picked SAF folder. Never the
 * source of truth -- [file] in the app-private dir is what the in-app Downloads screen opens
 * either way, so any failure here (permission revoked, provider rejects the name, disk full on
 * the target) just returns false; callers must not fail the download row over it.
 */
fun exportToTree(context: Context, file: File, treeUri: Uri): Boolean = try {
    val treeDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    val newDocUri = DocumentsContract.createDocument(context.contentResolver, treeDocUri, mimeFor(file.name), file.name)
    if (newDocUri == null) {
        false
    } else {
        val out = context.contentResolver.openOutputStream(newDocUri)
        if (out == null) false
        else {
            out.use { sink -> file.inputStream().use { it.copyTo(sink) } }
            true
        }
    }
} catch (e: SecurityException) {
    false // grant revoked (folder moved/deleted, or permission never survived a reboot)
} catch (e: Exception) {
    false // IO failure on the target provider -- private copy already exists, nothing lost
}

private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    "m4a", "mp3", "opus", "ogg", "wav" -> "audio/*"
    else -> "application/octet-stream"
}
