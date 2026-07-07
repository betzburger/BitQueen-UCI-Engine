package de.peterbetz.bitqueen.uci

import java.util.concurrent.atomic.AtomicInteger

actual fun getProcessorCount(): Int = Runtime.getRuntime().availableProcessors()
actual fun flushStdout() {
    System.out.flush()
}

actual class CommonAtomicInt actual constructor(initialValue: Int) {
    private val atom = AtomicInteger(initialValue)
    actual var value: Int
        get() = atom.get()
        set(v) { atom.set(v) }
}
