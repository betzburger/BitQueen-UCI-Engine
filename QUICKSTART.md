# BitQueen UCI Engine - Quick Start Guide

## Installation

### Option 1: Using Pre-Built Gradle Project
If you have IntelliJ IDEA or Gradle installed:

```bash
cd BitQueenUCI
./gradlew jar
```

The JAR will be at: `build/libs/BitQueen-UCI-2.2.jar`

### Option 2: Manual Build
If you have Kotlin compiler installed:

```bash
cd BitQueenUCI
./build.sh
```

### Option 3: Import into IDE
1. Open IntelliJ IDEA
2. File → Open → Select `BitQueenUCI` folder
3. Wait for Gradle sync
4. Build → Build Project
5. Run → Run 'BitQueenUCI'

## Testing the Engine

### Command Line Test
```bash
java -jar build/libs/BitQueen-UCI-2.2.jar
```

Then type UCI commands:
```
uci
isready
ucinewgame
position startpos moves e2e4
go movetime 1000
quit
```

### With Arena Chess GUI (Windows)

1. **Download Arena**: http://www.playwitharena.com/
2. **Install Engine**:
   - Engines → Install New Engine
   - Select `BitQueen-UCI-2.2.jar`
   - Type: UCI
   
3. **Configure Options**:
   - Engines → Manage → BitQueen 2.2
   - Set Hash size (recommended: 128-512 MB)
   - Set Threads (use CPU cores - 1)
   
4. **Play**:
   - Level → New → Select BitQueen 2.2
   - Start playing!

### With ChessBase (Windows/Mac)

1. **Install Engine**:
   - Home → Add Engine
   - Browse to `BitQueen-UCI-2.2.jar`
   
2. **Configure**:
   - Right-click engine → Properties
   - Set Hash and Threads
   
3. **Analyze**:
   - Open a game
   - Right-click → Engine Analysis
   - Select BitQueen

### With Cute Chess GUI (Cross-platform)

1. **Download**: https://cutechess.com/
2. **Add Engine**:
   - Tools → Settings → Engines
   - Add → Name: BitQueen 2.2
   - Command: `java`
   - Arguments: `-jar /full/path/to/BitQueen-UCI-2.2.jar`
   - Protocol: UCI
   
3. **Tournament**:
   - Tools → Tournament
   - Add BitQueen and other engines
   - Play!

### With SCID vs. PC

1. **Add Engine**:
   - Tools → Analysis Engines
   - New → UCI Engine
   - Select JAR file
   
2. **Use**:
   - Tools → Start Engine #1
   - Engine will analyze your games

## UCI Command Reference

### Basic Commands
```
uci                    # Initialize engine
isready               # Check if ready
ucinewgame           # Start new game
position startpos     # Set start position
position fen <fen>    # Set position from FEN
go depth 10          # Search to depth 10
go movetime 5000     # Search for 5 seconds
go wtime 300000 btime 300000  # Time control
stop                  # Stop search
quit                  # Exit
```

### Options
```
setoption name Hash value 256          # Set hash size
setoption name Threads value 4         # Set thread count
setoption name Contempt value 10       # Set contempt
setoption name OwnBook value true      # Enable book
setoption name Clear Hash             # Clear hash table
```

## Performance Tips

### Optimal Settings

**For Analysis (High Accuracy)**:
- Hash: 1024-4096 MB
- Threads: CPU cores - 1
- Contempt: 0

**For Blitz Games**:
- Hash: 128-256 MB
- Threads: 2-4
- Contempt: 10 (aggressive)

**For Long Games**:
- Hash: 512-2048 MB
- Threads: CPU cores - 1
- Contempt: 0-20

### Expected Strength

| Time Control | Approximate Elo |
|--------------|----------------|
| 1 min/game   | 2300-2400     |
| 5 min/game   | 2400-2500     |
| 15 min/game  | 2500-2600     |
| Fixed depth 20| 2600+         |

## Troubleshooting

### Engine Not Starting
- **Check Java**: `java -version` (need JDK 17+)
- **Check JAR**: File exists and is executable
- **Permissions**: Make sure JAR is not blocked

### Slow Performance
- **Increase Hash**: More hash = better performance
- **Reduce Threads**: Too many threads can slow down
- **Check CPU**: Background processes affecting speed

### GUI Not Recognizing Engine
- **Use full path**: Don't use relative paths
- **Check protocol**: Must be UCI (not XBoard)
- **Test manually**: Run from command line first

## Advanced Usage

### Creating Engine vs Engine Matches

```bash
# Using cutechess-cli
cutechess-cli \
  -engine cmd=java arg=-jar arg=BitQueen-UCI-2.2.jar name="BitQueen" \
  -engine cmd=stockfish name="Stockfish" \
  -each tc=60+0.6 \
  -games 100 \
  -repeat \
  -pgnout games.pgn
```

### Running Analysis

```bash
# Position analysis
echo "position startpos moves e2e4 e7e5
go movetime 10000" | java -jar BitQueen-UCI-2.2.jar
```

## Support

For bugs or feature requests, contact the author.

**Author**: Peter Betz  
**Version**: 2.2  
**Year**: 2026

Good luck and have fun! ♟️
