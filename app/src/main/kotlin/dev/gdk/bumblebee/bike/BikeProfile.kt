package dev.gdk.bumblebee.bike

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.gdk.bumblebee.R
import dev.gdk.bumblebee.ble.android.BikePrefs
import dev.gdk.bumblebee.ride.SosTemplate
import dev.gdk.bumblebee.ui.theme.Accent

/** The V-Strom 250 SX paint options, with the app accent each one suggests. */
enum class BikeVariant(val label: String, @DrawableRes val image: Int, val paint: Color, val accent: Color) {
    YELLOW("Champion Yellow", R.drawable.bike_yellow, Color(0xFFF2CE1B), Color(0xFFFAC816)),
    BLUE("Metallic Triton Blue", R.drawable.bike_blue, Color(0xFF1F45B5), Color(0xFF6FA8FF)),
    BLACK("Glass Sparkle Black", R.drawable.bike_black, Color(0xFF1B1B1E), Color(0xFFFF6B6B)),
    ORANGE("Pearl Blaze Orange", R.drawable.bike_orange, Color(0xFFE8501E), Color(0xFFFF8A4C)),
    RED("Metallic Sonoma Red", R.drawable.bike_red, Color(0xFF9E1B2A), Color(0xFFFF6478)),
    WHITE("Pearl Glacier White", R.drawable.bike_white, Color(0xFFE9ECEF), Color(0xFF7FD3FF)),
}

/**
 * Accents a rider can pick instead of the bike's. All are light enough to carry the app's dark
 * text, which a free colour picker couldn't promise.
 */
enum class AccentChoice(val label: String, val color: Color) {
    LIME("Bumblebee lime", Accent.DEFAULT),
    YELLOW("Champion yellow", Color(0xFFFAC816)),
    ORANGE("Blaze orange", Color(0xFFFF8A4C)),
    RED("Sonoma red", Color(0xFFFF6478)),
    BLUE("Triton blue", Color(0xFF6FA8FF)),
    ICE("Glacier ice", Color(0xFF7FD3FF)),
    MINT("Mint", Color(0xFF82D6C6)),
}

/** The bike being shown right now; screens read this so a profile edit repaints them at once. */
object CurrentBike {
    var variant by mutableStateOf(BikeVariant.YELLOW)
    var regNo by mutableStateOf("")
}

data class EmergencyContact(val name: String, val number: String)

/** Who the bike is: paint, plate, the app's colour, and the crash text. Lives on this phone. */
class BikeProfile(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val bike = BikePrefs(context)

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var variant: BikeVariant
        get() = prefs.getString(KEY_VARIANT, null)?.let { v -> BikeVariant.entries.firstOrNull { it.name == v } } ?: BikeVariant.YELLOW
        set(value) {
            prefs.edit().putString(KEY_VARIANT, value.name).apply()
            applyAccent()
        }

    /** Null follows the bike's colour. */
    var accentChoice: AccentChoice?
        get() = prefs.getString(KEY_ACCENT, null)?.let { v -> AccentChoice.entries.firstOrNull { it.name == v } }
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_ACCENT) else putString(KEY_ACCENT, value.name) }.apply()
            applyAccent()
        }

    val accent: Color get() = accentChoice?.color ?: variant.accent

    /** Pushes the colour, paint and plate to the screens. Called at launch and after every change. */
    fun applyAccent() {
        Accent.color = accent
        CurrentBike.variant = variant
        CurrentBike.regNo = regNo
    }

    /** Formatted, e.g. "KA 01 AB 1234"; empty when unknown. */
    var regNo: String
        get() = prefs.getString(KEY_REG, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_REG, value.trim()).apply()
            CurrentBike.regNo = value.trim()
        }

    var sosTemplate: String
        get() = prefs.getString(KEY_SOS, null) ?: SosTemplate.DEFAULT
        set(value) = prefs.edit().putString(KEY_SOS, value).apply()

    /** Off by default: the plate on a shared image is for a buyer, not for social posts. */
    var regOnHistoryCard: Boolean
        get() = prefs.getBoolean(KEY_REG_CARD, false)
        set(value) = prefs.edit().putBoolean(KEY_REG_CARD, value).apply()

    /**
     * Up to three contacts. Numbers stay in [BikePrefs.emergencyContacts] (backups and older code
     * read them there); names live here, keyed by number.
     */
    var contacts: List<EmergencyContact>
        get() {
            val names = prefs.getStringSet(KEY_NAMES, emptySet()).orEmpty()
                .mapNotNull { row -> row.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                .toMap()
            return bike.emergencyContacts.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
                .map { EmergencyContact(names[it].orEmpty(), it) }
        }
        set(value) {
            val kept = value.filter { it.number.isNotBlank() }.distinctBy { it.number }.take(3)
            bike.emergencyContacts = kept.joinToString(",") { it.number.trim() }
            prefs.edit().putStringSet(KEY_NAMES, kept.map { "${it.number.trim()}\t${it.name.trim()}" }.toSet()).apply()
        }

    private companion object {
        const val NAME = "bumblebee_bike"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_VARIANT = "variant"
        const val KEY_ACCENT = "accent"
        const val KEY_REG = "reg_no"
        const val KEY_SOS = "sos_template"
        const val KEY_REG_CARD = "reg_on_history_card"
        const val KEY_NAMES = "contact_names"
    }
}
