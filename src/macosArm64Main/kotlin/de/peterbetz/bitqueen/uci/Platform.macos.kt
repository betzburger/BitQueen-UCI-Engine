@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package de.peterbetz.bitqueen.uci

import platform.Foundation.NSProcessInfo
import platform.posix.fflush
import platform.posix.stdout
import kotlin.concurrent.AtomicInt

actual fun getProcessorCount(): Int = NSProcessInfo.processInfo.activeProcessorCount.toInt()
actual fun flushStdout() {
    fflush(stdout)
}

actual class CommonAtomicInt actual constructor(initialValue: Int) {
    private val atom = AtomicInt(initialValue)
    actual var value: Int
        get() = atom.value
        set(v) { atom.value = v }
}
