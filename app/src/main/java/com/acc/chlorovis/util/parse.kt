package com.acc.chlorovis.util

import com.acc.chlorovis.extension.hexToByteArray

fun parseSensorData(hexString: String): String {
    val data = hexString.hexToByteArray()

    if (data.size < 5) {
        throw IllegalArgumentException("Invalid data length")
    }

    val nodeNumber = data[0].toInt()
    val temperatureIntegerPart = data[1].toInt()
    val temperatureFractionalPart = data[2].toInt()
    val humidityIntegerPart = data[3].toInt()
    val humidityFractionalPart = data[4].toInt()

    val temperature = "$temperatureIntegerPart.$temperatureFractionalPart"
    val humidity = "$humidityIntegerPart.$humidityFractionalPart"

    return "Node: $nodeNumber\nTemperature: $temperature°C\nHumidity: $humidity%"
}
