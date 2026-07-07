package de.peterbetz.bitqueen.core


/**
 * A wrapper around ULong for Chess Bitboard operations (64 squares).
 * Layout: A1=0, B1=1, ... H1=7, A2=8, ... H8=63
 * Port from Swift: ChessBitboard (struct) -> value class
 */
@kotlin.jvm.JvmInline
value class ChessBitboard(val rawValue: ULong) {

    companion object {
        val EMPTY = ChessBitboard(0uL)
        val FULL = ChessBitboard(ULong.MAX_VALUE)
    }

    // MARK: - Basic Operations

    /**
     * Returns a new bitboard with the bit at [index] set.
     */
    fun withBitSet(index: Int): ChessBitboard {
        return ChessBitboard(rawValue or (1uL shl index))
    }

    /**
     * Returns a new bitboard with the bit at [index] cleared.
     */
    fun withBitCleared(index: Int): ChessBitboard {
        return ChessBitboard(rawValue and (1uL shl index).inv())
    }

    fun isSet(index: Int): Boolean {
        return (rawValue and (1uL shl index)) != 0uL
    }

    val popCount: Int
        get() = rawValue.countOneBits()

    val lsbIndex: Int?
        get() = if (rawValue == 0uL) null else rawValue.countTrailingZeroBits()

    /**
     * Returns a pair of (NewBitboard, PoppedIndex?)
     * Used to iterate bits.
     * Swift: mutating func popLSB() -> Int?
     */
    fun popLSB(): Pair<ChessBitboard, Int?> {
        if (rawValue == 0uL) return Pair(this, null)
        val index = rawValue.countTrailingZeroBits()
        val newRaw = rawValue and (rawValue - 1uL)
        return Pair(ChessBitboard(newRaw), index)
    }

    // MARK: - Operators

    infix fun or(other: ChessBitboard): ChessBitboard =
        ChessBitboard(rawValue or other.rawValue)

    infix fun and(other: ChessBitboard): ChessBitboard =
        ChessBitboard(rawValue and other.rawValue)

    infix fun xor(other: ChessBitboard): ChessBitboard =
        ChessBitboard(rawValue xor other.rawValue)
        
    fun inv(): ChessBitboard = ChessBitboard(rawValue.inv())

    // Overloading operators for convenience (Kotlin 2.0+)
    operator fun plus(other: ChessBitboard) = this or other // Union
    // operator fun minus(other: ChessBitboard) ?? No direct equivalent usually

    override fun toString(): String {
        val sb = StringBuilder()
        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                val index = rank * 8 + file
                sb.append(if (isSet(index)) "1 " else ". ")
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}

// MARK: - ULong Extensions for Bitboard logic

fun ULong.takeLowestOneBit(): ULong {
    return this and (this.inv() + 1uL)
}

fun ULong.takeHighestOneBit(): ULong {
    if (this == 0uL) return 0uL
    return 1uL shl (63 - countLeadingZeroBits())
}
