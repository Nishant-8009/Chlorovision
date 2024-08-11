package com.acc.chlorovis

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.acc.chlorovis.ble.BLEManager

class BLEScanService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start BLE scanning here
        BLEManager.startScan(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        // Stop BLE scanning here
        BLEManager.stopScan()
        super.onDestroy()
    }
}
