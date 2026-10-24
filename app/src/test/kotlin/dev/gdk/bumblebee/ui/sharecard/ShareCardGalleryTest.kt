package dev.gdk.bumblebee.ui.sharecard

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareCardGalleryTest {
    @Test
    fun `save writes a png through MediaStore`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.YELLOW) }
        val result = ShareCardGallery.savePng(context, bitmap, "bumblebee-ride-1.png")
        assertTrue(result.exceptionOrNull()?.message ?: "", result.isSuccess)
    }
}
