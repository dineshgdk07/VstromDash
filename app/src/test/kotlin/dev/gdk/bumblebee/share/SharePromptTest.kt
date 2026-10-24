package dev.gdk.bumblebee.share

import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.MainActivity
import dev.gdk.bumblebee.ride.Highlight
import dev.gdk.bumblebee.ride.HighlightKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SharePromptTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `parse reads share route`() {
        assertEquals(42L, SharePrompt.parse("share:42"))
        assertNull(SharePrompt.parse("garage"))
        assertNull(SharePrompt.parse("share:x"))
    }

    @Test fun `notification says the highlight and opens share`() {
        val n = SharePrompt.notification(
            ctx,
            42,
            Highlight(HighlightKind.TOP_SPEED, "New top speed", "New top speed: 112 km/h"),
        )
        assertEquals("New top speed: 112 km/h", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Share it?", n.extras.getString(Notification.EXTRA_TEXT))
        assertEquals("share:42", shadowOf(n.contentIntent).savedIntent.getStringExtra(MainActivity.EXTRA_OPEN))
    }
}
