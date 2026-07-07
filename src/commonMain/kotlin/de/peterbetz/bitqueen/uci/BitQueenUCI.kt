package de.peterbetz.bitqueen.uci

import de.peterbetz.bitqueen.core.*
import kotlinx.coroutines.*

/**
 * BitQueen UCI Engine v3.0
 * Full UCI (Universal Chess Interface) compatible chess engine
 * 
 * Features:
 * - 64-bit Bitboards with Magic-style Ray Lookups
 * - Principal Variation Search (PVS) with Aspiration Windows
 * - Transposition Table with Zobrist Hashing
 * - Tapered Evaluation (Middle Game / End Game)
 * - Advanced Pawn Structure Analysis
 * - King Safety Evaluation
 * - Mobility Evaluation
 * - Multi-Core Search (Lazy SMP)
 * - Opening Book Support
 * - Time Management
 * 
 * @author Peter Betz
 * @version 3.0
 */

// UCI Options
data class UCIOptions(
    var hashSizeMB: Int = 512,
    var threads: Int = 4,
    var contempt: Int = 0,
    var ponder: Boolean = false,
    var ownBook: Boolean = false,
    var multiPV: Int = 1,
    var skillLevel: Int = 20,
    var moveOverhead: Int = 50
)

class BitQueenUCIEngine {
    private val options = UCIOptions()
    private var transpositionTable = TranspositionTable(options.hashSizeMB)
    private var search = ChessMultiCoreSearch(transpositionTable, options.threads)
    private val moveGenerator = ChessBitboardMoveGenerator
    private val bookManager = ChessOpeningBookManager()
    
    // Texel tuning cache: preparsed states + labeled results.
    private var texelStates: Array<ChessBitboardGameState>? = null
    private var texelResults: DoubleArray? = null

    private var currentPosition = ChessBitboardGameState()
    private var positionHistory = mutableListOf<ULong>()
    private var moveHistoryLAN = mutableListOf<String>()
    
    private val isSearching = AtomicBoolean(false)
    private var searchJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    fun start() {
        // Unbuffered output is preferred for UCI
        
        println("BitQueen UCI v4.2 by Peter Betz")
        
        var running = true
        while (running) {
            val input = readlnOrNull() ?: break
            val tokens = input.trim().split("\\s+".toRegex())
            
            if (tokens.isEmpty()) continue
            
            when (tokens[0].lowercase()) {
                "uci" -> handleUCI()
                "debug" -> handleDebug(tokens)
                "isready" -> handleIsReady()
                "setoption" -> handleSetOption(tokens)
                "register" -> handleRegister()
                "ucinewgame" -> handleNewGame()
                "position" -> handlePosition(tokens)
                "go" -> handleGo(tokens)
                "eval" -> handleEval()
                "getparam" -> handleGetParam(tokens)
                "listparams" -> handleListParams()
                "loadepd" -> handleLoadEpd(tokens)
                "mse" -> handleMse(tokens)
                "stop" -> handleStop()
                "ponderhit" -> handlePonderHit()
                "quit" -> {
                    handleQuit()
                    running = false
                }
                else -> println("info string Unknown command: $input")
            }
            
            flushStdout()
        }
    }
    
    private fun handleUCI() {
        println("id name BitQueen 4.2")
        println("id author Peter Betz")
        println()
        
        // Hash size option
        println("option name Hash type spin default ${options.hashSizeMB} min 1 max 32768")
        
        // Threads option
        println("option name Threads type spin default ${options.threads} min 1 max ${getProcessorCount()}")
        
        // Contempt option
        println("option name Contempt type spin default ${options.contempt} min -100 max 100")
        
        // Ponder (not fully implemented but advertised for compatibility)
        println("option name Ponder type check default ${options.ponder}")
        
        // Own Book
        println("option name OwnBook type check default ${options.ownBook}")
        
        // MultiPV
        println("option name MultiPV type spin default ${options.multiPV} min 1 max 5")
        
        // Skill Level
        println("option name Skill Level type spin default ${options.skillLevel} min 1 max 20")

        // Move Overhead (time buffer for communication lag)
        println("option name Move Overhead type spin default ${options.moveOverhead} min 0 max 1000")

        // Clear Hash button
        println("option name Clear Hash type button")

        // Expose all scalar EvalParams as tunable spin options (for texel / SPRT).
        val tunableScalars = listOf(
            "PAWN_VALUE_MG","KNIGHT_VALUE_MG","BISHOP_VALUE_MG","ROOK_VALUE_MG","QUEEN_VALUE_MG",
            "PAWN_VALUE_EG","KNIGHT_VALUE_EG","BISHOP_VALUE_EG","ROOK_VALUE_EG","QUEEN_VALUE_EG",
            "KING_ATTACK_UNITS_KNIGHT","KING_ATTACK_UNITS_BISHOP","KING_ATTACK_UNITS_ROOK","KING_ATTACK_UNITS_QUEEN",
            "PAWN_ISOLATED","PAWN_DOUBLED","PAWN_CONNECTED_MG","PAWN_CONNECTED_EG",
            "ROOK_OPEN_FILE_MG","ROOK_OPEN_FILE_EG","ROOK_SEMI_OPEN_MG","ROOK_SEMI_OPEN_EG",
            "ROOK_ON_7TH_MG","ROOK_ON_7TH_EG","DOUBLED_ROOKS_MG","DOUBLED_ROOKS_EG",
            "PAWN_SHIELD_MISSING","KING_OPEN_FILE_OWN","KING_OPEN_FILE_ADJ","KING_SEMI_OPEN_OWN","KING_SEMI_OPEN_ADJ",
            "BISHOP_PAIR_MG","BISHOP_PAIR_EG","BACK_RANK_MINOR","KNIGHT_OUTPOST",
            "MOB_KNIGHT_MG","MOB_BISHOP_MG","MOB_ROOK_MG","MOB_QUEEN_MG",
            "MOB_KNIGHT_EG","MOB_BISHOP_EG","MOB_ROOK_EG","MOB_QUEEN_EG",
            "TROPISM_KNIGHT_MG","TROPISM_KNIGHT_EG","TROPISM_ROOK_MG","TROPISM_ROOK_EG",
            "TROPISM_QUEEN_MG","TROPISM_QUEEN_EG","TEMPO"
        )
        for (p in tunableScalars) {
            val v = EvalParams.getParam(p) ?: continue
            println("option name $p type spin default $v min -10000 max 10000")
        }

        println("uciok")
        flushStdout()
    }
    
    private fun handleDebug(tokens: List<String>) {
        // Debug mode - can be extended
        if (tokens.size > 1) {
            when (tokens[1].lowercase()) {
                "on" -> println("info string Debug mode ON")
                "off" -> println("info string Debug mode OFF")
            }
        }
    }
    
    private fun handleIsReady() {
        println("readyok")
        flushStdout()
    }
    
    private fun handleSetOption(tokens: List<String>) {
        // Parse: setoption name <name> value <value>
        val nameIndex = tokens.indexOfFirst { it.lowercase() == "name" }
        val valueIndex = tokens.indexOfFirst { it.lowercase() == "value" }
        
        if (nameIndex == -1) return
        
        val optionName = if (valueIndex > nameIndex) {
            tokens.subList(nameIndex + 1, valueIndex).joinToString(" ")
        } else {
            tokens.subList(nameIndex + 1, tokens.size).joinToString(" ")
        }
        
        val optionValue = if (valueIndex != -1 && valueIndex < tokens.size - 1) {
            tokens.subList(valueIndex + 1, tokens.size).joinToString(" ")
        } else {
            ""
        }
        
        when (optionName.lowercase()) {
            "hash" -> {
                val newSize = optionValue.toIntOrNull()
                if (newSize != null && newSize in 1..32768) {
                    options.hashSizeMB = newSize
                    transpositionTable = TranspositionTable(newSize)
                    search = ChessMultiCoreSearch(transpositionTable, options.threads)
                    println("info string Hash size set to $newSize MB")
                }
            }
            "threads" -> {
                val newThreads = optionValue.toIntOrNull()
                if (newThreads != null && newThreads in 1..getProcessorCount()) {
                    options.threads = newThreads
                    search = ChessMultiCoreSearch(transpositionTable, newThreads)
                    println("info string Threads set to $newThreads")
                }
            }
            "contempt" -> {
                val newContempt = optionValue.toIntOrNull()
                if (newContempt != null && newContempt in -100..100) {
                    options.contempt = newContempt
                    println("info string Contempt set to $newContempt")
                }
            }
            "ponder" -> {
                options.ponder = optionValue.lowercase() == "true"
                println("info string Ponder ${if (options.ponder) "enabled" else "disabled"}")
            }
            "ownbook" -> {
                options.ownBook = optionValue.lowercase() == "true"
                println("info string Own book ${if (options.ownBook) "enabled" else "disabled"}")
            }
            "multipv" -> {
                val newMultiPV = optionValue.toIntOrNull()
                if (newMultiPV != null && newMultiPV in 1..5) {
                    options.multiPV = newMultiPV
                    println("info string MultiPV set to $newMultiPV")
                }
            }
            "skill level" -> {
                val newSkill = optionValue.toIntOrNull()
                if (newSkill != null && newSkill in 1..20) {
                    options.skillLevel = newSkill
                    search.skillLevel = newSkill
                    println("info string Skill Level set to $newSkill")
                }
            }
            "move overhead" -> {
                val newOverhead = optionValue.toIntOrNull()
                if (newOverhead != null && newOverhead in 0..1000) {
                    options.moveOverhead = newOverhead
                    println("info string Move Overhead set to $newOverhead ms")
                }
            }
            "clear hash" -> {
                transpositionTable.clear()
                println("info string Hash table cleared")
            }
            else -> {
                // Try EvalParams tunable (case-sensitive original token form)
                val originalName = if (valueIndex > nameIndex) {
                    tokens.subList(nameIndex + 1, valueIndex).joinToString(" ")
                } else {
                    tokens.subList(nameIndex + 1, tokens.size).joinToString(" ")
                }
                val intVal = optionValue.toIntOrNull()
                if (intVal != null && EvalParams.setParam(originalName, intVal)) {
                    println("info string EvalParam $originalName set to $intVal")
                } else {
                    println("info string Unknown option: $optionName")
                }
            }
        }

        flushStdout()
    }

    private fun handleGetParam(tokens: List<String>) {
        if (tokens.size < 2) {
            println("info string Usage: getparam <NAME>")
            flushStdout()
            return
        }
        val name = tokens[1]
        val v = EvalParams.getParam(name)
        if (v == null) {
            println("info string Unknown param: $name")
        } else {
            println("param $name $v")
        }
        flushStdout()
    }

    private fun handleListParams() {
        for (n in EvalParams.listParamNames()) {
            val v = EvalParams.getParam(n) ?: continue
            println("param $n $v")
        }
        flushStdout()
    }

    private fun handleLoadEpd(tokens: List<String>) {
        if (tokens.size < 2) { println("info string Usage: loadepd <path>"); flushStdout(); return }
        val path = tokens.subList(1, tokens.size).joinToString(" ")
        val lines = readFileLines(path)
        if (lines == null) { println("info string Cannot open $path"); flushStdout(); return }
        val states = ArrayList<ChessBitboardGameState>(lines.size)
        val results = ArrayList<Double>(lines.size)
        var skipped = 0
        for (line in lines) {
            val bar = line.indexOf('|')
            if (bar < 0) { skipped++; continue }
            val fen = line.substring(0, bar).trim()
            val res = line.substring(bar + 1).trim().toDoubleOrNull()
            if (res == null) { skipped++; continue }
            try {
                states.add(FenParser.parse(fen))
                results.add(res)
            } catch (e: Exception) { skipped++ }
        }
        texelStates = states.toTypedArray()
        texelResults = DoubleArray(results.size) { results[it] }
        println("info string loaded ${states.size} positions (skipped $skipped)")
        println("loaded ${states.size}")
        flushStdout()
    }

    private fun handleMse(tokens: List<String>) {
        val states = texelStates
        val results = texelResults
        if (states == null || results == null) {
            println("info string No dataset loaded"); flushStdout(); return
        }
        // Parse optional K (default 1.13). Passed as float string.
        val k = if (tokens.size >= 2) tokens[1].toDoubleOrNull() ?: 1.13 else 1.13
        var err = 0.0
        val n = states.size
        var i = 0
        while (i < n) {
            val ev = ChessEvaluation.evaluate(states[i], 0).toDouble()
            // sigmoid(ev, k) = 1/(1+exp(-k*ev/400))
            val s = 1.0 / (1.0 + kotlin.math.exp(-k * ev / 400.0))
            val d = results[i] - s
            err += d * d
            i++
        }
        val mse = err / n
        println("mse $mse")
        flushStdout()
    }

    private fun handleEval() {
        // ChessEvaluation.evaluate returns a White-POV score (positive = white better).
        val v = ChessEvaluation.evaluate(currentPosition, 0)
        println("eval $v")
        flushStdout()
    }
    
    private fun handleRegister() {
        // BitQueen is free and open source
        println("info string BitQueen is free software")
    }
    
    private fun handleNewGame() {
        transpositionTable.clear()
        currentPosition = FenParser.parse(START_FEN)
        positionHistory.clear()
        positionHistory.add(currentPosition.hash)
        moveHistoryLAN.clear()
        println("info string New game started")
        flushStdout()
    }
    
    private val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    private fun handlePosition(tokens: List<String>) {
        var index = 1
        
        // Parse position
        when {
            index < tokens.size && tokens[index] == "startpos" -> {
                currentPosition = FenParser.parse(START_FEN)
                positionHistory.clear()
                positionHistory.add(currentPosition.hash)
                moveHistoryLAN.clear()
                index++
            }
            index < tokens.size && tokens[index] == "fen" -> {
                index++
                val fenParts = mutableListOf<String>()
                while (index < tokens.size && tokens[index] != "moves") {
                    fenParts.add(tokens[index])
                    index++
                }
                val fen = fenParts.joinToString(" ")
                try {
                    currentPosition = FenParser.parse(fen)
                    positionHistory.clear()
                    positionHistory.add(currentPosition.hash)
                    moveHistoryLAN.clear()
                } catch (e: Exception) {
                    println("info string Invalid FEN: $fen")
                    return
                }
            }
        }
        
        // Apply moves
        if (index < tokens.size && tokens[index] == "moves") {
            index++
            while (index < tokens.size) {
                val moveLAN = tokens[index]
                val move = parseLAN(moveLAN, currentPosition)
                if (move != null) {
                    applyMove(currentPosition, move)
                    positionHistory.add(currentPosition.hash)
                    moveHistoryLAN.add(moveLAN)
                } else {
                    println("info string Invalid move: $moveLAN")
                    return
                }
                index++
            }
        }
    }
    
    private fun handleGo(tokens: List<String>) {
        if (isSearching.get()) {
            println("info string Already searching")
            return
        }
        
        var searchDepth = 100 // Default high depth for time-managed search
        var wtime: Long? = null
        var btime: Long? = null
        var winc: Long? = null
        var binc: Long? = null
        var movestogo: Int? = null
        var movetime: Long? = null
        var infinite = false
        var ponder = false
        
        var i = 1
        while (i < tokens.size) {
            when (tokens[i].lowercase()) {
                "depth" -> {
                    i++
                    if (i < tokens.size) searchDepth = tokens[i].toIntOrNull() ?: searchDepth
                }
                "wtime" -> {
                    i++
                    if (i < tokens.size) wtime = tokens[i].toLongOrNull()
                }
                "btime" -> {
                    i++
                    if (i < tokens.size) btime = tokens[i].toLongOrNull()
                }
                "winc" -> {
                    i++
                    if (i < tokens.size) winc = tokens[i].toLongOrNull()
                }
                "binc" -> {
                    i++
                    if (i < tokens.size) binc = tokens[i].toLongOrNull()
                }
                "movestogo" -> {
                    i++
                    if (i < tokens.size) movestogo = tokens[i].toIntOrNull()
                }
                "movetime" -> {
                    i++
                    if (i < tokens.size) movetime = tokens[i].toLongOrNull()
                }
                "infinite" -> infinite = true
                "ponder" -> ponder = true
            }
            i++
        }
        
        // Calculate time limit
        val timeLimit = when {
            movetime != null -> movetime
            infinite -> Long.MAX_VALUE
            else -> {
                val myTime = if (currentPosition.whiteToMove) wtime else btime
                val myInc = if (currentPosition.whiteToMove) winc else binc

                if (myTime != null) {
                    calculateTimeLimit(myTime, myInc ?: 0, movestogo)
                } else {
                    5000L // Default 5 seconds
                }
            }
        }
        
        isSearching.set(true)
        
        searchJob = scope.launch {
            try {
                // Check opening book first
                if (options.ownBook) {
                    val bookMove = bookManager.getBookMove(moveHistoryLAN, currentPosition.whiteToMove)
                    if (bookMove != null) {
                        val move = parseLAN(bookMove, currentPosition)
                        if (move != null) {
                            withContext(Dispatchers.IO) {
                                println("info string Opening book move")
                                println("bestmove $bookMove")
                                flushStdout()
                            }
                            isSearching.set(false)
                            return@launch
                        }
                    }
                }
                
                // Start search
                // Note: positionHistory includes currentPosition.hash. 
                // startSearch adds state.hash to stack automatically, so we drop the last one to avoid duplication (which triggers immediate draw).
                val historyForSearch = if (positionHistory.isNotEmpty()) positionHistory.dropLast(1) else emptyList()
                
                search.startSearch(
                    state = currentPosition,
                    history = historyForSearch,
                    timeLimitMs = timeLimit,
                    maxDepth = searchDepth,
                    contempt = options.contempt,
                    onProgress = { depth ->
                        val score = search.currentScore.value
                        val bestMove = search.bestMove.value
                        if (bestMove != null) {
                            val moveStr = bestMove.toLAN()
                            println("info depth $depth score cp $score pv $moveStr")
                            flushStdout()
                        }
                    }
                )
                
                val bestMove = search.bestMove.value
                
                withContext(Dispatchers.IO) {
                    if (bestMove != null) {
                        val moveStr = bestMove.toLAN()
                        val score = search.currentScore.value
                        println("info score cp $score")
                        println("bestmove $moveStr")
                    } else {
                        println("info string No legal moves found")
                        println("bestmove 0000")
                    }
                    flushStdout()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.IO) {
                    println("info string Search error: ${e.message}")
                    println("bestmove 0000")
                    flushStdout()
                }
            } finally {
                isSearching.set(false)
            }
        }
    }
    
    private fun calculateTimeLimit(myTime: Long, myInc: Long, movesToGo: Int?): Long {
        val buffer = options.moveOverhead.toLong()
        val availableTime = (myTime - buffer).coerceAtLeast(100L)

        val limit = when {
            movesToGo != null -> {
                val mtg = movesToGo.coerceAtLeast(1)
                val base = availableTime / mtg
                val incShare = (myInc * 0.75).toLong()
                (base + incShare).coerceAtMost(availableTime / 2)
            }
            else -> {
                availableTime / 20 + (myInc * 0.75).toLong()
            }
        }
        return limit.coerceIn(10L, availableTime)
    }
    
    private fun handleStop() {
        search.stop()
        runBlocking {
            searchJob?.join()
        }
    }
    
    private fun handlePonderHit() {
        // Ponder hit - convert pondering to normal search
        println("info string Ponder hit")
    }
    
    private fun handleQuit() {
        handleStop()
        scope.cancel()
    }
}

fun main(args: Array<String>) {
    val engine = BitQueenUCIEngine()
    engine.start()
}
