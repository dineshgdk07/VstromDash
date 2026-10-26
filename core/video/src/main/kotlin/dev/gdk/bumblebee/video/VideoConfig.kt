package dev.gdk.bumblebee.video

/** What rides the route: a glowing dot, or the rider's own bike in its paint. */
enum class MarkerStyle(val label: String) { DOT("Glow dot"), BIKE("My bike") }

/** How the live readouts look: clean numbers, or panels styled on the bike's own dash. */
enum class HudStyle(val label: String) { CLASSIC("Classic"), CLUSTER("Cluster") }

/** A named starting point in the Studio; any change after picking one makes it [CUSTOM]. */
enum class VideoPreset(val label: String, val blurb: String) {
    CINEMATIC("Cinematic", "Balanced: real light, soft glow, the full story"),
    ADVENTURE("Adventure", "Bolder hills, closer camera, brighter trail"),
    DATA("Data", "Contours and every readout, clearer air"),
    MINIMAL("Minimal", "Just the land and the line"),
    CUSTOM("Custom", "Your own mix"),
}

/**
 * Every choice the rider can make about a ride video. Ranges are enforced by [clamped], so a
 * stored or hand-edited value can't break a render. New options get a default here, and old
 * saved configs simply pick it up.
 */
data class VideoConfig(
    val preset: VideoPreset = VideoPreset.CINEMATIC,
    // Size and time
    val shape: VideoShape = VideoShape.VERTICAL,
    /** Null for the recommended length. */
    val lengthS: Int? = null,
    // Story
    val title: String? = null,
    val titleCard: Boolean = true,
    val places: Boolean = true,
    val moments: Boolean = true,
    val finishCard: Boolean = true,
    /** A short teaser of the best moment before the title. */
    val coldOpen: Boolean = true,
    /** Named acts (the climb, the twisties, after the break) with a card as each begins. */
    val chapters: Boolean = true,
    /** The finale climbs to a top-down view and redraws the whole route in light. */
    val mapReveal: Boolean = true,
    // Overlays
    val hud: Boolean = true,
    val clock: Boolean = true,
    val elevationStrip: Boolean = true,
    val hudStyle: HudStyle = HudStyle.CLASSIC,
    /** Ride number, weather and sunrise/sunset on the title and closing cards. */
    val tags: Boolean = true,
    // Personal
    val marker: MarkerStyle = MarkerStyle.BIKE,
    /** Marker size, × the default. */
    val markerSize: Double = 1.0,
    /** The rider's own photos from the ride, as postcards where they were taken. */
    val photos: Boolean = true,
    // Camera
    /** Chase height and distance, × the automatic value. */
    val cameraDistance: Double = 1.0,
    /** Null for automatic height exaggeration. */
    val exaggeration: Double? = null,
    /** A different camera move for each kind of moment, rather than one orbit for all. */
    val momentShots: Boolean = true,
    /** How much the camera leans into turns, 0 (level) to 1. */
    val bank: Double = 0.6,
    /** Rise to a near top-down view over hairpins. */
    val hairpinView: Boolean = true,
    // Look
    val pathWidth: Double = 1.0,
    val glow: Double = 1.0,
    /** Haze toward the horizon, × the default. */
    val haze: Double = 1.0,
    val contours: Boolean = true,
    // World
    /** Light follows the ride's own clock: a sunset ride gets darker as it goes. */
    val timeOfDay: Boolean = true,
    /** Cloud cover, 0 (clear) to 1 (overcast); clouds drift and cast shadows. */
    val clouds: Double = 0.3,
    /** Lakes, reservoirs and sea found in the terrain, with reflections. */
    val water: Boolean = true,
    /** Towns along the route, pinned in 3D as they come into view. */
    val placeLabels: Boolean = true,
    val vignette: Boolean = true,
    val grain: Boolean = true,
) {
    fun clamped(): VideoConfig = copy(
        lengthS = lengthS?.coerceIn(5, VideoLength.MAX_S),
        title = title?.trim()?.take(40)?.ifBlank { null },
        cameraDistance = cameraDistance.coerceIn(0.5, 2.0),
        exaggeration = exaggeration?.coerceIn(1.0, 4.0),
        bank = bank.coerceIn(0.0, 1.0),
        clouds = clouds.coerceIn(0.0, 1.0),
        markerSize = markerSize.coerceIn(0.5, 2.0),
        pathWidth = pathWidth.coerceIn(0.5, 2.5),
        glow = glow.coerceIn(0.0, 2.0),
        haze = haze.coerceIn(0.2, 2.0),
    )

    /** Applies a preset's look and story choices, keeping size, length and title. */
    fun withPreset(p: VideoPreset): VideoConfig {
        val base = when (p) {
            VideoPreset.CINEMATIC, VideoPreset.CUSTOM -> VideoConfig()
            VideoPreset.ADVENTURE -> VideoConfig(cameraDistance = 0.8, exaggeration = 2.4, pathWidth = 1.3, glow = 1.5, haze = 0.8, bank = 1.0, clouds = 0.45)
            VideoPreset.DATA -> VideoConfig(cameraDistance = 1.2, haze = 0.5, pathWidth = 1.1, glow = 0.8, grain = false, clouds = 0.0, hudStyle = HudStyle.CLUSTER)
            VideoPreset.MINIMAL -> VideoConfig(
                hud = false, clock = false, elevationStrip = false, moments = false, places = false,
                contours = false, grain = false, pathWidth = 0.8, glow = 0.7,
                coldOpen = false, chapters = false, bank = 0.3, clouds = 0.0, placeLabels = false,
                tags = false, marker = MarkerStyle.DOT,
            )
        }
        return base.copy(preset = p, shape = shape, lengthS = lengthS, title = title)
    }

    /** A plain key=value form for storing and passing between components. */
    fun encode(): String = listOf(
        "preset" to preset.name,
        "shape" to shape.name,
        "length" to (lengthS?.toString() ?: ""),
        "title" to (title ?: "").replace(";", ",").replace("=", "-"),
        "titleCard" to titleCard,
        "places" to places,
        "moments" to moments,
        "finishCard" to finishCard,
        "coldOpen" to coldOpen,
        "chapters" to chapters,
        "mapReveal" to mapReveal,
        "momentShots" to momentShots,
        "bank" to bank,
        "hairpinView" to hairpinView,
        "hud" to hud,
        "clock" to clock,
        "strip" to elevationStrip,
        "hudStyle" to hudStyle.name,
        "tags" to tags,
        "marker" to marker.name,
        "markerSize" to markerSize,
        "photos" to photos,
        "camera" to cameraDistance,
        "exag" to (exaggeration?.toString() ?: ""),
        "pathWidth" to pathWidth,
        "glow" to glow,
        "haze" to haze,
        "contours" to contours,
        "timeOfDay" to timeOfDay,
        "clouds" to clouds,
        "water" to water,
        "placeLabels" to placeLabels,
        "vignette" to vignette,
        "grain" to grain,
    ).joinToString(";") { (k, v) -> "$k=$v" }

    companion object {
        /** Reads [encode]'s form; unknown or bad entries keep their defaults. */
        fun decode(s: String?): VideoConfig {
            if (s.isNullOrBlank()) return VideoConfig()
            val m = s.split(';').mapNotNull { e -> e.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            val d = VideoConfig()
            fun b(k: String, def: Boolean) = m[k]?.toBooleanStrictOrNull() ?: def
            fun n(k: String, def: Double) = m[k]?.toDoubleOrNull() ?: def
            return VideoConfig(
                preset = m["preset"]?.let { v -> VideoPreset.entries.firstOrNull { it.name == v } } ?: d.preset,
                shape = m["shape"]?.let { v -> VideoShape.entries.firstOrNull { it.name == v } } ?: d.shape,
                lengthS = m["length"]?.toIntOrNull(),
                title = m["title"]?.ifBlank { null },
                titleCard = b("titleCard", d.titleCard),
                places = b("places", d.places),
                moments = b("moments", d.moments),
                finishCard = b("finishCard", d.finishCard),
                coldOpen = b("coldOpen", d.coldOpen),
                chapters = b("chapters", d.chapters),
                mapReveal = b("mapReveal", d.mapReveal),
                momentShots = b("momentShots", d.momentShots),
                bank = n("bank", d.bank),
                hairpinView = b("hairpinView", d.hairpinView),
                hud = b("hud", d.hud),
                clock = b("clock", d.clock),
                elevationStrip = b("strip", d.elevationStrip),
                hudStyle = m["hudStyle"]?.let { v -> HudStyle.entries.firstOrNull { it.name == v } } ?: d.hudStyle,
                tags = b("tags", d.tags),
                marker = m["marker"]?.let { v -> MarkerStyle.entries.firstOrNull { it.name == v } } ?: d.marker,
                markerSize = n("markerSize", d.markerSize),
                photos = b("photos", d.photos),
                cameraDistance = n("camera", d.cameraDistance),
                exaggeration = m["exag"]?.toDoubleOrNull(),
                pathWidth = n("pathWidth", d.pathWidth),
                glow = n("glow", d.glow),
                haze = n("haze", d.haze),
                contours = b("contours", d.contours),
                timeOfDay = b("timeOfDay", d.timeOfDay),
                clouds = n("clouds", d.clouds),
                water = b("water", d.water),
                placeLabels = b("placeLabels", d.placeLabels),
                vignette = b("vignette", d.vignette),
                grain = b("grain", d.grain),
            ).clamped()
        }
    }
}
