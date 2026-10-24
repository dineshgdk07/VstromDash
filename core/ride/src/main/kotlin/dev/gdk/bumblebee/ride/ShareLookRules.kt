package dev.gdk.bumblebee.ride

/**
 * What each look can show. A look has a number of stat slots the rider fills, and shows some
 * metrics in its own place (Cockpit's speed dial, Ride Pass's distance and moving time, the big
 * number), so those are not offered as stats.
 *
 * The rider's choice is one ordered list shared by all looks. Each look shows the first stats on
 * it that it can, so switching looks keeps the rider's order.
 */
object ShareLookRules {
    fun statSlots(look: ShareLook): Int = if (look == ShareLook.RIDE_PASS) 1 else ShareMetrics.MAX_STATS

    /** Night Glow and Poster lead with a big number the rider picks. */
    fun hasBigNumber(look: ShareLook): Boolean = look == ShareLook.NIGHT_GLOW || look == ShareLook.POSTER

    fun builtIn(look: ShareLook, hero: ShareMetricKind?): Set<ShareMetricKind> = when (look) {
        ShareLook.NIGHT_GLOW, ShareLook.POSTER -> setOfNotNull(hero)
        ShareLook.COCKPIT -> setOf(ShareMetricKind.MAX_SPEED)
        ShareLook.RIDE_PASS -> setOf(ShareMetricKind.DISTANCE, ShareMetricKind.MOVING)
    }

    /** Stats the rider can pick on [look]. */
    fun choices(look: ShareLook, available: List<ShareMetric>, hero: ShareMetricKind?): List<ShareMetric> {
        val fixed = builtIn(look, hero)
        return available.filter { it.kind !in fixed }
    }

    /** The stats [look] shows: the rider's order, or the default order, skipping built-in metrics. */
    fun stats(
        look: ShareLook,
        available: List<ShareMetric>,
        hero: ShareMetricKind?,
        preference: List<ShareMetricKind>?,
    ): List<ShareMetric> {
        val order = preference ?: ShareMetrics.defaultOrder(available)
        return ShareMetrics.select(choices(look, available, hero), order, statSlots(look))
    }

    /**
     * The rider's order after tapping [kind] on [look], where [shown] is what the look shows now.
     * A shown stat is removed. A hidden one is added while there is room; a one-stat look swaps.
     * Null when the look is already full.
     */
    fun toggle(
        look: ShareLook,
        preference: List<ShareMetricKind>,
        shown: List<ShareMetricKind>,
        kind: ShareMetricKind,
    ): List<ShareMetricKind>? {
        if (kind in shown) return preference - kind
        if (statSlots(look) == 1) return listOf(kind) + (preference - kind)
        if (shown.size >= statSlots(look)) return null
        return (preference - kind) + kind
    }
}
