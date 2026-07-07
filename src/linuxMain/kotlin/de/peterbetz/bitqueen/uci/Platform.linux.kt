package de.peterbetz.bitqueen.uci

import platform.posix.sysconf
import platform.posix._SC_NPROCESSORS_ONLN

actual fun getProcessorCount(): Int = sysconf(_SC_NPROCESSORS_ONLN).toInt().coerceAtLeast(1)
