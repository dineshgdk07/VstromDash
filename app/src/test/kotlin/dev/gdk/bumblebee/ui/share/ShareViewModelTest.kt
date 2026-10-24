package dev.gdk.bumblebee.ui.share

import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ride.GpsFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShareViewModelTest {
    private val app = ApplicationProvider.getApplicationContext<DashApp>()

    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun `ride subject builds four previews with the ride name as headline`() = runTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val id = seedRide(name = "Nandi run")
        val vm = ShareViewModel(app, ShareSubject.Ride(id))
        await { vm.ui.value.previews.size == 4 }
        assertEquals(4, vm.ui.value.previews.size)
        assertTrue(vm.ui.value.previews.values.all { it.headline == "Nandi run" })
    }

    @Test fun `rename commits to the ride`() = runTest {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val id = seedRide()
        val vm = ShareViewModel(app, ShareSubject.Ride(id))
        await { !vm.ui.value.busy }
        vm.rename("Coorg")
        vm.commitName()
        await { app.capture.rideById(id)?.name == "Coorg" }
        assertEquals("Coorg", app.capture.rideById(id)!!.name)
    }

    private fun await(ready: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            if (kotlinx.coroutines.runBlocking { ready() }) return
            Thread.sleep(20)
        }
    }

    private suspend fun seedRide(name: String? = null): Long {
        val start = System.currentTimeMillis()
        val id = app.capture.startRide(start, gpsOnly = true)
        val steps = 24
        for (i in 0 until steps) {
            val t = i / (steps - 1.0)
            app.capture.onGps(
                GpsFix(
                    ts = start + (t * 3_600_000).toLong(),
                    lat = 12.97 + t * 0.18,
                    lng = 77.59,
                    speedMps = 15f,
                ),
            )
        }
        app.capture.finishRide(start + 3_600_000)
        if (name != null) app.capture.renameRide(id, name)
        return id
    }
}
