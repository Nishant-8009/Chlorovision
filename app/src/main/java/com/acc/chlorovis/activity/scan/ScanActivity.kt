package com.acc.chlorovis.activity.scan


import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.acc.chlorovis.BuildConfig
import com.acc.chlorovis.R
import com.acc.chlorovis.activity.scan.fragment.DeviceInfoFragment
import com.acc.chlorovis.activity.scan.fragment.RSSIFilterFragment
import com.acc.chlorovis.ble.BLEManager
import com.acc.chlorovis.ble.BLEManager.bAdapter
import com.acc.chlorovis.ble.ENABLE_BLUETOOTH_REQUEST_CODE
import com.acc.chlorovis.databinding.ActivityScanBinding
import com.acc.chlorovis.BLEScanService
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class ScanActivity : AppCompatActivity(), ScanAdapter.Delegate, ScanInterface {

    private lateinit var binding: ActivityScanBinding
    private var scanItem: MenuItem? = null
    private lateinit var sharedPreferences: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, BLEScanService::class.java))
        binding = DataBindingUtil.setContentView(this, R.layout.activity_scan)

        sharedPreferences = getSharedPreferences("ThemePrefs", MODE_PRIVATE)

        // Set the initial state of the switch
        val isDarkTheme = sharedPreferences.getBoolean("isDarkTheme", false)
        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        binding.themeSwitch.isChecked = isDarkTheme

        // Set a listener on the switch to toggle the theme
        binding.themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            // Save the theme preference
            with(sharedPreferences.edit()) {
                putBoolean("isDarkTheme", isChecked)
                apply()
            }
        }
        setSupportActionBar(binding.toolbar)
        // Inflate the custom layout for the ActionBar
        val actionBar = supportActionBar
        actionBar?.setDisplayShowTitleEnabled(true)
        actionBar?.setDisplayUseLogoEnabled(true)
        val customActionBar = LayoutInflater.from(this).inflate(R.layout.custom_actionbar, null)

        // Set the custom view for the ActionBar
        actionBar?.customView = customActionBar

        // Find the ImageView in the custom layout and set its image resource and dimensions
        val logoImageView = customActionBar.findViewById<ImageView>(R.id.logoImageView)
        logoImageView.setImageResource(R.drawable.awadh)
        logoImageView.layoutParams.height = resources.getDimensionPixelSize(R.dimen.logo_height) // Set the desired height

        setupRecyclerView()

        BLEManager.scanInterface = this
        BLEManager.startScan(this)


    }

    override fun onResume() {
        super.onResume()

        binding.themeSwitch.isChecked = sharedPreferences.getBoolean("isDarkTheme", false)
        if (!bAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    override fun onStop() {
        super.onStop()

    }

    /** Permission & Bluetooth Requests */

    // Prompt to Enable BT
    override fun promptEnableBluetooth() {
        if (!bAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            ActivityCompat.startActivityForResult(
                this, enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE, null
            )
        }
    }



    // Request Runtime Permissions (Based on Android Version)
    @SuppressLint("ObsoleteSdkInt")
    override fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            ))
        }
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    // Request Permissions if Not Given by User (Limit 2)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (BLEManager.hasPermissions(this)) {
            BLEManager.startScan(this)
        } else {
            requestPermissions()
        }
    }

    /** Toolbar Menu */

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)

        // Get Current App Version
        menu.findItem(R.id.appVersionItem).apply {
            title = "$title ${BuildConfig.VERSION_NAME}"
        }

        scanItem = menu.findItem(R.id.scanItem)

        return true
    }

    // Item on Toolbar Selected
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.scanItem -> {
                if (BLEManager.isScanning) {
                    BLEManager.stopScan()
                    item.setIcon(R.drawable.ic_play)
                } else {
                    BLEManager.startScan(this)
                    item.setIcon(R.drawable.ic_pause)
                }
            }
            R.id.rssiFilterItem -> {
                RSSIFilterFragment().show(supportFragmentManager, "rssiFilterFragment")
            }
            R.id.deviceInfoItem -> {
                DeviceInfoFragment().show(supportFragmentManager, "deviceInfoFragment")
            }
        }

        return false
    }

    /** Recycler View */

    // Sets Up the Recycler View for BLE Scan List
    private fun setupRecyclerView() {
        // Create & Set Adapter
        BLEManager.scanAdapter = ScanAdapter(BLEManager.scanResults, this)

        binding.scanResultsRecyclerView.apply {
            adapter = BLEManager.scanAdapter
            layoutManager = LinearLayoutManager(
                this@ScanActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        // Turns Off Update Animation
        val animator = binding.scanResultsRecyclerView.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    // Connect Button Clicked
    override fun onConnectButtonClick(result: ScanResult) {
    }

    // Item Clicked (Show Advertising Data)
    override fun onItemClick(dialog: DialogFragment) {
        dialog.show(supportFragmentManager, "advertisingDataFragment")
    }

    /** Helper Functions */

    // Go to ConnectionInterface Activity
    override fun startIntent() {
    }

    override fun startToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
