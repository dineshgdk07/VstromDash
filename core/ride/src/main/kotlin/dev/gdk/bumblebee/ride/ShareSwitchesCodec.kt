package dev.gdk.bumblebee.ride

/** Remembered share settings as `key=value;` pairs. Photo is stored as the look's own background. */
object ShareSwitchesCodec {
    fun encode(s: ShareSwitches): String {
        val background = if (s.background == ShareBackground.PHOTO) ShareBackground.OWN else s.background
        val parts = ArrayList<String>(9)
        parts += "look=${s.look.name}"
        parts += "shape=${s.shape.name}"
        parts += "background=${background.name}"
        parts += "accent=${s.accent.name}"
        parts += "big=${s.bigNumber?.name.orEmpty()}"
        if (s.stats != null) parts += "stats=${s.stats.joinToString(",") { it.name }}"
        parts += "places=${if (s.showPlaces) "1" else "0"}"
        parts += "highlight=${if (s.showHighlight) "1" else "0"}"
        parts += "weather=${if (s.showWeather) "1" else "0"}"
        return parts.joinToString(";")
    }

    fun decode(raw: String?, available: List<ShareMetricKind>): ShareSwitches {
        if (raw.isNullOrBlank()) return ShareSwitches()
        val map = HashMap<String, String>()
        for (part in raw.split(';')) {
            val idx = part.indexOf('=')
            if (idx <= 0) continue
            map[part.substring(0, idx)] = part.substring(idx + 1)
        }
        val look = enumOr(map["look"], ShareLook.entries, ShareLook.NIGHT_GLOW)
        val shape = enumOr(map["shape"], ShareShape.entries, ShareShape.STORY)
        var background = enumOr(map["background"], ShareBackground.entries, ShareBackground.OWN)
        if (background == ShareBackground.PHOTO) background = ShareBackground.OWN
        val accent = enumOr(map["accent"], ShareAccent.entries, ShareAccent.YELLOW)
        val big = when (val name = map["big"]) {
            null -> ShareMetricKind.DISTANCE
            "" -> null
            else -> ShareMetricKind.entries.firstOrNull { it.name == name } ?: ShareMetricKind.DISTANCE
        }
        val stats = if ("stats" !in map) {
            null
        } else {
            map.getValue("stats").split(',')
                .filter { it.isNotEmpty() }
                .mapNotNull { name -> ShareMetricKind.entries.firstOrNull { it.name == name } }
                .filter { it in available }
        }
        return ShareSwitches(
            look = look,
            shape = shape,
            background = background,
            accent = accent,
            bigNumber = big,
            stats = stats,
            showPlaces = flag(map["places"], default = true),
            showHighlight = flag(map["highlight"], default = true),
            showWeather = flag(map["weather"], default = true),
        )
    }

    private fun flag(raw: String?, default: Boolean): Boolean = when (raw) {
        "1" -> true
        "0" -> false
        else -> default
    }

    private fun <T : Enum<T>> enumOr(raw: String?, values: List<T>, fallback: T): T {
        if (raw == null) return fallback
        return values.firstOrNull { it.name == raw } ?: fallback
    }
}
