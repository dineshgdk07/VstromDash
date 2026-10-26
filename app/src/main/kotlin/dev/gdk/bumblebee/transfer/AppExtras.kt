package dev.gdk.bumblebee.transfer

import android.content.Context
import dev.gdk.bumblebee.snapshot.SnapshotFile
import dev.gdk.bumblebee.snapshot.SnapshotSettings
import java.io.File

/**
 * What the app keeps outside the database and the garage folder: its settings stores (bike
 * profile, saved places, share and video choices…) and the ride video photos and music. A pack
 * carries them so a new phone, or the store build after a test build, picks up where this left off.
 */
interface AppExtras {
    fun settings(): SnapshotSettings
    fun files(): List<SnapshotFile>

    /** Puts them back; [rideIds] maps the pack's ride ids to this phone's. Returns what it did. */
    fun restore(settings: SnapshotSettings, files: List<SnapshotFile>, rideIds: Map<Long, Long>, overwrite: Boolean, onStep: (String) -> Unit): List<String>

    companion object {
        val NONE = object : AppExtras {
            override fun settings() = SnapshotSettings()
            override fun files() = emptyList<SnapshotFile>()
            override fun restore(settings: SnapshotSettings, files: List<SnapshotFile>, rideIds: Map<Long, Long>, overwrite: Boolean, onStep: (String) -> Unit) = emptyList<String>()
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

    override fun files(): List<SnapshotFile> = FOLDERS.flatMap { folder ->
        val root = File(app.filesDir, folder)
        root.walkTopDown().filter { it.isFile }.map { f ->
            SnapshotFile.lazy("$EXTRA/$folder/${f.relativeTo(root).invariantSeparatorsPath}") { f.readBytes() }
        }.toList()
    }

    override fun restore(settings: SnapshotSettings, files: List<SnapshotFile>, rideIds: Map<Long, Long>, overwrite: Boolean, onStep: (String) -> Unit): List<String> {
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
        val extras = files.filter { it.relativePath.startsWith("$EXTRA/") }
        if (extras.isNotEmpty()) {
            onStep("Restoring ride video photos and music")
            var photos = 0
            var music = 0
            var skipped = 0
            for (file in extras) {
                val parts = file.relativePath.removePrefix("$EXTRA/").split('/')
                if (parts.size < 2 || parts.any { it.isEmpty() || it == "." || it == ".." }) continue
                val target = when (parts[0]) {
                    PHOTOS -> {
                        // Photos live under their ride's id, which may be different here.
                        val local = parts.getOrNull(1)?.toLongOrNull()?.let { rideIds[it] }
                        if (local == null) {
                            skipped++
                            continue
                        }
                        File(app.filesDir, (listOf(PHOTOS, local.toString()) + parts.drop(2)).joinToString("/"))
                    }
                    MUSIC -> File(app.filesDir, parts.joinToString("/"))
                    else -> continue
                }
                if (target.exists() && !overwrite) continue
                target.parentFile?.mkdirs()
                target.writeBytes(file.bytes)
                if (parts[0] == PHOTOS) photos++ else music++
            }
            if (photos > 0) done += "Ride video photos: $photos files"
            if (music > 0) done += "Music: the soundtrack and its beat"
            if (skipped > 0) done += "Skipped $skipped photo files whose ride wasn't brought in"
        }
        return done
    }

    companion object {
        const val EXTRA = "extra"
        private const val PHOTOS = "video-photos"
        private const val MUSIC = "video-music"
        private val FOLDERS = listOf(PHOTOS, MUSIC)

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
