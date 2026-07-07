@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package de.peterbetz.bitqueen.uci

import platform.Foundation.NSProcessInfo
import platform.posix.fflush
import platform.posix.stdout
import platform.posix.fopen
import platform.posix.fclose
import platform.posix.fgets
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlin.concurrent.AtomicInt

actual fun getProcessorCount(): Int = NSProcessInfo.processInfo.activeProcessorCount.toInt()
actual fun flushStdout() {
    fflush(stdout)
}

actual fun readFileLines(path: String): List<String>? {
    val fp = fopen(path, "r") ?: return null
    val out = ArrayList<String>()
    try {
        memScoped {
            val buf = allocArray<ByteVar>(8192)
            while (true) {
                val r = fgets(buf, 8192, fp) ?: break
                val line = r.toKString().trimEnd('\n', '\r')
                if (line.isNotEmpty()) out.add(line)
            }
        }
    } finally {
        fclose(fp)
    }
    return out
}

actual class CommonAtomicInt actual constructor(initialValue: Int) {
    private val atom = AtomicInt(initialValue)
    actual var value: Int
        get() = atom.value
        set(v) { atom.value = v }
}
