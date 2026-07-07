# BitQueen UCI Chess Engine v4.2

A professional, UCI-compatible chess engine written in Kotlin by Peter Betz.

BitQueen Engine originally came from my Checkers project and was later converted to a Chess engine.

## Features

### Core Engine
- **64-bit Bitboards** with Magic-style Ray Lookups for fast move generation
- **Principal Variation Search (PVS)** with Aspiration Windows
- **Transposition Table** with Zobrist Hashing for position caching
- **Tapered Evaluation** with separate Middle Game and End Game piece-square tables
- **Advanced Pawn Structure** analysis (isolated, doubled, passed pawns)
- **King Safety** evaluation with attacker counting
- **Mobility Evaluation** for knights and sliding pieces
- **SEE Pruning** in Quiescence Search
- **Late Move Pruning** (LMP)
- **Internal Iterative Deepening** (IID)

### Search Features
- **Multi-Core Search** using Lazy SMP parallelization
- **Opening Book** support with common openings
- **Time Management** with soft and hard limits
- **3-fold Repetition** detection
- **50-move Rule** support

### UCI Options
- **Hash** (1-32768 MB): Transposition table size
- **Threads** (1-CPU cores): Number of search threads
- **Contempt** (-100 to 100): Draw/win preference in centipawns
- **OwnBook** (true/false): Use internal opening book
- **MultiPV** (1-5): Number of principal variations to show
- **Clear Hash**: Button to clear transposition table

## Building

### Requirements
- JDK 17 or higher
- Gradle 8.x or higher

### Compile
```bash
cd BitQueenUCI
./gradlew build
```

### Create Executable JAR
```bash
./gradlew jar
```

The executable JAR will be created at: `build/libs/BitQueen-UCI-4.2.jar`

## Usage

### Command Line
```bash
java -jar build/libs/BitQueen-UCI-4.2.jar
```

### With Chess GUIs
BitQueen UCI is compatible with all standard UCI chess GUIs:
- **Arena Chess GUI** (Windows)
- **ChessBase** (Windows/Mac)
- **SCID vs. PC** (Cross-platform)
- **Tarrasch Chess GUI** (Windows)
- **Lucas Chess** (Windows)
- **Cute Chess** (Cross-platform)
- **PyChess** (Linux)

#### Example Setup (Arena):
1. Open Arena
2. Go to "Engines" → "Install New Engine"
3. Select `BitQueen-UCI-4.2.jar`
4. Configure options if desired
5. Start playing!

## UCI Protocol Compliance

BitQueen implements the complete UCI protocol:

### Commands
- `uci` - Engine identification and options
- `isready` - Synchronization
- `ucinewgame` - Start new game
- `position [startpos | fen <fenstring>] moves <move1> ... <moveN>` - Set position
- `go [searchmoves <moves>] [depth <d>] [movetime <t>] [wtime <t>] [btime <t>] [winc <t>] [binc <t>] [movestogo <n>] [infinite]` - Start search
- `stop` - Stop search
- `quit` - Exit engine

### Info Output
The engine outputs standard UCI info strings including:
- `depth` - Current search depth
- `score cp` - Evaluation in centipawns
- `pv` - Principal variation
- `nodes` - Nodes searched
- `nps` - Nodes per second

## Strength

BitQueen 4.2 is estimated at approximately **2700-2800 Elo** (CCRL scale) depending on hardware and time control.

### Optimizations
- Runs efficiently on multi-core systems
- Adapts search depth based on time constraints
- Uses opening book for faster opening play
- Tapered evaluation for accurate endgame play

## Technical Details

### Architecture
```
BitQueen UCI
├── UCI Protocol Handler
│   ├── Command Parser
│   ├── Option Management
│   └── Output Formatter
│
├── Search Engine
│   ├── Principal Variation Search
│   ├── Aspiration Windows
│   ├── Transposition Table
│   ├── Multi-Core Lazy SMP
│   └── Time Management
│
├── Move Generation
│   ├── Bitboard Representation
│   ├── Magic Bitboards
│   └── Legal Move Validation
│
└── Evaluation
    ├── Material Counter
    ├── Piece-Square Tables (Tapered)
    ├── Pawn Structure
    ├── King Safety
    └── Mobility

```

### Performance
Typical performance metrics (on modern hardware):
- **Move Generation**: ~50-100M moves/sec
- **Nodes per Second**: ~1-5M nps (single thread)
- **Multi-Core Efficiency**: 60-80% scaling

## License

BitQueen is free and open-source software.

## Credits

**Author**: Peter Betz  
**Engine**: BitQueen v4.2  
**Year**: 2026

## Version History

### v4.2 (2026)
- **Tuning Infrastructure (Texel Support):** Refactored all evaluation constants into a tunable global `EvalParams` interface. Exposed parameters via UCI options.
- **Tuning Commands:** Added custom UCI command extensions (`getparam`, `listparams`, `loadepd`, `mse`, `eval`) to compute Mean Squared Error (MSE) for automated optimization.
- **Pawn Hash Table:** Implemented a lock-free, per-thread Pawn Hash Table (65,536 entries) to cache pawn structure scores, substantially reducing evaluation overhead.
- **Platform Optimizations:** Native MacOS ARM file reading optimizations via Homebrew integration.

### v4.1 (2026)
- **Singular Extensions:** Dynamically extends search depth by +1 for singularly best moves, verified by probing alternative moves.
- **Improving Heuristic:** Tracked static evaluation across moves to dynamically scale pruning/reduction margins when position is improving.
- **Aspiration Window Tweaks:** Centers the aspiration window on the root TT score with widening search limits.
- **Internal Iterative Reductions (IIR):** Reduces depth by 1 when no TT move is available, saving search nodes.
- **Search Repetition Detection:** Rewrote repetition detection to differentiate in-tree 2-fold loops from global 3-fold repetitions.

### v4.0 (2026)
- **History Heuristic Enhancements:** Added separate history tables for White and Black moves (indexed by side-to-move).
- **Counter-Move Heuristic:** Implemented a `counterMoves` table to track best replies to previous moves.
- **LMR Optimization:** Replaced dynamic LMR calculations with a precalculated logarithmic lookup table.
- **Evaluation Adjustments:** Blocked passed pawn bonus scaling (reduced by 30%) and restricted mobility counts to ignore squares attacked by opponent pawns.
- **Search Pruning Improvements:** Dynamic RFP margins, Razoring margins, and Futility Pruning for quiet moves at shallow depth.

### v3.1 (2026)
- **Mate Score TT Fix:** Standardized mate score representation inside the Transposition Table to prevent depth-dependent anomalies.
- **Null Move Pruning Guard:** Restored Null Move Pruning safety by checking for remaining non-pawn material.
- **Killer Move Comparison Fix:** Corrected killer move comparisons to check move parameters (from, to, flag) instead of object references.
- **Doubled Pawn Detection:** Fixed doubled pawn penalty logic to check for pawns specifically behind the current pawn.

### v3.0 (2026)
- **Endgame Knowledge:** Added insufficient material detection (K vs K, KB vs K, KN vs K, KNN vs K, KB vs KB same color) and scale factors to scale score toward draw with lone minor pieces.
- **Rook Evaluation:** Added bonuses for doubled rooks and rooks on the 7th rank.
- **Skill Level Option:** Introduced a UCI `Skill Level` parameter (1-20) to dynamically weaken play by limiting search depth, adding evaluation noise, and disabling advanced search pruning.
- **Search Pruning:** Introduced Reverse Futility Pruning (RFP) and Razoring at shallow depths.
- **History Aging:** Implemented gravity-based aging for history tables to prevent value overflow.

### v2.2 (2026)
- Multi-core search implementation (Lazy SMP)
- Improved time management
- King safety evaluation
- Mobility evaluation
- SEE pruning in quiescence
- Late move pruning
- Internal iterative deepening
- UCI protocol enhancements

### v2.1 (2025)
- Advanced pawn structure evaluation
- Tapered evaluation
- Opening book support
- 3-fold repetition detection

### v2.0 (2025)
- Initial bitboard implementation
- Principal variation search
- Transposition table
- Basic evaluation

## Support

For questions or issues, please contact the author.

Happy Chess Playing! ♟️
