package de.peterbetz.bitqueen.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lazy SMP (Symmetric Multi-Processing) Search Wrapper.
 * Runs multiple search threads sharing the same Transposition Table.
 */
class ChessMultiCoreSearch(
    private val tt: TranspositionTable,
    private val threadCount: Int = 1
) {
    private val workers = List(threadCount) { ChessBitboardSearch(tt) }
    
    var skillLevel: Int = 20

    private val _bestMove = MutableStateFlow<BitboardChessMove?>(null)
    val bestMove = _bestMove.asStateFlow()
    
    private val _currentDepth = MutableStateFlow(0)
    val currentDepth = _currentDepth.asStateFlow()
    
    private val _currentScore = MutableStateFlow(0)
    val currentScore = _currentScore.asStateFlow()

    suspend fun startSearch(
        state: ChessBitboardGameState,
        history: List<ULong>,
        timeLimitMs: Long,
        maxDepth: Int,
        contempt: Int = 0,
        onProgress: ((Int) -> Unit)? = null
    ) = coroutineScope {
        // (debug log removed — non-UCI-compliant output broke GUI parsers)
        _bestMove.value = null
        _currentDepth.value = 0
        
        // Set contempt and skill level on all workers
        workers.forEach { it.contempt = contempt; it.skillLevel = skillLevel }
        
        val jobs = mutableListOf<Job>()
        
        // Launch primary worker
        val primary = workers[0]
        jobs += launch(Dispatchers.Default) {
            // Observe depth from primary
            val depthJob = launch {
                primary.currentDepth.collect { _currentDepth.value = it }
            }
            val scoreJob = launch {
                primary.currentScore.collect { _currentScore.value = it }
            }
            
            primary.startSearch(state, history, timeLimitMs, null, maxDepth, onProgress = onProgress)
            _bestMove.value = primary.bestMove.value
            
            depthJob.cancel()
            scoreJob.cancel()
            
            // Cancel others when primary is done
            jobs.forEach { if (it != this) it.cancel() }
        }
        
        // Launch helper workers with depth diversity for better TT filling
        for (i in 1 until workers.size) {
            val worker = workers[i]
            // Alternate: odd helpers search +1 depth, even helpers search same depth
            // This explores different parts of the search tree
            val depthOffset = if (i % 2 == 1) 1 else 0
            val helperMaxDepth = maxDepth + depthOffset
            jobs += launch(Dispatchers.Default) {
                worker.startSearch(state, history, timeLimitMs, null, helperMaxDepth, output = false)
            }
        }
        
        try {
            jobs.joinAll()
        } finally {
            stop()
        }
    }
    
    fun stop() {
        workers.forEach { it.stop() }
    }

    fun inCheck(state: ChessBitboardGameState): Boolean {
        return workers[0].inCheck(state)
    }
}
