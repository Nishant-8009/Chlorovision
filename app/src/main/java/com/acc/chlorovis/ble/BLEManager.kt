package com.acc.chlorovis.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.acc.chlorovis.BLEApplication
import com.acc.chlorovis.activity.scan.ScanInterface
import com.acc.chlorovis.activity.scan.ScanAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import timber.log.Timber

const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val GATT_MAX_MTU_SIZE = 517
private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"


interface ScanResultListener {
    fun onScanResultUpdated(result: ScanResult)
}
@Suppress("unused")
@SuppressLint("NotifyDataSetChanged", "MissingPermission")
object BLEManager {

    var scanInterface: ScanInterface? = null
    var scanListener: ScanResultListener? = null

    var bGatt: BluetoothGatt? = null
    var scanAdapter: ScanAdapter? = null

    // BLE Queue System (Coroutines)
    private val channel = Channel<BLEResult>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val scanResultListeners = mutableListOf<ScanResultListener>()
    var isScanning = false
    private var isConnected = false
    var deviceNameFilter = ""
    var deviceRSSIFilter = ""

    // List of BLE Scan Results
    val scanResults = mutableListOf<ScanResult>()

    val bAdapter: BluetoothAdapter by lazy {
        val bluetoothManager =
            BLEApplication.app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    fun registerScanResultListener(listener: ScanResultListener) {
        if (!scanResultListeners.contains(listener)) {
            scanResultListeners.add(listener)
        }
    }

    // Unregister a listener
    fun unregisterScanResultListener(listener: ScanResultListener) {
        scanResultListeners.remove(listener)
    }

    private fun notifyScanResultUpdated(result: ScanResult) {
        for (listener in scanResultListeners) {
            listener.onScanResultUpdated(result)
        }
    }
    private val bleScanner: BluetoothLeScanner by lazy {
        bAdapter.bluetoothLeScanner
    }

    /** Bluetooth 5 */

    @RequiresApi(Build.VERSION_CODES.O)
    fun checkBluetooth5Support() {
        Timber.i("LE 2M PHY Supported: ${bAdapter.isLe2MPhySupported}")
        Timber.i("LE Coded PHY Supported: ${bAdapter.isLeCodedPhySupported}")
        Timber.i("LE Extended Advertising Supported: ${bAdapter.isLeExtendedAdvertisingSupported}")
        Timber.i("LE Periodic Advertising Supported: ${bAdapter.isLePeriodicAdvertisingSupported}")
    }

    /** BLE Scan */

    @SuppressLint("ObsoleteSdkInt")
    fun startScan(context: Context) {
        if (!hasPermissions(context)) {
            scanInterface?.requestPermissions()
        } else if (!isScanning) {
            scanResults.clear()
            scanAdapter?.notifyDataSetChanged()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                checkBluetooth5Support()
            }

            val filters = listOf<ScanFilter>()
            val settings = scanSettings

            bleScanner.startScan(filters, settings, scanCallback)
            isScanning = true
            Timber.i("BLE Scan Started")
        }
    }

    fun stopScan() {
        if (isScanning) {
            bleScanner.stopScan(scanCallback)
            isScanning = false
            Timber.i("BLE Scan Stopped")
        }
    }

    private val scanSettings: ScanSettings
        get() {
            val builder = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setLegacy(false)
                    .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            }

            return builder.build()
        }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceAddress = result.device.address
            val index = scanResults.indexOfFirst { it.device.address == deviceAddress }

            if (index != -1) {
                updateExistingResult(index, result)
            } else {
                handleNewResult(result)
            }

            // Notify listeners about the updated scan result
            notifyScanResultUpdated(result)
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("Scan Failed! Code: $errorCode")
        }

        private fun updateExistingResult(index: Int, result: ScanResult) {
            scanResults[index] = result
            scanAdapter?.notifyItemChanged(index)
        }

        private fun handleNewResult(result: ScanResult) {
            val device = result.device
            Timber.i("Found BLE device! Name: ${device.name ?: "Unnamed"}, address: ${device.address}")

            if (isDeviceFiltered(result)) return

            scanResults.add(result)
            scanAdapter?.notifyItemInserted(scanResults.size - 1)
        }

        private fun isDeviceFiltered(result: ScanResult): Boolean {
            return scanAdapter?.filterCompare(result, deviceNameFilter, "name") != true ||
                    scanAdapter?.filterCompare(result, deviceRSSIFilter, "rssi") != true
        }

    }


    /** Helper Functions */

    fun hasPermissions(context: Context): Boolean {
        return hasLocationPermission(context) && hasBluetoothPermission(context)
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun hasLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true

        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true

        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

}