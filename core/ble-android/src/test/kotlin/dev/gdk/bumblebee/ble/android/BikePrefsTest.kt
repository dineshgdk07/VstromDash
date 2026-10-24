package dev.gdk.bumblebee.ble.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.snapshot.SnapshotWidget
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

    @Test
    fun `service remaining km is last plus interval minus odo`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("bumblebee_ble", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = BikePrefs(ctx)
        assertEquals(null, prefs.remainingServiceKm(12000))
        prefs.serviceIntervalKm = 1000
        prefs.lastServiceOdoKm = 11500
        assertEquals(500, prefs.remainingServiceKm(12000))
        assertEquals(-50, prefs.remainingServiceKm(12550))
    }

    @Test
    fun `maps nav to cluster defaults on`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("bumblebee_ble", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = BikePrefs(ctx)
        assertEquals(true, prefs.mapsNavToCluster)
        prefs.mapsNavToCluster = false
        assertEquals(false, BikePrefs(ctx).mapsNavToCluster)
    }

    @Test
    fun `reminder windows default to two weeks and 200 km`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("bumblebee_ble", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = BikePrefs(ctx)
        assertEquals(true, prefs.remindersEnabled)
        assertEquals(14, prefs.remindDays)
        assertEquals(200, prefs.remindKm)
        prefs.remindDays = 7
        prefs.remindKm = 50
        prefs.remindersEnabled = false
        assertEquals(7, BikePrefs(ctx).remindDays)
        assertEquals(50, BikePrefs(ctx).remindKm)
        assertEquals(false, BikePrefs(ctx).remindersEnabled)
    }

    @Test
    fun `pause window defaults to 45 minutes and can turn off`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("bumblebee_ble", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = BikePrefs(ctx)
        assertEquals(45, prefs.pauseWindowMin)
        prefs.pauseWindowMin = 0
        assertEquals(0, BikePrefs(ctx).pauseWindowMin)
        prefs.pauseWindowMin = 120
        assertEquals(120, BikePrefs(ctx).pauseWindowMin)
    }

    @Test
    fun `snapshot prefs round trip the bike without writing the widget`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("bumblebee_ble", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = BikePrefs(ctx)
        prefs.bikeMac = "AA:BB:CC:DD:EE:FF"
        prefs.riderName = "GDK"
        prefs.tankCapacityL = 14f
        val widget = SnapshotWidget(null, null, null, "", 0.0, 0.0, "", false, "garage", 0L)
        val snapshot = prefs.toSnapshotPrefs(widget)
        ctx.getSharedPreferences("bumblebee_ble", Context.MODE_PRIVATE).edit().clear().commit()
        BikePrefs(ctx).writeSnapshotPrefs(snapshot)
        val restored = BikePrefs(ctx)
        assertEquals("AA:BB:CC:DD:EE:FF", restored.bikeMac)
        assertEquals("GDK", restored.riderName)
        assertEquals(14f, restored.tankCapacityL)
        assertEquals(widget, snapshot.widget)
    }
}
