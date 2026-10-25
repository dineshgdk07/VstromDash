package dev.gdk.bumblebee.ble.android

import android.content.Context
import dev.gdk.bumblebee.snapshot.SnapshotPrefs
import dev.gdk.bumblebee.snapshot.SnapshotWidget

/** Persisted pairing: bike MAC and rider name for a536 identity. */
class BikePrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var bikeMac: String?
        get() = prefs.getString(KEY_MAC, null)
        set(value) {
            prefs.edit().putString(KEY_MAC, value).apply()
        }

    var riderName: String
        get() = prefs.getString(KEY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
        set(value) {
            prefs.edit().putString(KEY_NAME, value).apply()
        }

    /** 0 means the rider has not set an interval yet. */
    var serviceIntervalKm: Int
        get() = prefs.getInt(KEY_SERVICE_INTERVAL, 0)
        set(value) {
            prefs.edit().putInt(KEY_SERVICE_INTERVAL, value.coerceAtLeast(0)).apply()
        }

    var lastServiceOdoKm: Int?
        get() = prefs.getInt(KEY_LAST_SERVICE_ODO, -1).takeIf { it >= 0 }
        set(value) {
            prefs.edit().putInt(KEY_LAST_SERVICE_ODO, value ?: -1).apply()
        }

    var rcExpiry: String
        get() = prefs.getString(KEY_RC, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_RC, value).apply()
        }

    var insuranceExpiry: String
        get() = prefs.getString(KEY_INSURANCE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_INSURANCE, value).apply()
        }

    var pucExpiry: String
        get() = prefs.getString(KEY_PUC, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_PUC, value).apply()
        }

    fun toSnapshotPrefs(widget: SnapshotWidget): SnapshotPrefs = SnapshotPrefs(
        bikeMac = bikeMac,
        riderName = riderName,
        serviceIntervalKm = serviceIntervalKm,
        lastServiceOdoKm = lastServiceOdoKm,
        rcExpiry = rcExpiry,
        insuranceExpiry = insuranceExpiry,
        pucExpiry = pucExpiry,
        mapsNavToCluster = mapsNavToCluster,
        tankCapacityL = tankCapacityL,
        remindersEnabled = remindersEnabled,
        remindDays = remindDays,
        remindKm = remindKm,
        pauseWindowMin = pauseWindowMin,
        autoConnect = autoConnect,
        skipAutoConnectDate = skipAutoConnectDate,
        crashSensitivity = crashSensitivity,
        emergencyContacts = emergencyContacts,
        bloodGroup = bloodGroup,
        medicalNote = medicalNote,
        lastReminderStamp = lastReminderStamp,
        widget = widget,
    )

    fun writeSnapshotPrefs(prefs: SnapshotPrefs) {
        bikeMac = prefs.bikeMac
        riderName = prefs.riderName
        serviceIntervalKm = prefs.serviceIntervalKm
        lastServiceOdoKm = prefs.lastServiceOdoKm
        rcExpiry = prefs.rcExpiry
        insuranceExpiry = prefs.insuranceExpiry
        pucExpiry = prefs.pucExpiry
        mapsNavToCluster = prefs.mapsNavToCluster
        tankCapacityL = prefs.tankCapacityL
        remindersEnabled = prefs.remindersEnabled
        remindDays = prefs.remindDays
        remindKm = prefs.remindKm
        pauseWindowMin = prefs.pauseWindowMin
        autoConnect = prefs.autoConnect
        skipAutoConnectDate = prefs.skipAutoConnectDate
        crashSensitivity = prefs.crashSensitivity
        emergencyContacts = prefs.emergencyContacts
        bloodGroup = prefs.bloodGroup
        medicalNote = prefs.medicalNote
        lastReminderStamp = prefs.lastReminderStamp
    }

    fun remainingServiceKm(odoKm: Int?): Int? {
        val interval = serviceIntervalKm
        val last = lastServiceOdoKm
        if (interval <= 0 || last == null || odoKm == null) return null
        return last + interval - odoKm
    }

    /** Google Maps notifications → cluster turn arrows. Maps API comes later. */
    var mapsNavToCluster: Boolean
        get() = prefs.getBoolean(KEY_MAPS_NAV, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MAPS_NAV, value).apply()
        }

    /** V-Strom 250 SX tank is 12 L unless the rider overrides. */
    var tankCapacityL: Float
        get() = prefs.getFloat(KEY_TANK_L, 12f)
        set(value) {
            prefs.edit().putFloat(KEY_TANK_L, value.coerceAtLeast(1f)).apply()
        }

    var remindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMIND_ON, true)
        set(value) {
            prefs.edit().putBoolean(KEY_REMIND_ON, value).apply()
        }

    var remindDays: Int
        get() = prefs.getInt(KEY_REMIND_DAYS, 14).coerceAtLeast(1)
        set(value) {
            prefs.edit().putInt(KEY_REMIND_DAYS, value.coerceIn(1, 180)).apply()
        }

    var remindKm: Int
        get() = prefs.getInt(KEY_REMIND_KM, 200).coerceAtLeast(10)
        set(value) {
            prefs.edit().putInt(KEY_REMIND_KM, value.coerceIn(10, 5_000)).apply()
        }

    /** 0 turns auto-continue off. Default is 45 minutes. */
    var pauseWindowMin: Int
        get() = prefs.getInt(KEY_PAUSE_MIN, 45)
        set(value) {
            prefs.edit().putInt(KEY_PAUSE_MIN, value).apply()
        }

    var autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_CONNECT, value).apply()
        }

    var skipAutoConnectDate: String
        get() = prefs.getString(KEY_SKIP_AUTO, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SKIP_AUTO, value).apply()
        }

    /** Crash alerts on or off. On by default. */
    var crashDetection: Boolean
        get() = prefs.getBoolean(KEY_CRASH_ON, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CRASH_ON, value).apply()
        }

    var crashSensitivity: String
        get() = prefs.getString(KEY_CRASH_SENS, "NORMAL") ?: "NORMAL"
        set(value) {
            prefs.edit().putString(KEY_CRASH_SENS, value).apply()
        }

    var emergencyContacts: String
        get() = prefs.getString(KEY_SOS, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SOS, value).apply()
        }

    var bloodGroup: String
        get() = prefs.getString(KEY_BLOOD, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_BLOOD, value).apply()
        }

    var medicalNote: String
        get() = prefs.getString(KEY_MEDICAL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_MEDICAL, value).apply()
        }

    var lastReminderStamp: String
        get() = prefs.getString(KEY_REMIND_STAMP, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_REMIND_STAMP, value).apply()
        }

    companion object {
        private const val PREFS = "bumblebee_ble"
        private const val KEY_MAC = "bike_mac"
        private const val KEY_NAME = "rider_name"
        private const val KEY_SERVICE_INTERVAL = "service_interval_km"
        private const val KEY_LAST_SERVICE_ODO = "last_service_odo_km"
        private const val KEY_RC = "doc_rc_expiry"
        private const val KEY_INSURANCE = "doc_insurance_expiry"
        private const val KEY_PUC = "doc_puc_expiry"
        private const val KEY_MAPS_NAV = "maps_nav_to_cluster"
        private const val KEY_TANK_L = "tank_capacity_l"
        private const val KEY_REMIND_ON = "garage_reminders"
        private const val KEY_REMIND_DAYS = "garage_remind_days"
        private const val KEY_REMIND_KM = "garage_remind_km"
        private const val KEY_REMIND_STAMP = "garage_remind_stamp"
        private const val KEY_PAUSE_MIN = "pause_window_min"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_SKIP_AUTO = "skip_auto_connect_date"
        private const val KEY_CRASH_SENS = "crash_sensitivity"
        private const val KEY_CRASH_ON = "crash_detection"
        private const val KEY_SOS = "emergency_contacts"
        private const val KEY_BLOOD = "blood_group"
        private const val KEY_MEDICAL = "medical_note"
        const val DEFAULT_NAME = "RIDER"
    }
}
