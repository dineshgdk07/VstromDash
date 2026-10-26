package dev.gdk.bumblebee.transfer

import android.content.Context
import dev.gdk.bumblebee.snapshot.SnapshotSettings

/**
 * What the app keeps outside the database and the garage folder that a pack carries: its settings
 * stores (bike profile, saved places, share and video choices…), so a new phone, or the store
 * build after a test build, picks up where this left off. Media (photos picked for ride videos,
 * the music, rendered videos and cards) isn't packed: it's heavy and easy to add again.
 */
interface AppExtras {
    fun settings(): SnapshotSettings

    /** Puts them back; returns what it did. */
    fun restore(settings: SnapshotSettings, onStep: (String) -> Unit): List<String>

    companion object {
        val NONE = object : AppExtras {
            override fun settings() = SnapshotSettings()
            override fun restore(settings: SnapshotSettings, onStep: (String) -> Unit) = emptyList<String>()
        }
    }
}

class AndroidAppExtras(context: Context) : AppExtras {
    private val app = context.applicationContext

    override fun settings(): SnapshotSettings {
        val stores = mutableMapOf<String, Map<String, Any>>()
        for ((store, only) in STORES) {
            val all = app.getSharedPreferences(store, Context.MODE_PRIVATE).all
            val values = all.filterKeys { only == null || it in only }
                .mapNotNull { (k, v) -> v?.let { k to it } }
                .toMap()
            if (values.isNotEmpty()) stores[store] = values
        }
        return SnapshotSettings(stores)
    }

    override fun restore(settings: SnapshotSettings, onStep: (String) -> Unit): List<String> {
        val done = mutableListOf<String>()
        if (settings.stores.isNotEmpty()) {
            onStep("Restoring settings")
            var keys = 0
            for ((store, values) in settings.stores) {
                // Only stores this app knows, and only the keys it allows from each.
                if (store !in STORES) continue
                val only = STORES[store]
                val edit = app.getSharedPreferences(store, Context.MODE_PRIVATE).edit()
                for ((key, value) in values) {
                    if (only != null && key !in only) continue
                    when (value) {
                        is String -> edit.putString(key, value)
                        is Boolean -> edit.putBoolean(key, value)
                        is Int -> edit.putInt(key, value)
                        is Long -> edit.putLong(key, value)
                        is Float -> edit.putFloat(key, value)
                        is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                        else -> continue
                    }
                    keys++
                }
                edit.commit()
            }
            done += "Settings: $keys values in ${settings.stores.size} groups"
        }
        return done
    }

    companion object {

        /**
         * Settings stores to carry, and which keys (null for all). Left out: the bike link and
         * widget (packed on their own), the encrypted navigation keys (sealed to this phone's
         * Keystore, so another phone can't read them: re-enter them there), and per-install flags.
         */
        private val STORES: Map<String, Set<String>?> = mapOf(
            "bumblebee_bike" to null,
            "advanced" to null,
            "bumblebee_journey" to null,
            "bumblebee_nav" to setOf("mode"),
            "bumblebee_explorer" to null,
            "bumblebee_fuel" to null,
            "bumblebee_garage" to null,
            "bumblebee_share" to null,
            "video_studio" to null,
        )
    }
}
