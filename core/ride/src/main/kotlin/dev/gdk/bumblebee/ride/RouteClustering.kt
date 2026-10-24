package dev.gdk.bumblebee.ride

import kotlin.math.floor

data class GridCell(val row: Long, val col: Long)

data class RouteCluster(
    val clusterId: Int,
    val rideIds: List<Long>,
    val representativeSignature: Set<GridCell>,
)

object RouteClustering {
    const val GRID_DEG: Double = 0.001
    const val SIMILARITY_THRESHOLD: Double = 0.60
    const val MIN_POINTS: Int = 3
    const val UNTRACKED: Int = -1

    fun cluster(
        rideIds: List<Long>,
        tracks: Map<Long, List<GpsFix>>,
        gridDeg: Double = GRID_DEG,
        similarity: Double = SIMILARITY_THRESHOLD,
        minPoints: Int = MIN_POINTS,
    ): List<RouteCluster> {
        if (rideIds.isEmpty()) return emptyList()
        val reps = mutableListOf<MutableSet<GridCell>>()
        val members = mutableListOf<MutableList<Long>>()
        val untracked = mutableListOf<Long>()
        for (id in rideIds.sorted()) {
            val pts = tracks[id].orEmpty()
            if (pts.size < minPoints) {
                untracked += id
                continue
            }
            val sig = signature(pts, gridDeg)
            if (sig.isEmpty()) {
                untracked += id
                continue
            }
            val match = reps.indexOfFirst { jaccard(it, sig) >= similarity }
            if (match >= 0) {
                reps[match].addAll(sig)
                members[match] += id
            } else {
                reps += sig.toHashSet()
                members += mutableListOf(id)
            }
        }
        val real = reps.indices.map { i ->
            RouteCluster(i, members[i].toList(), reps[i].toSet())
        }.sortedByDescending { it.rideIds.size }
        return if (untracked.isEmpty()) real
        else real + RouteCluster(UNTRACKED, untracked, emptySet())
    }

    internal fun cellOf(lat: Double, lng: Double, gridDeg: Double = GRID_DEG): GridCell =
        GridCell(floorDiv(lat, gridDeg), floorDiv(lng, gridDeg))

    internal fun signature(points: List<GpsFix>, gridDeg: Double = GRID_DEG): Set<GridCell> =
        points.mapTo(HashSet()) { cellOf(it.lat, it.lng, gridDeg) }

    internal fun jaccard(a: Set<GridCell>, b: Set<GridCell>): Double {
        if (a.isEmpty() && b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        val union = a.size + b.size - inter
        return if (union == 0) 0.0 else inter.toDouble() / union
    }

    private fun floorDiv(value: Double, divisor: Double): Long =
        floor(value / divisor + 1e-9).toLong()
}

data class RouteRide(
    val id: Long,
    val startTs: Long,
    val endTs: Long?,
    val km: Double,
    val movingSec: Int,
)

data class RouteStats(
    val clusterId: Int,
    val runCount: Int,
    val medianKm: Double?,
    val totalKm: Double,
    val medianMovingSec: Int?,
    val bestMovingSec: Int?,
    val litresEst: Double?,
    val isUntracked: Boolean,
)

object RouteLeaderboard {
    fun leaderboard(
        clusters: List<RouteCluster>,
        rides: List<RouteRide>,
        kmPerL: Double? = null,
    ): List<RouteStats> {
        val byId = rides.associateBy { it.id }
        val real = clusters.filter { it.clusterId != RouteClustering.UNTRACKED }
            .map { stats(it, byId, kmPerL, untracked = false) }
            .sortedByDescending { it.runCount }
        val untracked = clusters.firstOrNull { it.clusterId == RouteClustering.UNTRACKED }
            ?.let { stats(it, byId, null, untracked = true) }
        return if (untracked != null) real + untracked else real
    }

    private fun stats(
        cluster: RouteCluster,
        byId: Map<Long, RouteRide>,
        kmPerL: Double?,
        untracked: Boolean,
    ): RouteStats {
        val members = cluster.rideIds.mapNotNull { byId[it] }
        val kms = members.map { it.km }.filter { it > 0 }
        val moving = members.map { it.movingSec }.filter { it > 0 }
        val medianKm = kms.takeIf { it.isNotEmpty() }?.sorted().let { s ->
            if (s == null) null else median(s)
        }
        val usable = kmPerL?.takeIf { it > 0 }
        val litres = if (!untracked && usable != null && medianKm != null) medianKm / usable else null
        return RouteStats(
            clusterId = cluster.clusterId,
            runCount = members.size,
            medianKm = if (untracked) null else medianKm,
            totalKm = kms.sum(),
            medianMovingSec = if (untracked) null else moving.takeIf { it.isNotEmpty() }?.sorted()?.let { medianInt(it) },
            bestMovingSec = if (untracked) null else moving.minOrNull(),
            litresEst = litres,
            isUntracked = untracked,
        )
    }

    private fun median(values: List<Double>): Double {
        val mid = values.size / 2
        return if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2.0
    }

    private fun medianInt(values: List<Int>): Int {
        val mid = values.size / 2
        return if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2
    }
}
