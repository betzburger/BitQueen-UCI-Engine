package de.peterbetz.bitqueen.core

/**
 * Utility functions for Chess Engine operations.
 */

fun BitboardChessMove.toLAN(): String {
    val files = arrayOf("a", "b", "c", "d", "e", "f", "g", "h")
    val ranks = arrayOf("1", "2", "3", "4", "5", "6", "7", "8")
    val f = files[from % 8] + ranks[from / 8]
    val t = files[to % 8] + ranks[to / 8]
    val promo = when (flag) {
        ChessMoveFlag.PROMO_QUEEN, ChessMoveFlag.PROMO_QUEEN_CAPTURE -> "q"
        ChessMoveFlag.PROMO_ROOK, ChessMoveFlag.PROMO_ROOK_CAPTURE -> "r"
        ChessMoveFlag.PROMO_BISHOP, ChessMoveFlag.PROMO_BISHOP_CAPTURE -> "b"
        ChessMoveFlag.PROMO_KNIGHT, ChessMoveFlag.PROMO_KNIGHT_CAPTURE -> "n"
        else -> ""
    }
    return "$f$t$promo"
}

fun parseLAN(lan: String, state: ChessBitboardGameState): BitboardChessMove? {
    if (lan.length < 4) return null
    val fromFile = lan[0] - 'a'
    val fromRank = lan[1].digitToInt() - 1
    val toFile = lan[2] - 'a'
    val toRank = lan[3].digitToInt() - 1
    val fromSq = fromRank * 8 + fromFile
    val toSq = toRank * 8 + toFile
    
    val promoChar = if (lan.length > 4) lan[4].lowercaseChar() else null
    
    val moves = ChessBitboardMoveGenerator.generateMoves(state)
    return moves.find { move ->
        move.from == fromSq && move.to == toSq && (promoChar == null || when (move.flag) {
            ChessMoveFlag.PROMO_QUEEN, ChessMoveFlag.PROMO_QUEEN_CAPTURE -> promoChar == 'q'
            ChessMoveFlag.PROMO_ROOK, ChessMoveFlag.PROMO_ROOK_CAPTURE -> promoChar == 'r'
            ChessMoveFlag.PROMO_BISHOP, ChessMoveFlag.PROMO_BISHOP_CAPTURE -> promoChar == 'b'
            ChessMoveFlag.PROMO_KNIGHT, ChessMoveFlag.PROMO_KNIGHT_CAPTURE -> promoChar == 'n'
            else -> false
        })
    }
}

fun applyMove(state: ChessBitboardGameState, move: BitboardChessMove) {
    val us = state.whiteToMove
    val them = !us
    val from = move.from
    val to = move.to
    val keys = ChessBitboardZobristKeys

    state.hash = state.hash xor keys.sideToMove
    state.hash = state.hash xor keys.castlingRights[state.castlingRights]
    if (state.enPassantTarget != null) {
        state.hash = state.hash xor keys.enPassantFiles[state.enPassantTarget!! % 8]
    }

    val movingPieceType = getPieceType(from, us, state)
    val movingPieceIdx = pieceIndex(movingPieceType, us)
    state.hash = state.hash xor keys.pieces[movingPieceIdx][from]
    clearPiece(from, us, state)

    if (move.isCapture) {
        if (move.flag == ChessMoveFlag.EP_CAPTURE) {
            val epPawnSq = if (us) to - 8 else to + 8
            val capturedIdx = pieceIndex(ChessPieceType.PAWN, them)
            state.hash = state.hash xor keys.pieces[capturedIdx][epPawnSq]
            clearPiece(epPawnSq, them, state)
        } else {
            val capturedType = getPieceType(to, them, state)
            val capturedIdx = pieceIndex(capturedType, them)
            state.hash = state.hash xor keys.pieces[capturedIdx][to]
            clearPiece(to, them, state)
        }
    }

    if (move.isPromotion) {
        var promoType = ChessPieceType.QUEEN
        when (move.flag) {
            ChessMoveFlag.PROMO_QUEEN, ChessMoveFlag.PROMO_QUEEN_CAPTURE -> promoType = ChessPieceType.QUEEN
            ChessMoveFlag.PROMO_ROOK, ChessMoveFlag.PROMO_ROOK_CAPTURE -> promoType = ChessPieceType.ROOK
            ChessMoveFlag.PROMO_BISHOP, ChessMoveFlag.PROMO_BISHOP_CAPTURE -> promoType = ChessPieceType.BISHOP
            ChessMoveFlag.PROMO_KNIGHT, ChessMoveFlag.PROMO_KNIGHT_CAPTURE -> promoType = ChessPieceType.KNIGHT
            else -> {}
        }
        val promoIdx = pieceIndex(promoType, us)
        state.hash = state.hash xor keys.pieces[promoIdx][to]
        setPiece(to, promoType, us, state)
    } else {
        state.hash = state.hash xor keys.pieces[movingPieceIdx][to]
        setPiece(to, movingPieceType, us, state)
    }

    if (move.flag == ChessMoveFlag.KING_CASTLE) {
        val rFrom = if (us) 7 else 63
        val rTo = if (us) 5 else 61
        val rookIdx = pieceIndex(ChessPieceType.ROOK, us)
        state.hash = state.hash xor keys.pieces[rookIdx][rFrom]
        clearPiece(rFrom, us, state)
        state.hash = state.hash xor keys.pieces[rookIdx][rTo]
        setPiece(rTo, ChessPieceType.ROOK, us, state)
    } else if (move.flag == ChessMoveFlag.QUEEN_CASTLE) {
        val rFrom = if (us) 0 else 56
        val rTo = if (us) 3 else 59
        val rookIdx = pieceIndex(ChessPieceType.ROOK, us)
        state.hash = state.hash xor keys.pieces[rookIdx][rFrom]
        clearPiece(rFrom, us, state)
        state.hash = state.hash xor keys.pieces[rookIdx][rTo]
        setPiece(rTo, ChessPieceType.ROOK, us, state)
    }

    if (movingPieceType == ChessPieceType.KING) {
        state.castlingRights = state.castlingRights and (if (us) 3.inv() else 12.inv())
    }
    if (movingPieceType == ChessPieceType.ROOK) {
        if (us) {
            if (from == 0) state.castlingRights = state.castlingRights and 2.inv()
            else if (from == 7) state.castlingRights = state.castlingRights and 1.inv()
        } else {
            if (from == 56) state.castlingRights = state.castlingRights and 8.inv()
            else if (from == 63) state.castlingRights = state.castlingRights and 4.inv()
        }
    }
    if (move.isCapture) {
         if (to == 0) state.castlingRights = state.castlingRights and 2.inv()
         else if (to == 7) state.castlingRights = state.castlingRights and 1.inv()
         else if (to == 56) state.castlingRights = state.castlingRights and 8.inv()
         else if (to == 63) state.castlingRights = state.castlingRights and 4.inv()
    }

    state.enPassantTarget = null
    if (move.flag == ChessMoveFlag.DOUBLE_PAWN_PUSH) {
        state.enPassantTarget = if (us) from + 8 else from - 8
    }

    state.hash = state.hash xor keys.castlingRights[state.castlingRights]
    if (state.enPassantTarget != null) {
        state.hash = state.hash xor keys.enPassantFiles[state.enPassantTarget!! % 8]
    }
    state.whiteToMove = !state.whiteToMove
}

private fun pieceIndex(type: ChessPieceType, white: Boolean): Int {
    val offset = if (white) 0 else 6
    return offset + when (type) {
        ChessPieceType.PAWN -> 0
        ChessPieceType.KNIGHT -> 1
        ChessPieceType.BISHOP -> 2
        ChessPieceType.ROOK -> 3
        ChessPieceType.QUEEN -> 4
        ChessPieceType.KING -> 5
        else -> 0
    }
}

private fun getPieceType(sq: Int, white: Boolean, state: ChessBitboardGameState): ChessPieceType {
    if (white) {
        if (state.wP.isSet(sq)) return ChessPieceType.PAWN
        if (state.wN.isSet(sq)) return ChessPieceType.KNIGHT
        if (state.wB.isSet(sq)) return ChessPieceType.BISHOP
        if (state.wR.isSet(sq)) return ChessPieceType.ROOK
        if (state.wQ.isSet(sq)) return ChessPieceType.QUEEN
        if (state.wK.isSet(sq)) return ChessPieceType.KING
    } else {
        if (state.bP.isSet(sq)) return ChessPieceType.PAWN
        if (state.bN.isSet(sq)) return ChessPieceType.KNIGHT
        if (state.bB.isSet(sq)) return ChessPieceType.BISHOP
        if (state.bR.isSet(sq)) return ChessPieceType.ROOK
        if (state.bQ.isSet(sq)) return ChessPieceType.QUEEN
        if (state.bK.isSet(sq)) return ChessPieceType.KING
    }
    return ChessPieceType.PAWN
}

private fun clearPiece(sq: Int, white: Boolean, state: ChessBitboardGameState) {
    if (white) {
        state.wP = state.wP.withBitCleared(sq); state.wN = state.wN.withBitCleared(sq); state.wB = state.wB.withBitCleared(sq)
        state.wR = state.wR.withBitCleared(sq); state.wQ = state.wQ.withBitCleared(sq); state.wK = state.wK.withBitCleared(sq)
    } else {
        state.bP = state.bP.withBitCleared(sq); state.bN = state.bN.withBitCleared(sq); state.bB = state.bB.withBitCleared(sq)
        state.bR = state.bR.withBitCleared(sq); state.bQ = state.bQ.withBitCleared(sq); state.bK = state.bK.withBitCleared(sq)
    }
}

private fun setPiece(sq: Int, type: ChessPieceType, white: Boolean, state: ChessBitboardGameState) {
    if (white) {
        when (type) {
            ChessPieceType.PAWN -> state.wP = state.wP.withBitSet(sq)
            ChessPieceType.KNIGHT -> state.wN = state.wN.withBitSet(sq)
            ChessPieceType.BISHOP -> state.wB = state.wB.withBitSet(sq)
            ChessPieceType.ROOK -> state.wR = state.wR.withBitSet(sq)
            ChessPieceType.QUEEN -> state.wQ = state.wQ.withBitSet(sq)
            ChessPieceType.KING -> state.wK = state.wK.withBitSet(sq)
            else -> {}
        }
    } else {
        when (type) {
            ChessPieceType.PAWN -> state.bP = state.bP.withBitSet(sq)
            ChessPieceType.KNIGHT -> state.bN = state.bN.withBitSet(sq)
            ChessPieceType.BISHOP -> state.bB = state.bB.withBitSet(sq)
            ChessPieceType.ROOK -> state.bR = state.bR.withBitSet(sq)
            ChessPieceType.QUEEN -> state.bQ = state.bQ.withBitSet(sq)
            ChessPieceType.KING -> state.bK = state.bK.withBitSet(sq)
            else -> {}
        }
    }
}
