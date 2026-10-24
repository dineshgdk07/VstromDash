package dev.gdk.bumblebee.ble.android

import androidx.test.core.app.ApplicationProvider
import dev.gdk.bumblebee.ble.ConnectionState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BleClientApiTest {
    @Test
    fun `constructs idle`() {
        val client = BleClient(ApplicationProvider.getApplicationContext())
        assertTrue(client.state.value is ConnectionState.Idle)
        client.shutdown()
    }
}
