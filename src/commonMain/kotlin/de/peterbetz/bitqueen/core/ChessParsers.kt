package de.peterbetz.bitqueen.core

object FenParser {
    fun parse(fen: String): ChessBitboardGameState {
        val parts = fen.trim().split(" ")
        val state = ChessBitboardGameState()
        
        // 1. Piece Placement
        if (parts.isNotEmpty()) {
            val rows = parts[0].split("/")
            if (rows.size == 8) {
                // Clear default board
                state.wP = ChessBitboard(0UL); state.wN = ChessBitboard(0UL); state.wB = ChessBitboard(0UL)
                state.wR = ChessBitboard(0UL); state.wQ = ChessBitboard(0UL); state.wK = ChessBitboard(0UL)
                state.bP = ChessBitboard(0UL); state.bN = ChessBitboard(0UL); state.bB = ChessBitboard(0UL)
                state.bR = ChessBitboard(0UL); state.bQ = ChessBitboard(0UL); state.bK = ChessBitboard(0UL)
                
                for (rank in 0..7) {
                    val rowString = rows[rank] // rank 0 in FEN is actually Rank 8 on board? Yes, FEN starts rank 8.
                    // Internal board: 0..7 is Rank 1, 56..63 is Rank 8? 
                    // Let's verify standard square mapping. usually sq = rank * 8 + file.
                    // If rank 0 is bottom (White), then FEN rank 8 is top.
                    // FEN string "rnbqkbnr" is Rank 8.
                    // So FEN row 0 maps to internal Rank 7 (indices 56-63).
                    
                    val internalRank = 7 - rank
                    var file = 0
                    for (char in rowString) {
                        if (char.isDigit()) {
                            file += char.digitToInt()
                        } else {
                            val sq = internalRank * 8 + file
                            when (char) {
                                'P' -> state.wP = state.wP.withBitSet(sq)
                                'N' -> state.wN = state.wN.withBitSet(sq)
                                'B' -> state.wB = state.wB.withBitSet(sq)
                                'R' -> state.wR = state.wR.withBitSet(sq)
                                'Q' -> state.wQ = state.wQ.withBitSet(sq)
                                'K' -> state.wK = state.wK.withBitSet(sq)
                                'p' -> state.bP = state.bP.withBitSet(sq)
                                'n' -> state.bN = state.bN.withBitSet(sq)
                                'b' -> state.bB = state.bB.withBitSet(sq)
                                'r' -> state.bR = state.bR.withBitSet(sq)
                                'q' -> state.bQ = state.bQ.withBitSet(sq)
                                'k' -> state.bK = state.bK.withBitSet(sq)
                            }
                            file++
                        }
                    }
                }
            }
        }
        
        // 2. Side to Move
        if (parts.size > 1) {
            state.whiteToMove = parts[1] == "w"
        }
        
        // 3. Castling Rights
        if (parts.size > 2) {
            val castling = parts[2]
            var rights = 0
            if (castling.contains("K")) rights = rights or 1
            if (castling.contains("Q")) rights = rights or 2
            if (castling.contains("k")) rights = rights or 4
            if (castling.contains("q")) rights = rights or 8
            state.castlingRights = rights
        }
        
        // 4. En Passant
        if (parts.size > 3) {
            val ep = parts[3]
            state.enPassantTarget = if (ep == "-") null else {
                if (ep.length == 2) {
                    val f = ep[0] - 'a'
                    val r = ep[1].digitToInt() - 1 // 1-based to 0-based
                    r * 8 + f
                } else null
            }
        }
        
        // 5. Halfmove Clock (skip for now, or store in state if it has field)
        // 6. Fullmove Number (skip)
        
        state.recomputeHash()
        return state
    }
    
    fun toFen(state: ChessBitboardGameState): String {
        val builder = StringBuilder()
        
        // 1. Board
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val sq = rank * 8 + file
                val piece = getPieceChar(state, sq)
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        builder.append(empty)
                        empty = 0
                    }
                    builder.append(piece)
                }
            }
            if (empty > 0) builder.append(empty)
            if (rank > 0) builder.append("/")
        }
        
        // 2. Side
        builder.append(" ").append(if (state.whiteToMove) "w" else "b")
        
        // 3. Castling
        builder.append(" ")
        var c = ""
        if ((state.castlingRights and 1) != 0) c += "K"
        if ((state.castlingRights and 2) != 0) c += "Q"
        if ((state.castlingRights and 4) != 0) c += "k"
        if ((state.castlingRights and 8) != 0) c += "q"
        if (c.isEmpty()) c = "-"
        builder.append(c)
        
        // 4. En Passant
        builder.append(" ")
        val ep = state.enPassantTarget
        if (ep != null) {
            val file = (ep % 8)
            val rank = (ep / 8)
            builder.append(('a'.code + file).toChar())
            builder.append(rank + 1)
        } else {
            builder.append("-")
        }
        
        // 5. Clocks (Dummy values for now)
        builder.append(" 0 1")
        
        return builder.toString()
    }
    
    private fun getPieceChar(state: ChessBitboardGameState, sq: Int): Char? {
        if (state.wP.isSet(sq)) return 'P'
        if (state.wN.isSet(sq)) return 'N'
        if (state.wB.isSet(sq)) return 'B'
        if (state.wR.isSet(sq)) return 'R'
        if (state.wQ.isSet(sq)) return 'Q'
        if (state.wK.isSet(sq)) return 'K'
        if (state.bP.isSet(sq)) return 'p'
        if (state.bN.isSet(sq)) return 'n'
        if (state.bB.isSet(sq)) return 'b'
        if (state.bR.isSet(sq)) return 'r'
        if (state.bQ.isSet(sq)) return 'q'
        if (state.bK.isSet(sq)) return 'k'
        return null
    }
}

object PgnParser {
    
    data class ParsedGame(
        val tags: Map<String, String>,
        val moves: List<String> // SAN moves
    )
    
    fun parsePgn(pgn: String): ParsedGame {
        val tags = mutableMapOf<String, String>()
        val moveTokens = mutableListOf<String>()
        
        // Simple regex-based parsing
        // Tags: [Key "Value"]
        val tagRegex = Regex("\\[(\\w+)\\s+\"([^\"]*)\"\\]")
        tagRegex.findAll(pgn).forEach { match ->
            tags[match.groupValues[1]] = match.groupValues[2]
        }
        
        // Remove comments {} and ()
        var cleanPgn = pgn.replace(Regex("\\{[^}]*\\}"), "") // Remove {} comments
        cleanPgn = cleanPgn.replace(Regex("\\([^)]*\\)"), "") // Remove () variations
        cleanPgn = cleanPgn.replace(Regex("\\[[^\\]]*\\]"), "") // Remove tags
        
        // Extract moves
        val tokens = cleanPgn.split(Regex("\\s+"))
        for (token in tokens) {
            if (token.isBlank()) continue
            if (token.endsWith(".")) continue // Move numbers like "1."
            if (token.matches(Regex("\\d+\\.\\.?\\.?"))) continue // "1..." or "1."
            
            // Basic filtering for result
            if (token == "1-0" || token == "0-1" || token == "1/2-1/2" || token == "*") break
            
            moveTokens.add(token)
        }
        
        return ParsedGame(tags, moveTokens)
    }
    
    // Convert SAN to Move. Requires current state and MoveGenerator
    fun sanToMove(san: String, state: ChessBitboardGameState): BitboardChessMove? {
        val moves = ChessBitboardMoveGenerator.generateMoves(state)
        
        // Clean SAN (remove check/mate checks +, #)
        val cleanSan = san.replace("+", "").replace("#", "")
        
        // Castling
        if (cleanSan == "O-O" || cleanSan == "0-0") {
             return moves.firstOrNull { 
                 it.flag == ChessMoveFlag.KING_CASTLE 
             }
        }
        if (cleanSan == "O-O-O" || cleanSan == "0-0-0") {
             return moves.firstOrNull { 
                 it.flag == ChessMoveFlag.QUEEN_CASTLE 
             }
        }
        
        // Basic SAN parsing
        // Pattern: [Piece][fromFile/Rank?][x?][TargetFile][TargetRank][Promotion?]
        
        // 1. Identify Target Square
        if (cleanSan.length < 2) return null
        
        val lastChar = cleanSan.last()
        var promotion: Char? = null
        var targetStr = ""
        
        // Check promotion e.g. e8=Q
        if (cleanSan.contains("=")) {
             val parts = cleanSan.split("=")
             if (parts.size == 2) {
                 promotion = parts[1].first()
                 targetStr = parts[0].takeLast(2)
             }
        } else if (cleanSan.last().isUpperCase() && !cleanSan.last().isDigit() && cleanSan.last() != 'O') {
             // Rare notation e8Q
             promotion = lastChar
             targetStr = cleanSan.substring(cleanSan.length - 3, cleanSan.length - 1)
        } else {
            targetStr = cleanSan.takeLast(2)
        }
        
        // Validate target
        if (targetStr.length != 2) return null
        val targetFile = targetStr[0] - 'a'
        val targetRank = targetStr[1].digitToInt() - 1
        val toSq = targetRank * 8 + targetFile
        if (toSq !in 0..63) return null
        
        // 2. Identify Piece Type
        val firstChar = cleanSan.first()
        val isPawn = firstChar in 'a'..'h'
        val pieceType = if (isPawn) ChessPieceType.PAWN else when(firstChar) {
            'N' -> ChessPieceType.KNIGHT
            'B' -> ChessPieceType.BISHOP
            'R' -> ChessPieceType.ROOK
            'Q' -> ChessPieceType.QUEEN
            'K' -> ChessPieceType.KING
            else -> ChessPieceType.PAWN // Fallback
        }
        
        // 3. Disambiguation
        // Remaining chars between Piece (or empty) and Target (and capture 'x')
        // Examples: Nbd7 (Piece:N, b is disambig), R1e2 (Piece:R, 1 is disambig)
        // exd5 (Pawn, e is disambig, x capture)
        var disambig = cleanSan.dropLast(targetStr.length + (if (promotion != null) 2 else 0)) // remove target + promo
        if (!isPawn) disambig = disambig.drop(1) // remove piece char
        disambig = disambig.replace("x", "") // remove capture
        
        // Filter moves that match PieceType, ToSq
        var candidates = moves.filter { move ->
             move.to == toSq && getPieceType(state, move.from) == pieceType 
        }
        
        // Filter by disambiguation
        if (disambig.isNotEmpty()) {
            candidates = candidates.filter { move ->
                val fromFile = move.from % 8
                val fromRank = move.from / 8
                
                var match = true
                for (char in disambig) {
                    if (char in 'a'..'h') {
                        if (fromFile != (char - 'a')) match = false
                    } else if (char.isDigit()) {
                        if (fromRank != (char.digitToInt() - 1)) match = false
                    }
                }
                match
            }
        }
        
        // Filter by Promotion
        if (promotion != null) {
            candidates = candidates.filter { move ->
                 move.isPromotion && when(move.flag) {
                     ChessMoveFlag.PROMO_QUEEN, ChessMoveFlag.PROMO_QUEEN_CAPTURE -> promotion == 'Q'
                     ChessMoveFlag.PROMO_ROOK, ChessMoveFlag.PROMO_ROOK_CAPTURE -> promotion == 'R'
                     ChessMoveFlag.PROMO_BISHOP, ChessMoveFlag.PROMO_BISHOP_CAPTURE -> promotion == 'B'
                     ChessMoveFlag.PROMO_KNIGHT, ChessMoveFlag.PROMO_KNIGHT_CAPTURE -> promotion == 'N'
                     else -> false
                 }
            }
        } else {
             // ensure not a promotion move if unstated (usually defaults to Queen, but strictly SAN should specify)
             // Actually, if it is a promotion square, moves will be generated as promotions.
             // If SAN didn't specify, standard PGN usually implies Queen, OR user interface logic handles it.
             // PGN strict says "=" is required.
             // However, for parsing robustness, if we are promoting and no char, maybe assume Queen?
             // But candidates will be explicitly PROMO flags.
             // If san doesn't have "=", we should pick the move that isn't a promo if possible? 
             // Impossible for Pawn to 8th rank to not be promo.
             // If san is "e8", that is invalid SAN. Should be "e8=Q".
             // We stick to strict: if candidates are promotion moves but SAN has no promo char, we fail/return first/Queen?
             // Let's assume Queen if ambiguous.
             if (candidates.any { it.isPromotion }) {
                 candidates = candidates.filter { 
                     it.flag == ChessMoveFlag.PROMO_QUEEN || it.flag == ChessMoveFlag.PROMO_QUEEN_CAPTURE 
                 }
             }
        }
        
        return candidates.firstOrNull()
    }
    
    private fun getPieceType(state: ChessBitboardGameState, sq: Int): ChessPieceType {
        if (state.wP.isSet(sq) || state.bP.isSet(sq)) return ChessPieceType.PAWN
        if (state.wN.isSet(sq) || state.bN.isSet(sq)) return ChessPieceType.KNIGHT
        if (state.wB.isSet(sq) || state.bB.isSet(sq)) return ChessPieceType.BISHOP
        if (state.wR.isSet(sq) || state.bR.isSet(sq)) return ChessPieceType.ROOK
        if (state.wQ.isSet(sq) || state.bQ.isSet(sq)) return ChessPieceType.QUEEN
        if (state.wK.isSet(sq) || state.bK.isSet(sq)) return ChessPieceType.KING
        return ChessPieceType.NONE
    }
}
