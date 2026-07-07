package de.peterbetz.bitqueen.core

/**
 * Utility object for converting chess moves to descriptive notation.
 * Descriptive notation was the standard before algebraic notation.
 * Example: "P-K4" (Pawn to King 4), "N-KB3" (Knight to King's Bishop 3)
 */
object ChessMoveNotation {
    
    /**
     * Converts a BitboardChessMove to descriptive notation.
     * Examples:
     * - e2-e4 becomes "P-K4"
     * - g1-f3 becomes "N-KB3"
     * - e1-g1 (castling) becomes "O-O"
     * - d1xd8 becomes "QxQ"
     */
    fun BitboardChessMove.toDescriptiveNotation(state: ChessBitboardGameState): String {
        // Handle castling
        if (flag == ChessMoveFlag.KING_CASTLE) return "O-O"
        if (flag == ChessMoveFlag.QUEEN_CASTLE) return "O-O-O"
        
        val isWhite = state.whiteToMove
        val pieceType = getPieceTypeAt(from, isWhite, state)
        val pieceSymbol = getPieceSymbol(pieceType)
        
        // Get descriptive file and rank for destination
        val toFile = to % 8
        val toRank = to / 8
        val descriptiveFile = getDescriptiveFile(toFile, isWhite)
        val descriptiveRank = if (isWhite) toRank + 1 else 8 - toRank
        
        // Build notation
        val capture = if (isCapture) "x" else "-"
        
        // For captures, include captured piece if it's not a pawn
        val capturedPiece = if (isCapture && flag != ChessMoveFlag.EP_CAPTURE) {
            val capturedType = getPieceTypeAt(to, !isWhite, state)
            if (capturedType != ChessPieceType.PAWN) {
                getPieceSymbol(capturedType)
            } else ""
        } else ""
        
        // Handle promotion
        val promotion = if (isPromotion) {
            val promoType = when (flag) {
                ChessMoveFlag.PROMO_QUEEN, ChessMoveFlag.PROMO_QUEEN_CAPTURE -> ChessPieceType.QUEEN
                ChessMoveFlag.PROMO_ROOK, ChessMoveFlag.PROMO_ROOK_CAPTURE -> ChessPieceType.ROOK
                ChessMoveFlag.PROMO_BISHOP, ChessMoveFlag.PROMO_BISHOP_CAPTURE -> ChessPieceType.BISHOP
                ChessMoveFlag.PROMO_KNIGHT, ChessMoveFlag.PROMO_KNIGHT_CAPTURE -> ChessPieceType.KNIGHT
                else -> ChessPieceType.QUEEN
            }
            "=" + getPieceSymbol(promoType)
        } else ""
        
        return if (isCapture && capturedPiece.isNotEmpty()) {
            "$pieceSymbol$capture$capturedPiece"
        } else {
            "$pieceSymbol$capture$descriptiveFile$descriptiveRank$promotion"
        }
    }
    
    /**
     * Get piece symbol for descriptive notation
     */
    private fun getPieceSymbol(type: ChessPieceType): String {
        return when (type) {
            ChessPieceType.PAWN -> "P"
            ChessPieceType.KNIGHT -> "N"
            ChessPieceType.BISHOP -> "B"
            ChessPieceType.ROOK -> "R"
            ChessPieceType.QUEEN -> "Q"
            ChessPieceType.KING -> "K"
            else -> ""
        }
    }
    
    /**
     * Get descriptive file name (QR, QN, QB, Q, K, KB, KN, KR)
     * Based on the piece's position relative to the King/Queen side
     */
    private fun getDescriptiveFile(file: Int, isWhite: Boolean): String {
        return when (file) {
            0 -> "QR"  // Queen's Rook file (a-file)
            1 -> "QN"  // Queen's Knight file (b-file)
            2 -> "QB"  // Queen's Bishop file (c-file)
            3 -> "Q"   // Queen file (d-file)
            4 -> "K"   // King file (e-file)
            5 -> "KB"  // King's Bishop file (f-file)
            6 -> "KN"  // King's Knight file (g-file)
            7 -> "KR"  // King's Rook file (h-file)
            else -> ""
        }
    }
    
    /**
     * Get piece type at a square
     */
    private fun getPieceTypeAt(sq: Int, white: Boolean, state: ChessBitboardGameState): ChessPieceType {
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


    /**
     * Converts a move to Standard Algebraic Notation (SAN).
     * e.g. "e4", "Nf3", "axb5", "Rfe1", "O-O"
     */
    fun BitboardChessMove.toSan(state: ChessBitboardGameState): String {
        // 1. Castling
        if (flag == ChessMoveFlag.KING_CASTLE) return "O-O"
        if (flag == ChessMoveFlag.QUEEN_CASTLE) return "O-O-O"

        val isWhite = state.whiteToMove
        val pieceType = getPieceTypeAt(from, isWhite, state)
        
        // 2. Disambiguation
        // Generate all legal moves to see if another piece of same type can move to same square
        var disambiguation = ""
        if (pieceType != ChessPieceType.PAWN) {
            val moves = ChessBitboardMoveGenerator.generateMoves(state)
            val others = moves.filter { 
                it.to == this.to && 
                it.from != this.from && 
                getPieceTypeAt(it.from, isWhite, state) == pieceType 
            }
            
            if (others.isNotEmpty()) {
                val fromFile = from % 8
                val fromRank = from / 8
                
                // Check if file is unique
                val fileUnique = others.none { (it.from % 8) == fromFile }
                val rankUnique = others.none { (it.from / 8) == fromRank }
                
                if (fileUnique) {
                    disambiguation = getFileChar(fromFile).toString()
                } else if (rankUnique) {
                    disambiguation = (fromRank + 1).toString()
                } else {
                    disambiguation = getFileChar(fromFile).toString() + (fromRank + 1).toString()
                }
            }
        }

        // 3. Components
        val piecePrefix = if (pieceType == ChessPieceType.PAWN) "" else getPieceSymbol(pieceType)
        val captureMarker = if (isCapture) "x" else ""
        
        val toFile = to % 8
        val toRank = to / 8
        val targetSquare = "${getFileChar(toFile)}${toRank + 1}"
        
        // Pawn Capture: must include file
        var pawnPrefix = ""
        if (pieceType == ChessPieceType.PAWN && isCapture) {
            pawnPrefix = getFileChar(from % 8).toString()
        }
        
        // Promotion
        val promotion = if (isPromotion) {
            val type = when (flag) {
                ChessMoveFlag.PROMO_QUEEN, ChessMoveFlag.PROMO_QUEEN_CAPTURE -> "Q"
                ChessMoveFlag.PROMO_ROOK, ChessMoveFlag.PROMO_ROOK_CAPTURE -> "R"
                ChessMoveFlag.PROMO_BISHOP, ChessMoveFlag.PROMO_BISHOP_CAPTURE -> "B"
                ChessMoveFlag.PROMO_KNIGHT, ChessMoveFlag.PROMO_KNIGHT_CAPTURE -> "N"
                else -> ""
            }
            "=$type"
        } else ""

        // Check/Mate is appended by caller usually, but we can check next state?
        // Let's leave Check/Mate to the high level logic or check here?
        // Standard SAN includes it.
        // But checking next state here might be expensive if called in loop?
        // Ideally we should.
        // We will skip +/# for now, logic in model handles appending it if feasible, 
        // OR we just return the move string and Model appends result at end of game?
        // Standard PGN moves usually have +/#.
        // We will skip here to avoid infinite recursion or heavy calc (since this might be used in move gen loops).
        // Actually, for PGN generation, we can afford it.
        
        return "$pawnPrefix$piecePrefix$disambiguation$captureMarker$targetSquare$promotion"
    }

    private fun getFileChar(file: Int): Char {
        return ('a'.code + file).toChar()
    }
}
