package dev.gdk.bumblebee.garage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object GarageFiles {
    fun copyIn(context: Context, source: Uri, folder: String, id: Long, displayName: String?): String? {
        val safe = (displayName ?: "file").replace(Regex("[^A-Za-z0-9._-]"), "_").take(48)
        val dir = File(context.filesDir, "garage/$folder").apply { mkdirs() }
        val dest = File(dir, "${id}_$safe")
        return runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dest.absolutePath
        }.getOrNull()
    }

    fun mime(context: Context, uri: Uri): String? = context.contentResolver.getType(uri)

    fun displayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    fun shareUri(context: Context, path: String): Uri? {
        val file = File(path)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    }

    fun viewIntent(context: Context, path: String, mime: String?): Intent? {
        val uri = shareUri(context, path) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}
