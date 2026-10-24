package dev.gdk.bumblebee.ui.ride

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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RideRenameTest {
    private val app = ApplicationProvider.getApplicationContext<DashApp>()

    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun `renameRide updates open detail title`() = runTest {
        val id = seedRide()
        Dispatchers.setMain(Dispatchers.Unconfined)
        val vm = RideViewModel(app)
        vm.openRide(id)
        await { vm.ui.value.detail?.row?.id == id }
        vm.renameRide(id, "Nandi run")
        await { vm.ui.value.detail?.row?.name == "Nandi run" }
        assertEquals("Nandi run", vm.ui.value.detail!!.row.name)
    }

    private fun await(ready: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            if (ready()) return
            Thread.sleep(20)
        }
    }

    private suspend fun seedRide(): Long {
        val start = System.currentTimeMillis()
        val id = app.capture.startRide(start, gpsOnly = true)
        val steps = 8
        for (i in 0 until steps) {
            val t = i / (steps - 1.0)
            app.capture.onGps(
                GpsFix(
                    ts = start + (t * 60_000).toLong(),
                    lat = 12.97 + t * 0.02,
                    lng = 77.59,
                    speedMps = 12f,
                ),
            )
        }
        app.capture.finishRide(start + 60_000)
        return id
    }
}
