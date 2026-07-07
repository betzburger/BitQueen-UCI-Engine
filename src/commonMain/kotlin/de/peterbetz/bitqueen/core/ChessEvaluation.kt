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

    // Phase Calculation Weights (structural, not tunable)
    private val phaseInc = intArrayOf(0, 1, 1, 2, 4, 0)
    private const val totalPhase = 24

    // Piece values and safety table are now read from EvalParams.
    private fun pieceValMG(type: Int): Int = when (type) {
        0 -> EvalParams.PAWN_VALUE_MG; 1 -> EvalParams.KNIGHT_VALUE_MG
        2 -> EvalParams.BISHOP_VALUE_MG; 3 -> EvalParams.ROOK_VALUE_MG
        4 -> EvalParams.QUEEN_VALUE_MG; else -> 0
    }
    private fun pieceValEG(type: Int): Int = when (type) {
        0 -> EvalParams.PAWN_VALUE_EG; 1 -> EvalParams.KNIGHT_VALUE_EG
        2 -> EvalParams.BISHOP_VALUE_EG; 3 -> EvalParams.ROOK_VALUE_EG
        4 -> EvalParams.QUEEN_VALUE_EG; else -> 0
    }

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

    // Outpost masks: squares where enemy pawns could advance to attack.
    // For white outpost on sq: check if any black pawn on adjacent files at rank >= sq's rank
    private val whiteOutpostMasks = ULongArray(64) { sq ->
        val r = sq / 8; val f = sq % 8; var m: ULong = 0uL
        for (nr in r..7) {
            if (f > 0) m = m or (1uL shl (nr * 8 + f - 1))
            if (f < 7) m = m or (1uL shl (nr * 8 + f + 1))
        }
        m
    }
    // For black outpost on sq: check if any white pawn on adjacent files at rank <= sq's rank
    private val blackOutpostMasks = ULongArray(64) { sq ->
        val r = sq / 8; val f = sq % 8; var m: ULong = 0uL
        for (nr in 0..r) {
            if (f > 0) m = m or (1uL shl (nr * 8 + f - 1))
            if (f < 7) m = m or (1uL shl (nr * 8 + f + 1))
        }
        m
    }

    // PSTs are now stored in EvalParams so the tuner can modify them.
    private fun mgTable(type: Int): IntArray = when (type) {
        0 -> EvalParams.PST_PAWN_MG; 1 -> EvalParams.PST_KNIGHT_MG
        2 -> EvalParams.PST_BISHOP_MG; 3 -> EvalParams.PST_ROOK_MG
        4 -> EvalParams.PST_QUEEN_MG; else -> EvalParams.PST_KING_MG
    }
    private fun egTable(type: Int): IntArray = when (type) {
        0 -> EvalParams.PST_PAWN_EG; 1 -> EvalParams.PST_KNIGHT_EG
        2 -> EvalParams.PST_BISHOP_EG; 3 -> EvalParams.PST_ROOK_EG
        4 -> EvalParams.PST_QUEEN_EG; else -> EvalParams.PST_KING_EG
    }

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

    fun evaluate(state: ChessBitboardGameState, contempt: Int = 0, pawnHash: PawnHashTable? = null): Int {
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

        evaluatePawnStructure(state, score, pawnHash)
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

        val tempo = EvalParams.TEMPO
        return finalScore + (if (state.whiteToMove) tempo else -tempo)
    }

    private fun evaluatePieces(bitboard: ChessBitboard, type: Int, isWhite: Boolean, score: Score) {
        var bb = bitboard
        val mgVal = pieceValMG(type)
        val egVal = pieceValEG(type)
        val mgTable = mgTable(type)
        val egTable = egTable(type)
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

    private fun computePawnHashKey(wP: ULong, bP: ULong): ULong {
        var k: ULong = 0uL
        var b = wP
        while (b != 0uL) {
            val sq = b.countTrailingZeroBits()
            k = k xor ChessBitboardZobristKeys.pieces[0][sq]
            b = b and (b - 1uL)
        }
        b = bP
        while (b != 0uL) {
            val sq = b.countTrailingZeroBits()
            k = k xor ChessBitboardZobristKeys.pieces[6][sq]
            b = b and (b - 1uL)
        }
        return k
    }

    /**
     * Pure-pawn structure features (depend only on pawn bitboards).
     * Cacheable. Returns mg/eg via the entry.
     */
    private fun computePurePawnStructure(wPraw: ULong, bPraw: ULong, outMg: IntArray, outEg: IntArray) {
        val wP = ChessBitboard(wPraw)
        val bP = ChessBitboard(bPraw)
        val moveGen = ChessBitboardMoveGenerator

        val isolatedPenalty = EvalParams.PAWN_ISOLATED
        val doubledPenalty = EvalParams.PAWN_DOUBLED
        val connectedBonusMG = EvalParams.PAWN_CONNECTED_MG
        val connectedBonusEG = EvalParams.PAWN_CONNECTED_EG
        val passedBonusMG = EvalParams.PASSED_BONUS_MG
        val passedBonusEG = EvalParams.PASSED_BONUS_EG

        val backwardPenalty = -12 // penalty for backward pawns

        var mg = 0
        var eg = 0

        var tempWP = wP
        while (true) {
            val (newP, sq) = tempWP.popLSB()
            tempWP = newP
            if (sq == null) break
            val file = sq!! % 8
            val rank = sq / 8
            val isolated = (wPraw and adjacentFiles[file]) == 0uL
            if (isolated) { mg += isolatedPenalty; eg += isolatedPenalty }
            val wPawnsBehind = wPraw and fileMasks[file] and ((1uL shl sq) - 1uL)
            if (wPawnsBehind != 0uL) { mg += doubledPenalty; eg += doubledPenalty }
            val isDefended = (moveGen.getPawnAttacks(sq, false).rawValue and wPraw) != 0uL
            if (isDefended) { mg += connectedBonusMG; eg += connectedBonusEG }
            if ((bPraw and whitePassedMasks[sq]) == 0uL) {
                mg += passedBonusMG[rank]
                eg += passedBonusEG[rank]
            }
            // Backward pawn: not isolated, not defended, no friendly pawns behind on adjacent files,
            // and stop square is attacked by enemy pawns
            if (!isolated && !isDefended && rank < 6) {
                val behindMask = if (sq > 0) (1uL shl sq) - 1uL else 0uL
                val friendlyBehindAdj = wPraw and adjacentFiles[file] and behindMask
                if (friendlyBehindAdj == 0uL) {
                    val stopSq = sq + 8
                    if (stopSq < 64 && (moveGen.getPawnAttacks(stopSq, false).rawValue and bPraw) != 0uL) {
                        mg += backwardPenalty; eg += backwardPenalty
                    }
                }
            }
        }

        var tempBP = bP
        while (true) {
            val (newP, sq) = tempBP.popLSB()
            tempBP = newP
            if (sq == null) break
            val file = sq!! % 8
            val rank = sq / 8
            val isolated = (bPraw and adjacentFiles[file]) == 0uL
            if (isolated) { mg -= isolatedPenalty; eg -= isolatedPenalty }
            val aboveMask = if (sq < 63) (1uL shl (sq + 1)) - 1uL else ULong.MAX_VALUE
            val bPawnsBehind = bPraw and fileMasks[file] and aboveMask.inv()
            if (bPawnsBehind != 0uL) { mg -= doubledPenalty; eg -= doubledPenalty }
            val isDefended = (moveGen.getPawnAttacks(sq, true).rawValue and bPraw) != 0uL
            if (isDefended) { mg -= connectedBonusMG; eg -= connectedBonusEG }
            if ((wPraw and blackPassedMasks[sq]) == 0uL) {
                val whiteRank = 7 - rank
                mg -= passedBonusMG[whiteRank]
                eg -= passedBonusEG[whiteRank]
            }
            // Backward pawn for black
            if (!isolated && !isDefended && rank > 1) {
                val behindMask = (1uL shl sq).inv() and ((1uL shl sq) - 1uL).inv()  // bits above sq
                val friendlyBehindAdj = bPraw and adjacentFiles[file] and behindMask
                if (friendlyBehindAdj == 0uL) {
                    val stopSq = sq - 8
                    if (stopSq >= 0 && (moveGen.getPawnAttacks(stopSq, true).rawValue and wPraw) != 0uL) {
                        mg -= backwardPenalty; eg -= backwardPenalty
                    }
                }
            }
        }

        outMg[0] = mg
        outEg[0] = eg
    }

    private fun evaluatePawnStructure(state: ChessBitboardGameState, score: Score, pawnHash: PawnHashTable?) {
        val wPraw = state.wP.rawValue
        val bPraw = state.bP.rawValue

        var pureMg = 0
        var pureEg = 0
        if (pawnHash != null) {
            val key = computePawnHashKey(wPraw, bPraw)
            val entry = pawnHash.probe(key)
            if (entry != null) {
                pureMg = entry.mg
                pureEg = entry.eg
            } else {
                val mgArr = IntArray(1); val egArr = IntArray(1)
                computePurePawnStructure(wPraw, bPraw, mgArr, egArr)
                pureMg = mgArr[0]; pureEg = egArr[0]
                pawnHash.store(key, pureMg, pureEg)
            }
        } else {
            val mgArr = IntArray(1); val egArr = IntArray(1)
            computePurePawnStructure(wPraw, bPraw, mgArr, egArr)
            pureMg = mgArr[0]; pureEg = egArr[0]
        }
        score.mg += pureMg
        score.eg += pureEg

        // Non-cached: passed-pawn blocker reduction + king proximity (depends on piece positions)
        val passedBonusMG = EvalParams.PASSED_BONUS_MG
        val passedBonusEG = EvalParams.PASSED_BONUS_EG

        // King squares for proximity bonus
        val wKingSq = state.wK.lsbIndex ?: 0
        val bKingSq = state.bK.lsbIndex ?: 0

        var tempWP = state.wP
        while (true) {
            val (newP, sq) = tempWP.popLSB()
            tempWP = newP
            if (sq == null) break
            if ((bPraw and whitePassedMasks[sq]) == 0uL) {
                val rank = sq!! / 8
                val stopSq = sq + 8
                if (stopSq in 0..63 && state.blackOccupied.isSet(stopSq)) {
                    val pmg = passedBonusMG[rank]
                    val peg = passedBonusEG[rank]
                    score.mg -= pmg * 3 / 10
                    score.eg -= peg * 3 / 10
                }
                // King proximity to passed pawn (endgame bonus)
                // Friendly king close = good, enemy king close = bad
                if (rank >= 3) { // Only for advanced passed pawns (rank 4+)
                    val friendlyDist = chebyshevDist(wKingSq, sq)
                    val enemyDist = chebyshevDist(bKingSq, sq)
                    // Bonus scales with pawn advancement
                    val proximityBonus = (enemyDist - friendlyDist) * rank * 3
                    score.eg += proximityBonus
                }
            }
        }
        var tempBP = state.bP
        while (true) {
            val (newP, sq) = tempBP.popLSB()
            tempBP = newP
            if (sq == null) break
            if ((wPraw and blackPassedMasks[sq]) == 0uL) {
                val whiteRank = 7 - (sq!! / 8)
                val stopSq = sq - 8
                if (stopSq in 0..63 && state.whiteOccupied.isSet(stopSq)) {
                    val pmg = passedBonusMG[whiteRank]
                    val peg = passedBonusEG[whiteRank]
                    score.mg += pmg * 3 / 10
                    score.eg += peg * 3 / 10
                }
                // King proximity for black passed pawns
                if (whiteRank >= 3) {
                    val friendlyDist = chebyshevDist(bKingSq, sq)
                    val enemyDist = chebyshevDist(wKingSq, sq)
                    val proximityBonus = (enemyDist - friendlyDist) * whiteRank * 3
                    score.eg -= proximityBonus
                }
            }
        }
    }

    // Chebyshev (king) distance between two squares
    private fun chebyshevDist(sq1: Int, sq2: Int): Int {
        val file1 = sq1 % 8; val rank1 = sq1 / 8
        val file2 = sq2 % 8; val rank2 = sq2 / 8
        return maxOf(abs(file1 - file2), abs(rank1 - rank2))
    }

    private fun evaluateRooks(state: ChessBitboardGameState, score: Score) {
         val openFileBonusMG = EvalParams.ROOK_OPEN_FILE_MG
         val openFileBonusEG = EvalParams.ROOK_OPEN_FILE_EG
         val semiOpenBonusMG = EvalParams.ROOK_SEMI_OPEN_MG
         val semiOpenBonusEG = EvalParams.ROOK_SEMI_OPEN_EG
         val rookOn7thMG = EvalParams.ROOK_ON_7TH_MG
         val rookOn7thEG = EvalParams.ROOK_ON_7TH_EG
         val doubledRooksMG = EvalParams.DOUBLED_ROOKS_MG
         val doubledRooksEG = EvalParams.DOUBLED_ROOKS_EG

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
                if(!hasPawn) penalty += EvalParams.PAWN_SHIELD_MISSING
            }
            score.mg += penalty
        } else if(!isWhite && rank >= 5) {
            var penalty = 0
            for(f in max(0, file-1)..min(7, file+1)) {
                val sq1 = (rank - 1) * 8 + f
                val sq2 = (rank - 2) * 8 + f
                val hasPawn = ((pawns.rawValue shr sq1) and 1uL) == 1uL || ((pawns.rawValue shr sq2) and 1uL) == 1uL
                if(!hasPawn) penalty += EvalParams.PAWN_SHIELD_MISSING
            }
            score.mg -= penalty // Black penalty -> White Advantage
        }

        // Chess 4.6: open file next to king penalty (FKSOPF)
        for(f in max(0, file-1)..min(7, file+1)) {
            val fMask = fileMasks[f]
            val anyPawn = (state.wP.rawValue or state.bP.rawValue) and fMask
            if(anyPawn == 0uL) {
                // Fully open file adjacent to or on king
                val penalty = if(f == file) EvalParams.KING_OPEN_FILE_OWN else EvalParams.KING_OPEN_FILE_ADJ
                if(isWhite) score.mg -= penalty else score.mg += penalty
            } else {
                val ownPawns = if(isWhite) state.wP.rawValue else state.bP.rawValue
                if((ownPawns and fMask) == 0uL) {
                    // Semi-open file (no own pawns)
                    val penalty = if(f == file) EvalParams.KING_SEMI_OPEN_OWN else EvalParams.KING_SEMI_OPEN_ADJ
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
            if((moveGen.getKnightAttacks(sq!!) and zone).rawValue != 0uL) { attackUnits += EvalParams.KING_ATTACK_UNITS_KNIGHT; attackersCount++ }
        }
        var bi = enemyBishops
        while(true) {
            val (n, sq) = bi.popLSB(); bi = n; if(sq == null) break
            if((moveGen.getBishopAttacks(sq!!, occupied) and zone).rawValue != 0uL) { attackUnits += EvalParams.KING_ATTACK_UNITS_BISHOP; attackersCount++ }
        }
        var ro = enemyRooks
        while(true) {
            val (n, sq) = ro.popLSB(); ro = n; if(sq == null) break
            if((moveGen.getRookAttacks(sq!!, occupied) and zone).rawValue != 0uL) { attackUnits += EvalParams.KING_ATTACK_UNITS_ROOK; attackersCount++ }
        }
        var qu = enemyQueens
        while(true) {
            val (n, sq) = qu.popLSB(); qu = n; if(sq == null) break
            val att = moveGen.getBishopAttacks(sq!!, occupied) or moveGen.getRookAttacks(sq, occupied)
            if((att and zone).rawValue != 0uL) { attackUnits += EvalParams.KING_ATTACK_UNITS_QUEEN; attackersCount++ }
        }

        if(attackersCount > 1) {
            val safetyTable = EvalParams.SAFETY_TABLE
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
        val bishopPairMG = EvalParams.BISHOP_PAIR_MG
        val bishopPairEG = EvalParams.BISHOP_PAIR_EG

        if(state.wB.popCount >= 2) { score.mg += bishopPairMG; score.eg += bishopPairEG }
        if(state.bB.popCount >= 2) { score.mg -= bishopPairMG; score.eg -= bishopPairEG }

        // Chess 4.6: penalty for knights/bishops on back rank (FNBKRK, FBBKRK)
        val backRankPenalty = EvalParams.BACK_RANK_MINOR
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

        val outpostBonus = EvalParams.KNIGHT_OUTPOST
        val bPraw = state.bP.rawValue
        val wPraw = state.wP.rawValue
        var wN = state.wN
        while(true) {
            val (n, sq) = wN.popLSB(); wN = n; if(sq == null) break
            val rank = sq!! / 8
            if(rank in 3..5) {
                // Must be supported by own pawn AND no enemy pawn can attack it
                val supported = (moveGen.getPawnAttacks(sq, false).rawValue and wPraw) != 0uL
                val safe = (whiteOutpostMasks[sq] and bPraw) == 0uL
                if (supported && safe) {
                    score.mg += outpostBonus; score.eg += outpostBonus
                }
            }
        }
        var bN = state.bN
        while(true) {
            val (n, sq) = bN.popLSB(); bN = n; if(sq == null) break
            val rank = sq!! / 8
            if(rank in 2..4) {
                val supported = (moveGen.getPawnAttacks(sq, true).rawValue and bPraw) != 0uL
                val safe = (blackOutpostMasks[sq] and wPraw) == 0uL
                if (supported && safe) {
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

        // Types in evaluatePieces: N=1, B=2, R=3, Q=4
        val mobValMG = intArrayOf(0, EvalParams.MOB_KNIGHT_MG, EvalParams.MOB_BISHOP_MG, EvalParams.MOB_ROOK_MG, EvalParams.MOB_QUEEN_MG)
        val mobValEG = intArrayOf(0, EvalParams.MOB_KNIGHT_EG, EvalParams.MOB_BISHOP_EG, EvalParams.MOB_ROOK_EG, EvalParams.MOB_QUEEN_EG)

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
            score.mg += (7 - dist) * EvalParams.TROPISM_KNIGHT_MG; score.eg += (7 - dist) * EvalParams.TROPISM_KNIGHT_EG
        }
        var bN = state.bN
        while (true) {
            val (n, sq) = bN.popLSB(); bN = n; if (sq == null) break
            val dist = max(abs(sq!! % 8 - bKFile), abs(sq / 8 - bKRank))
            score.mg -= (7 - dist) * EvalParams.TROPISM_KNIGHT_MG; score.eg -= (7 - dist) * EvalParams.TROPISM_KNIGHT_EG
        }

        // Rook tropism
        var wR = state.wR
        while (true) {
            val (n, sq) = wR.popLSB(); wR = n; if (sq == null) break
            val dist = max(abs(sq!! % 8 - wKFile), abs(sq / 8 - wKRank))
            score.mg += (7 - dist) * EvalParams.TROPISM_ROOK_MG; score.eg += (7 - dist) * EvalParams.TROPISM_ROOK_EG
        }
        var bR = state.bR
        while (true) {
            val (n, sq) = bR.popLSB(); bR = n; if (sq == null) break
            val dist = max(abs(sq!! % 8 - bKFile), abs(sq / 8 - bKRank))
            score.mg -= (7 - dist) * EvalParams.TROPISM_ROOK_MG; score.eg -= (7 - dist) * EvalParams.TROPISM_ROOK_EG
        }

        // Queen tropism
        var wQ = state.wQ
        while (true) {
            val (n, sq) = wQ.popLSB(); wQ = n; if (sq == null) break
            val dist = max(abs(sq!! % 8 - wKFile), abs(sq / 8 - wKRank))
            score.mg += (7 - dist) * EvalParams.TROPISM_QUEEN_MG; score.eg += (7 - dist) * EvalParams.TROPISM_QUEEN_EG
        }
        var bQ = state.bQ
        while (true) {
            val (n, sq) = bQ.popLSB(); bQ = n; if (sq == null) break
            val dist = max(abs(sq!! % 8 - bKFile), abs(sq / 8 - bKRank))
            score.mg -= (7 - dist) * EvalParams.TROPISM_QUEEN_MG; score.eg -= (7 - dist) * EvalParams.TROPISM_QUEEN_EG
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
