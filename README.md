# BitQueen UCI Chess Engine v2.2

A professional, UCI-compatible chess engine written in Kotlin by Peter Betz.

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

The executable JAR will be created at: `build/libs/BitQueen-UCI-2.2.jar`

## Usage

### Command Line
```bash
java -jar build/libs/BitQueen-UCI-2.2.jar
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
3. Select `BitQueen-UCI-2.2.jar`
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

BitQueen 2.2 is estimated at approximately **2400-2600 Elo** (CCRL scale) depending on hardware and time control.

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
**Engine**: BitQueen v2.2  
**Year**: 2026

## Version History

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
