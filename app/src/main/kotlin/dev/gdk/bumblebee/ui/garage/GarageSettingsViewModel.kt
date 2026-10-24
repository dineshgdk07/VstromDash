package dev.gdk.bumblebee.ui.garage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.BikeBridgeService
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ble.android.BleScanner
import dev.gdk.bumblebee.ble.android.DiscoveredBike
import dev.gdk.bumblebee.garage.GarageReminders
import dev.gdk.bumblebee.ui.share.SharePrefs
import dev.gdk.bumblebee.nav.MapsNavSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GarageSettingsUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val bikeMac: String? = null,
    val riderName: String = "RIDER",
    val scanning: Boolean = false,
    val devices: List<DiscoveredBike> = emptyList(),
    val status: String = "",
    val tankKmPerL: Double? = null,
    val rupeesPerKm: Double? = null,
    val rangeKm: Double? = null,
    val mapsNavToCluster: Boolean = true,
    val remindersEnabled: Boolean = true,
    val remindDays: String = "14",
    val remindKm: String = "200",
    val pauseWindowMin: Int = 45,
    val autoConnect: Boolean = true,
    val sharePrompt: Boolean = true,
    val sosContacts: String = "",
    val crashSensitivity: String = "NORMAL",
    val bloodGroup: String = "",
    val medicalNote: String = "",
)

class GarageSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DashApp
    private val scanner = BleScanner(application)
    private val _ui = MutableStateFlow(fromPrefs())
    val ui: StateFlow<GarageSettingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            app.connection.collect { connection ->
                _ui.update { it.copy(connection = connection, bikeMac = app.prefs.bikeMac) }
            }
        }
        viewModelScope.launch {
            scanner.results.collect { map ->
                val sorted = map.values.sortedWith(
                    compareByDescending<DiscoveredBike> { it.looksLikeSuzuki }
                        .thenByDescending { it.rssi },
                )
                _ui.update { it.copy(devices = sorted) }
            }
        }
        refreshEconomics()
    }

    fun refreshEconomics() {
        val fuel = app.latestTelemetry.value?.fuelBars
        val odo = app.latestTelemetry.value?.odometerKm
        viewModelScope.launch {
            val snap = reloadGarage(app, fuel, odo)
            _ui.update {
                it.copy(
                    tankKmPerL = snap.tankKmPerL,
                    rupeesPerKm = snap.rupeesPerKm,
                    rangeKm = snap.rangeKm,
                )
            }
        }
    }

    fun setRiderName(value: String) { _ui.update { it.copy(riderName = value) } }

    fun saveRiderName() {
        val name = _ui.value.riderName.trim().ifBlank { "RIDER" }
        app.prefs.riderName = name
        _ui.update { it.copy(riderName = name, status = "Rider name saved.") }
    }

    fun setRemindersEnabled(on: Boolean) {
        app.prefs.remindersEnabled = on
        _ui.update { it.copy(remindersEnabled = on, status = if (on) "Reminders on." else "Reminders off.") }
    }

    fun setRemindDays(value: String) { _ui.update { it.copy(remindDays = value) } }
    fun setRemindKm(value: String) { _ui.update { it.copy(remindKm = value) } }

    fun saveReminders() {
        app.prefs.remindDays = _ui.value.remindDays.toIntOrNull() ?: 14
        app.prefs.remindKm = _ui.value.remindKm.toIntOrNull() ?: 200
        app.prefs.lastReminderStamp = ""
        _ui.update {
            it.copy(
                remindDays = app.prefs.remindDays.toString(),
                remindKm = app.prefs.remindKm.toString(),
                status = "Remind ${app.prefs.remindDays} days or ${app.prefs.remindKm} km ahead.",
            )
        }
        viewModelScope.launch { GarageReminders.refresh(app) }
    }

    fun startScan() {
        scanner.start()
        _ui.update { it.copy(scanning = true, status = "Scanning…") }
    }

    fun stopScan() {
        scanner.stop()
        _ui.update { it.copy(scanning = false) }
    }

    fun pair(mac: String) {
        stopScan()
        app.prefs.bikeMac = mac
        _ui.update { it.copy(bikeMac = mac, status = "Connecting $mac") }
        BikeBridgeService.connect(getApplication(), mac)
    }

    fun connectSaved() {
        val mac = app.prefs.bikeMac ?: return
        pair(mac)
    }

    fun disconnectBike() {
        BikeBridgeService.stop(getApplication())
        _ui.update { it.copy(status = "Disconnecting…") }
    }

    fun forgetBike() {
        BikeBridgeService.stop(getApplication())
        app.prefs.bikeMac = null
        _ui.update { it.copy(bikeMac = null, status = "Saved bike cleared.") }
    }

    fun setPauseWindow(minutes: Int) {
        app.prefs.pauseWindowMin = minutes
        _ui.update { it.copy(pauseWindowMin = minutes) }
    }

    fun setAutoConnect(on: Boolean) {
        app.prefs.autoConnect = on
        _ui.update { it.copy(autoConnect = on, status = if (on) "Auto-connect is on." else "Auto-connect is off.") }
    }

    fun setSharePrompt(on: Boolean) {
        SharePrefs(getApplication()).promptEnabled = on
        _ui.update { it.copy(sharePrompt = on, status = if (on) "Share prompt is on." else "Share prompt is off.") }
    }

    fun skipAutoConnectToday() {
        app.prefs.skipAutoConnectDate = LocalDate.now().toString()
        _ui.update { it.copy(status = "Auto-connect is off for the rest of today.") }
    }

    fun setSosContacts(value: String) { _ui.update { it.copy(sosContacts = value) } }
    fun setCrashSensitivity(value: String) {
        app.prefs.crashSensitivity = value
        _ui.update { it.copy(crashSensitivity = value) }
    }
    fun setBloodGroup(value: String) { _ui.update { it.copy(bloodGroup = value) } }
    fun setMedicalNote(value: String) { _ui.update { it.copy(medicalNote = value) } }

    fun onTestSosPermission(granted: Boolean) {
        if (!granted) {
            _ui.update { it.copy(status = "SMS permission is required to send a test.") }
            return
        }
        val numbers = app.prefs.emergencyContacts.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        if (numbers.isEmpty()) {
            _ui.update { it.copy(status = "Save at least one emergency number first.") }
            return
        }
        val sms = android.telephony.SmsManager.getDefault()
        val body = "TEST from Bumblebee. This is not an emergency."
        for (number in numbers) {
            runCatching { sms.sendTextMessage(number, null, body, null, null) }
        }
        _ui.update { it.copy(status = "Test SMS sent to ${numbers.size} contact${if (numbers.size == 1) "" else "s"}.") }
    }

    fun saveSafety() {
        val contacts = _ui.value.sosContacts.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        app.prefs.emergencyContacts = contacts.joinToString(",")
        app.prefs.bloodGroup = _ui.value.bloodGroup.trim()
        app.prefs.medicalNote = _ui.value.medicalNote.trim()
        _ui.update {
            it.copy(
                sosContacts = app.prefs.emergencyContacts,
                status = "Safety details saved. This is a best-effort aid, not a certified emergency device.",
            )
        }
    }

    fun setMapsNavToCluster(enabled: Boolean) {
        app.prefs.mapsNavToCluster = enabled
        if (!enabled) MapsNavSource.clear()
        _ui.update {
            it.copy(
                mapsNavToCluster = enabled,
                status = if (enabled) {
                    "Cluster will follow Google Maps notifications. Maps APIs can wait."
                } else {
                    "Cluster nav from Maps notifications is off."
                },
            )
        }
    }

    override fun onCleared() {
        scanner.stop()
        super.onCleared()
    }

    private fun fromPrefs(): GarageSettingsUiState {
        return GarageSettingsUiState(
            connection = app.connection.value,
            bikeMac = app.prefs.bikeMac,
            riderName = app.prefs.riderName,
            mapsNavToCluster = app.prefs.mapsNavToCluster,
            remindersEnabled = app.prefs.remindersEnabled,
            remindDays = app.prefs.remindDays.toString(),
            remindKm = app.prefs.remindKm.toString(),
            pauseWindowMin = app.prefs.pauseWindowMin,
            autoConnect = app.prefs.autoConnect,
            sharePrompt = SharePrefs(getApplication()).promptEnabled,
            sosContacts = app.prefs.emergencyContacts,
            crashSensitivity = app.prefs.crashSensitivity,
            bloodGroup = app.prefs.bloodGroup,
            medicalNote = app.prefs.medicalNote,
        )
    }
}
