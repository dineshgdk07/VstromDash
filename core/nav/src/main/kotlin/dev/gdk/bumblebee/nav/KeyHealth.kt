package dev.gdk.bumblebee.nav

/** What went wrong with a key, in terms a rider can act on. */
enum class KeyProblem(val advice: String) {
    INVALID("The key isn't valid. Copy it again from the console, without spaces."),
    API_DISABLED("This API isn't enabled for the key's project. Enable it in the Cloud console, then wait a minute."),
    BILLING("Billing isn't set up on the key's project. Add a billing account in the Cloud console."),
    APP_BLOCKED("The key's Android restriction doesn't match this app. Add package dev.gdk.bumblebee (and .debug) with this phone's signing SHA-1, or remove the restriction."),
    API_BLOCKED("The key is restricted to other APIs. Add this API under the key's API restrictions."),
    QUOTA("The key has hit its request limit. Try again later or raise the quota."),
    NETWORK("Couldn't reach the service. Check the internet connection."),
    OTHER("The service refused the request."),
}

/** One API's result in a key check. */
data class ApiCheck(
    val api: String,
    val ok: Boolean,
    val problem: KeyProblem? = null,
    val detail: String? = null,
    /** Nice to have; navigation works without it. */
    val optional: Boolean = false,
) {
    val message: String get() = when {
        ok -> detail ?: "Working"
        optional && !detail.isNullOrBlank() -> detail
        problem == KeyProblem.OTHER && !detail.isNullOrBlank() -> detail
        else -> problem?.advice ?: "Failed"
    }
}

/**
 * Turns an HTTP status and error body from Google or OpenRouteService into a [KeyProblem].
 * Google puts a machine reason in error.details[].reason and error.status; the human message
 * is kept as a fallback.
 */
object KeyHealth {
    private val REASON = Regex(""""reason"\s*:\s*"([A-Z_]+)"""")
    private val STATUS = Regex(""""status"\s*:\s*"([A-Z_]+)"""")
    private val MESSAGE = Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)"""")

    fun classify(code: Int, body: String): KeyProblem {
        val reasons = REASON.findAll(body).map { it.groupValues[1] }.toSet()
        val status = STATUS.find(body)?.groupValues?.get(1)
        val text = body.lowercase()
        return when {
            "API_KEY_INVALID" in reasons || "api key not valid" in text || "invalid api key" in text || "access to resource" in text && code == 403 && "key" in text -> KeyProblem.INVALID
            "BILLING_DISABLED" in reasons || "billing" in text -> KeyProblem.BILLING
            "SERVICE_DISABLED" in reasons || "has not been used in project" in text || "is disabled" in text -> KeyProblem.API_DISABLED
            "API_KEY_ANDROID_APP_BLOCKED" in reasons || "android client application" in text -> KeyProblem.APP_BLOCKED
            "API_KEY_SERVICE_BLOCKED" in reasons || "are blocked" in text -> KeyProblem.API_BLOCKED
            code == 429 || status == "RESOURCE_EXHAUSTED" || "RATE_LIMIT_EXCEEDED" in reasons || "quota" in text -> KeyProblem.QUOTA
            code == 401 || status == "UNAUTHENTICATED" -> KeyProblem.INVALID
            code == 403 -> KeyProblem.INVALID
            else -> KeyProblem.OTHER
        }
    }

    /** The service's own message, unescaped and shortened, for the cases [classify] can't name. */
    fun message(body: String): String? =
        MESSAGE.find(body)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\n", " ")?.take(180)?.takeIf { it.isNotBlank() }
}
