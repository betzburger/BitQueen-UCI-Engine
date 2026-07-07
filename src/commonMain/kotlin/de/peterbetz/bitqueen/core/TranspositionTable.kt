package de.peterbetz.bitqueen.core

/**
 * High-performance Transposition Table.
 * Uses primitive arrays to avoid GC pressure and allow better caching.
 * Note: Not strictly thread-safe in this implementation, but data races 
 * on primitives are usually acceptable in Lazy SMP if we double check the key.
 */
/**
 * High-performance Transposition Table.
 * Uses primitive arrays to avoid GC pressure and allow better caching.
 * Note: Not strictly thread-safe in this implementation, but data races 
 * on primitives are usually acceptable in Lazy SMP if we double check the key.
 */
class TranspositionTable(val size: Int = 1_048_576) {
    
    private val keys = ULongArray(size)
    private val scores = IntArray(size)
    private val depths = IntArray(size)
    private val flags = ByteArray(size)
    private val moveFroms = IntArray(size)
    private val moveTos = IntArray(size)

    fun get(key: ULong): Entry? {
        val index = (key % size.toULong()).toInt()
        if (keys[index] == key) {
            return Entry(
                key = keys[index],
                score = scores[index],
                depth = depths[index],
                flag = flags[index],
                moveFrom = moveFroms[index],
                moveTo = moveTos[index]
            )
        }
        return null
    }

    fun store(key: ULong, score: Int, depth: Int, flag: Byte, moveFrom: Int, moveTo: Int) {
        val index = (key % size.toULong()).toInt()
        
        // Replacement strategy: Depth-preferred
        if (keys[index] == 0UL || depths[index] <= depth || keys[index] == key) {
            keys[index] = key
            scores[index] = score
            depths[index] = depth
            flags[index] = flag
            moveFroms[index] = moveFrom
            moveTos[index] = moveTo
        }
    }

    fun clear() {
        for (i in 0 until size) {
            keys[i] = 0UL
        }
    }

    data class Entry(
        val key: ULong,
        val score: Int,
        val depth: Int,
        val flag: Byte,
        val moveFrom: Int,
        val moveTo: Int
    )

    companion object {
        const val EXACT = 0.toByte()
        const val LOWER_BOUND = 1.toByte() // Beta cutoff (Result >= Score)
        const val UPPER_BOUND = 2.toByte() // Alpha cutoff (Result <= Score)
    }
}
