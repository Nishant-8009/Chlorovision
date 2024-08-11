package com.acc.chlorovis.extension

import timber.log.Timber

// Converts Hexadecimal to ASCII
fun String.hexToASCII(): String {
    // Uncomment the following line if you want to filter out non-hex characters
    // val hexString = this.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    val charList = chunked(2).map {
        try {
            it.toInt(16).toChar()
        } catch (e: NumberFormatException) {
            Timber.e("Invalid hex character: $it in string: $this")
            ' ' // Return a space or some default character in case of an error
        }
    }
    return String(charList.toCharArray())
}

// Converts Hex String to ByteArray
// Reference: https://stackoverflow.com/questions/66613717/kotlin-convert-hex-string-to-bytearray
fun String.hexToByteArray(): ByteArray {
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

// Removes White Space from String
fun String.removeWhiteSpace(): String {
    return filter { !it.isWhitespace() }
}
