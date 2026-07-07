@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package de.peterbetz.bitqueen.uci

import platform.posix.getenv
import kotlinx.cinterop.toKString

actual fun getProcessorCount(): Int {
    val env = getenv("NUMBER_OF_PROCESSORS")
    return env?.toKString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
}
