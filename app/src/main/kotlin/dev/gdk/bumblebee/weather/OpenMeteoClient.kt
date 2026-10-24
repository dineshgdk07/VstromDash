package dev.gdk.bumblebee.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Open-Meteo forecast lookup. The service needs no key or account, so there is
 * nothing to configure; it just needs a coordinate and network access.
 */
class OpenMeteoClient(private val endpoint: String = DEFAULT_ENDPOINT) {

    suspend fun fetch(lat: Double, lon: Double): WeatherSnapshot? = withContext(Dispatchers.IO) {
        val url = URL(
            "$endpoint?latitude=%.4f&longitude=%.4f".format(lat, lon) +
                "&current=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=1",
        )
        runCatching {
            val body = (url.openConnection() as HttpURLConnection).run {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                try {
                    if (responseCode != HttpURLConnection.HTTP_OK) return@run null
                    inputStream.bufferedReader().use { it.readText() }
                } finally {
                    disconnect()
                }
            } ?: return@runCatching null
            parse(body)
        }.getOrNull()
    }

    private fun parse(body: String): WeatherSnapshot? {
        val root = JSONObject(body)
        val current = root.optJSONObject("current") ?: return null
        if (!current.has("temperature_2m")) return null
        val daily = root.optJSONObject("daily")
        return WeatherSnapshot(
            tempC = current.getDouble("temperature_2m"),
            highC = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeIf { !it.isNaN() },
            lowC = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeIf { !it.isNaN() },
            code = current.optInt("weather_code", -1),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
    }
}
