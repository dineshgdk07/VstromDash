package dev.gdk.bumblebee.ui.share

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StickerShareTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `sticker goes to instagram stories when installed`() {
        val i = StickerShare.intent(ctx, Uri.parse("content://x/s.png"), installed = true)
        assertEquals("com.instagram.share.ADD_TO_STORY", i.action)
        assertEquals("com.instagram.android", i.`package`)
        assertEquals(Uri.parse("content://x/s.png"), i.getParcelableExtra("interactive_asset_uri"))
    }

    @Test fun `sticker falls back to the share sheet`() =
        assertEquals(
            Intent.ACTION_CHOOSER,
            StickerShare.intent(ctx, Uri.parse("content://x/s.png"), installed = false).action,
        )
}
