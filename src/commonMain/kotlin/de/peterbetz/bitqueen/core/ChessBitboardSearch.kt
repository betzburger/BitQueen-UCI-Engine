package de.peterbetz.bitqueen.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.TimeSource

class ChessBitboardSearch(
    private val transpositionTable: TranspositionTable
) {

    // MARK: - Configuration
    private val moveGenerator = ChessBitboardMoveGenerator

    // Engine Options
    var useNullMovePruning: Boolean = true
    var contempt: Int = 0

    // Skill Level (1-20, 20 = full strength)
    var skillLevel: Int = 20
    // Derived from skillLevel:
    private val skillMaxDepth: Int get() = when {
        skillLevel <= 2  -> 2
        skillLevel <= 4  -> 3
        skillLevel <= 6  -> 4
        skillLevel <= 8  -> 5
        skillLevel <= 10 -> 6
        skillLevel <= 12 -> 8
        skillLevel <= 14 -> 10
        skillLevel <= 16 -> 12
        skillLevel <= 18 -> 15
        else -> 100 // unlimited
    }
    private val skillEvalNoise: Int get() = when {
        skillLevel <= 2  -> 150
        skillLevel <= 4  -> 120
        skillLevel <= 6  -> 90
        skillLevel <= 8  -> 65
        skillLevel <= 10 -> 45
        skillLevel <= 12 -> 30
        skillLevel <= 14 -> 18
        skillLevel <= 16 -> 10
        skillLevel <= 18 -> 4
        else -> 0
    }
    private val skillUseNullMove: Boolean get() = skillLevel >= 9
    private val skillUseLMR: Boolean get() = skillLevel >= 11
    private val skillUseReverseFutility: Boolean get() = skillLevel >= 13
    private val skillUseRazoring: Boolean get() = skillLevel >= 13

    // MARK: - Search State
    private val _nodesVisited = MutableStateFlow(0)
    val nodesVisited = _nodesVisited.asStateFlow()

    private val _currentDepth = MutableStateFlow(0)
    val currentDepth = _currentDepth.asStateFlow()

    private val _bestMove = MutableStateFlow<BitboardChessMove?>(null)
    val bestMove = _bestMove.asStateFlow()

    private val _searchInfo = MutableStateFlow("")
    val searchInfo = _searchInfo.asStateFlow()

    private val _currentScore = MutableStateFlow(0)
    val currentScore = _currentScore.asStateFlow()

    @kotlin.concurrent.Volatile
    private var shouldStop: Boolean = false
    private var startTimeMark: kotlin.time.TimeMark? = null
    private var timeLimitMs: Long = 5000

    private var localNodesVisited = 0 

    // History for Cycle Detection
    private val searchStack = ArrayList<ULong>(100)

    // Heuristics
    private val killerMoves = Array(64) { arrayOfNulls<BitboardChessMove>(2) }
    private val historyTable = IntArray(4096) // 64*64

    // Constants
    private val INFINITY = 1000000
    private val MATE_SCORE = 100000

    enum class TTFlag(val value: Byte) {
        EXACT(0),
        LOWER_BOUND(1),
        UPPER_BOUND(2)
    }

    fun stop() {
        shouldStop = true
    }

    fun clearTT() {
        transpositionTable.clear()
        historyTable.fill(0)
        for(row in killerMoves) row.fill(null)
    }

    fun startSearch(
        state: ChessBitboardGameState,
        history: List<ULong> = emptyList(),
        timeLimitMs: Long = 5000,
        softTimeLimitMs: Long? = null,
        maxDepth: Int = 20,
        output: Boolean = true,
        onProgress: ((Int) -> Unit)? = null
    ) {
        this.timeLimitMs = timeLimitMs
        val softLimit = softTimeLimitMs ?: timeLimitMs
        val effectiveMaxDepth = min(maxDepth, skillMaxDepth)
        this.startTimeMark = TimeSource.Monotonic.markNow()
        this.shouldStop = false
        this.localNodesVisited = 0
        _nodesVisited.value = 0
        _bestMove.value = null

        this.searchStack.clear()
        this.searchStack.addAll(history)
        this.searchStack.add(state.hash)

        var alpha = -INFINITY
        var beta = INFINITY
        var previousScore: Int? = null
        var previousBestMove: BitboardChessMove? = null

        for (depth in 1..effectiveMaxDepth) {
            _currentDepth.value = depth
            transpositionTable.newGeneration()
            onProgress?.invoke(depth)

            if (previousScore != null && depth >= 4) {
                val window = 35
                alpha = max(-INFINITY, previousScore!! - window)
                beta = min(INFINITY, previousScore!! + window)
            } else {
                alpha = -INFINITY
                beta = INFINITY
            }

            var bestInIteration: Pair<BitboardChessMove, Int>? = null

            while (true) {
                bestInIteration = searchRoot(state, depth, alpha, beta)
                if (shouldStop) break
                val best = bestInIteration ?: break

                if (best.second <= alpha) {
                    alpha = -INFINITY
                    beta = INFINITY
                    continue
                }
                if (best.second >= beta) {
                    beta = INFINITY
                    alpha = -INFINITY
                    continue
                }
                break
            }

            if (shouldStop) break

            val (move, score) = bestInIteration ?: break
            _bestMove.value = move
            _currentScore.value = score
            
            if (output) {
                 val elapsed = startTimeMark?.elapsedNow()?.inWholeMilliseconds ?: 1L
                 val nps = if(elapsed > 0) (localNodesVisited * 1000L / elapsed).toInt() else 0
                 
                 var scoreString = ""
                 if (abs(score) > 90000) {
                     val mateIn = (MATE_SCORE - abs(score) + 1) / 2
                     val sign = if (score > 0) "" else "-"
                     scoreString = "mate $sign$mateIn"
                 } else {
                     scoreString = "cp $score"
                 }
                 
                 val pv = move.toLAN()
                 val info = "info depth $depth score $scoreString nodes $localNodesVisited nps $nps pv $pv"
                 _searchInfo.value = info
                 println(info)
            }

            val timeSpent = startTimeMark?.elapsedNow()?.inWholeMilliseconds ?: 0L
            if (timeSpent > softLimit) {
                var stop = true
                if (previousBestMove != null && previousBestMove != move) {
                    if (timeSpent < timeLimitMs * 0.8) stop = false
                }
                if (previousScore != null && score < previousScore!! - 50) {
                     if (timeSpent < timeLimitMs * 0.9) stop = false
                }
                if (stop) break
            }
            previousScore = score
            previousBestMove = move

            if (shouldStop || (startTimeMark?.elapsedNow()?.inWholeMilliseconds ?: 0L) > timeLimitMs) {
                break
            }
        }
        _nodesVisited.value = localNodesVisited
    }

    private fun searchRoot(state: ChessBitboardGameState, depth: Int, alpha: Int, beta: Int): Pair<BitboardChessMove, Int>? {
        var a = alpha
        val moves = moveGenerator.generateMoves(state).toMutableList()
        if (moves.isEmpty()) return null

        val ttEntry = transpositionTable.get(state.hash)
        var ttMovePacked: Int = 0
        if (ttEntry != null) {
            ttMovePacked = ttEntry.moveFrom
        }

        scoreMoves(moves, ttMovePacked, 0, state)
        moves.sortByDescending { it.score }

        var bestMove: BitboardChessMove? = null
        var bestScore = -INFINITY
        var legalMoveFound = false

        for (move in moves) {
            val nextState = state.copy()
            applyMove(nextState, move)
            
            if (isIllegal(nextState)) continue
            legalMoveFound = true

            searchStack.add(nextState.hash)
            val score = -negamax(nextState, depth - 1, -beta, -a, 1)
            searchStack.removeAt(searchStack.lastIndex)

            if (shouldStop) return null

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
            if (score > a) {
                a = score
            }
        }

        if (!legalMoveFound) return null

        if (bestMove != null) {
            val packed = packMove(bestMove!!)
            val flag = if (bestScore >= beta) TTFlag.LOWER_BOUND.value else if (bestScore > alpha) TTFlag.EXACT.value else TTFlag.UPPER_BOUND.value
            transpositionTable.store(state.hash, bestScore, depth, flag, packed, 0)
            return Pair(bestMove!!, bestScore)
        }
        return null
    }

    private fun negamax(state: ChessBitboardGameState, depth: Int, alpha: Int, beta: Int, ply: Int): Int {
        if (localNodesVisited % 1024 == 0) {
            if ((startTimeMark?.elapsedNow()?.inWholeMilliseconds ?: 0L) > timeLimitMs) shouldStop = true
        }
        if (shouldStop) return 0

        val currentHash = state.hash
        if (searchStack.count { it == currentHash } >= 2) return -contempt

        val maxMateScore = MATE_SCORE - ply
        var a = alpha
        var b = beta
        if (a < -maxMateScore) a = -maxMateScore
        if (b > maxMateScore - 1) b = maxMateScore - 1
        if (a >= b) return a

        if (depth <= 0) return quiescence(state, a, b, ply, 0)

        localNodesVisited++

        var ttMovePacked = 0
        var hasTTMove = false
        val ttEntry = transpositionTable.get(state.hash)
        
        if (ttEntry != null) {
            ttMovePacked = ttEntry.moveFrom
            hasTTMove = ttEntry.moveFrom != 0
            if (ttEntry.depth >= depth) {
                var ttScore = ttEntry.score
                if (ttScore > 90000) ttScore -= ply
                if (ttScore < -90000) ttScore += ply
                
                if (ttEntry.flag == TranspositionTable.EXACT) return ttScore
                if (ttEntry.flag == TranspositionTable.LOWER_BOUND) {
                    if (ttScore >= b) return ttScore
                    a = max(a, ttScore)
                }
                if (ttEntry.flag == TranspositionTable.UPPER_BOUND) {
                    if (ttScore <= a) return ttScore
                    b = min(b, ttScore)
                }
            }
        }

        if (depth >= 4 && !hasTTMove) {
            negamax(state, depth - 2, a, b, ply)
            val iidEntry = transpositionTable.get(state.hash)
            if (iidEntry != null && iidEntry.moveFrom != 0) {
                ttMovePacked = iidEntry.moveFrom
                hasTTMove = true
            }
        }

        val inCheck = inCheck(state)
        var searchDepth = depth
        if (inCheck && ply < 64) searchDepth++

        if (searchDepth <= 0) return quiescence(state, a, b, ply, 0)

        // Static eval for pruning decisions (computed once, reused)
        // Skill Level: add evaluation noise for weaker play
        val noise = if (skillEvalNoise > 0) Random.nextInt(-skillEvalNoise, skillEvalNoise + 1) else 0
        val staticEval = if (!inCheck && searchDepth <= 6) {
            val eval = ChessEvaluation.evaluate(state, contempt)
            (if (state.whiteToMove) eval else -eval) + noise
        } else 0

        // Reverse Futility Pruning: if eval is far above beta, prune
        if (skillUseReverseFutility && !inCheck && searchDepth <= 6 && abs(b) < 90000) {
            val rfpMargin = when (searchDepth) { 1 -> 200; 2 -> 400; 3 -> 600; 4 -> 800; 5 -> 1000; else -> 1200 }
            if (staticEval - rfpMargin >= b) return (staticEval + b) / 2

        }

        // Razoring: if eval is far below alpha at shallow depth, drop to quiescence
        if (skillUseRazoring && !inCheck && searchDepth <= 2 && abs(a) < 90000) {
            val razorMargin = if (searchDepth == 1) 300 else 600
            if (staticEval + razorMargin < a) {
                val qScore = quiescence(state, a, b, ply, 0)
                if (qScore < a) return qScore
            }
        }

        if (skillUseNullMove && useNullMovePruning && !inCheck && searchDepth >= 3 && hasNonPawnMaterial(state)) {
            val nullState = state.copy()
            applyNullMove(nullState)
            val score = -negamax(nullState, searchDepth - 1 - 2, -b, -b + 1, ply + 1)
            if (score >= b) return b
        }

        var enableFutility = false
        if (searchDepth <= 3 && !inCheck && abs(a) < 90000 && abs(b) < 90000) {
            val margin = when (searchDepth) { 1 -> 350; 2 -> 600; else -> 900 }
            // Reuse staticEval if available (depth <= 6), otherwise compute
            val standPat = if (searchDepth <= 6) staticEval else {
                val eval = ChessEvaluation.evaluate(state, contempt)
                if (state.whiteToMove) eval else -eval
            }
            if (standPat + margin < a) enableFutility = true
        }

        val moves = moveGenerator.generateMoves(state).toMutableList()
        scoreMoves(moves, ttMovePacked, ply, state)
        moves.sortByDescending { it.score }

        var bestScore = -INFINITY
        var bestMovePacked = 0
        var legalMovesCount = 0

        for ((index, move) in moves.withIndex()) {
            val nextState = state.copy()
            applyMove(nextState, move)
            if (isIllegal(nextState)) continue
            legalMovesCount++

            val givesCheck = inCheck(nextState)
            if (!inCheck && searchDepth in 2..3 && !move.isCapture && !move.isPromotion && !givesCheck) {
                if (legalMovesCount >= (3 + searchDepth * searchDepth)) continue
            }
            if (enableFutility && !move.isCapture && !move.isPromotion && !givesCheck) continue

            searchStack.add(nextState.hash)

            var doFullSearch = true
            var score = 0
            
            if (skillUseLMR && searchDepth >= 3 && index >= 3 && !move.isCapture && !move.isPromotion && !inCheck && !givesCheck) {
                 val r = 1.0 + (ln(searchDepth.toDouble()) * ln(index.toDouble()) / 1.95)
                 var reduction = r.toInt()
                 if (reduction < 1) reduction = 1
                 score = -negamax(nextState, searchDepth - 1 - reduction, -a - 1, -a, ply + 1)
                 if (score > a) doFullSearch = true else doFullSearch = false
            }

            if (doFullSearch) {
                if (legalMovesCount == 1) {
                    score = -negamax(nextState, searchDepth - 1, -b, -a, ply + 1)
                } else {
                    score = -negamax(nextState, searchDepth - 1, -a - 1, -a, ply + 1)
                    if (score > a && score < b) {
                        score = -negamax(nextState, searchDepth - 1, -b, -a, ply + 1)
                    }
                }
            } else {
                score = a
            }

            searchStack.removeAt(searchStack.lastIndex)
            if (shouldStop) return 0

            if (score > bestScore) {
                bestScore = score
                bestMovePacked = packMove(move)
            }
            if (score > a) {
                a = score
            }
            if (a >= b) {
                if (!move.isCapture) {
                    updateKiller(move, ply)
                    updateHistory(move, searchDepth)
                }
                var storeScore = score
                if (storeScore > 90000) storeScore += ply
                if (storeScore < -90000) storeScore -= ply
                transpositionTable.store(state.hash, storeScore, searchDepth, TranspositionTable.LOWER_BOUND, bestMovePacked, 0)
                return score
            }
        }

        if (legalMovesCount == 0) {
            return if (inCheck) -MATE_SCORE + ply else 0
        }
        if (bestScore == -INFINITY) return a
        
        val flag = if (bestScore >= b) TranspositionTable.LOWER_BOUND else if (bestScore > alpha) TranspositionTable.EXACT else TranspositionTable.UPPER_BOUND
        var storeScore = bestScore
        if (storeScore > 90000) storeScore += ply
        if (storeScore < -90000) storeScore -= ply
        transpositionTable.store(state.hash, storeScore, searchDepth, flag, bestMovePacked, 0)
        return bestScore
    }

    private fun quiescence(state: ChessBitboardGameState, alpha: Int, beta: Int, ply: Int, qsDepth: Int): Int {
        localNodesVisited++
        if (localNodesVisited % 2048 == 0) {
             if ((startTimeMark?.elapsedNow()?.inWholeMilliseconds ?: 0L) > timeLimitMs) shouldStop = true
        }
        if (shouldStop) return 0

        val inCheck = inCheck(state)
        var a = alpha
        var standPat = -INFINITY

        if (!inCheck) {
            val eval = ChessEvaluation.evaluate(state, contempt)
            val qNoise = if (skillEvalNoise > 0) Random.nextInt(-skillEvalNoise, skillEvalNoise + 1) else 0
            standPat = (if (state.whiteToMove) eval else -eval) + qNoise
            if (standPat >= beta) return beta
            if (standPat > a) a = standPat
        }

        val moves = moveGenerator.generateMoves(state).toMutableList()
        scoreMoves(moves, 0, ply, state)
        moves.sortByDescending { it.score }
        var legalMovesCount = 0

        for (move in moves) {
            val potentiallyInteresting = inCheck || move.isCapture || move.isPromotion
            if (!potentiallyInteresting && qsDepth > 0) continue

            if (!inCheck && move.isCapture && !move.isPromotion) {
                 val victim = getPieceType(move.to, !state.whiteToMove, state)
                 val victimVal = getPieceValue(victim)
                 if (standPat + victimVal + 200 < a) continue
            }

            val nextState = state.copy()
            applyMove(nextState, move)
            if (isIllegal(nextState)) continue

            val givesCheck = inCheck(nextState)
            if (!inCheck) {
                if (!move.isCapture && !move.isPromotion) {
                    if (!givesCheck) continue
                    if (qsDepth > 0) continue
                }
            }

            legalMovesCount++
            val score = -quiescence(nextState, -beta, -a, ply + 1, qsDepth + 1)
            
            if (shouldStop) return 0
            if (score >= beta) return beta
            if (score > a) a = score
        }

        if (inCheck && legalMovesCount == 0) return -MATE_SCORE + ply
        return a
    }

    private fun packMove(move: BitboardChessMove): Int {
        return (move.from shl 6) or move.to
    }

    private fun scoreMoves(moves: MutableList<BitboardChessMove>, ttMovePacked: Int, ply: Int, state: ChessBitboardGameState) {
        val ttFrom = if(ttMovePacked != 0) (ttMovePacked shr 6) and 0x3F else -1
        val ttTo = if(ttMovePacked != 0) ttMovePacked and 0x3F else -1
        
        for(move in moves) {
            var score = 0
            if(move.from == ttFrom && move.to == ttTo) {
                score = 20000
            } else if(move.isCapture) {
                val victim = getPieceType(move.to, !state.whiteToMove, state)
                val attacker = getPieceType(move.from, state.whiteToMove, state)
                score = 10000 + (getPieceValue(victim) * 10) - getPieceValue(attacker)
            } else {
                if(ply < 64 && sameMove(killerMoves[ply][0], move)) score = 9000
                else if(ply < 64 && sameMove(killerMoves[ply][1], move)) score = 8000
                else {
                   val idx = move.from * 64 + move.to
                   score = historyTable.getOrElse(idx) { 0 }
                }
            }
            move.score = score
        }
    }

    private fun hasNonPawnMaterial(state: ChessBitboardGameState): Boolean {
        return if (state.whiteToMove) {
            (state.wN.rawValue or state.wB.rawValue or state.wR.rawValue or state.wQ.rawValue) != 0uL
        } else {
            (state.bN.rawValue or state.bB.rawValue or state.bR.rawValue or state.bQ.rawValue) != 0uL
        }
    }

    private fun sameMove(a: BitboardChessMove?, b: BitboardChessMove?): Boolean {
        if (a == null || b == null) return false
        return a.from == b.from && a.to == b.to && a.flag == b.flag
    }

    private fun updateKiller(move: BitboardChessMove, ply: Int) {
        if (ply >= 64) return
        if (sameMove(killerMoves[ply][0], move)) return
        killerMoves[ply][1] = killerMoves[ply][0]
        killerMoves[ply][0] = move
    }

    private fun updateHistory(move: BitboardChessMove, depth: Int) {
        val idx = move.from * 64 + move.to
        if(idx >= 4096) return
        // Gravity-based aging: bonus is reduced as value grows (avoids global collapse)
        val bonus = depth * depth
        val current = historyTable[idx]
        // Scale bonus down as history value grows (max ~8000)
        historyTable[idx] = current + bonus - (current * bonus / 8192)
    }

    fun inCheck(state: ChessBitboardGameState): Boolean {
        val king = if (state.whiteToMove) state.wK else state.bK
        val kSq = king.lsbIndex ?: return true 
        return moveGenerator.isSquareAttacked(kSq, !state.whiteToMove, state)
    }

    private fun isIllegal(state: ChessBitboardGameState): Boolean {
        val us = !state.whiteToMove
        val king = if (us) state.wK else state.bK
        val kSq = king.lsbIndex ?: return true
        return moveGenerator.isSquareAttacked(kSq, !us, state)
    }

    private fun applyNullMove(state: ChessBitboardGameState) {
        val keys = ChessBitboardZobristKeys
        state.whiteToMove = !state.whiteToMove
        state.hash = state.hash xor keys.sideToMove
        if (state.enPassantTarget != null) {
            state.hash = state.hash xor keys.enPassantFiles[state.enPassantTarget!! % 8]
            state.enPassantTarget = null
        }
    }

    private fun applyMove(state: ChessBitboardGameState, move: BitboardChessMove) {
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

    private fun getPieceValue(type: ChessPieceType): Int {
        return when (type) {
            ChessPieceType.PAWN -> 100
            ChessPieceType.KNIGHT -> 320
            ChessPieceType.BISHOP -> 330
            ChessPieceType.ROOK -> 500
            ChessPieceType.QUEEN -> 900
            ChessPieceType.KING -> 20000
            else -> 0
        }
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
}
