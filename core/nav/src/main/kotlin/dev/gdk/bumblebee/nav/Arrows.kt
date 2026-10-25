package dev.gdk.bumblebee.nav

/**
 * The arrows a rider can tell apart, each with the Mappls maneuver ID the cluster expects.
 * [ManeuverMap] turns that ID into the byte actually sent.
 */
enum class ArrowKind(val label: String, val mapplsId: Int) {
    STRAIGHT("Straight", 7),
    LEFT("Left", 0),
    SLIGHT_LEFT("Slight left", 1),
    SHARP_LEFT("Sharp left", 2),
    RIGHT("Right", 3),
    SLIGHT_RIGHT("Slight right", 4),
    SHARP_RIGHT("Sharp right", 5),
    U_TURN("U-turn", 6),
    KEEP_LEFT("Keep left", 11),
    KEEP_RIGHT("Keep right", 12),
    FORK_LEFT("Fork left", 15),
    FORK_RIGHT("Fork right", 16),
    EXIT_LEFT("Exit left", 17),
    EXIT_RIGHT("Exit right", 18),
    MERGE_LEFT("Merge left", 19),
    MERGE_RIGHT("Merge right", 20),
    ROUNDABOUT("Roundabout", 72),
    ARRIVE("Arrive", 8),
    ;

    val clusterByte: Int get() = ManeuverMap.mapplsIdToClusterByte(mapplsId) ?: ManeuverMap.DEFAULT_CLUSTER_BYTE

    companion object {
        fun ofMappls(id: Int): ArrowKind? = entries.firstOrNull { it.mapplsId == id }
    }
}

/**
 * Reads Google Maps' instruction wording. Maps splits a step across the title ("300 m"), the text
 * ("Turn left onto MG Road") and sometimes the sub-text, so every part is read together. Most
 * specific phrases are checked first: "slight left" before "left", "exit" before "keep".
 */
object ManeuverText {
    fun classify(vararg parts: String?): ArrowKind? {
        val s = parts.filterNotNull().joinToString(" · ").lowercase()
        if (s.isBlank()) return null
        fun has(vararg words: String) = words.any { it in s }
        val left = Regex("""\bleft\b""").containsMatchIn(s)
        val right = Regex("""\bright\b""").containsMatchIn(s)
        return when {
            has("u-turn", "u turn", "make a u") -> ArrowKind.U_TURN
            has("arrive", "destination", "you have reached", "will be on the") -> ArrowKind.ARRIVE
            has("roundabout", "rotary", "traffic circle") -> ArrowKind.ROUNDABOUT
            has("exit", "ramp") && left -> ArrowKind.EXIT_LEFT
            has("exit", "ramp") && right -> ArrowKind.EXIT_RIGHT
            has("fork") && left -> ArrowKind.FORK_LEFT
            has("fork") && right -> ArrowKind.FORK_RIGHT
            has("merge") && left -> ArrowKind.MERGE_LEFT
            has("merge") -> ArrowKind.MERGE_RIGHT
            has("slight left", "bear left", "slightly left") -> ArrowKind.SLIGHT_LEFT
            has("slight right", "bear right", "slightly right") -> ArrowKind.SLIGHT_RIGHT
            has("sharp left") -> ArrowKind.SHARP_LEFT
            has("sharp right") -> ArrowKind.SHARP_RIGHT
            has("keep left", "stay left") -> ArrowKind.KEEP_LEFT
            has("keep right", "stay right") -> ArrowKind.KEEP_RIGHT
            has("turn left", "left onto", "left on ", "left at", "left toward") -> ArrowKind.LEFT
            has("turn right", "right onto", "right on ", "right at", "right toward") -> ArrowKind.RIGHT
            has("continue", "straight", "head ", "go ") -> ArrowKind.STRAIGHT
            else -> null
        }
    }

    /**
     * The instruction with distances and road names stripped, so "In 300 m, turn left onto MG Road"
     * and "Turn left onto 80 Feet Rd" learn and recall as the same phrase: "turn left".
     */
    fun phraseKey(instruction: String?): String? {
        val s = instruction?.lowercase()?.trim() ?: return null
        val cut = s
            .replace(Regex("""(?:in\s+)?\d+(?:[.,]\d+)?\s*(?:km|kilometers?|m|meters?|mi|miles?|ft|feet)\b,?"""), " ")
            .split(" onto ", " toward ", " towards ", " on ", " to ", " at ", " for ", " ·")
            .first()
            .replace(Regex("[^a-z -]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cut.takeIf { it.length >= 3 }
    }
}

/**
 * A small perceptual fingerprint of Maps' arrow picture. Maps uses one fixed image per kind of
 * turn, so once a rider says which arrow an image means, every later notification with that image
 * gets it right.
 */
object ArrowIcon {
    /**
     * Difference hash of a 9 x 8 grey grid ([grey] row-major, 0..255): one bit per neighbour pair,
     * set when the left pixel is brighter.
     */
    fun dHash(grey: IntArray): Long {
        require(grey.size == 72) { "need a 9 x 8 grid" }
        var hash = 0L
        var bit = 0
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                if (grey[row * 9 + col] > grey[row * 9 + col + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}

/**
 * What the rider taught the app. Looked up first by arrow picture (close fingerprints count), then
 * by instruction phrase. Kept as plain lines so it can live in a small file.
 */
class ArrowMemory(
    private val byIcon: MutableMap<Long, ArrowKind> = mutableMapOf(),
    private val byPhrase: MutableMap<String, ArrowKind> = mutableMapOf(),
) {
    fun recall(iconHash: Long?, instruction: String?): ArrowKind? {
        if (iconHash != null) {
            byIcon[iconHash]?.let { return it }
            byIcon.entries.minByOrNull { ArrowIcon.distance(it.key, iconHash) }
                ?.takeIf { ArrowIcon.distance(it.key, iconHash) <= NEAR_BITS }
                ?.let { return it.value }
        }
        return ManeuverText.phraseKey(instruction)?.let { byPhrase[it] }
    }

    /** Remembers by picture when there is one, and always by phrase as a backup. */
    fun teach(iconHash: Long?, instruction: String?, arrow: ArrowKind) {
        if (iconHash != null) byIcon[iconHash] = arrow
        ManeuverText.phraseKey(instruction)?.let { byPhrase[it] = arrow }
    }

    fun forget() {
        byIcon.clear()
        byPhrase.clear()
    }

    val size: Int get() = byIcon.size + byPhrase.size

    fun encode(): String = buildString {
        byIcon.forEach { (h, a) -> append("i\t").append(java.lang.Long.toHexString(h)).append('\t').append(a.name).append('\n') }
        byPhrase.forEach { (p, a) -> append("p\t").append(p).append('\t').append(a.name).append('\n') }
    }

    companion object {
        /** Bits two fingerprints may differ by and still be the same picture (rescaling, anti-aliasing). */
        const val NEAR_BITS = 6

        fun decode(text: String): ArrowMemory {
            val m = ArrowMemory()
            text.lineSequence().forEach { line ->
                val bits = line.split('\t')
                if (bits.size != 3) return@forEach
                val arrow = ArrowKind.entries.firstOrNull { it.name == bits[2] } ?: return@forEach
                when (bits[0]) {
                    "i" -> java.lang.Long.parseUnsignedLong(bits[1], 16).let { m.byIcon[it] = arrow }
                    "p" -> m.byPhrase[bits[1]] = arrow
                }
            }
            return m
        }
    }
}

/** How the arrow for a step was decided, shown to the rider so a wrong guess is easy to spot. */
enum class ArrowSource { TAUGHT, TEXT, DEFAULT }

/** Picks the arrow: the rider's own correction, then Maps' wording, then straight ahead. */
fun pickArrow(memory: ArrowMemory, iconHash: Long?, vararg parts: String?): Pair<ArrowKind, ArrowSource> {
    val instruction = parts.filterNotNull().firstOrNull { ManeuverText.classify(it) != null } ?: parts.filterNotNull().firstOrNull()
    memory.recall(iconHash, instruction)?.let { return it to ArrowSource.TAUGHT }
    ManeuverText.classify(*parts)?.let { return it to ArrowSource.TEXT }
    return ArrowKind.STRAIGHT to ArrowSource.DEFAULT
}
