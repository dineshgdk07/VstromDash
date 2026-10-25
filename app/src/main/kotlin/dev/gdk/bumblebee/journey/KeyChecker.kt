package dev.gdk.bumblebee.journey

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.gdk.bumblebee.BuildConfig
import dev.gdk.bumblebee.nav.ApiCheck
import dev.gdk.bumblebee.nav.KeyProblem
import dev.gdk.bumblebee.nav.LatLng
import java.io.IOException
import java.security.MessageDigest

/**
 * Who this app is, as Google's web APIs want to hear it when a key is restricted to Android
 * apps: the package name and the SHA-1 of the signing certificate.
 */
object AppIdentity {
    fun sha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        val cert = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return null
        MessageDigest.getInstance("SHA-1").digest(cert.toByteArray()).joinToString("") { "%02X".format(it) }
    }.getOrNull()

    fun headers(context: Context): Map<String, String> = buildMap {
        put("X-Android-Package", context.packageName)
        sha1(context)?.let { put("X-Android-Cert", it) }
    }

    /** The fingerprint as the Cloud console shows it: AA:BB:… */
    fun sha1Pretty(context: Context): String? = sha1(context)?.chunked(2)?.joinToString(":")
}

/**
 * Tries each API a key will be used for, one small request each, and reports per API what works
 * and what to fix. Search and routing must both pass before a key is saved.
 */
object KeyChecker {
    private val A = LatLng(12.9716, 77.5946)
    private val B = LatLng(12.9784, 77.6408)

    suspend fun check(context: Context, mode: NavMode, key: String): List<ApiCheck> {
        val out = mutableListOf<ApiCheck>()
        val routing = when (mode) {
            NavMode.GOOGLE -> GoogleRouting(key, AppIdentity.headers(context))
            NavMode.OPEN -> OrsRouting(key)
            NavMode.NOTIFICATIONS -> return emptyList()
        }
        val searchName = if (mode == NavMode.GOOGLE) "Places API (New) · search" else "Geocoding · search"
        val routeName = if (mode == NavMode.GOOGLE) "Routes API · directions" else "Directions"
        out += attempt(searchName) {
            val hits = routing.search("MG Road", A)
            if (hits.isEmpty()) "Answered, but found nothing for a test search" else null
        }
        out += attempt(routeName) {
            val r = routing.route(A, PlaceHit("Test", null, at = B))
            if (r.points.size < 2) "Answered, but returned no route" else null
        }
        if (mode == NavMode.GOOGLE) {
            out += ApiCheck(
                "Maps SDK · map display",
                ok = BuildConfig.HAS_MAPS_KEY,
                detail = if (BuildConfig.HAS_MAPS_KEY) "Built in" else "Not built in. Add MAPS_API_KEY to local.properties and rebuild to see Google's map; navigation works without it.",
                optional = true,
            )
        }
        return out
    }

    /** True when every required API passed. */
    fun usable(checks: List<ApiCheck>) = checks.isNotEmpty() && checks.filter { !it.optional }.all { it.ok }

    private suspend fun attempt(api: String, block: suspend () -> String?): ApiCheck = try {
        val warning = block()
        if (warning == null) ApiCheck(api, true) else ApiCheck(api, false, KeyProblem.OTHER, warning)
    } catch (e: RoutingException) {
        ApiCheck(api, false, e.problem ?: KeyProblem.OTHER, e.message)
    } catch (e: IOException) {
        ApiCheck(api, false, KeyProblem.NETWORK)
    } catch (e: Exception) {
        ApiCheck(api, false, KeyProblem.OTHER, e.message ?: "Unexpected answer from the service")
    }
}
