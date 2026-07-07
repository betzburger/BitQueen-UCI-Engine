package de.peterbetz.bitqueen.core

/**
 * Zobrist-Keys for Chess Hash generation.
 * Port from Swift: ChessBitboardZobristKeys
 */
object ChessBitboardZobristKeys {
    
    // [PieceType][SquareIndex]
    // 0-5 White (P, N, B, R, Q, K), 6-11 Black (P, N, B, R, Q, K)
    val pieces: Array<ULongArray>
    val sideToMove: ULong
    val castlingRights: ULongArray
    val enPassantFiles: ULongArray

    init {
        val p = Array(12) { ULongArray(64) }
        val c = ULongArray(16)
        val ep = ULongArray(8)

        // Deterministic PRNG to match Swift version
        var state: ULong = 0x123456789ABCDEF0uL
        
        fun next(): ULong {
            state = state xor (state shl 13)
            state = state xor (state shr 7)
            state = state xor (state shl 17)
            return state
        }

        for (pc in 0 until 12) {
            for (sq in 0 until 64) {
                p[pc][sq] = next()
            }
        }

        for (i in 0 until 16) { c[i] = next() }
        for (i in 0 until 8) { ep[i] = next() }
        sideToMove = next()

        pieces = p
        castlingRights = c
        enPassantFiles = ep
    }
}
