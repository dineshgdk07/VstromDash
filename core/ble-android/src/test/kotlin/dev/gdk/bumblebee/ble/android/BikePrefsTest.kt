package dev.gdk.bumblebee.ble.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BikePrefsTest {

    @Test
    fun `bikeMac is null by default and round trips`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("bumblebee_ble", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = BikePrefs(ctx)
        assertNull(prefs.bikeMac)
        prefs.bikeMac = "AA:BB:CC:DD:EE:FF"
        assertEquals("AA:BB:CC:DD:EE:FF", prefs.bikeMac)
        assertEquals("AA:BB:CC:DD:EE:FF", BikePrefs(ctx).bikeMac)
    }

    @Test
    fun `riderName defaults to RIDER and persists`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("bumblebee_ble", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = BikePrefs(ctx)
        assertEquals("RIDER", prefs.riderName)
        prefs.riderName = "GDK"
        assertEquals("GDK", BikePrefs(ctx).riderName)
    }
}
