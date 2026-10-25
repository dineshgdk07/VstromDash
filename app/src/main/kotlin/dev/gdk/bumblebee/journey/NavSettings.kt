package dev.gdk.bumblebee.journey

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where turn-by-turn comes from. */
enum class NavMode(val label: String) {
    /** Read Google Maps' own notification; Maps does the navigating. */
    NOTIFICATIONS("Maps notifications"),
    /** Search and route with the rider's Google key; this app navigates. */
    GOOGLE("Google"),
    /** Search and route with a free OpenRouteService key, on the app's own map. */
    OPEN("OpenRouteService"),
}

/**
 * Navigation settings. API keys are encrypted with a key that never leaves the phone's
 * Keystore, so a backup or a rooted file read doesn't hand them out.
 */
class NavSettings(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var mode: NavMode
        get() = prefs.getString(KEY_MODE, null)?.let { v -> NavMode.entries.firstOrNull { it.name == v } } ?: NavMode.NOTIFICATIONS
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var googleKey: String
        get() = read(KEY_GOOGLE)
        set(value) = write(KEY_GOOGLE, value)

    var orsKey: String
        get() = read(KEY_ORS)
        set(value) = write(KEY_ORS, value)

    /** The key the current mode needs, or blank if it isn't set. */
    fun activeKey(): String = when (mode) {
        NavMode.GOOGLE -> googleKey
        NavMode.OPEN -> orsKey
        NavMode.NOTIFICATIONS -> ""
    }

    private fun read(key: String): String {
        val stored = prefs.getString(key, null) ?: return ""
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes, 0, IV))
            String(cipher.doFinal(bytes, IV, bytes.size - IV), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun write(key: String, value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val sealed = cipher.iv + cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val NAME = "bumblebee_nav"
        const val KEY_MODE = "mode"
        const val KEY_GOOGLE = "google_key"
        const val KEY_ORS = "ors_key"
        const val ALIAS = "bumblebee_nav_keys"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV = 12
    }
}
