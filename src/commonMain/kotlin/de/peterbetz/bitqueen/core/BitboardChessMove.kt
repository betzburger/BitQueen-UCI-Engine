package de.peterbetz.bitqueen.core

/**
 * Low-level move flag for engine optimization.
 * Port from Swift: ChessMoveFlag
 */
enum class ChessMoveFlag(val value: Int) {
    QUIET(0),
    DOUBLE_PAWN_PUSH(1),
    KING_CASTLE(2),
    QUEEN_CASTLE(3),
    CAPTURE(4),
    EP_CAPTURE(5),
    PROMO_KNIGHT(8),
    PROMO_BISHOP(9),
    PROMO_ROOK(10),
    PROMO_QUEEN(11),
    PROMO_KNIGHT_CAPTURE(12),
    PROMO_BISHOP_CAPTURE(13),
    PROMO_ROOK_CAPTURE(14),
    PROMO_QUEEN_CAPTURE(15);
    
    companion object {
        fun fromValue(value: Int): ChessMoveFlag = entries.firstOrNull { it.value == value } ?: QUIET
    }
}

/**
 * Engine Move Structure.
 * Port from Swift: BitboardChessMove
 */
data class BitboardChessMove(
    val from: Int,
    val to: Int,
    val flag: ChessMoveFlag,
    var score: Int = 0
) {
    val isCapture: Boolean
        get() = flag == ChessMoveFlag.CAPTURE || flag == ChessMoveFlag.EP_CAPTURE ||
                flag == ChessMoveFlag.PROMO_KNIGHT_CAPTURE || flag == ChessMoveFlag.PROMO_BISHOP_CAPTURE ||
                flag == ChessMoveFlag.PROMO_ROOK_CAPTURE || flag == ChessMoveFlag.PROMO_QUEEN_CAPTURE

    val isPromotion: Boolean
        get() = flag.value >= 8

    override fun toString(): String {
        val files = arrayOf("a", "b", "c", "d", "e", "f", "g", "h")
        val ranks = arrayOf("1", "2", "3", "4", "5", "6", "7", "8")
        val f = files[from % 8] + ranks[from / 8]
        val t = files[to % 8] + ranks[to / 8]
        return "$f$t ($flag)"
    }
}
