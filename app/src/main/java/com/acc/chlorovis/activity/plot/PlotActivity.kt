package com.acc.chlorovis.activity.plot

import android.bluetooth.le.ScanResult
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.acc.chlorovis.R
import com.acc.chlorovis.databinding.ActivityPlotBinding
import com.acc.chlorovis.ble.BLEManager.registerScanResultListener
import com.acc.chlorovis.ble.BLEManager.unregisterScanResultListener
import com.acc.chlorovis.ble.ScanResultListener
import timber.log.Timber
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate


class PlotActivity : AppCompatActivity(), ScanResultListener {

    private lateinit var binding: ActivityPlotBinding
    private lateinit var xChart: LineChart
    private lateinit var yChart: LineChart
    private lateinit var zChart: LineChart

    private var deviceAddress: String? = null
    private lateinit var sharedPreferences: SharedPreferences
    private val xEntries = ArrayList<Entry>()
    private val yEntries = ArrayList<Entry>()
    private val zEntries = ArrayList<Entry>()
    private var time = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_plot)

        sharedPreferences = getSharedPreferences("ThemePrefs", MODE_PRIVATE)

        // Set the initial state of the switch
        val isDarkTheme = sharedPreferences.getBoolean("isDarkTheme", false)
        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        setSupportActionBar(binding.toolbar)
        // Set the custom navigation icon
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_black)
        }
        xChart = binding.xChart
        yChart = binding.yChart
        zChart = binding.zChart

        // Initialize charts
        initChart(xChart)
        initChart(yChart)
        initChart(zChart)

        // Set custom marker
        val markerX = CustomMarkerView(this, R.layout.marker_view_humid)
        xChart.marker = markerX
        val markerY = CustomMarkerView(this, R.layout.marker_view_temp)
        yChart.marker = markerY

        zChart.marker = markerY

        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")

        // Register this activity as a listener
        registerScanResultListener(this)

        // Get data from the intent
        val xData = intent.getFloatArrayExtra("X_DATA")
        val yData = intent.getFloatArrayExtra("Y_DATA")
        val zData = intent.getFloatArrayExtra("Z_DATA")

        xData?.let {
            for (i in it.indices) {
                addEntryToChart(xChart, xEntries, time + i , it[i], "#FF0000") // Red for X
            }
        }

        yData?.let {
            for (i in it.indices) {
                addEntryToChart(yChart, yEntries, time + i , it[i], "#FFFF00") // Yellow for Y
            }
        }

        zData?.let {
            for (i in it.indices) {
                addEntryToChart(zChart, zEntries, time + i , it[i], "#0000FF") // Blue for Z
            }
        }
    }

    //gets the new scanned result and checks that the address is same as the opened device and if yes then it adds the new entry to graph
    override fun onScanResultUpdated(result: ScanResult){
        if (result.device.address == deviceAddress) {

            // Access the byte array from the scan record
            val bytes = result.scanRecord?.bytes ?: byteArrayOf()

            // Convert bytes to signed integers and log them
            val signedBytes = bytes.map { it.toInt() }
            Timber.d("Signed Bytes array: %s", signedBytes.joinToString())

            // Extract bytes if the array has the required length
            val xd = signedBytes.getOrNull(5)
            val xf = signedBytes.getOrNull(6)?.toUByte()?.toInt()
            val yd = signedBytes.getOrNull(7)
            val yf = signedBytes.getOrNull(8)?.toUByte()?.toInt()
            val zd = signedBytes.getOrNull(9)
            val zf = signedBytes.getOrNull(10)?.toUByte()?.toInt()

            if (xd != null && xf != null && yd != null && yf != null && zd != null && zf != null) {
                val X: Double = xd.toDouble() + (xf.toDouble() / 100.0)
                val Y: Double = yd.toDouble() + (yf.toDouble() / 100.0)
                val Z: Double = zd.toDouble() + (zf.toDouble() / 100.0)
                addNewData(X.toFloat(),Y.toFloat(),Z.toFloat())
            }
        }
    }

    //initialises the graph
    private fun initChart(chart: LineChart) {
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)

        // Disable grid lines and axis labels
        chart.xAxis.setDrawGridLines(false)
        chart.axisLeft.setDrawGridLines(false)
        chart.axisRight.setDrawGridLines(false)

        // Disable legend
        chart.legend.isEnabled = false


    }

    //add entry to each graph and has color for each graph
    private fun addEntryToChart(chart: LineChart, entries: ArrayList<Entry>, x: Float, y: Float, color: String) {
        entries.add(Entry(x, y))

        // Check if the number of entries exceeds 30
        if (entries.size > 30) {
            entries.removeAt(0) // Remove the oldest entry
        }

        val dataSet = LineDataSet(entries, when (chart) {
            xChart -> "X"
            yChart -> "Y"
            else -> "Z"
        })

        dataSet.setDrawValues(false)  // Disable values on data points

        // Enable cubic Bezier curve
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER

        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM

        dataSet.color = Color.parseColor(color) // Line color
        dataSet.setCircleColor(Color.parseColor(color)) // Point color
        dataSet.circleRadius = 1f // Point radius
        dataSet.setDrawCircleHole(false) // Filled circles
        dataSet.highLightColor = Color.parseColor(color) // Highlight color
        dataSet.setDrawHighlightIndicators(true) // Draw highlight indicators
        dataSet.lineWidth = 2f

        // Enable fill and set fill color
        dataSet.setDrawFilled(true)
        dataSet.fillColor = when (chart) {
            xChart -> resources.getColor(R.color.x_fill, null)
            yChart -> resources.getColor(R.color.y_fill, null)
            else -> resources.getColor(R.color.z_fill, null)
        }

        dataSet.valueTextSize = 0f // Hide value text

        val lineData = LineData(dataSet)
        chart.data = lineData
        chart.invalidate() // Refresh the chart
    }

    override fun onSupportNavigateUp(): Boolean {
        // Unregister the listener to avoid memory leaks
        unregisterScanResultListener(this)
        onBackPressed()
        return true
    }

    fun addNewData(x: Float, y: Float, z: Float) {
        time += 1
        addEntryToChart(xChart, xEntries, time, x, "#FF0000") // Red for X
        addEntryToChart(yChart, yEntries, time, y, "#FFFF00") // Yellow for Y
        addEntryToChart(zChart, zEntries, time, z, "#0000FF") // Blue for Z
    }


}
