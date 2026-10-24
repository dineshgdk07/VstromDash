package dev.gdk.bumblebee.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotInspectTest {

    @Test
    fun `current manifest is accepted`() {
        val verdict = inspectManifest(goodManifest(), payloadSha256 = "abc", actualCounts = goodManifest().counts)
        assertTrue(verdict is SnapshotVerdict.Ok)
    }

    @Test
    fun `debug label does not reject`() {
        val verdict = inspectManifest(goodManifest().copy(buildType = "debug"), "abc", goodManifest().counts)
        assertTrue(verdict is SnapshotVerdict.Ok)
    }

    @Test
    fun `wrong magic is rejected`() {
        val verdict = inspectManifest(goodManifest().copy(magic = "zip"), "abc", goodManifest().counts)
        assertEquals("This file is not a Bumblebee snapshot.", (verdict as SnapshotVerdict.Rejected).message)
    }

    @Test
    fun `newer format is rejected`() {
        val verdict = inspectManifest(goodManifest().copy(format = 2), "abc", goodManifest().counts)
        assertEquals("This file was made by a newer app.", (verdict as SnapshotVerdict.Rejected).message)
    }

    @Test
    fun `newer schema is rejected`() {
        val verdict = inspectManifest(goodManifest().copy(schema = 9), "abc", goodManifest().counts)
        assertEquals("This file was made by a newer app.", (verdict as SnapshotVerdict.Rejected).message)
    }

    @Test
    fun `older schema is rejected until a decoder exists`() {
        val verdict = inspectManifest(goodManifest().copy(schema = 7), "abc", goodManifest().counts)
        assertEquals(
            "This file was made by an older app. Pack it again with this version.",
            (verdict as SnapshotVerdict.Rejected).message,
        )
    }

    @Test
    fun `checksum mismatch names the damage`() {
        val verdict = inspectManifest(goodManifest(), payloadSha256 = "nope", actualCounts = goodManifest().counts)
        assertEquals("The file is damaged. The checksum does not match.", (verdict as SnapshotVerdict.Rejected).message)
    }

    @Test
    fun `count mismatch names the section`() {
        val counts = goodManifest().counts.toMutableMap().apply { this["rides"] = 2 }
        val verdict = inspectManifest(goodManifest(), "abc", counts)
        val rejected = verdict as SnapshotVerdict.Rejected
        assertEquals("The file is damaged. The rides count does not match.", rejected.message)
        assertTrue(rejected.log.any { it.contains("rides") })
    }
}

private fun goodManifest(): SnapshotManifest {
    val counts = (SnapshotSections.ORDER + "prefs").associateWith { 1 }
    return SnapshotManifest(
        magic = SnapshotFormat.MAGIC,
        format = SnapshotFormat.FORMAT,
        schema = SnapshotFormat.APP_SCHEMA,
        exportedAt = 0L,
        buildType = "release",
        counts = counts,
        sha256 = "abc",
    )
}
