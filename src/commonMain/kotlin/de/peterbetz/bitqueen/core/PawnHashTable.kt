package de.peterbetz.bitqueen.core

/**
 * Per-thread pawn hash table caching pure-pawn structure features.
 * 65536 entries (2^16). Last-writer-wins, no locking.
 */
class PawnHashEntry(var key: ULong = 0uL, var mg: Int = 0, var eg: Int = 0)

class PawnHashTable(size: Int = 65536) {
    private val mask = size - 1
    private val table: Array<PawnHashEntry> = Array(size) { PawnHashEntry() }

    fun probe(key: ULong): PawnHashEntry? {
        val e = table[(key.toInt() and mask)]
        return if (e.key == key && key != 0uL) e else null
    }

    fun store(key: ULong, mg: Int, eg: Int) {
        val e = table[(key.toInt() and mask)]
        e.key = key; e.mg = mg; e.eg = eg
    }

    fun clear() {
        for (e in table) { e.key = 0uL; e.mg = 0; e.eg = 0 }
    }
}
