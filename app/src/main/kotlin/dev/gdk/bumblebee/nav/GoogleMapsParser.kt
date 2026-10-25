package dev.gdk.bumblebee.nav

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.service.notification.StatusBarNotification

object GoogleMapsParser {
    const val PKG_GOOGLE_MAPS = "com.google.android.apps.maps"

    /**
     * Reads one Google Maps navigation notification. Maps spreads a step across the title, text
     * and sub-text and the order changes between versions, so every field is searched for the
     * distance, the instruction and the trip summary. The arrow comes from the rider's own
     * corrections first (matched on Maps' arrow picture), then the wording.
     */
    fun parse(context: Context, sbn: StatusBarNotification): ParsedNavData? {
        if (sbn.packageName != PKG_GOOGLE_MAPS) return null
        val n = sbn.notification ?: return null
        if (!looksLikeNavigation(n)) return null
        val extras = n.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (title.isNullOrBlank() && subText.isNullOrBlank() && text.isNullOrBlank()) return null

        // The trip summary is the part with a clock time or "min" in it.
        val summary = listOfNotNull(subText, text, title).firstOrNull { looksLikeSummary(it) }
        val stepParts = listOfNotNull(title, text, subText).filter { it != summary }
        val (distNext, distNextUnit) = stepParts.firstNotNullOfOrNull { extractInlineDistance(it) } ?: distanceFromProgress(extras)
        val eta = normalizeEta(summary, twelveHour = true)
        val (distTotal, distTotalUnit) = extractTotalDistance(summary)
        val instruction = stepParts.firstOrNull { ManeuverText.classify(it) != null }
            ?: stepParts.firstOrNull { extractInlineDistance(it) == null }
            ?: stepParts.firstOrNull()
        val hash = iconHash(context, n)
        val (arrow, source) = pickArrow(ArrowTeacher.memory(context), hash, *stepParts.toTypedArray())
        val parsed = ParsedNavData(
            maneuverId = arrow.clusterByte,
            distNext = distNext,
            distNextUnit = distNextUnit,
            eta = eta,
            distTotal = distTotal,
            distTotalUnit = distTotalUnit,
            streetName = instruction,
            arrow = arrow,
            arrowSource = source,
            iconHash = hash,
            instruction = instruction,
            summary = summary,
        )
        ArrowTeacher.log(title, text, subText, parsed)
        return parsed
    }

    internal fun looksLikeNavigation(n: Notification): Boolean {
        if (n.category == Notification.CATEGORY_NAVIGATION) return true
        val max = n.extras.getInt("android.progressMax", -1)
        return max > 0
    }

    private val SUMMARY = Regex("""(?i)(\d{1,2}:\d{2}|\bmin\b|\bhr\b|\bh\b|\barrive\b|\beta\b)""")

    private fun looksLikeSummary(s: String) = SUMMARY.containsMatchIn(s)

    private fun extractInlineDistance(text: String): Pair<String, String>? {
        val re = Regex("""(?i)(?:in\s+)?(\d+(?:[.,]\d+)?)\s*(kilometers?|km|miles?|mi|meters?|m|feet?|ft)\b""")
        val match = re.find(text) ?: return null
        return normalizeDistance(match.value)
    }

    private fun distanceFromProgress(extras: android.os.Bundle): Pair<String, String> {
        val max = extras.getInt("android.progressMax", -1)
        val cur = extras.getInt("android.progress", 0)
        if (max <= 0) return "0000" to "M"
        val remainingMeters = (max - cur).coerceAtLeast(0)
        return formatClusterDistance(remainingMeters.toDouble())
    }

    /** Fingerprint of Maps' arrow picture (its large icon), or null if there isn't one. */
    private fun iconHash(context: Context, n: Notification): Long? = runCatching {
        val drawable = n.getLargeIcon()?.loadDrawable(context) ?: return null
        val src = (drawable as? BitmapDrawable)?.bitmap ?: run {
            val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 64, 64)
            drawable.draw(android.graphics.Canvas(b))
            b
        }
        val small = Bitmap.createScaledBitmap(src, 9, 8, true)
        // Arrows are drawn as shapes on a clear background, so opacity counts as brightness.
        val grey = IntArray(72) { i ->
            val c = small.getPixel(i % 9, i / 9)
            val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            maxOf(lum, 255 - lum) * Color.alpha(c) / 255
        }
        ArrowIcon.dHash(grey)
    }.getOrNull()
}
