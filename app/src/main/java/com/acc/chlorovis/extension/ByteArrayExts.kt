package com.acc.chlorovis.extension

// Converts Byte Array to Hexadecimal String
fun ByteArray.toHexString(): String =
    joinToString(separator = " ", prefix = "0x") { String.format("%02x", it) }