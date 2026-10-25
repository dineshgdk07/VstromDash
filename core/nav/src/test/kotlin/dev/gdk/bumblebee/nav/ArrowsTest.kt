package dev.gdk.bumblebee.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrowsTest {
    @Test fun `the instruction is read from every part of the notification`() {
        // Maps often puts only the distance in the title.
        assertEquals(ArrowKind.LEFT, ManeuverText.classify("300 m", "Turn left onto MG Road"))
        assertEquals(ArrowKind.SLIGHT_RIGHT, ManeuverText.classify("1.2 km", "Slight right toward Hosur Rd"))
        assertEquals(ArrowKind.ROUNDABOUT, ManeuverText.classify("At the roundabout, take the 2nd exit onto NH 48"))
        assertEquals(ArrowKind.EXIT_LEFT, ManeuverText.classify("Take the exit on the left toward Airport"))
        assertEquals(ArrowKind.FORK_RIGHT, ManeuverText.classify("Keep right at the fork"))
        assertEquals(ArrowKind.KEEP_LEFT, ManeuverText.classify("Keep left to stay on ORR"))
        assertEquals(ArrowKind.ARRIVE, ManeuverText.classify("Your destination will be on the right"))
        assertEquals(ArrowKind.U_TURN, ManeuverText.classify("Make a U-turn"))
        assertNull(ManeuverText.classify("300 m"))
    }

    @Test fun `phrases drop distances and road names`() {
        assertEquals("turn left", ManeuverText.phraseKey("In 300 m, turn left onto MG Road"))
        assertEquals("turn left", ManeuverText.phraseKey("Turn left onto 80 Feet Rd"))
        assertEquals("keep right", ManeuverText.phraseKey("Keep right at the fork"))
    }

    @Test fun `a taught arrow wins, by picture first`() {
        val memory = ArrowMemory()
        val icon = 0x0F0F_00FF_1234_5678L
        assertEquals(ArrowKind.LEFT to ArrowSource.TEXT, pickArrow(memory, icon, "300 m", "Turn left onto MG Road"))
        memory.teach(icon, "Turn left onto MG Road", ArrowKind.SLIGHT_LEFT)
        // Same picture with a couple of bits off still matches.
        assertEquals(ArrowKind.SLIGHT_LEFT to ArrowSource.TAUGHT, pickArrow(memory, icon xor 0b101L, "200 m", "Bear left"))
        // No picture: the phrase is remembered too.
        assertEquals(ArrowKind.SLIGHT_LEFT to ArrowSource.TAUGHT, pickArrow(memory, null, "Turn left onto Church St"))
        assertEquals(ArrowKind.STRAIGHT to ArrowSource.DEFAULT, pickArrow(ArrowMemory(), null, "300 m"))
    }

    @Test fun `memory survives a round trip`() {
        val m = ArrowMemory()
        m.teach(-5L, "Turn right onto X", ArrowKind.SHARP_RIGHT)
        val back = ArrowMemory.decode(m.encode())
        assertEquals(ArrowKind.SHARP_RIGHT, back.recall(-5L, null))
        assertEquals(ArrowKind.SHARP_RIGHT, back.recall(null, "Turn right"))
        assertEquals(2, back.size)
    }

    @Test fun `fingerprint compares neighbours`() {
        val grid = IntArray(72) { i -> if (i % 9 < 4) 200 else 10 }
        val h = ArrowIcon.dHash(grid)
        // Only the step from column 3 to 4 is brighter-to-darker, once per row.
        assertEquals(8, java.lang.Long.bitCount(h))
        assertEquals(0, ArrowIcon.distance(h, ArrowIcon.dHash(grid.copyOf())))
    }

    @Test fun `every arrow reaches a real cluster byte`() {
        ArrowKind.entries.forEach { assert(it.clusterByte in 1..52) { it.name } }
        assertEquals(9, ArrowKind.ARRIVE.clusterByte)
        assertEquals(1, ArrowKind.LEFT.clusterByte)
    }
}
