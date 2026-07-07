package de.peterbetz.bitqueen.core

/**
 * High-performance Transposition Table.
 * Uses primitive arrays to avoid GC pressure and allow better caching.
 * Size is always power-of-2 for fast bitmask indexing.
 * Note: Not strictly thread-safe in this implementation, but data races
 * on primitives are usually acceptable in Lazy SMP if we double check the key.
 */
class TranspositionTable(sizeMB: Int = 128) {

    // Calculate power-of-2 entry count from MB
    // Each entry: key(8) + score(4) + depth(1 packed with gen+flag) + move(4) ≈ 20 bytes
    val size: Int
    private val mask: Int

    private val keys: ULongArray
    private val scores: IntArray
    private val depths: ByteArray       // search depth (0..127)
    private val flagsAndGen: ByteArray  // upper 6 bits = generation, lower 2 bits = flag
    private val moves: IntArray         // packed: from<<6 | to

    @kotlin.concurrent.Volatile
    private var generation: Int = 0     // incremented on each new game/search

    init {
        // Calculate entries: sizeMB * 1024 * 1024 / ~20 bytes per entry
        val targetEntries = (sizeMB.toLong() * 1024 * 1024 / 20).toInt()
        // Round down to power of 2
        var s = 1
        while (s * 2 <= targetEntries && s * 2 > 0) s *= 2
        size = s
        mask = size - 1

        keys = ULongArray(size)
        scores = IntArray(size)
        depths = ByteArray(size)
        flagsAndGen = ByteArray(size)
        moves = IntArray(size)
    }

    fun get(key: ULong): Entry? {
        val index = (key.toInt()) and mask
        if (keys[index] == key) {
            return Entry(
                key = keys[index],
                score = scores[index],
                depth = depths[index].toInt() and 0x7F,
                flag = (flagsAndGen[index].toInt() and 0x03).toByte(),
                moveFrom = moves[index]
            )
        }
        return null
    }

    fun store(key: ULong, score: Int, depth: Int, flag: Byte, moveFrom: Int, moveTo: Int) {
        val index = (key.toInt()) and mask
        val gen = (generation and 0x3F) shl 2

        // Replacement strategy: replace if
        // 1. Slot is empty
        // 2. Same position (always update with new info)
        // 3. New entry is from current generation and old entry is stale
        // 4. New entry has equal or greater depth
        val existingGen = (flagsAndGen[index].toInt() and 0xFC.toInt())
        val existingDepth = depths[index].toInt() and 0x7F

        if (keys[index] == 0UL ||
            keys[index] == key ||
            existingGen != gen ||
            existingDepth <= depth) {

            keys[index] = key
            scores[index] = score
            depths[index] = (depth and 0x7F).toByte()
            flagsAndGen[index] = (gen or (flag.toInt() and 0x03)).toByte()
            moves[index] = moveFrom
        }
    }

    /** Call at start of each new search iteration to age old entries */
    fun newGeneration() {
        generation++
    }

    fun clear() {
        keys.fill(0UL)
        depths.fill(0)
        flagsAndGen.fill(0)
        generation = 0
    }

    data class Entry(
        val key: ULong,
        val score: Int,
        val depth: Int,
        val flag: Byte,
        val moveFrom: Int
    )

    companion object {
        const val EXACT = 0.toByte()
        const val LOWER_BOUND = 1.toByte()
        const val UPPER_BOUND = 2.toByte()
    }
}
