package dev.gdk.bumblebee.ride

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

data class JourneyConfig(
    val gapMaxMin: Int = 120,
    val minSegments: Int = 2,
    val minTotalKm: Int = 15,
    val sameCalendarDay: Boolean = false,
    val odoSlackKm: Int = 1,
)

data class JourneyRide(
    val id: Long,
    val startTs: Long,
    val endTs: Long,
    val odoStartKm: Int?,
    val odoEndKm: Int?,
    val mergedIntoId: Long? = null,
)

data class JourneySuggestion(
    val rideIds: List<Long>,
    val totalKm: Int,
    val startTs: Long,
    val endTs: Long,
    val dateIso: String,
)

object JourneyDetector {
    fun detect(
        rides: List<JourneyRide>,
        cfg: JourneyConfig = JourneyConfig(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<JourneySuggestion> {
        val ended = rides.filter { it.mergedIntoId == null && it.odoEndKm != null }
            .sortedBy { it.startTs }
        if (ended.isEmpty()) return emptyList()
        val out = mutableListOf<JourneySuggestion>()
        var run = mutableListOf(ended.first())
        fun close() {
            if (run.size < cfg.minSegments) return
            val first = run.first()
            val last = run.last()
            val startOdo = first.odoStartKm ?: return
            val km = (last.odoEndKm!! - startOdo).coerceAtLeast(0)
            if (km < cfg.minTotalKm) return
            if (cfg.sameCalendarDay && dateIso(first.startTs, zone) != dateIso(last.startTs, zone)) return
            out += JourneySuggestion(
                rideIds = run.map { it.id },
                totalKm = km,
                startTs = first.startTs,
                endTs = last.endTs,
                dateIso = dateIso(first.startTs, zone),
            )
        }
        for (i in 1 until ended.size) {
            val prev = run.last()
            val cur = ended[i]
            val gapMin = (cur.startTs - prev.endTs) / 60_000L
            val chains = odoChains(prev, cur, cfg.odoSlackKm)
            val sameDay = !cfg.sameCalendarDay || dateIso(prev.startTs, zone) == dateIso(cur.startTs, zone)
            if (gapMin in 0..cfg.gapMaxMin.toLong() && chains && sameDay) {
                run += cur
            } else {
                close()
                run = mutableListOf(cur)
            }
        }
        close()
        return out.sortedByDescending { it.startTs }
    }

    fun caption(suggestion: JourneySuggestion, zone: ZoneId = ZoneId.systemDefault()): String {
        val day = DateTimeFormatter.ofPattern("EEE d MMM").withZone(zone).format(Instant.ofEpochMilli(suggestion.startTs))
        val from = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(suggestion.startTs))
        val to = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(suggestion.endTs))
        val stops = suggestion.rideIds.size - 1
        val stopWord = if (stops == 1) "1 engine-off" else "$stops engine-offs"
        val overnight = dateIso(suggestion.startTs, zone) != dateIso(suggestion.endTs, zone)
        val night = if (overnight) " · continued overnight" else ""
        return "$day · ${suggestion.rideIds.size} sessions · $stopWord · ${suggestion.totalKm} km · $from–$to$night"
    }

    private fun odoChains(prev: JourneyRide, cur: JourneyRide, slackKm: Int): Boolean {
        val end = prev.odoEndKm ?: return false
        val start = cur.odoStartKm ?: return false
        return abs(start - end) <= slackKm.coerceAtLeast(0)
    }

    private fun dateIso(ts: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(ts).atZone(zone).toLocalDate().toString()
}
