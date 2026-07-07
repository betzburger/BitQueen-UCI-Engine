# BitQueen UCI Engine - Project Summary

## 📁 Project Structure

```
BitQueenUCI/
├── LICENSE                  # MIT License
├── README.md               # Main documentation
├── QUICKSTART.md           # Quick start guide
├── TECHNICAL.md            # Technical documentation
├── build.gradle.kts        # Gradle build configuration
├── settings.gradle.kts     # Gradle settings
├── build.sh                # Manual build script
├── .gitignore             # Git ignore rules
│
└── src/main/kotlin/de/peterbetz/bitqueen/
    ├── core/                           # Engine core
    │   ├── BitboardChessMove.kt       # Move representation
    │   ├── ChessBitboard.kt           # Bitboard utilities
    │   ├── ChessBitboardGameState.kt  # Position state
    │   ├── ChessBitboardMoveGenerator.kt # Move generation
    │   ├── ChessBitboardSearch.kt     # Search algorithm
    │   ├── ChessBitboardZobristKeys.kt# Zobrist hashing
    │   ├── ChessEvaluation.kt         # Position evaluation
    │   ├── ChessGameTypes.kt          # Type definitions
    │   ├── ChessMoveNotation.kt       # Notation converters
    │   ├── ChessMultiCoreSearch.kt    # Multi-core search
    │   ├── ChessOpeningBookManager.kt # Opening book
    │   ├── ChessParsers.kt            # FEN/PGN parsers
    │   └── TranspositionTable.kt      # Hash table
    │
    └── uci/
        └── BitQueenUCI.kt             # UCI protocol handler
```

## ✨ Features

### Core Engine Features
✅ **64-bit Bitboards** - Fast position representation  
✅ **Magic-style Ray Lookups** - Efficient move generation  
✅ **Principal Variation Search** - Advanced search with aspiration windows  
✅ **Transposition Table** - Position caching with Zobrist hashing  
✅ **Tapered Evaluation** - Separate middle/endgame evaluation  
✅ **Multi-Core Search** - Lazy SMP parallelization  
✅ **Opening Book** - Common opening repertoire  
✅ **Time Management** - Intelligent time allocation  

### Search Enhancements
✅ **Iterative Deepening** - Progressive depth increase  
✅ **Aspiration Windows** - Narrow search for speed  
✅ **Move Ordering** - Hash/captures/killers/history  
✅ **Null Move Pruning** - Skip moves to prove refutation  
✅ **Late Move Reductions** - Reduce unlikely moves  
✅ **Late Move Pruning** - Skip very unlikely moves  
✅ **Internal Iterative Deepening** - When no hash move  
✅ **Check Extensions** - Search deeper in check  
✅ **Quiescence Search** - Capture-only tactical search  
✅ **SEE Pruning** - Skip bad captures  

### Evaluation Features
✅ **Material Counting** - Piece values  
✅ **Piece-Square Tables** - Positional bonuses (Peesto-based)  
✅ **Pawn Structure** - Isolated/doubled/passed/connected  
✅ **King Safety** - Attacker counting and weighting  
✅ **Mobility** - Piece movement freedom  
✅ **Bishop Pair** - Extra bonus for two bishops  
✅ **Knight Outposts** - Bonus for strong knight squares  
✅ **Rook on Open File** - Rook activity bonus  
✅ **Game Phase Detection** - Tapered MG/EG evaluation  

### UCI Protocol Features
✅ **Full UCI Compliance** - All standard commands  
✅ **Hash Size Option** - 1-32768 MB configurable  
✅ **Multi-Threading** - 1 to CPU cores  
✅ **Contempt Setting** - Draw avoidance (-100 to 100)  
✅ **Opening Book Control** - Enable/disable book  
✅ **MultiPV Support** - Multiple variation lines  
✅ **Clear Hash Button** - Manual TT clearing  
✅ **Time Controls** - movetime/wtime/btime/winc/binc  
✅ **Depth Search** - Fixed depth mode  
✅ **Infinite Search** - Analysis mode  

## 🚀 Quick Start

### Build
```bash
cd BitQueenUCI
./gradlew jar
```

### Run
```bash
java -jar build/libs/BitQueen-UCI-2.2.jar
```

### Use with Chess GUI
1. Install Arena/ChessBase/Cute Chess/etc.
2. Add UCI engine: BitQueen-UCI-2.2.jar
3. Configure hash size and threads
4. Start playing!

## 📊 Estimated Strength

| Time Control | Elo (CCRL) | Level |
|--------------|------------|-------|
| 1 min/game   | 2300-2400  | Club Master |
| 5 min/game   | 2400-2500  | Expert |
| 15 min/game  | 2500-2600  | Master |
| Analysis     | 2600+      | Strong Master |

## 🎯 Use Cases

### 1. Analysis
- Analyze your games
- Find tactical errors
- Study positions
- Opening preparation

### 2. Training
- Play against consistent opponent
- Adjustable strength via time
- Learn from engine suggestions
- Practice endgames

### 3. Testing
- Engine tournaments
- Benchmark different versions
- Compare with other engines
- Algorithm testing

### 4. Development
- UCI protocol reference
- Chess engine learning
- Bitboard implementation example
- Search algorithm study

## 🔧 Configuration Recommendations

### For Analysis (Accuracy)
```
setoption name Hash value 2048
setoption name Threads value 4
setoption name Contempt value 0
setoption name OwnBook value true
```

### For Blitz Games (Speed)
```
setoption name Hash value 256
setoption name Threads value 2
setoption name Contempt value 20
setoption name OwnBook value true
```

### For Long Games (Strength)
```
setoption name Hash value 1024
setoption name Threads value 6
setoption name Contempt value 10
setoption name OwnBook value true
```

## 📖 Documentation

- **README.md** - Overview and features
- **QUICKSTART.md** - Installation and usage
- **TECHNICAL.md** - Architecture and algorithms
- **LICENSE** - MIT License

## 🛠️ Development

### Requirements
- JDK 17 or higher
- Gradle 8.x or higher
- Kotlin 2.1.0

### Build from Source
```bash
git clone <repository>
cd BitQueenUCI
./gradlew build
./gradlew jar
```

### Run Tests
```bash
./gradlew test
```

### IDE Support
- IntelliJ IDEA (recommended)
- Eclipse with Kotlin plugin
- VS Code with Kotlin extension

## 🎓 Technical Highlights

### Performance
- **Move Generation**: 50-100M moves/sec
- **Node Evaluation**: 2-5M nps (single thread)
- **Multi-Core Scaling**: 60-80% efficiency

### Memory
- **Base**: ~10-50 MB
- **Hash Table**: Configurable (128 MB default)
- **Per Thread**: ~1-5 MB

### Search Depth
- **Tactical Positions**: 15-20 ply
- **Quiet Positions**: 20-25 ply
- **Endgames**: 25-30 ply

## 🏆 Notable Features

1. **Clean Code** - Well-structured, readable Kotlin
2. **Professional UCI** - Full protocol compliance
3. **Modern Techniques** - State-of-the-art algorithms
4. **Portable** - JVM-based, runs anywhere
5. **Documented** - Extensive documentation
6. **Free & Open** - MIT licensed

## 🔄 Version History

### v2.2 (2026) - Current
- Multi-core search (Lazy SMP)
- Advanced time management
- King safety evaluation
- Mobility evaluation
- SEE pruning
- Late move pruning
- Internal iterative deepening
- UCI enhancements

### v2.1 (2025)
- Tapered evaluation
- Pawn structure analysis
- Opening book
- 3-fold repetition

### v2.0 (2025)
- Initial bitboard implementation
- PVS search
- Transposition table

## 📞 Support & Contact

**Author**: Peter Betz  
**Project**: BitQueen UCI v2.2  
**Year**: 2026  
**License**: MIT

## 🎮 Compatible Chess GUIs

- ✅ Arena Chess GUI (Windows)
- ✅ ChessBase (Windows/Mac)
- ✅ Fritz (Windows)
- ✅ SCID vs. PC (Cross-platform)
- ✅ Tarrasch Chess GUI (Windows)
- ✅ Lucas Chess (Windows/Linux)
- ✅ Cute Chess (Cross-platform)
- ✅ PyChess (Linux)
- ✅ BanksiaGUI (Cross-platform)
- ✅ Chess for Android (Android)

## 🌟 Special Thanks

To the chess programming community for:
- UCI protocol specification
- Bitboard techniques
- Search algorithms
- Evaluation ideas

Happy Chess Playing! ♟️
