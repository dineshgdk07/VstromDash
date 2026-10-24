package dev.gdk.bumblebee.ui.share

import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareLook
import dev.gdk.bumblebee.ride.ShareMetricKind
import dev.gdk.bumblebee.ride.ShareSwitches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharePrefsTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `prefs round trip and never return photo`() {
        val prefs = SharePrefs(ctx)
        prefs.save(ShareSwitches(look = ShareLook.COCKPIT, background = ShareBackground.PHOTO))
        val back = prefs.load(ShareMetricKind.values().toList())
        assertEquals(ShareLook.COCKPIT, back.look)
        assertEquals(ShareBackground.OWN, back.background)
    }

    @Test fun `prompt defaults on`() = assertTrue(SharePrefs(ctx).promptEnabled)
}
