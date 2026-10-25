package dev.gdk.bumblebee.journey

import dev.gdk.bumblebee.nav.KeyHealth
import dev.gdk.bumblebee.nav.KeyProblem
import dev.gdk.bumblebee.nav.LatLng
import dev.gdk.bumblebee.nav.Maneuvers
import dev.gdk.bumblebee.nav.NavRoute
import dev.gdk.bumblebee.nav.Polyline
import dev.gdk.bumblebee.nav.RouteStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A search result: something to navigate to. Google results carry a place id, others a point. */
data class PlaceHit(val label: String, val detail: String?, val placeId: String? = null, val at: LatLng? = null)

class RoutingException(message: String, val problem: KeyProblem? = null) : Exception(message)

/** Search and routing from one service. */
interface Routing {
    suspend fun search(text: String, near: LatLng?): List<PlaceHit>
    suspend fun route(from: LatLng, to: PlaceHit): NavRoute

    companion object {
        fun of(context: android.content.Context, settings: NavSettings): Routing? = when (settings.mode) {
            NavMode.GOOGLE -> settings.googleKey.takeIf { it.isNotBlank() }?.let { GoogleRouting(it, AppIdentity.headers(context)) }
            NavMode.OPEN -> settings.orsKey.takeIf { it.isNotBlank() }?.let { OrsRouting(it) }
            NavMode.NOTIFICATIONS -> null
        }
    }
}

private suspend fun http(
    url: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
): String = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val problem = KeyHealth.classify(code, text)
            throw RoutingException(if (problem == KeyProblem.OTHER) explain(code, text) else problem.advice, problem)
        }
        text
    } finally {
        conn.disconnect()
    }
}

/** A rider-readable reason for a failed call, from the status and the service's own message. */
private fun explain(code: Int, body: String): String =
    "The routing service said $code. ${KeyHealth.message(body).orEmpty()}".trim()

/**
 * Google Places (New) autocomplete and the Routes API in two-wheeler mode. Google's terms allow
 * showing these routes only on a Google map, which the Journey screen respects.
 */
class GoogleRouting(private val key: String, private val identity: Map<String, String> = emptyMap()) : Routing {
    override suspend fun search(text: String, near: LatLng?): List<PlaceHit> {
        val body = JSONObject().put("input", text)
        near?.let {
            body.put(
                "locationBias",
                JSONObject().put(
                    "circle",
                    JSONObject().put("center", JSONObject().put("latitude", it.lat).put("longitude", it.lng)).put("radius", 50_000.0),
                ),
            )
        }
        val json = JSONObject(
            http(
                "https://places.googleapis.com/v1/places:autocomplete",
                method = "POST",
                headers = identity + ("X-Goog-Api-Key" to key),
                body = body.toString(),
            ),
        )
        val list = json.optJSONArray("suggestions") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val p = list.getJSONObject(i).optJSONObject("placePrediction") ?: return@mapNotNull null
            val main = p.optJSONObject("structuredFormat")?.optJSONObject("mainText")?.optString("text")
            val secondary = p.optJSONObject("structuredFormat")?.optJSONObject("secondaryText")?.optString("text")
            val full = p.optJSONObject("text")?.optString("text").orEmpty()
            PlaceHit(main ?: full, secondary, placeId = p.optString("placeId").takeIf { it.isNotBlank() })
        }
    }

    override suspend fun route(from: LatLng, to: PlaceHit): NavRoute {
        fun point(p: LatLng) = JSONObject().put("location", JSONObject().put("latLng", JSONObject().put("latitude", p.lat).put("longitude", p.lng)))
        val destination = when {
            to.placeId != null -> JSONObject().put("placeId", to.placeId)
            to.at != null -> point(to.at)
            else -> JSONObject().put("address", to.label)
        }
        val body = JSONObject()
            .put("origin", point(from))
            .put("destination", destination)
            .put("travelMode", "TWO_WHEELER")
            .put("routingPreference", "TRAFFIC_AWARE")
            .put("languageCode", "en-IN")
            .put("units", "METRIC")
        val fields = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline," +
            "routes.legs.steps.distanceMeters,routes.legs.steps.polyline.encodedPolyline,routes.legs.steps.navigationInstruction"
        val json = JSONObject(
            http(
                "https://routes.googleapis.com/directions/v2:computeRoutes",
                method = "POST",
                headers = identity + mapOf("X-Goog-Api-Key" to key, "X-Goog-FieldMask" to fields),
                body = body.toString(),
            ),
        )
        val r = json.optJSONArray("routes")?.optJSONObject(0) ?: throw RoutingException("No route found to ${to.label}.")
        val points = Polyline.decode(r.getJSONObject("polyline").getString("encodedPolyline"))
        // Each step's instruction is what to do at its start, so the arrow for the turn that ends
        // step n is step n+1's maneuver.
        val raw = mutableListOf<Triple<Int, String?, String?>>()
        val legs = r.optJSONArray("legs") ?: JSONArray()
        val distances = mutableListOf<Double>()
        var cursor = 0
        for (l in 0 until legs.length()) {
            val steps = legs.getJSONObject(l).optJSONArray("steps") ?: continue
            for (s in 0 until steps.length()) {
                val st = steps.getJSONObject(s)
                // Where this step ends on the whole route: the route point nearest its last point,
                // searching forward so a route that doubles back stays in order.
                val last = Polyline.decode(st.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()).lastOrNull()
                if (last != null) {
                    var best = cursor
                    var bestD = Double.MAX_VALUE
                    for (i in cursor until points.size) {
                        val d = dev.gdk.bumblebee.nav.RouteGuide.metres(points[i], last)
                        if (d < bestD) {
                            bestD = d
                            best = i
                        }
                        if (bestD < 1.0) break
                    }
                    cursor = best
                }
                val nav = st.optJSONObject("navigationInstruction")
                raw += Triple(cursor, nav?.optString("maneuver"), nav?.optString("instructions"))
                distances += st.optDouble("distanceMeters", 0.0)
            }
        }
        val steps = raw.mapIndexed { i, (end, _, _) ->
            val next = raw.getOrNull(i + 1)
            val arrow = if (next == null) dev.gdk.bumblebee.nav.ArrowKind.ARRIVE else Maneuvers.google(next.second, next.third)
            RouteStep(arrow, next?.third?.replace('\n', ' ') ?: "Arrive at ${to.label}", distances[i], end)
        }
        return NavRoute(
            points = points,
            steps = steps,
            distanceM = r.optDouble("distanceMeters", 0.0),
            durationS = r.optString("duration").removeSuffix("s").toDoubleOrNull() ?: 0.0,
            destination = to.label,
        )
    }
}

/**
 * OpenRouteService (openrouteservice.org): Pelias autocomplete and driving directions. Free keys
 * allow a few thousand requests a day, plenty for one rider.
 */
class OrsRouting(private val key: String) : Routing {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    override suspend fun search(text: String, near: LatLng?): List<PlaceHit> {
        val focus = near?.let { "&focus.point.lat=${it.lat}&focus.point.lon=${it.lng}" }.orEmpty()
        val json = JSONObject(http("https://api.openrouteservice.org/geocode/autocomplete?api_key=${enc(key)}&text=${enc(text)}&boundary.country=IN$focus"))
        val features = json.optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val f = features.getJSONObject(i)
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val props = f.optJSONObject("properties")
            val name = props?.optString("name").orEmpty()
            val label = props?.optString("label").orEmpty()
            PlaceHit(name.ifBlank { label }, label.removePrefix(name).trimStart(',', ' ').ifBlank { null }, at = LatLng(c.getDouble(1), c.getDouble(0)))
        }
    }

    override suspend fun route(from: LatLng, to: PlaceHit): NavRoute {
        val dest = to.at ?: search(to.label, from).firstOrNull()?.at ?: throw RoutingException("Couldn't find ${to.label}.")
        val body = JSONObject()
            .put("coordinates", JSONArray().put(JSONArray().put(from.lng).put(from.lat)).put(JSONArray().put(dest.lng).put(dest.lat)))
            .put("instructions", true)
            .put("language", "en")
            .put("units", "m")
        val json = JSONObject(
            http(
                "https://api.openrouteservice.org/v2/directions/driving-car/json",
                method = "POST",
                headers = mapOf("Authorization" to key),
                body = body.toString(),
            ),
        )
        val r = json.optJSONArray("routes")?.optJSONObject(0) ?: throw RoutingException("No route found to ${to.label}.")
        val points = Polyline.decode(r.getString("geometry"))
        val summary = r.optJSONObject("summary")
        val raw = mutableListOf<RouteStep>()
        val segments = r.optJSONArray("segments") ?: JSONArray()
        // ORS steps start with the maneuver, like Google's, so shift arrows forward one step.
        val flat = mutableListOf<JSONObject>()
        for (s in 0 until segments.length()) {
            val steps = segments.getJSONObject(s).optJSONArray("steps") ?: continue
            for (i in 0 until steps.length()) flat += steps.getJSONObject(i)
        }
        flat.forEachIndexed { i, st ->
            val next = flat.getOrNull(i + 1)
            val wp = st.optJSONArray("way_points")
            val end = wp?.optInt(1, points.lastIndex) ?: points.lastIndex
            if (next == null && st.optInt("type") == 10) return@forEachIndexed
            val arrow = next?.let { Maneuvers.ors(it.optInt("type", 6), it.optString("instruction")) } ?: dev.gdk.bumblebee.nav.ArrowKind.ARRIVE
            raw += RouteStep(arrow, next?.optString("instruction") ?: "Arrive at ${to.label}", st.optDouble("distance", 0.0), end.coerceAtMost(points.lastIndex))
        }
        return NavRoute(points, raw, summary?.optDouble("distance") ?: 0.0, summary?.optDouble("duration") ?: 0.0, to.label)
    }
}
