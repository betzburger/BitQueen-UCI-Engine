package de.peterbetz.bitqueen.uci

expect fun getProcessorCount(): Int
expect fun flushStdout()

expect class CommonAtomicInt(initialValue: Int) {
    var value: Int
}

class AtomicBoolean(initialValue: Boolean) {
    private val atom = CommonAtomicInt(if (initialValue) 1 else 0)
    
    fun get(): Boolean = atom.value != 0
    fun set(value: Boolean) {
        atom.value = if (value) 1 else 0
    }
}
