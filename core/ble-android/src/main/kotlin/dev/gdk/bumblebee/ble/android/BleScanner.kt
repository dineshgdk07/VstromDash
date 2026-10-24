package dev.gdk.bumblebee.ble.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Unfiltered LE scan for pairing. Suzuki clusters often omit 0xFFF0 from the
 * advertisement, so we do not use a service-UUID filter.
 */
@SuppressLint("MissingPermission")
class BleScanner(context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _results = MutableStateFlow<Map<String, DiscoveredBike>>(emptyMap())
    val results: StateFlow<Map<String, DiscoveredBike>> = _results.asStateFlow()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val mac = result.device.address ?: return
            val rawName = result.device.name ?: result.scanRecord?.deviceName
            val bonded = result.device.bondState == BluetoothDevice.BOND_BONDED
            val prev = _results.value[mac]
            val smoothedRssi = if (prev != null && prev.rssi != Int.MIN_VALUE) {
                (0.3f * result.rssi + 0.7f * prev.rssi).toInt()
            } else {
                result.rssi
            }
            _results.value = _results.value + (mac to DiscoveredBike(
                mac = mac,
                name = rawName,
                rssi = smoothedRssi,
                bonded = bonded,
            ))
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "onScanFailed errorCode=$errorCode")
        }
    }

    fun start() {
        _results.value = emptyMap()
        seedBonded()
        val scanner = adapter?.bluetoothLeScanner ?: run {
            Log.w(TAG, "BluetoothLeScanner null — adapter=$adapter")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, callback)
        } catch (t: Throwable) {
            Log.e(TAG, "startScan threw", t)
        }
    }

    fun stop() {
        try {
            adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (t: Throwable) {
            Log.w(TAG, "stopScan threw", t)
        }
    }

    private fun seedBonded() {
        val bonded = adapter?.bondedDevices ?: return
        val seeded = mutableMapOf<String, DiscoveredBike>()
        for (d in bonded) {
            if (d.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) continue
            val mac = d.address ?: continue
            seeded[mac] = DiscoveredBike(
                mac = mac,
                name = d.name,
                rssi = Int.MIN_VALUE,
                bonded = true,
            )
        }
        if (seeded.isNotEmpty()) _results.value = seeded
    }

    companion object {
        private const val TAG = "BleScanner"
    }
}
