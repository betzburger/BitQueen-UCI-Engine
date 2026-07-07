package de.peterbetz.bitqueen.uci

import de.peterbetz.bitqueen.core.*
import kotlinx.coroutines.*

/**
 * BitQueen UCI Engine v2.2
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
 * @version 2.2
 */

// UCI Options
data class UCIOptions(
    var hashSizeMB: Int = 128,
    var threads: Int = (getProcessorCount() - 1).coerceAtLeast(1),
    var contempt: Int = 0,
    var ponder: Boolean = false,
    var ownBook: Boolean = true,
    var multiPV: Int = 1
)

class BitQueenUCIEngine {
    private val options = UCIOptions()
    private var transpositionTable = TranspositionTable(options.hashSizeMB)
    private var search = ChessMultiCoreSearch(transpositionTable, options.threads)
    private val moveGenerator = ChessBitboardMoveGenerator
    private val bookManager = ChessOpeningBookManager()
    
    private var currentPosition = ChessBitboardGameState()
    private var positionHistory = mutableListOf<ULong>()
    private var moveHistoryLAN = mutableListOf<String>()
    
    private val isSearching = AtomicBoolean(false)
    private var searchJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    fun start() {
        // Unbuffered output is preferred for UCI
        
        println("BitQueen UCI v2.2 by Peter Betz")
        
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
        println("id name BitQueen 2.2")
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
        
        // Clear Hash button
        println("option name Clear Hash type button")
        
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
            "clear hash" -> {
                transpositionTable.clear()
                println("info string Hash table cleared")
            }
        }
        
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
        // Soft time management
        val buffer = 50L // Safety buffer
        val availableTime = (myTime - buffer).coerceAtLeast(100L)
        
        return when {
            movesToGo != null -> {
                // Fixed moves to go
                (availableTime / movesToGo.coerceAtLeast(1)) + (myInc * 0.8).toLong()
            }
            else -> {
                // Proportional time allocation
                (availableTime / 40) + (myInc * 0.8).toLong()
            }
        }.coerceIn(10L, availableTime)
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
