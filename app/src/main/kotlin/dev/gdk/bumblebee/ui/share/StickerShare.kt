package dev.gdk.bumblebee.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Instagram Stories sticker, or the share sheet when Instagram is absent. */
object StickerShare {
    const val IG_PACKAGE = "com.instagram.android"

    fun intent(@Suppress("UNUSED_PARAMETER") context: Context, stickerUri: Uri, installed: Boolean): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, stickerUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("sticker.png", stickerUri)
        }
        if (!installed) return Intent.createChooser(send, "Share sticker")
        return Intent("com.instagram.share.ADD_TO_STORY").apply {
            setType("image/png")
            putExtra("interactive_asset_uri", stickerUri)
            setPackage(IG_PACKAGE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = send.clipData
        }
    }

    fun instagramInstalled(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(IG_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }
}
