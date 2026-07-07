package de.peterbetz.bitqueen.core

/**
 * Represents the identity of a player.
 * Port from Swift: PlayerIdentity
 */
enum class PlayerIdentity(val value: Int) {
    BLANK(0),
    PLAYER_ONE(1), // White
    PLAYER_TWO(2); // Black

    val isWhite: Boolean get() = this == PLAYER_ONE
    val isBlack: Boolean get() = this == PLAYER_TWO

    fun opponent(): PlayerIdentity {
        return when (this) {
            PLAYER_ONE -> PLAYER_TWO
            PLAYER_TWO -> PLAYER_ONE
            else -> BLANK
        }
    }
    
    companion object {
        fun fromValue(value: Int): PlayerIdentity = entries.firstOrNull { it.value == value } ?: BLANK
    }
}

/**
 * Abstract piece type (Pawn, Knight, etc.) without color.
 * Port from Swift: ChessPieceType
 */
enum class ChessPieceType {
    NONE, PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING
}

/**
 * Represents a specific piece (Color + Type) backed by an Int value for board array compatibility.
 * Port from Swift: ChessPiece
 */
enum class ChessPiece(val value: Int) {
    BLANK(0),

    // White (10-15)
    WHITE_PAWN(10),
    WHITE_KNIGHT(11),
    WHITE_BISHOP(12),
    WHITE_ROOK(13),
    WHITE_QUEEN(14),
    WHITE_KING(15),

    // Black (80-85)
    BLACK_PAWN(80),
    BLACK_KNIGHT(81),
    BLACK_BISHOP(82),
    BLACK_ROOK(83),
    BLACK_QUEEN(84),
    BLACK_KING(85);

    val isWhite: Boolean
        get() = value in 10..19

    val isBlack: Boolean
        get() = value in 80..89

    val player: PlayerIdentity
        get() = when {
            isWhite -> PlayerIdentity.PLAYER_ONE
            isBlack -> PlayerIdentity.PLAYER_TWO
            else -> PlayerIdentity.BLANK
        }

    val type: ChessPieceType
        get() = when (this) {
            WHITE_PAWN, BLACK_PAWN -> ChessPieceType.PAWN
            WHITE_KNIGHT, BLACK_KNIGHT -> ChessPieceType.KNIGHT
            WHITE_BISHOP, BLACK_BISHOP -> ChessPieceType.BISHOP
            WHITE_ROOK, BLACK_ROOK -> ChessPieceType.ROOK
            WHITE_QUEEN, BLACK_QUEEN -> ChessPieceType.QUEEN
            WHITE_KING, BLACK_KING -> ChessPieceType.KING
            else -> ChessPieceType.NONE
        }

    companion object {
        fun fromValue(value: Int): ChessPiece = entries.firstOrNull { it.value == value } ?: BLANK
    }
}
