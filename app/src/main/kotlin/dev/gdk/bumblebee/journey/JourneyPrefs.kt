package dev.gdk.bumblebee.journey

import android.content.Context
import dev.gdk.bumblebee.ride.JourneyEntry
import org.json.JSONArray
import org.json.JSONObject

/** A destination the rider saved: Home, Work, or anything they named. */
data class SavedPlace(val name: String, val query: String)

/** Night and rain layout: follow the conditions, always, or never. */
enum class BigMode { AUTO, ON, OFF }

/** Journey settings and the trip log. Lives on this phone. */
class JourneyPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var places: List<SavedPlace>
        get() = runCatching {
            val arr = JSONArray(prefs.getString(KEY_PLACES, "[]"))
            (0 until arr.length()).map { i -> arr.getJSONObject(i).let { SavedPlace(it.getString("n"), it.getString("q")) } }
        }.getOrDefault(emptyList())
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(JSONObject().put("n", it.name).put("q", it.query)) }
            prefs.edit().putString(KEY_PLACES, arr.toString()).apply()
        }

    /** Start recording a ride when Maps guidance starts and nothing is recording. */
    var autoRecord: Boolean
        get() = prefs.getBoolean(KEY_AUTO, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    var bigMode: BigMode
        get() = prefs.getString(KEY_BIG, null)?.let { v -> BigMode.entries.firstOrNull { it.name == v } } ?: BigMode.AUTO
        set(value) = prefs.edit().putString(KEY_BIG, value.name).apply()

    /** Where the rider said they were heading, set when they start from a place in the app. */
    var pendingDestination: String?
        get() = prefs.getString(KEY_PENDING, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_PENDING) else putString(KEY_PENDING, value) }.apply()

    /** The ETA last shared, as minutes of the day, to spot when it slips. */
    var sharedEtaMinute: Int?
        get() = prefs.getInt(KEY_SHARED, -1).takeIf { it >= 0 }
        set(value) = prefs.edit().putInt(KEY_SHARED, value ?: -1).apply()

    var log: List<JourneyEntry>
        get() = runCatching {
            val arr = JSONArray(prefs.getString(KEY_LOG, "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                JourneyEntry(
                    startTs = o.getLong("s"),
                    endTs = o.optLong("e", -1).takeIf { it >= 0 },
                    destination = o.optString("d").takeIf { it.isNotEmpty() },
                    firstEtaTs = o.optLong("eta", -1).takeIf { it >= 0 },
                    firstTotalKm = o.optDouble("km").takeIf { !it.isNaN() },
                    arrived = o.optBoolean("a"),
                )
            }
        }.getOrDefault(emptyList())
        set(value) {
            val arr = JSONArray()
            value.takeLast(KEEP).forEach { e ->
                arr.put(
                    JSONObject().put("s", e.startTs).put("e", e.endTs ?: -1).put("d", e.destination.orEmpty())
                        .put("eta", e.firstEtaTs ?: -1).put("km", e.firstTotalKm ?: Double.NaN).put("a", e.arrived),
                )
            }
            prefs.edit().putString(KEY_LOG, arr.toString()).apply()
        }

    private companion object {
        const val NAME = "bumblebee_journey"
        const val KEY_PLACES = "places"
        const val KEY_AUTO = "auto_record"
        const val KEY_BIG = "big_mode"
        const val KEY_PENDING = "pending_destination"
        const val KEY_SHARED = "shared_eta"
        const val KEY_LOG = "log"
        const val KEEP = 200
    }
}
