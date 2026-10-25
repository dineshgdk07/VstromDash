package dev.gdk.bumblebee.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyHealthTest {
    private fun google(code: Int, status: String, reason: String, message: String) =
        """{"error":{"code":$code,"message":"$message","status":"$status","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"$reason","domain":"googleapis.com"}]}}"""

    @Test fun `google reasons map to advice`() {
        assertEquals(KeyProblem.INVALID, KeyHealth.classify(400, google(400, "INVALID_ARGUMENT", "API_KEY_INVALID", "API key not valid. Please pass a valid API key.")))
        assertEquals(
            KeyProblem.API_DISABLED,
            KeyHealth.classify(403, google(403, "PERMISSION_DENIED", "SERVICE_DISABLED", "Routes API has not been used in project 123 before or it is disabled.")),
        )
        assertEquals(KeyProblem.BILLING, KeyHealth.classify(403, google(403, "PERMISSION_DENIED", "BILLING_DISABLED", "This API method requires billing to be enabled.")))
        assertEquals(
            KeyProblem.APP_BLOCKED,
            KeyHealth.classify(403, google(403, "PERMISSION_DENIED", "API_KEY_ANDROID_APP_BLOCKED", "Requests from this Android client application <empty> are blocked.")),
        )
        assertEquals(
            KeyProblem.API_BLOCKED,
            KeyHealth.classify(403, google(403, "PERMISSION_DENIED", "API_KEY_SERVICE_BLOCKED", "Requests to this API places.googleapis.com method are blocked.")),
        )
        assertEquals(KeyProblem.QUOTA, KeyHealth.classify(429, google(429, "RESOURCE_EXHAUSTED", "RATE_LIMIT_EXCEEDED", "Quota exceeded")))
    }

    @Test fun `openrouteservice errors`() {
        assertEquals(KeyProblem.INVALID, KeyHealth.classify(403, """{"error":"Access to this API has been disallowed"}"""))
        assertEquals(KeyProblem.INVALID, KeyHealth.classify(401, """{"error":"Unauthorized"}"""))
        assertEquals(KeyProblem.QUOTA, KeyHealth.classify(429, """{"error":"Rate limit exceeded"}"""))
    }

    @Test fun `messages unescape`() {
        assertEquals("Use \"key\" here", KeyHealth.message("""{"error":{"message":"Use \"key\" here"}}"""))
        assertEquals("Couldn't reach the service. Check the internet connection.", ApiCheck("Routes", false, KeyProblem.NETWORK).message)
        assertEquals("Working", ApiCheck("Routes", true).message)
    }
}
