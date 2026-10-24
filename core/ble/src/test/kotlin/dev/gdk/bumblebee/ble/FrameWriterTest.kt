package dev.gdk.bumblebee.ble

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameWriterTest {

    @Test
    fun `take prefers urgent then heartbeat then nav`() = runTest {
        val w = FrameWriter()
        w.enqueue(FrameWriter.Entry(FrameWriter.Priority.NAV, byteArrayOf(1), "nav"))
        w.enqueue(FrameWriter.Entry(FrameWriter.Priority.HEARTBEAT, byteArrayOf(2), "hb"))
        w.enqueue(FrameWriter.Entry(FrameWriter.Priority.URGENT, byteArrayOf(3), "id"))
        assertEquals("id", w.take().note)
        assertEquals("hb", w.take().note)
        assertEquals("nav", w.take().note)
    }

    @Test
    fun `duplicate nav frames are dropped`() = runTest {
        val w = FrameWriter()
        val nav = byteArrayOf(0xA5.toByte(), 0x31)
        w.enqueue(FrameWriter.Entry(FrameWriter.Priority.NAV, nav, "a"))
        w.enqueue(FrameWriter.Entry(FrameWriter.Priority.NAV, nav.copyOf(), "b"))
        w.enqueue(FrameWriter.Entry(FrameWriter.Priority.URGENT, byteArrayOf(9), "u"))
        assertEquals("u", w.take().note)
        assertEquals("a", w.take().note)
        // Second identical NAV never queued — next take would hang. Prove lastNav is first copy.
        assertTrue(w.lastNav.value!!.contentEquals(nav))
    }
}
