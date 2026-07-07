@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package de.peterbetz.bitqueen.uci

import platform.posix._SC_NPROCESSORS_ONLN
import platform.posix.fflush
import platform.posix.stdout
import platform.posix.sysconf
import kotlin.concurrent.AtomicInt

actual fun getProcessorCount(): Int = sysconf(_SC_NPROCESSORS_ONLN).toInt().coerceAtLeast(1)
actual fun flushStdout() {
    fflush(stdout)
}

actual class CommonAtomicInt actual constructor(initialValue: Int) {
    private val atom = AtomicInt(initialValue)
    actual var value: Int
        get() = atom.value
        set(v) { atom.value = v }
}
