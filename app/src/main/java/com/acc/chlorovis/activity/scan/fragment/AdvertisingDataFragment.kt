package com.acc.chlorovis.activity.scan.fragment

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Bundle
import android.content.Intent
import android.view.*
import android.widget.Toast

import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import com.acc.chlorovis.R
import com.acc.chlorovis.activity.plot.PlotActivity
import com.acc.chlorovis.ble.BLEManager.registerScanResultListener
import com.acc.chlorovis.ble.BLEManager.unregisterScanResultListener
import com.acc.chlorovis.ble.ScanResultListener
import com.acc.chlorovis.databinding.FragmentAdvertisingDataBinding
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale


@SuppressLint("MissingPermission")
class AdvertisingDataFragment: DialogFragment(), ScanResultListener {

    //to store the device address called row
    private var deviceAddress: String? = "not find"

    companion object {
        private const val ARG_DEVICE_ADDRESS = "device_address"
        private const val MAX_QUEUE_SIZE = 1000
        fun newInstance(deviceAddress: String): AdvertisingDataFragment {
            val fragment = AdvertisingDataFragment()
            val args = Bundle()
            args.putString(ARG_DEVICE_ADDRESS, deviceAddress)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceAddress = arguments?.getString(ARG_DEVICE_ADDRESS)
    }

    private lateinit var binding: FragmentAdvertisingDataBinding



    private val chlorData = LinkedList<Pair<String, Float>>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate<FragmentAdvertisingDataBinding>(
            inflater,
            R.layout.fragment_advertising_data,
            container,
            false
        )

        // Register this fragment as a listener
        registerScanResultListener(this)

        binding.okButton.setOnClickListener {
            dismiss()
        }

        // Set up download button click listener
        binding.downloadButton.setOnClickListener {
            downloadDataAsExcel()
        }

        // Return the root view of the binding
        return binding.root
    }

    // Implement the interface method
    override fun onScanResultUpdated(result: ScanResult) {

        val currentTime = System.currentTimeMillis()
        val formattedTime = dateFormat.format(Date(currentTime))

        // Update the UI with the new ScanResult
        updateUI(result, formattedTime)
    }

    private fun updateUI(result: ScanResult, formattedTime: String) {

        //to check whether the scanned result is having same device id
        if (result.device.address == deviceAddress) {

            // Access the byte array from the scan record
            val bytes = result.scanRecord?.bytes ?: byteArrayOf()

            // Convert bytes to signed integers and log them
            val signedBytes = bytes.map { it.toInt() }

            // Extract bytes if the array has the required length
            val deviceID = signedBytes.getOrNull(4)?.toUByte()?.toInt()
            val chlorod = signedBytes.getOrNull(5)
            val chlorof = signedBytes.getOrNull(6)?.toUByte()?.toInt()

            val chloro = "$chlorod.$chlorof"

            if (chlorod != null && chlorof != null) {
                val X: Double = (chlorod.toDouble()) + (chlorof.toDouble() / 100.0)
                // Add the values to the lists if they are not null
                chlorData.add(formattedTime to X.toFloat())
                if (chlorData.size > MAX_QUEUE_SIZE) chlorData.removeFirst()
            }

            // Update the UI elements
            requireActivity().runOnUiThread {
                binding.Byte0Text.text = deviceAddress
                binding.Byte2Text.text = chloro
                binding.Byte1Text.text = deviceID?.toString() ?: ""

            }
        }
    }


    private fun downloadDataAsExcel() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Chlorophyll Data")

        val headerRow = sheet.createRow(0)
        val headerCell1 = headerRow.createCell(0)
        headerCell1.setCellValue("Timestamp")
        val headerCell2 = headerRow.createCell(1)
        headerCell2.setCellValue("SPAD value")


        for ((index, data) in chlorData.withIndex()) {
            val (timestamp, chlorophyll) = data
            val row = sheet.createRow(index + 1)
            val cell1 = row.createCell(0)
            cell1.setCellValue(timestamp)
            val cell2 = row.createCell(1)
            cell2.setCellValue(chlorophyll.toDouble())
        }

        // Write the workbook to a file
        try {
            val fileName = "chlorophyll_data.xlsx"
            val fileOutputStream = requireContext().openFileOutput(fileName, Context.MODE_PRIVATE)
            workbook.write(fileOutputStream)
            fileOutputStream.close()
            workbook.close()

            // Notify the user of the file location
            val file = File(requireContext().filesDir, fileName)
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            startActivity(Intent.createChooser(intent, "Share via"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to create Excel file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Unregister the listener to avoid memory leaks
        unregisterScanResultListener(this)
    }

    override fun onResume() {
        super.onResume()
        // Set Fragment Dimensions
        val width = WindowManager.LayoutParams.MATCH_PARENT
        val height = WindowManager.LayoutParams.WRAP_CONTENT
        dialog?.window?.setLayout(width, height)
    }
}