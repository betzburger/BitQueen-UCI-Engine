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
    private var gameHistoryEnd: Int = 0

    // Heuristics
    private val killerMoves = Array(64) { arrayOfNulls<BitboardChessMove>(2) }
    // Butterfly history indexed by side-to-move: [stm][from*64+to]
    private val historyTable = Array(2) { IntArray(4096) } // [stm][64*64]
    // Counter-move table indexed by previous move's from/to
    private val counterMoves = Array(64) { arrayOfNulls<BitboardChessMove>(64) }

    // Static eval stack for "improving" heuristic
    private val staticEvalStack = IntArray(128)

    // LMR reduction table: lmrTable[depth][moveIndex]
    private val LMR_MAX_DEPTH = 64
    private val LMR_MAX_MOVES = 64
    private val lmrTable: Array<IntArray> = Array(LMR_MAX_DEPTH) { d ->
        IntArray(LMR_MAX_MOVES) { m ->
            if (d < 1 || m < 1) 1
            else {
                val r = 0.8 + ln(d.toDouble()) * ln(m.toDouble()) / 2.25
                r.toInt().coerceAtLeast(1)
            }
        }
    }

    // Constants
    private val INFINITY = 1000000
    private val MATE_SCORE = 100000
    // SEE piece values (indexed by ChessPieceType ordinal-like mapping via getPieceValue)
    private val SEE_PAWN = 100
    private val SEE_KNIGHT = 320
    private val SEE_BISHOP = 330
    private val SEE_ROOK = 500
    private val SEE_QUEEN = 900
    private val SEE_KING = 10000

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
        historyTable[0].fill(0)
        historyTable[1].fill(0)
        for(row in killerMoves) row.fill(null)
        for(row in counterMoves) row.fill(null)
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

        // History aging: halve all values to dampen signal from previous searches
        for (c in 0..1) {
            for (i in historyTable[c].indices) {
                historyTable[c][i] /= 2
            }
        }
        staticEvalStack.fill(0)

        this.searchStack.clear()
        this.searchStack.addAll(history)
        this.searchStack.add(state.hash)
        // Everything at index < gameHistoryEnd is from the actual game history (plus the current root position).
        // Indices >= gameHistoryEnd are positions reached within this search tree.
        this.gameHistoryEnd = this.searchStack.size

        var alpha = -INFINITY
        var beta = INFINITY
        var previousScore: Int? = null
        var previousBestMove: BitboardChessMove? = null

        for (depth in 1..effectiveMaxDepth) {
            _currentDepth.value = depth
            transpositionTable.newGeneration()
            onProgress?.invoke(depth)

            // Aspiration window widening (incremental). Delta is reset each ID iteration.
            // Safer variant: on fail-low only lower alpha (leave beta); on fail-high only raise beta (leave alpha).
            var delta = 20
            if (previousScore != null && depth >= 4) {
                alpha = max(-INFINITY, previousScore!! - delta)
                beta = min(INFINITY, previousScore!! + delta)
            } else if (previousScore == null && depth >= 2) {
                // TT-based aspiration start: if root TT entry has an EXACT, non-mate score, center on it.
                val rootEntry = transpositionTable.get(state.hash)
                if (rootEntry != null &&
                    rootEntry.flag == TranspositionTable.EXACT &&
                    abs(rootEntry.score) < 90000) {
                    val ttCenter = rootEntry.score
                    val wideDelta = 60
                    alpha = max(-INFINITY, ttCenter - wideDelta)
                    beta = min(INFINITY, ttCenter + wideDelta)
                    delta = wideDelta
                } else {
                    alpha = -INFINITY
                    beta = INFINITY
                }
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
                    alpha = max(-INFINITY, best.second - delta)
                    delta += delta / 2 + 5
                    if (delta > 800) { alpha = -INFINITY; beta = INFINITY }
                    continue
                }
                if (best.second >= beta) {
                    beta = min(INFINITY, best.second + delta)
                    delta += delta / 2 + 5
                    if (delta > 800) { alpha = -INFINITY; beta = INFINITY }
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
            val score = -negamax(nextState, depth - 1, -beta, -a, 1, move)
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

    private fun negamax(state: ChessBitboardGameState, depth: Int, alpha: Int, beta: Int, ply: Int, prevMove: BitboardChessMove? = null): Int {
        if (localNodesVisited % 1024 == 0) {
            if ((startTimeMark?.elapsedNow()?.inWholeMilliseconds ?: 0L) > timeLimitMs) shouldStop = true
        }
        if (shouldStop) return 0

        val currentHash = state.hash
        // Repetition detection:
        //   2-fold WITHIN search tree (indices >= gameHistoryEnd, excluding the current stack entry) -> draw
        //   3-fold counting game history -> draw
        run {
            var totalCount = 0
            var searchRepeat = false
            val last = searchStack.lastIndex
            for (i in 0 until searchStack.size) {
                if (searchStack[i] == currentHash) {
                    totalCount++
                    if (i != last && i >= gameHistoryEnd) searchRepeat = true
                }
            }
            if (searchRepeat) return -contempt
            if (totalCount >= 3) return -contempt
        }

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

        // Internal Iterative Reductions: if we have no TT move, reduce depth by 1.
        var depth = depth
        if (!hasTTMove && depth >= 4) {
            depth--
        }

        val inCheck = inCheck(state)
        var searchDepth = depth
        if (inCheck && ply < 64) searchDepth++

        if (searchDepth <= 0) return quiescence(state, a, b, ply, 0)

        val isPvNode = (b - a > 1)

        // Static eval for pruning decisions (computed once, reused)
        // Skill Level: add evaluation noise for weaker play
        val noise = if (skillEvalNoise > 0) Random.nextInt(-skillEvalNoise, skillEvalNoise + 1) else 0
        val staticEval = if (!inCheck) {
            val eval = ChessEvaluation.evaluate(state, contempt)
            (if (state.whiteToMove) eval else -eval) + noise
        } else 0

        // Store static eval in stack for improving heuristic; sentinel -INFINITY when in check
        if (ply < 128) {
            staticEvalStack[ply] = if (inCheck) -INFINITY else staticEval
        }
        val improving = !inCheck && ply >= 2 && staticEvalStack[ply - 2] != -INFINITY &&
            staticEvalStack[ply] > staticEvalStack[ply - 2]

        // Reverse Futility Pruning: linear 75*depth, reduced when improving
        if (skillUseReverseFutility && !inCheck && searchDepth <= 8 && abs(b) < 90000) {
            var rfpMargin = 75 * searchDepth
            if (improving) rfpMargin = rfpMargin * 4 / 5
            if (staticEval - rfpMargin >= b) return (staticEval + b) / 2
        }

        // Razoring: if eval + 200 + 200*depth is still below alpha at shallow depth, drop to quiescence
        if (skillUseRazoring && !inCheck && searchDepth <= 2 && abs(a) < 90000) {
            val razorMargin = 200 + 200 * searchDepth
            if (staticEval + razorMargin < a) {
                val qScore = quiescence(state, a, b, ply, 0)
                if (qScore < a) return qScore
            }
        }

        // Adaptive Null Move Pruning
        if (skillUseNullMove && useNullMovePruning && !inCheck && searchDepth >= 3 && hasNonPawnMaterial(state) && abs(b) < 90000) {
            val rAdaptive = (3 + searchDepth / 4 + min((staticEval - b) / 200, 3) + (if (improving) 1 else 0)).coerceAtLeast(2)
            val nullState = state.copy()
            applyNullMove(nullState)
            val score = -negamax(nullState, (searchDepth - 1 - rAdaptive).coerceAtLeast(0), -b, -b + 1, ply + 1, null)
            if (score >= b) return b
        }

        var enableFutility = false
        if (searchDepth <= 3 && !inCheck && abs(a) < 90000 && abs(b) < 90000) {
            val margin = when (searchDepth) { 1 -> 350; 2 -> 600; else -> 900 }
            if (staticEval + margin < a) enableFutility = true
        }

        val moves = moveGenerator.generateMoves(state).toMutableList()
        scoreMoves(moves, ttMovePacked, ply, state, prevMove)
        moves.sortByDescending { it.score }

        // Singular Extension test (multi-cut-style variant — no exclude-move parameter needed).
        // If the TT move looks like a singular best move, extend its search depth by +1.
        // Conditions: depth>=8, TT entry deep enough, lower-bound/exact, non-mate score.
        // We probe the next few non-TT moves with reduced depth and zero window below singularBeta;
        // if none of them reach singularBeta, we consider the TT move singular.
        var singularExtend = false
        if (depth >= 8 && hasTTMove && ttEntry != null &&
            ttEntry.depth >= depth - 3 &&
            (ttEntry.flag == TranspositionTable.LOWER_BOUND || ttEntry.flag == TranspositionTable.EXACT) &&
            abs(ttEntry.score) < 90000) {
            val singularBeta = ttEntry.score - depth * 2
            val reducedDepth = (depth - 1) / 2
            var probed = 0
            var anyBeat = false
            // Iterate moves in their sorted order; skip the TT move; probe up to 4 non-TT legal moves.
            for (m in moves) {
                if (packMove(m) == ttMovePacked) continue
                val ns = state.copy()
                applyMove(ns, m)
                if (isIllegal(ns)) continue
                searchStack.add(ns.hash)
                val sc = -negamax(ns, reducedDepth, -singularBeta, -singularBeta + 1, ply + 1, m)
                searchStack.removeAt(searchStack.lastIndex)
                if (shouldStop) { anyBeat = true; break }
                if (sc >= singularBeta) { anyBeat = true; break }
                probed++
                if (probed >= 4) break
            }
            if (!anyBeat && probed > 0) singularExtend = true
        }

        var bestScore = -INFINITY
        var bestMovePacked = 0
        var bestMoveRef: BitboardChessMove? = null
        var legalMovesCount = 0
        var legalQuietsVisited = 0
        val triedQuiets = ArrayList<BitboardChessMove>(16)

        for ((index, move) in moves.withIndex()) {
            val nextState = state.copy()
            applyMove(nextState, move)
            if (isIllegal(nextState)) continue
            legalMovesCount++

            val isQuiet = !move.isCapture && !move.isPromotion
            val givesCheck = inCheck(nextState)

            // SEE pruning: skip bad captures / bad quiets at shallow depth.
            // Never fires on the first legal move (we must have a best move to return).
            if (legalMovesCount >= 2 && !inCheck && bestScore > -90000 && !move.isPromotion) {
                if (move.isCapture && searchDepth <= 6) {
                    val seeThreshold = -searchDepth * 50
                    if (see(state, move) < seeThreshold) continue
                } else if (!move.isCapture && !givesCheck && searchDepth <= 3) {
                    val seeThreshold = -searchDepth * 80
                    if (see(state, move) < seeThreshold) continue
                }
            }

            // Late Move Pruning (LMP): at shallow depth, skip quiets after a threshold
            if (!inCheck && isQuiet && !givesCheck && searchDepth <= 4 && bestScore > -90000) {
                val lmpLimit = when (searchDepth) { 1 -> 4; 2 -> 8; 3 -> 14; else -> 22 }
                if (legalQuietsVisited >= lmpLimit) { continue }
            }

            if (enableFutility && isQuiet && !givesCheck) continue

            if (isQuiet) {
                legalQuietsVisited++
                triedQuiets.add(move)
            }

            searchStack.add(nextState.hash)

            var score: Int
            var reduction = 0
            var didLMR = false

            if (skillUseLMR && searchDepth >= 3 && index >= 2 && isQuiet && !inCheck && !givesCheck) {
                val depthIdx = if (searchDepth < LMR_MAX_DEPTH) searchDepth else LMR_MAX_DEPTH - 1
                val moveIdx = if (index < LMR_MAX_MOVES) index else LMR_MAX_MOVES - 1
                reduction = lmrTable[depthIdx][moveIdx]
                val hIdx = move.from * 64 + move.to
                val stmIdx = if (state.whiteToMove) 0 else 1
                if (historyTable[stmIdx][hIdx] < 0) reduction += 1
                if (!improving) reduction += 1
                if (isPvNode && reduction > 1) reduction = 1
                if (reduction < 1) reduction = 1
                if (reduction > searchDepth - 1) reduction = (searchDepth - 1).coerceAtLeast(1)
                didLMR = true
            }

            // Singular extension: only applies to the TT move. Capped at +1 per move.
            // Total resulting depth must not exceed 2 * original depth.
            val isTTMove = (packMove(move) == ttMovePacked)
            val singExt = if (singularExtend && isTTMove && (searchDepth + 1) <= 2 * depth) 1 else 0
            val extDepth = searchDepth + singExt

            if (didLMR) {
                val reducedDepth = (extDepth - 1 - reduction).coerceAtLeast(0)
                score = -negamax(nextState, reducedDepth, -a - 1, -a, ply + 1, move)
                if (score > a && reduction > 1) {
                    // Full-depth zero-window re-search before full-window re-search
                    score = -negamax(nextState, extDepth - 1, -a - 1, -a, ply + 1, move)
                }
                if (score > a && score < b) {
                    score = -negamax(nextState, extDepth - 1, -b, -a, ply + 1, move)
                }
            } else {
                if (legalMovesCount == 1) {
                    score = -negamax(nextState, extDepth - 1, -b, -a, ply + 1, move)
                } else {
                    score = -negamax(nextState, extDepth - 1, -a - 1, -a, ply + 1, move)
                    if (score > a && score < b) {
                        score = -negamax(nextState, extDepth - 1, -b, -a, ply + 1, move)
                    }
                }
            }

            searchStack.removeAt(searchStack.lastIndex)
            if (shouldStop) return 0

            if (score > bestScore) {
                bestScore = score
                bestMovePacked = packMove(move)
                bestMoveRef = move
            }
            if (score > a) {
                a = score
            }
            if (a >= b) {
                val stmIdx = if (state.whiteToMove) 0 else 1
                if (isQuiet) {
                    updateKiller(move, ply)
                    updateHistory(move, searchDepth, stmIdx)
                    // Penalize all other tried quiets
                    for (q in triedQuiets) {
                        if (!sameMove(q, move)) penalizeHistory(q, searchDepth, stmIdx)
                    }
                    // Store counter-move
                    if (prevMove != null) {
                        counterMoves[prevMove.from][prevMove.to] = move
                    }
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

        // History malus on fail-low: if we had a TT move and no move caused a cutoff
        // (bestScore <= original alpha), penalize the TT move if it's a quiet.
        if (bestScore <= alpha && hasTTMove && ttMovePacked != 0) {
            val ttFrom = (ttMovePacked shr 6) and 0x3F
            val ttTo = ttMovePacked and 0x3F
            val stmIdx = if (state.whiteToMove) 0 else 1
            for (q in triedQuiets) {
                if (q.from == ttFrom && q.to == ttTo) {
                    penalizeHistory(q, searchDepth, stmIdx)
                    break
                }
            }
        }

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
                 val victim = if (move.flag == ChessMoveFlag.EP_CAPTURE) ChessPieceType.PAWN
                              else getPieceType(move.to, !state.whiteToMove, state)
                 val victimVal = getPieceValue(victim)
                 if (standPat + victimVal + 200 < a) continue
                 // SEE pruning: skip losing captures
                 if (see(state, move) < 0) continue
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

    private fun scoreMoves(moves: MutableList<BitboardChessMove>, ttMovePacked: Int, ply: Int, state: ChessBitboardGameState, prevMove: BitboardChessMove? = null) {
        val ttFrom = if(ttMovePacked != 0) (ttMovePacked shr 6) and 0x3F else -1
        val ttTo = if(ttMovePacked != 0) ttMovePacked and 0x3F else -1
        val stm = if (state.whiteToMove) 0 else 1
        val counter: BitboardChessMove? = if (prevMove != null) counterMoves[prevMove.from][prevMove.to] else null

        for(move in moves) {
            var score = 0
            if(move.from == ttFrom && move.to == ttTo) {
                score = 20000
            } else if(move.isCapture) {
                val victim = if (move.flag == ChessMoveFlag.EP_CAPTURE) ChessPieceType.PAWN
                             else getPieceType(move.to, !state.whiteToMove, state)
                val attacker = getPieceType(move.from, state.whiteToMove, state)
                val mvvLva = (getPieceValue(victim) * 10) - getPieceValue(attacker)
                // MVV-LVA shortcut: if attacker value <= victim value, capture is surely non-losing
                val attV = getPieceValue(attacker)
                val vicV = getPieceValue(victim)
                if (attV <= vicV) {
                    score = 10000 + mvvLva
                } else {
                    val seeScore = see(state, move)
                    score = if (seeScore >= 0) 10000 + mvvLva else -10000 + mvvLva
                }
            } else {
                if(ply < 64 && sameMove(killerMoves[ply][0], move)) score = 9000
                else if(ply < 64 && sameMove(killerMoves[ply][1], move)) score = 8000
                else if(counter != null && sameMove(counter, move)) score = 7500
                else {
                   val idx = move.from * 64 + move.to
                   score = historyTable[stm][idx]
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

    private fun updateHistory(move: BitboardChessMove, depth: Int, stm: Int) {
        val idx = move.from * 64 + move.to
        if(idx >= 4096) return
        val bonus = depth * depth
        val current = historyTable[stm][idx]
        historyTable[stm][idx] = (current + bonus - (current * bonus / 8192)).coerceIn(-16384, 16384)
    }

    private fun penalizeHistory(move: BitboardChessMove, depth: Int, stm: Int) {
        val idx = move.from * 64 + move.to
        if(idx >= 4096) return
        val malus = depth * depth
        val current = historyTable[stm][idx]
        historyTable[stm][idx] = (current - malus - (current * malus / 8192)).coerceIn(-16384, 16384)
    }

    // --- Static Exchange Evaluation ---

    private fun pieceValueForSEE(type: ChessPieceType): Int = when(type) {
        ChessPieceType.PAWN -> SEE_PAWN
        ChessPieceType.KNIGHT -> SEE_KNIGHT
        ChessPieceType.BISHOP -> SEE_BISHOP
        ChessPieceType.ROOK -> SEE_ROOK
        ChessPieceType.QUEEN -> SEE_QUEEN
        ChessPieceType.KING -> SEE_KING
        else -> 0
    }

    /**
     * Bitboard of all pieces (both colors) attacking `sq`, given occupancy `occ`.
     * Used for SEE x-ray handling.
     */
    private fun attackersTo(sq: Int, state: ChessBitboardGameState, occ: ULong): ULong {
        var attackers: ULong = 0uL
        // Pawns: white pawns attack sq if a black-pawn-attack-from-sq hits a white pawn (and vice versa)
        val wPawnAttackers = moveGenerator.getPawnAttacks(sq, false).rawValue and state.wP.rawValue
        val bPawnAttackers = moveGenerator.getPawnAttacks(sq, true).rawValue and state.bP.rawValue
        attackers = attackers or wPawnAttackers or bPawnAttackers
        // Knights
        attackers = attackers or (moveGenerator.getKnightAttacks(sq).rawValue and (state.wN.rawValue or state.bN.rawValue))
        // Kings
        attackers = attackers or (moveGenerator.getKingAttacks(sq).rawValue and (state.wK.rawValue or state.bK.rawValue))
        // Bishops / Queens (diagonal) using given occ
        val bishopLike = moveGenerator.getBishopAttacks(sq, ChessBitboard(occ)).rawValue
        attackers = attackers or (bishopLike and (state.wB.rawValue or state.wQ.rawValue or state.bB.rawValue or state.bQ.rawValue))
        // Rooks / Queens (orthogonal) using given occ
        val rookLike = moveGenerator.getRookAttacks(sq, ChessBitboard(occ)).rawValue
        attackers = attackers or (rookLike and (state.wR.rawValue or state.wQ.rawValue or state.bR.rawValue or state.bQ.rawValue))
        // Only consider pieces currently on the board (still in occ)
        return attackers and occ
    }

    /**
     * Returns the bitboard of the least-valuable attacker for `side`, and also returns its piece type via piece-type-ordered lookup.
     * Returns 0uL if none.
     */
    private fun leastValuableAttacker(attackers: ULong, side: Boolean, state: ChessBitboardGameState): Pair<ULong, ChessPieceType> {
        val p = if (side) state.wP.rawValue else state.bP.rawValue
        var m = attackers and p; if (m != 0uL) return Pair(m and (0uL - m), ChessPieceType.PAWN)
        val n = if (side) state.wN.rawValue else state.bN.rawValue
        m = attackers and n; if (m != 0uL) return Pair(m and (0uL - m), ChessPieceType.KNIGHT)
        val b = if (side) state.wB.rawValue else state.bB.rawValue
        m = attackers and b; if (m != 0uL) return Pair(m and (0uL - m), ChessPieceType.BISHOP)
        val r = if (side) state.wR.rawValue else state.bR.rawValue
        m = attackers and r; if (m != 0uL) return Pair(m and (0uL - m), ChessPieceType.ROOK)
        val q = if (side) state.wQ.rawValue else state.bQ.rawValue
        m = attackers and q; if (m != 0uL) return Pair(m and (0uL - m), ChessPieceType.QUEEN)
        val k = if (side) state.wK.rawValue else state.bK.rawValue
        m = attackers and k; if (m != 0uL) return Pair(m and (0uL - m), ChessPieceType.KING)
        return Pair(0uL, ChessPieceType.PAWN)
    }

    /**
     * Static Exchange Evaluation. Returns the material swap score for a capture.
     * Non-capture moves return 0.
     * Test expectations:
     *  - Qxp where pawn defended by pawn => -800   (gain 100, then lose 900)
     *  - Qxn where knight defended by pawn => -580 (gain 320, then lose 900)
     */
    fun see(state: ChessBitboardGameState, move: BitboardChessMove): Int {
        if (!move.isCapture) return 0
        val to = move.to
        val from = move.from

        // Victim value
        val victimType: ChessPieceType = if (move.flag == ChessMoveFlag.EP_CAPTURE) {
            ChessPieceType.PAWN
        } else {
            getPieceType(to, !state.whiteToMove, state)
        }
        val attackerType = getPieceType(from, state.whiteToMove, state)

        val gain = IntArray(32)
        var d = 0
        gain[0] = pieceValueForSEE(victimType)

        // Promotions: treat as capturing with a queen (approximation) — add promotion gain
        var currentAttackerVal = pieceValueForSEE(attackerType)
        if (move.isPromotion) {
            // Add queen - pawn as promotion gain; attacker becomes queen going forward
            gain[0] += SEE_QUEEN - SEE_PAWN
            currentAttackerVal = SEE_QUEEN
        }

        var occ = state.allOccupied.rawValue
        occ = occ and (1uL shl from).inv()
        if (move.flag == ChessMoveFlag.EP_CAPTURE) {
            val epPawnSq = if (state.whiteToMove) to - 8 else to + 8
            occ = occ and (1uL shl epPawnSq).inv()
        }

        var sideToMove = !state.whiteToMove
        var attackers = attackersTo(to, state, occ)

        while (true) {
            // Current side's attackers
            val sideMask = if (sideToMove)
                (state.wP.rawValue or state.wN.rawValue or state.wB.rawValue or state.wR.rawValue or state.wQ.rawValue or state.wK.rawValue)
            else
                (state.bP.rawValue or state.bN.rawValue or state.bB.rawValue or state.bR.rawValue or state.bQ.rawValue or state.bK.rawValue)
            val sideAttackers = attackers and sideMask
            if (sideAttackers == 0uL) break
            val (lvaBit, lvaType) = leastValuableAttacker(attackers, sideToMove, state)
            if (lvaBit == 0uL) break

            d++
            gain[d] = currentAttackerVal - gain[d - 1]
            // If we can't possibly improve, early cutoff
            if (max(-gain[d - 1], gain[d]) < 0) break

            currentAttackerVal = pieceValueForSEE(lvaType)

            // Remove used attacker from occupancy and attackers
            occ = occ and lvaBit.inv()
            attackers = attackers and lvaBit.inv()
            // Recompute x-rays along the diagonal/orthogonal through `to`
            val bishopLike = moveGenerator.getBishopAttacks(to, ChessBitboard(occ)).rawValue and
                    (state.wB.rawValue or state.wQ.rawValue or state.bB.rawValue or state.bQ.rawValue)
            val rookLike = moveGenerator.getRookAttacks(to, ChessBitboard(occ)).rawValue and
                    (state.wR.rawValue or state.wQ.rawValue or state.bR.rawValue or state.bQ.rawValue)
            attackers = (attackers or bishopLike or rookLike) and occ

            sideToMove = !sideToMove

            if (d >= 31) break
        }

        // Negamax backward pass
        while (d > 0) {
            gain[d - 1] = -max(-gain[d - 1], gain[d])
            d--
        }
        return gain[0]
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
