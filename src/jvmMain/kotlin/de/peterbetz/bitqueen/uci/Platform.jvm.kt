package de.peterbetz.bitqueen.uci

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

actual fun getProcessorCount(): Int = Runtime.getRuntime().availableProcessors()
actual fun flushStdout() {
    System.out.flush()
}

actual fun readFileLines(path: String): List<String>? {
    val file = File(path)
    if (!file.exists()) return null
    return file.readLines().map { it.trimEnd('\n', '\r') }.filter { it.isNotEmpty() }
}

actual class CommonAtomicInt actual constructor(initialValue: Int) {
    private val atom = AtomicInteger(initialValue)
    actual var value: Int
        get() = atom.get()
        set(v) { atom.set(v) }
}
