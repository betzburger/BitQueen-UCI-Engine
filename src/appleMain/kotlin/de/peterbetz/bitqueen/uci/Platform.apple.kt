package de.peterbetz.bitqueen.uci

import platform.Foundation.NSProcessInfo

actual fun getProcessorCount(): Int = NSProcessInfo.processInfo.activeProcessorCount.toInt()
