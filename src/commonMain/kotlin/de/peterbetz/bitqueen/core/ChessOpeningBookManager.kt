package de.peterbetz.bitqueen.core

/**
 * Manages the opening book.
 * Port from Swift: ChessOpeningBookManager
 */
class ChessOpeningBookManager {
    private val book: MutableMap<String, List<String>> = mutableMapOf()
    private val openingNames: MutableMap<String, String> = mutableMapOf()

    init {
        loadHardcodedBook()
    }

    /**
     * Returns a random book move for the current history.
     * History and Book use "Long Algebraic Notation" (e.g. "e2e4").
     */
    fun getBookMove(moveHistory: List<String>, isWhite: Boolean): String? {
        // 1. Direct Search
        val rawKey = moveHistory.joinToString(" ")
        val candidates = book[rawKey]
        if (candidates != null && candidates.isNotEmpty()) {
            val move = candidates.random()
            if (isMovePlausible(move, isWhite)) return move
        }

        // 2. Mirrored Search (If Black starts or color swap)
        val mirroredHistory = moveHistory.map { mirrorLAN(it) }
        val mirroredKey = mirroredHistory.joinToString(" ")
        val mirroredCandidates = book[mirroredKey]
        
        if (mirroredCandidates != null && mirroredCandidates.isNotEmpty()) {
             val move = mirroredCandidates.random()
             val mirroredMove = mirrorLAN(move)
             if (isMovePlausible(mirroredMove, isWhite)) return mirroredMove
        }

        return null
    }

    fun getOpeningName(moveHistory: List<String>): String? {
        val key = moveHistory.joinToString(" ")
        return openingNames[key]
    }

    private fun isMovePlausible(lan: String, isWhite: Boolean): Boolean {
        if (lan.length < 2) return false
        val rankChar = lan[1]
        val rank = rankChar.digitToIntOrNull() ?: return false
        
        return if (isWhite) {
            rank < 7 // White usually starts from 1,2
        } else {
            rank > 2 // Black usually starts from 7,8
        }
    }

    private fun mirrorLAN(lan: String): String {
         if (lan.length < 4) return lan
         val chars = lan.toCharArray()
         
         fun invertRank(c: Char): Char {
             val d = c.digitToIntOrNull() ?: return c
             return (9 - d).digitToChar()
         }
         
         chars[1] = invertRank(chars[1])
         chars[3] = invertRank(chars[3])
         return chars.concatToString()
    }

    private fun addMoves(key: String, moves: List<String>, name: String? = null) {
        book[key] = moves
        if (name != null) {
            openingNames[key] = name
        }
    }

    private fun loadHardcodedBook() {
        // Start
        addMoves("", listOf("e2e4", "d2d4", "g1f3", "c2c4", "b1c3"), "opening_chess_opening")

        // --- 1. e4 (King's Pawn) ---
        addMoves("e2e4", listOf("e7e5", "c7c5", "e7e6", "c7c6", "d7d6", "d7d5", "g8f6", "g7g6"), "opening_kings_pawn")

        // 1. e4 e5 (Open Game)
        addMoves("e2e4 e7e5", listOf("g1f3", "f1c4", "b1c3", "d2d4", "f2f4"), "opening_open_game")
        addMoves("e2e4 e7e5 g1f3", listOf("b8c6", "g8f6", "d7d6", "f7f5"), "opening_kings_knight")

        // Spanish (Ruy Lopez)
        addMoves("e2e4 e7e5 g1f3 b8c6", listOf("f1b5", "f1c4", "d2d4", "b1c3"), "opening_three_knights_italian_spanish")
        addMoves("e2e4 e7e5 g1f3 b8c6 f1b5", listOf("a7a6", "g8f6", "d7d6", "f7f5"), "opening_ruy_lopez")
        addMoves("e2e4 e7e5 g1f3 b8c6 f1b5 a7a6", listOf("b5a4", "b5xc6"), "opening_ruy_lopez_morphy")

        // Italian / Two Knights
        addMoves("e2e4 e7e5 g1f3 b8c6 f1c4", listOf("f8c5", "g8f6"), "opening_italian_game")
        addMoves("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5", listOf("c2c3", "d2d3", "b2b4"), "opening_giuoco_piano")

        // Sicilian Defense
        addMoves("e2e4 c7c5", listOf("g1f3", "b1c3", "c2c3", "d2d4", "f2f4"), "opening_sicilian_defense")
        addMoves("e2e4 c7c5 g1f3", listOf("d7d6", "b8c6", "e7e6", "g7g6", "a7a6"))
        addMoves("e2e4 c7c5 g1f3 d7d6", listOf("d2d4", "f1b5"), "opening_sicilian_moscow")

        // Scandinavian Defense
        addMoves("e2e4 d7d5", listOf("e4xd5"), "opening_scandinavian_defense")
        addMoves("e2e4 d7d5 e4xd5", listOf("d8xd5", "g8f6"))

        // Alekhine's Defense
        addMoves("e2e4 g8f6", listOf("e4e5"), "opening_alekhine_defense")
        addMoves("e2e4 g8f6 e4e5", listOf("f6d5"))

        // French & Caro-Kann
        addMoves("e2e4 e7e6", listOf("d2d4"), "opening_french_defense")
        addMoves("e2e4 e7e6 d2d4", listOf("d7d5"))
        addMoves("e2e4 c7c6", listOf("d2d4"), "opening_caro_kann_defense")
        addMoves("e2e4 c7c6 d2d4", listOf("d7d5"))

        // Pirc / Modern Defense
        addMoves("e2e4 d7d6", listOf("d2d4"), "opening_pirc_defense")
        addMoves("e2e4 g7g6", listOf("d2d4"), "opening_modern_defense")

        // --- 1. d4 (Queen's Pawn) ---
        addMoves("d2d4", listOf("d7d5", "g8f6", "f7f5", "e7e6", "d7d6", "g7g6"), "opening_queens_pawn")

        // Queen's Gambit
        addMoves("d2d4 d7d5", listOf("c2c4", "g1f3", "c1f4"), "opening_closed_game")
        addMoves("d2d4 d7d5 c2c4", listOf("e7e6", "c7c6", "d5c4", "e7e5"), "opening_queens_gambit")
        addMoves("d2d4 d7d5 c2c4 e7e6", listOf("b1c3", "g1f3"), "opening_queens_gambit_declined")

        // London System
        addMoves("d2d4 d7d5 g1f3", listOf("g8f6"))
        addMoves("d2d4 d7d5 g1f3 g8f6 c1f4", listOf("c7c5", "e7e6"), "opening_london_system")
        addMoves("d2d4 g8f6 g1f3 e7e6 c1f4", listOf("d7d5", "c7c5"), "opening_london_system")

        // King's Indian / Grünfeld
        addMoves("d2d4 g8f6", listOf("c2c4", "g1f3", "c1g5"), "opening_indian_defense")
        addMoves("d2d4 g8f6 c2c4", listOf("g7g6", "e7e6", "c7c5"), "opening_indian_defense")
        addMoves("d2d4 g8f6 c2c4 g7g6", listOf("b1c3", "g1f3"), "opening_kings_indian_grunfeld")
        addMoves("d2d4 g8f6 c2c4 g7g6 b1c3", listOf("d7d5", "f8g7"), "opening_grunfeld_kings_indian")

        // Nimzo-Indian / Queen's Indian
        addMoves("d2d4 g8f6 c2c4 e7e6", listOf("b1c3", "g1f3", "g2g3"))
        addMoves("d2d4 g8f6 c2c4 e7e6 b1c3", listOf("f8b4"), "opening_nimzo_indian_defense")
        addMoves("d2d4 g8f6 c2c4 e7e6 g1f3", listOf("b6b6", "d7d5", "c7c5"), "opening_queens_indian_bogo_indian")

        // Dutch Defense
        addMoves("d2d4 f7f5", listOf("c2c4", "g1f3", "g2g3"), "opening_dutch_defense")

        // --- Others ---
        addMoves("c2c4", listOf("e7e5", "c7c5", "g8f6", "e7e6"), "opening_english_opening")
        addMoves("g1f3", listOf("d7d5", "g8f6", "c7c5"), "opening_reti_opening")
        addMoves("b1c3", listOf("d7d5", "e7e5", "g8f6"), "opening_van_geet_opening")
    }
}
