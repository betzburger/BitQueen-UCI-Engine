package de.peterbetz.bitqueen.core

/**
 * The chess game state as Bitboards.
 * Port from Swift: ChessBitboardGameState
 */
data class ChessBitboardGameState(
    // White Pieces
    var wP: ChessBitboard = ChessBitboard.EMPTY,
    var wN: ChessBitboard = ChessBitboard.EMPTY,
    var wB: ChessBitboard = ChessBitboard.EMPTY,
    var wR: ChessBitboard = ChessBitboard.EMPTY,
    var wQ: ChessBitboard = ChessBitboard.EMPTY,
    var wK: ChessBitboard = ChessBitboard.EMPTY,

    // Black Pieces
    var bP: ChessBitboard = ChessBitboard.EMPTY,
    var bN: ChessBitboard = ChessBitboard.EMPTY,
    var bB: ChessBitboard = ChessBitboard.EMPTY,
    var bR: ChessBitboard = ChessBitboard.EMPTY,
    var bQ: ChessBitboard = ChessBitboard.EMPTY,
    var bK: ChessBitboard = ChessBitboard.EMPTY,

    // Status
    var whiteToMove: Boolean = true,
    var castlingRights: Int = 0, // Bitmask: WK, WQ, BK, BQ
    var enPassantTarget: Int? = null,
    var halfMoveClock: Int = 0,
    var fullMoveNumber: Int = 1,
    var hash: ULong = 0uL
) {

    val whiteOccupied: ChessBitboard
        get() = wP or wN or wB or wR or wQ or wK

    val blackOccupied: ChessBitboard
        get() = bP or bN or bB or bR or bQ or bK

    val allOccupied: ChessBitboard
        get() = whiteOccupied or blackOccupied

    val empty: ChessBitboard
        get() = allOccupied.inv()

    // MARK: - Hash Management

    fun recomputeHash() {
        var h: ULong = 0uL
        val keys = ChessBitboardZobristKeys

        // Helper to xor pieces
        fun xorPieces(bb: ChessBitboard, typeIdx: Int) {
            var temp = bb
            while (true) {
                val (newBb, idx) = temp.popLSB()
                temp = newBb
                if (idx == null) break
                h = h xor keys.pieces[typeIdx][idx]
            }
        }

        xorPieces(wP, 0); xorPieces(wN, 1); xorPieces(wB, 2); xorPieces(wR, 3); xorPieces(wQ, 4); xorPieces(wK, 5)
        xorPieces(bP, 6); xorPieces(bN, 7); xorPieces(bB, 8); xorPieces(bR, 9); xorPieces(bQ, 10); xorPieces(bK, 11)

        if (!whiteToMove) {
            h = h xor keys.sideToMove
        }

        h = h xor keys.castlingRights[castlingRights]

        enPassantTarget?.let { ep ->
            val file = ep % 8
            h = h xor keys.enPassantFiles[file]
        }

        this.hash = h
    }
}
