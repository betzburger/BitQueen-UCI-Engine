 package de.peterbetz.bitqueen.core
 
 import kotlin.math.abs
 import kotlin.math.max
 import kotlin.math.min
 
 /**
  * Static Evaluation Function using Tapered Evaluation and Peesto Tables.
  * Port from Swift: ChessEvaluation
  */
 object ChessEvaluation {

    // MARK: - Constants

    // Phase Calculation Weights
    private val phaseInc = intArrayOf(0, 1, 1, 2, 4, 0)
    private const val totalPhase = 24

    // Piece Values (Middlegame, Endgame) - Peesto style
    private val pieceValMG = intArrayOf(82, 337, 365, 477, 1025, 0)
    private val pieceValEG = intArrayOf(94, 281, 297, 512, 936, 0)

    // Safety Table
    private val safetyTable = intArrayOf(
        0, 0, 0, 2, 5, 8, 12, 18, 25, 35,
        45, 55, 65, 75, 85, 95, 105, 115, 125, 135,
        145, 155, 165, 175, 185, 195, 205, 215, 225, 235,
        245, 255, 265, 275, 285, 295, 300, 300, 300, 300
    )

    // MARK: - Precomputed Masks
    private val fileMasks = ULongArray(8) { f ->
        var m: ULong = 0uL
        for (r in 0..7) m = m or (1uL shl (r * 8 + f))
        m
    }

    private val rankMasks = ULongArray(8) { r ->
        var m: ULong = 0uL
        for (f in 0..7) m = m or (1uL shl (r * 8 + f))
        m
    }

    private val adjacentFiles = ULongArray(8) { f ->
        var m: ULong = 0uL
        if (f > 0) m = m or fileMasks[f - 1]
        if (f < 7) m = m or fileMasks[f + 1]
        m
    }

    private val whitePassedMasks = ULongArray(64) { sq ->
        val r = sq / 8
        val f = sq % 8
        var m: ULong = 0uL
        for (nr in (r + 1)..7) {
            m = m or (1uL shl (nr * 8 + f))
            if (f > 0) m = m or (1uL shl (nr * 8 + f - 1))
            if (f < 7) m = m or (1uL shl (nr * 8 + f + 1))
        }
        m
    }

    private val blackPassedMasks = ULongArray(64) { sq ->
        val r = sq / 8
        val f = sq % 8
        var m: ULong = 0uL
        for (nr in 0 until r) {
            m = m or (1uL shl (nr * 8 + f))
            if (f > 0) m = m or (1uL shl (nr * 8 + f - 1))
            if (f < 7) m = m or (1uL shl (nr * 8 + f + 1))
        }
        m
    }

    // MARK: - Piece-Square Tables (Peesto)
    private val pawnsMG = intArrayOf(
         0,   0,   0,   0,   0,   0,   0,   0,
        98, 134,  61,  95,  68, 126,  34, -11,
        -6,   7,  26,  31,  65,  56,  25, -20,
       -14,  13,   6,  21,  23,  12,  17, -23,
       -27,  -2,  -5,  12,  17,   6,  10, -25,
       -26,  -4,  -4, -10,   3,   3,  33, -12,
       -35,  -1, -20, -23, -15,  24,  38, -22,
         0,   0,   0,   0,   0,   0,   0,   0
    )
    private val pawnsEG = intArrayOf(
         0,   0,   0,   0,   0,   0,   0,   0,
       178, 173, 158, 134, 147, 132, 165, 187,
        94, 100,  85,  67,  56,  53,  82,  84,
        32,  24,  13,   5,  -2,   4,  17,  17,
        13,   9,  -3,  -7,  -7,  -8,   3,  -1,
         4,   7,  -6,   1,   0,  -5,  -1,  -8,
        13,   8,   8,  10,  13,   0,   2,  -7,
         0,   0,   0,   0,   0,   0,   0,   0
    )
    private val knightsMG = intArrayOf(
       -167, -89, -34, -49,  61, -97, -15, -107,
        -73, -41,  72,  36,  23,  62,   7,  -17,
        -47,  60,  37,  65,  84, 129,  73,   44,
         -9,  17,  19,  53,  37,  69,  18,   22,
        -13,   4,  16,  13,  28,  19,  21,   -8,
        -23,  -9,  12,  10,  19,  17,  25,  -16,
        -29, -53, -12,  -3,  -1,  18, -14,  -19,
       -105, -21, -58, -33, -17, -28, -19,  -23
    )
    private val knightsEG = intArrayOf(
        -58, -38, -13, -28, -31, -27, -63, -99,
        -25,  -8, -25,  -2,  -9, -25, -24, -52,
        -24, -20,  10,   9,  -1,  -9, -19, -41,
        -17,   3,  22,  22,  22,  11,   8, -18,
        -18,  -6,  16,  25,  16,  17,   4, -18,
        -23,  -3,  -1,  15,  10,  -3, -20, -22,
        -42, -20, -10,  -5,  -2, -20, -23, -44,
        -29, -51, -23, -15, -22, -18, -50, -64
    )
    private val bishopsMG = intArrayOf(
        -29,   4, -82, -37, -25, -42,   7,  -8,
        -26,  16, -18, -13,  30,  59,  18, -47,
        -16,  37,  43,  40,  35,  50,  37,  -2,
         -4,   5,  19,  50,  37,  37,   7,  -2,
         -6,  13,  13,  26,  34,  12,  10,   4,
          0,  15,  15,  15,  14,  27,  18,  10,
          4,  15,  16,   0,   7,  21,  33,   1,
        -33,  -3, -14, -21, -13, -12, -39, -21
    )
    private val bishopsEG = intArrayOf(
        -14, -21, -11,  -8, -7,  -9, -17, -24,
         -8,  -4,   7, -12, -3, -13,  -4, -14,
          2,  -8,   0,  -1, -2,   6,   0,   4,
         -3,   9,  12,   9, 14,  10,   3,   2,
         -6,   3,  13,  19,  7,  10,  -3,  -9,
        -12,  -3,   8,  10, 13,   3,  -7, -15,
        -14, -18,  -7,  -1,  4,  -9, -15, -27,
        -23,  -9, -23,  -5, -9, -16,  -5, -17
    )
    private val rooksMG = intArrayOf(
         32,  42,  32,  51, 63,  9,  31,  43,
         27,  32,  58,  62, 80, 67,  26,  44,
         -5,  19,  26,  36, 17, 45,  61,  16,
        -24, -11,   7,  26, 24, 35,  -8, -20,
        -36, -26, -12,  -1,  9, -7,   6, -23,
        -45, -25, -16, -17,  3,  0,  -5, -33,
        -44, -16, -20,  -9, -1, 11,  -6, -71,
        -19, -13,   1,  17, 16,  7, -37, -26
    )
    private val rooksEG = intArrayOf(
        13, 10, 18, 15, 12,  12,   8,   5,
        11, 13, 13, 11, -3,   3,   8,   3,
         7,  7,  7,  5,  4,  -3,  -5,  -3,
         4,  3, 13,  1,  2,   1,  -1,   2,
         3,  5,  8,  4, -5,  -6,  -8, -11,
        -4,  0, -5, -1, -7, -12,  -8, -16,
        -6, -6,  0,  2, -9,  -9, -11,  -3,
        -9,  2,  3, -1, -5, -13,   4, -20
    )
    private val queensMG = intArrayOf(
        -28,   0,  29,  12,  59,  44,  43,  45,
        -24, -39,  -5,   1, -16,  57,  28,  54,
        -13, -17,   7,   8,  29,  56,  47,  57,
        -27, -27, -16, -16,  -1,  17,  -2,   1,
         -9, -26, -9, -10,  -2,  -4,   3,  -3,
        -14,   2, -11,  -2,  -5,   2,  14,   5,
        -35,  -8,  11,   2,   8,  15,  -3,   1,
         -1, -18,  -9, -10, -30, -35, -20, -54
    )
    private val queensEG = intArrayOf(
         -9,  22,  22,  27,  27,  19,  10,  20,
        -17,  20,  32,  41,  58,  25,  30,   0,
        -20,   6,   9,  49,  47,  35,  19,   9,
          3,  22,  24,  45,  57,  40,  57,  36,
        -18,  28,  19,  47,  31,  34,  39,  23,
        -16, -27,  15,   6,   9,  17,  10,   5,
        -22, -23, -30, -16, -16, -23, -36, -32,
        -33, -28, -22, -43,  -5, -32, -20, -41
    )
    private val kingsMG = intArrayOf(
        -65,  23,  16, -15, -56, -34,   2,  13,
         29,  -1, -20,  -7,  -8,  -4, -38, -29,
         -9,  24,   2, -16, -20,   6,  22, -22,
        -17, -20, -12, -27, -30, -25, -14, -36,
        -49,  -1, -27, -39, -46, -44, -33, -51,
        -14, -14, -22, -46, -44, -30, -15, -27,
          1,   7,  -8, -64, -43, -16,   9,   8,
        -15,  36,  12, -54,   8, -28,  24,  14
    )
    private val kingsEG = intArrayOf(
        -74, -35, -18, -18, -11,  15,   4, -17,
        -12,  17,  14,  17,  17,  38,  23,  11,
         10,  17,  23,  15,  20,  45,  44,  13,
         -8,  22,  24,  27,  26,  33,  26,   3,
        -18,  -4,  21,  24,  27,  23,   9, -11,
        -19,  -3,  11,  21,  23,  16,   7,  -9,
        -27, -11,   4,  13,  14,   4,  -5, -17,
        -53, -34, -21, -11, -28, -14, -24, -43
    )

    private val mgTables = arrayOf(pawnsMG, knightsMG, bishopsMG, rooksMG, queensMG, kingsMG)
    private val egTables = arrayOf(pawnsEG, knightsEG, bishopsEG, rooksEG, queensEG, kingsEG)

    data class Score(var mg: Int = 0, var eg: Int = 0, var phase: Int = 0)

    // Chess 4.6 MBTAB: detect known drawn endgames
    private fun isInsufficientMaterial(state: ChessBitboardGameState): Boolean {
        // If any pawns, rooks, or queens exist, material is sufficient
        if (state.wP.rawValue != 0uL || state.bP.rawValue != 0uL) return false
        if (state.wR.rawValue != 0uL || state.bR.rawValue != 0uL) return false
        if (state.wQ.rawValue != 0uL || state.bQ.rawValue != 0uL) return false

        val wN = state.wN.popCount; val wB = state.wB.popCount
        val bN = state.bN.popCount; val bB = state.bB.popCount
        val wMinor = wN + wB; val bMinor = bN + bB

        // K vs K
        if (wMinor == 0 && bMinor == 0) return true
        // K+B vs K or K+N vs K
        if (wMinor == 0 && bMinor == 1) return true
        if (wMinor == 1 && bMinor == 0) return true
        // K+N+N vs K (cannot force mate)
        if (wN == 2 && wB == 0 && bMinor == 0) return true
        if (bN == 2 && bB == 0 && wMinor == 0) return true
        // K+B vs K+B same colored bishops
        if (wB == 1 && bB == 1 && wN == 0 && bN == 0) {
            val wBSq = state.wB.lsbIndex ?: return false
            val bBSq = state.bB.lsbIndex ?: return false
            val wBColor = (wBSq / 8 + wBSq % 8) % 2
            val bBColor = (bBSq / 8 + bBSq % 8) % 2
            if (wBColor == bBColor) return true
        }
        return false
    }

    // Chess 4.6: scale score toward draw when winning side has barely enough material
    private fun scaleFactor(state: ChessBitboardGameState, rawScore: Int): Int {
        if (rawScore == 0) return rawScore

        val dominated = rawScore > 0 // white winning
        val wP = state.wP.popCount; val bP = state.bP.popCount
        val wN = state.wN.popCount; val wB = state.wB.popCount
        val wR = state.wR.popCount; val wQ = state.wQ.popCount
        val bN = state.bN.popCount; val bB = state.bB.popCount
        val bR = state.bR.popCount; val bQ = state.bQ.popCount

        // Winning side has no pawns and only minor piece advantage: scale down
        if (dominated) {
            if (wP == 0 && wQ == 0 && wR == 0 && (wN + wB) <= 1 && bQ == 0 && bR == 0) {
                return rawScore / 4 // very hard to win with lone minor vs minors
            }
        } else {
            if (bP == 0 && bQ == 0 && bR == 0 && (bN + bB) <= 1 && wQ == 0 && wR == 0) {
                return rawScore / 4
            }
        }
        return rawScore
    }

    fun evaluate(state: ChessBitboardGameState, contempt: Int = 0): Int {
        // Chess 4.6: known drawn endgames return draw score immediately
        if (isInsufficientMaterial(state)) return 0

        val score = Score()
        val moveGen = ChessBitboardMoveGenerator

        evaluatePieces(state.wP, 0, true, score)
        evaluatePieces(state.wN, 1, true, score)
        evaluatePieces(state.wB, 2, true, score)
        evaluatePieces(state.wR, 3, true, score)
        evaluatePieces(state.wQ, 4, true, score)
        evaluatePieces(state.wK, 5, true, score)

        evaluatePieces(state.bP, 0, false, score)
        evaluatePieces(state.bN, 1, false, score)
        evaluatePieces(state.bB, 2, false, score)
        evaluatePieces(state.bR, 3, false, score)
        evaluatePieces(state.bQ, 4, false, score)
        evaluatePieces(state.bK, 5, false, score)

        evaluatePawnStructure(state, score)
        evaluateRooks(state, score)

        evaluateKingSafety(state, moveGen, true, score)
        evaluateKingSafety(state, moveGen, false, score)

        evaluateStrategicTerms(state, moveGen, score)
        evaluateTropism(state, score)
        evaluateMobility(state, moveGen, score)
        evaluateTradeDown(state, score)
        evaluateMopUp(state, score)

        val phase = min(score.phase, totalPhase)
        var finalScore = ((score.mg * phase) + (score.eg * (totalPhase - phase))) / totalPhase

        // Chess 4.6: scale toward draw when winning side has minimal material
        finalScore = scaleFactor(state, finalScore)

        // Apply contempt (positive for side to move)
        finalScore += if (state.whiteToMove) contempt else -contempt

        val tempo = 10
        return finalScore + (if (state.whiteToMove) tempo else -tempo)
    }

    private fun evaluatePieces(bitboard: ChessBitboard, type: Int, isWhite: Boolean, score: Score) {
        var bb = bitboard
        val mgVal = pieceValMG[type]
        val egVal = pieceValEG[type]
        val mgTable = mgTables[type]
        val egTable = egTables[type]
        val phaseValue = phaseInc[type]

        while (true) {
            val (newBb, sq) = bb.popLSB()
            bb = newBb
            if (sq == null) break

            if (isWhite) {
                score.mg += mgVal
                score.eg += egVal
                score.phase += phaseValue
                val tableIndex = sq!! xor 56
                score.mg += mgTable[tableIndex]
                score.eg += egTable[tableIndex]
            } else {
                score.mg -= mgVal
                score.eg -= egVal
                score.phase += phaseValue
                score.mg -= mgTable[sq!!]
                score.eg -= egTable[sq]
            }
        }
    }

    private fun evaluatePawnStructure(state: ChessBitboardGameState, score: Score) {
        val wP = state.wP
        val bP = state.bP
        val moveGen = ChessBitboardMoveGenerator

        val isolatedPenalty = -15
        val doubledPenalty = -10
        val connectedBonusMG = 15
        val connectedBonusEG = 20
        val passedBonusMG = intArrayOf(0, 5, 10, 20, 35, 60, 100, 0)
        val passedBonusEG = intArrayOf(0, 10, 20, 40, 70, 120, 200, 0)

        // White
        var tempWP = wP
        while (true) {
            val (newP, sq) = tempWP.popLSB()
            tempWP = newP
            if (sq == null) break
            
            val file = sq!! % 8
            val rank = sq / 8

            // Isolated
            if ((wP.rawValue and adjacentFiles[file]) == 0uL) {
                score.mg += isolatedPenalty; score.eg += isolatedPenalty
            }
            // Doubled — only penalize if there's a white pawn BEHIND (lower rank)
            val wPawnsBehind = wP.rawValue and fileMasks[file] and ((1uL shl sq) - 1uL)
            if (wPawnsBehind != 0uL) {
                score.mg += doubledPenalty; score.eg += doubledPenalty
            }
            // Connected
            val isDefended = (moveGen.getPawnAttacks(sq, false).rawValue and wP.rawValue) != 0uL
            if (isDefended) {
                score.mg += connectedBonusMG; score.eg += connectedBonusEG
            }
            // Passed
            if ((bP.rawValue and whitePassedMasks[sq]) == 0uL) {
                var pmg = passedBonusMG[rank]
                var peg = passedBonusEG[rank]
                val stopSq = sq + 8
                if (stopSq in 0..63 && state.blackOccupied.isSet(stopSq)) {
                    pmg = pmg * 7 / 10
                    peg = peg * 7 / 10
                }
                score.mg += pmg; score.eg += peg
            }
        }

        // Black
        var tempBP = bP
        while (true) {
            val (newP, sq) = tempBP.popLSB()
            tempBP = newP
            if (sq == null) break

            val file = sq!! % 8
            val rank = sq / 8

            // Isolated
            if ((bP.rawValue and adjacentFiles[file]) == 0uL) {
                score.mg -= isolatedPenalty; score.eg -= isolatedPenalty
            }
            // Doubled — only penalize if there's a black pawn BEHIND (higher rank)
            val aboveMask = if (sq < 63) (1uL shl (sq + 1)) - 1uL else ULong.MAX_VALUE
            val bPawnsBehind = bP.rawValue and fileMasks[file] and aboveMask.inv()
            if (bPawnsBehind != 0uL) {
                score.mg -= doubledPenalty; score.eg -= doubledPenalty
            }
            // Connected
            val isDefended = (moveGen.getPawnAttacks(sq, true).rawValue and bP.rawValue) != 0uL
            if (isDefended) {
                score.mg -= connectedBonusMG; score.eg -= connectedBonusEG
            }
            // Passed
            if ((wP.rawValue and blackPassedMasks[sq]) == 0uL) {
                val whiteRank = 7 - rank
                var pmg = passedBonusMG[whiteRank]
                var peg = passedBonusEG[whiteRank]
                val stopSq = sq - 8
                if (stopSq in 0..63 && state.whiteOccupied.isSet(stopSq)) {
                    pmg = pmg * 7 / 10
                    peg = peg * 7 / 10
                }
                score.mg -= pmg; score.eg -= peg
            }
        }
    }

    private fun evaluateRooks(state: ChessBitboardGameState, score: Score) {
         val openFileBonusMG = 20
         val openFileBonusEG = 10
         val semiOpenBonusMG = 10
         val semiOpenBonusEG = 5
         // Chess 4.6: rook on 7th rank bonus
         val rookOn7thMG = 25
         val rookOn7thEG = 40
         // Chess 4.6: doubled rooks bonus
         val doubledRooksMG = 15
         val doubledRooksEG = 25

         // Track which files have white/black rooks for doubled detection
         var wRookFiles = 0 // bitmask of files
         var bRookFiles = 0
         var wRookFileDup = 0 // files with >1 rook
         var bRookFileDup = 0

         var wR = state.wR
         while(true) {
             val (newR, sq) = wR.popLSB()
             wR = newR
             if(sq == null) break

             val file = sq!! % 8
             val rank = sq / 8
             val fileMask = fileMasks[file]
             val anyPawn = (state.wP.rawValue or state.bP.rawValue) and fileMask
             if(anyPawn == 0uL) {
                 score.mg += openFileBonusMG; score.eg += openFileBonusEG
             } else {
                 if((state.wP.rawValue and fileMask) == 0uL) {
                     score.mg += semiOpenBonusMG; score.eg += semiOpenBonusEG
                 }
             }
             // Rook on 7th rank (rank index 6 for white)
             if(rank == 6) {
                 score.mg += rookOn7thMG; score.eg += rookOn7thEG
             }
             // Doubled rooks detection
             val fileBit = 1 shl file
             if((wRookFiles and fileBit) != 0) wRookFileDup = wRookFileDup or fileBit
             wRookFiles = wRookFiles or fileBit
         }

         var bR = state.bR
         while(true) {
             val (newR, sq) = bR.popLSB()
             bR = newR
             if(sq == null) break

             val file = sq!! % 8
             val rank = sq / 8
             val fileMask = fileMasks[file]
             val anyPawn = (state.wP.rawValue or state.bP.rawValue) and fileMask
             if(anyPawn == 0uL) {
                 score.mg -= openFileBonusMG; score.eg -= openFileBonusEG
             } else {
                 if((state.bP.rawValue and fileMask) == 0uL) {
                     score.mg -= semiOpenBonusMG; score.eg -= semiOpenBonusEG
                 }
             }
             // Rook on 2nd rank (rank index 1 for black = "7th rank" from black's view)
             if(rank == 1) {
                 score.mg -= rookOn7thMG; score.eg -= rookOn7thEG
             }
             // Doubled rooks detection
             val fileBit = 1 shl file
             if((bRookFiles and fileBit) != 0) bRookFileDup = bRookFileDup or fileBit
             bRookFiles = bRookFiles or fileBit
         }

         // Award doubled rooks bonuses (count set bits in dup mask)
         var wd = wRookFileDup
         while(wd != 0) {
             score.mg += doubledRooksMG; score.eg += doubledRooksEG
             wd = wd and (wd - 1) // clear lowest set bit
         }
         var bd = bRookFileDup
         while(bd != 0) {
             score.mg -= doubledRooksMG; score.eg -= doubledRooksEG
             bd = bd and (bd - 1) // clear lowest set bit
         }
    }

    private fun evaluateKingSafety(state: ChessBitboardGameState, moveGen: ChessBitboardMoveGenerator, isWhite: Boolean, score: Score) {
        val kingBB = if(isWhite) state.wK else state.bK
        val kSq = kingBB.lsbIndex ?: return

        // 1. Pawn Shield
        val pawns = if(isWhite) state.wP else state.bP
        val file = kSq % 8
        val rank = kSq / 8

        if(isWhite && rank <= 2) {
            var penalty = 0
            for(f in max(0, file-1)..min(7, file+1)) {
                val sq1 = (rank + 1) * 8 + f
                val sq2 = (rank + 2) * 8 + f
                val hasPawn = ((pawns.rawValue shr sq1) and 1uL) == 1uL || ((pawns.rawValue shr sq2) and 1uL) == 1uL
                if(!hasPawn) penalty -= 20
            }
            score.mg += penalty
        } else if(!isWhite && rank >= 5) {
            var penalty = 0
            for(f in max(0, file-1)..min(7, file+1)) {
                val sq1 = (rank - 1) * 8 + f
                val sq2 = (rank - 2) * 8 + f
                val hasPawn = ((pawns.rawValue shr sq1) and 1uL) == 1uL || ((pawns.rawValue shr sq2) and 1uL) == 1uL
                if(!hasPawn) penalty -= 20
            }
            score.mg -= penalty // Black penalty -> White Advantage
        }

        // Chess 4.6: open file next to king penalty (FKSOPF)
        for(f in max(0, file-1)..min(7, file+1)) {
            val fMask = fileMasks[f]
            val anyPawn = (state.wP.rawValue or state.bP.rawValue) and fMask
            if(anyPawn == 0uL) {
                // Fully open file adjacent to or on king
                val penalty = if(f == file) 30 else 15
                if(isWhite) score.mg -= penalty else score.mg += penalty
            } else {
                val ownPawns = if(isWhite) state.wP.rawValue else state.bP.rawValue
                if((ownPawns and fMask) == 0uL) {
                    // Semi-open file (no own pawns)
                    val penalty = if(f == file) 15 else 8
                    if(isWhite) score.mg -= penalty else score.mg += penalty
                }
            }
        }

        // 2. Attackers
        // Chess 4.6: skip expensive attack calculation if enemy has no queen (FKSQBN)
        val enemyQueens = if(isWhite) state.bQ else state.wQ
        val enemyPieces = if(isWhite) state.blackOccupied else state.whiteOccupied
        if(enemyPieces.popCount < 2) return

        val kingZone = moveGen.getKingAttacks(kSq) or kingBB
        val zone = kingZone
        var attackUnits = 0
        var attackersCount = 0

        val enemyKnights = if(isWhite) state.bN else state.wN
        val enemyBishops = if(isWhite) state.bB else state.wB
        val enemyRooks = if(isWhite) state.bR else state.wR

        val occupied = state.allOccupied

        var kn = enemyKnights
        while(true) {
            val (n, sq) = kn.popLSB(); kn = n; if(sq == null) break
            if((moveGen.getKnightAttacks(sq!!) and zone).rawValue != 0uL) { attackUnits += 2; attackersCount++ }
        }
        var bi = enemyBishops
        while(true) {
            val (n, sq) = bi.popLSB(); bi = n; if(sq == null) break
            if((moveGen.getBishopAttacks(sq!!, occupied) and zone).rawValue != 0uL) { attackUnits += 2; attackersCount++ }
        }
        var ro = enemyRooks
        while(true) {
            val (n, sq) = ro.popLSB(); ro = n; if(sq == null) break
            if((moveGen.getRookAttacks(sq!!, occupied) and zone).rawValue != 0uL) { attackUnits += 3; attackersCount++ }
        }
        var qu = enemyQueens
        while(true) {
            val (n, sq) = qu.popLSB(); qu = n; if(sq == null) break
            val att = moveGen.getBishopAttacks(sq!!, occupied) or moveGen.getRookAttacks(sq, occupied)
            if((att and zone).rawValue != 0uL) { attackUnits += 5; attackersCount++ }
        }

        if(attackersCount > 1) {
            val index = min(safetyTable.size - 1, attackUnits + (attackersCount * 2))
            var penalty = safetyTable[index]
            // Chess 4.6: queen presence amplifies king danger (FKSQBN)
            if(enemyQueens.rawValue == 0uL) {
                penalty = penalty / 3 // much less dangerous without queen
            }
            if(isWhite) score.mg -= penalty else score.mg += penalty
        }
    }

    private fun evaluateStrategicTerms(state: ChessBitboardGameState, moveGen: ChessBitboardMoveGenerator, score: Score) {
        val bishopPairMG = 40
        val bishopPairEG = 60

        if(state.wB.popCount >= 2) { score.mg += bishopPairMG; score.eg += bishopPairEG }
        if(state.bB.popCount >= 2) { score.mg -= bishopPairMG; score.eg -= bishopPairEG }

        // Chess 4.6: penalty for knights/bishops on back rank (FNBKRK, FBBKRK)
        val backRankPenalty = -15
        val rank1Mask = rankMasks[0] // white back rank
        val rank8Mask = rankMasks[7] // black back rank
        val wNBackRank = (state.wN and ChessBitboard(rank1Mask)).popCount
        val wBBackRank = (state.wB and ChessBitboard(rank1Mask)).popCount
        val bNBackRank = (state.bN and ChessBitboard(rank8Mask)).popCount
        val bBBackRank = (state.bB and ChessBitboard(rank8Mask)).popCount
        score.mg += wNBackRank * backRankPenalty
        score.mg += wBBackRank * backRankPenalty
        score.mg -= bNBackRank * backRankPenalty
        score.mg -= bBBackRank * backRankPenalty

        val outpostBonus = 30
        var wN = state.wN
        while(true) {
            val (n, sq) = wN.popLSB(); wN = n; if(sq == null) break
            val rank = sq!! / 8
            if(rank in 3..5) {
                if((moveGen.getPawnAttacks(sq, false).rawValue and state.wP.rawValue) != 0uL) {
                    score.mg += outpostBonus; score.eg += outpostBonus
                }
            }
        }
        var bN = state.bN
        while(true) {
            val (n, sq) = bN.popLSB(); bN = n; if(sq == null) break
            val rank = sq!! / 8
            if(rank in 2..4) {
                if((moveGen.getPawnAttacks(sq, true).rawValue and state.bP.rawValue) != 0uL) {
                    score.mg -= outpostBonus; score.eg -= outpostBonus
                }
            }
        }
    }

    private fun allPawnAttacks(pawns: ChessBitboard, white: Boolean): ULong {
        // Parallel pawn attack generation
        val p = pawns.rawValue
        val notA = 0xfefefefefefefefeuL
        val notH = 0x7f7f7f7f7f7f7f7fuL
        return if (white) {
            ((p and notA) shl 7) or ((p and notH) shl 9)
        } else {
            ((p and notA) shr 9) or ((p and notH) shr 7)
        }
    }

    private fun evaluateMobility(state: ChessBitboardGameState, moveGen: ChessBitboardMoveGenerator, score: Score) {
        val occupied = state.allOccupied
        val whiteUs = state.whiteOccupied
        val blackUs = state.blackOccupied
        val enemyPawnAttacksForWhite = allPawnAttacks(state.bP, false)
        val enemyPawnAttacksForBlack = allPawnAttacks(state.wP, true)

        val mob = intArrayOf(0, 4, 3, 2, 1) // P, N, B, R, Q (Custom indices for types?) No, type is 1..4.
        // Types in evaluatePieces: N=1, B=2, R=3, Q=4
        val mobValMG = intArrayOf(0, 4, 3, 2, 1)
        val mobValEG = intArrayOf(0, 4, 3, 4, 2)

        // Wrapper helpers
        fun count(pt: Int, isWhite: Boolean, bb: ChessBitboard) {
            var temp = bb
            while(true) {
                val (n, sq) = temp.popLSB(); temp = n; if(sq == null) break
                val attacks: ChessBitboard = when(pt) {
                    1 -> moveGen.getKnightAttacks(sq!!)
                    2 -> moveGen.getBishopAttacks(sq!!, occupied)
                    3 -> moveGen.getRookAttacks(sq!!, occupied)
                    4 -> moveGen.getBishopAttacks(sq!!, occupied) or moveGen.getRookAttacks(sq, occupied)
                    else -> ChessBitboard.EMPTY
                }
                val enemyPawnAttackMask = if(isWhite) enemyPawnAttacksForWhite else enemyPawnAttacksForBlack
                val safeAttacks = attacks and (if(isWhite) whiteUs else blackUs).inv() and ChessBitboard(enemyPawnAttackMask.inv())
                val cnt = safeAttacks.popCount
                if(isWhite) {
                    score.mg += cnt * mobValMG[pt]
                    score.eg += cnt * mobValEG[pt]
                } else {
                    score.mg -= cnt * mobValMG[pt]
                    score.eg -= cnt * mobValEG[pt]
                }
            }
        }
        count(1, true, state.wN); count(2, true, state.wB); count(3, true, state.wR); count(4, true, state.wQ)
        count(1, false, state.bN); count(2, false, state.bB); count(3, false, state.bR); count(4, false, state.bQ)
    }

    // Chess 4.6 Tropism: bonus for pieces closer to the enemy king
    private fun evaluateTropism(state: ChessBitboardGameState, score: Score) {
        val wKingSq = state.bK.lsbIndex ?: return // target: enemy king
        val bKingSq = state.wK.lsbIndex ?: return
        val wKFile = wKingSq % 8; val wKRank = wKingSq / 8
        val bKFile = bKingSq % 8; val bKRank = bKingSq / 8

        // Knight tropism (strongest effect per Chess 4.6)
        var wN = state.wN
        while (true) {
            val (n, sq) = wN.popLSB(); wN = n; if (sq == null) break
            val dist = max(abs(sq!! % 8 - wKFile), abs(sq / 8 - wKRank))
            score.mg += (7 - dist) * 3; score.eg += (7 - dist) * 2
        }
        var bN = state.bN
        while (true) {
            val (n, sq) = bN.popLSB(); bN = n; if (sq == null) break
            val dist = max(abs(sq!! % 8 - bKFile), abs(sq / 8 - bKRank))
            score.mg -= (7 - dist) * 3; score.eg -= (7 - dist) * 2
        }

        // Rook tropism
        var wR = state.wR
        while (true) {
            val (n, sq) = wR.popLSB(); wR = n; if (sq == null) break
            val dist = max(abs(sq!! % 8 - wKFile), abs(sq / 8 - wKRank))
            score.mg += (7 - dist) * 2; score.eg += (7 - dist) * 2
        }
        var bR = state.bR
        while (true) {
            val (n, sq) = bR.popLSB(); bR = n; if (sq == null) break
            val dist = max(abs(sq!! % 8 - bKFile), abs(sq / 8 - bKRank))
            score.mg -= (7 - dist) * 2; score.eg -= (7 - dist) * 2
        }

        // Queen tropism
        var wQ = state.wQ
        while (true) {
            val (n, sq) = wQ.popLSB(); wQ = n; if (sq == null) break
            val dist = max(abs(sq!! % 8 - wKFile), abs(sq / 8 - wKRank))
            score.mg += (7 - dist) * 2; score.eg += (7 - dist) * 1
        }
        var bQ = state.bQ
        while (true) {
            val (n, sq) = bQ.popLSB(); bQ = n; if (sq == null) break
            val dist = max(abs(sq!! % 8 - bKFile), abs(sq / 8 - bKRank))
            score.mg -= (7 - dist) * 2; score.eg -= (7 - dist) * 1
        }
    }

    // Chess 4.6 Trade-Down: when ahead in material, reward piece exchanges
    private fun evaluateTradeDown(state: ChessBitboardGameState, score: Score) {
        val wPieceCount = state.wN.popCount + state.wB.popCount + state.wR.popCount + state.wQ.popCount
        val bPieceCount = state.bN.popCount + state.bB.popCount + state.bR.popCount + state.bQ.popCount
        val totalPieces = wPieceCount + bPieceCount // max ~14 at start (no pawns/kings)

        // Approximate material in centipawns (without pawns, which should NOT be traded)
        val wMat = state.wN.popCount * 320 + state.wB.popCount * 330 + state.wR.popCount * 500 + state.wQ.popCount * 900
        val bMat = state.bN.popCount * 320 + state.bB.popCount * 330 + state.bR.popCount * 500 + state.bQ.popCount * 900
        val materialEdge = wMat - bMat

        if (materialEdge == 0) return

        // Pieces traded away from the starting 14
        val piecesTraded = 14 - totalPieces
        // Trade-down bonus scales with material advantage and pieces already traded
        val tradeBonus = materialEdge * piecesTraded / 28 // ~half-pawn max at full advantage
        score.mg += tradeBonus / 3
        score.eg += tradeBonus / 2
    }

    private fun evaluateMopUp(state: ChessBitboardGameState, score: Score) {
        val advantage = score.eg
        if(advantage > 200) { // White Winning
             applyMopUp(true, state, score)
        } else if(advantage < -200) {
             applyMopUp(false, state, score)
        }
    }

    private fun applyMopUp(winnerIsWhite: Boolean, state: ChessBitboardGameState, score: Score) {
        val wK = if(winnerIsWhite) state.wK.lsbIndex else state.bK.lsbIndex
        val lK = if(winnerIsWhite) state.bK.lsbIndex else state.wK.lsbIndex
        if(wK == null || lK == null) return

        val lFile = lK % 8; val lRank = lK / 8
        val centerDist = max(3 - lFile, lFile - 4) + max(3 - lRank, lRank - 4)
        val pushBonus = centerDist * 10

        val wFile = wK % 8; val wRank = wK / 8
        val dist = abs(wFile - lFile) + abs(wRank - lRank)
        val closeBonus = (14 - dist) * 4

        val total = pushBonus + closeBonus
        if(winnerIsWhite) score.eg += total else score.eg -= total
    }
}
