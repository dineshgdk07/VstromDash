package dev.gdk.bumblebee.nav

import android.app.Notification
import android.service.notification.StatusBarNotification
import dev.gdk.bumblebee.nav.ManeuverMap.DEFAULT_CLUSTER_BYTE

object GoogleMapsParser {
    const val PKG_GOOGLE_MAPS = "com.google.android.apps.maps"

    fun parse(sbn: StatusBarNotification): ParsedNavData? {
        if (sbn.packageName != PKG_GOOGLE_MAPS) return null
        val n = sbn.notification ?: return null
        if (!looksLikeNavigation(n)) return null
        val extras = n.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (title.isNullOrBlank() && subText.isNullOrBlank() && text.isNullOrBlank()) return null

        val distFromTitle = title?.let { extractInlineDistance(it) }
        val (distNext, distNextUnit) = distFromTitle ?: distanceFromProgress(extras)
        val eta = normalizeEta(subText, twelveHour = true)
        val (distTotal, distTotalUnit) = extractTotalDistance(subText)
        val instruction = title ?: text
        val mapplsId = MapplsIdGuesser.fromText(instruction)
        val maneuverId = ManeuverMap.mapplsIdToClusterByte(mapplsId) ?: DEFAULT_CLUSTER_BYTE
        return ParsedNavData(
            maneuverId = maneuverId,
            distNext = distNext,
            distNextUnit = distNextUnit,
            eta = eta,
            distTotal = distTotal,
            distTotalUnit = distTotalUnit,
            streetName = instruction,
        )
    }

    internal fun looksLikeNavigation(n: Notification): Boolean {
        if (n.category == Notification.CATEGORY_NAVIGATION) return true
        val max = n.extras.getInt("android.progressMax", -1)
        return max > 0
    }

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
}
