package dev.gdk.bumblebee.ride

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExport {

    fun toGpx(name: String, startTs: Long, points: List<GpsFix>): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val safe = xml(name)
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gpx version="1.1" creator="Bumblebee Dash" xmlns="http://www.topografix.com/GPX/1/1">""")
            appendLine("  <metadata>")
            appendLine("    <name>$safe</name>")
            appendLine("    <time>${iso.format(Date(startTs))}</time>")
            appendLine("  </metadata>")
            appendLine("  <trk>")
            appendLine("    <name>$safe</name>")
            appendLine("    <trkseg>")
            for (p in points) {
                appendLine("""      <trkpt lat="${p.lat}" lon="${p.lng}">""")
                p.altitudeM?.let { appendLine("        <ele>$it</ele>") }
                appendLine("        <time>${iso.format(Date(p.ts))}</time>")
                appendLine("      </trkpt>")
            }
            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }
    }

    fun recap(
        title: String,
        km: Double,
        movingSec: Int,
        maxSpeedKmh: Int,
        story: String,
        tankKmPerL: Double? = null,
        bikeKmPerL: Double? = null,
        litres: Double? = null,
        litresFromFills: Boolean = false,
    ): String = buildString {
        appendLine(title)
        appendLine(String.format("%.1f km · moving %s · top %d km/h", km, clock(movingSec), maxSpeedKmh))
        if (story.isNotBlank()) appendLine(story)
        tankKmPerL?.let { appendLine(String.format("Tank km/L (fills): %.1f", it)) }
        bikeKmPerL?.let { appendLine(String.format("Cluster km/L (last reading): %.1f", it)) }
        litres?.let {
            val src = if (litresFromFills) "from fill km/L" else "from cluster km/L — estimate"
            appendLine(String.format("Fuel used: %.1f L (%s)", it, src))
        }
        append("Exported from Bumblebee Dash. OSM track, not a Maps screenshot.")
    }

    private fun xml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun clock(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
