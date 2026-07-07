#!/bin/sh

# BitQueen UCI Engine Build Script
# This script builds the BitQueen UCI chess engine

echo "Building BitQueen UCI Engine v2.2..."
echo ""

# Check for Java
if ! command -v java &> /dev/null; then
    echo "Error: Java not found. Please install JDK 17 or higher."
    exit 1
fi

# Create build directory
mkdir -p build/classes
mkdir -p build/libs

echo "Compiling Kotlin sources..."

# Find all Kotlin files
find src/main/kotlin -name "*.kt" > sources.txt

# Compile (using kotlinc if available, otherwise suggest manual compilation)
if command -v kotlinc &> /dev/null; then
    kotlinc -classpath ~/.m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.9.0/kotlinx-coroutines-core-jvm-1.9.0.jar \
            -d build/classes \
            @sources.txt
    
    echo "Creating JAR file..."
    
    # Create manifest
    cat > build/MANIFEST.MF << EOF
Manifest-Version: 1.0
Main-Class: de.peterbetz.bitqueen.uci.BitQueenUCIKt
EOF
    
    # Create JAR
    cd build/classes
    jar cfm ../libs/BitQueen-UCI-2.2.jar ../MANIFEST.MF de/
    cd ../..
    
    echo ""
    echo "Build successful!"
    echo "Executable JAR created at: build/libs/BitQueen-UCI-2.2.jar"
    echo ""
    echo "To run: java -jar build/libs/BitQueen-UCI-2.2.jar"
    
else
    echo ""
    echo "kotlinc not found. Please use Gradle or IntelliJ IDEA to build:"
    echo "  ./gradlew jar"
    echo ""
fi

rm -f sources.txt
