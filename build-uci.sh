#!/bin/bash

# BitQueen UCI Engine - Build Script (KMP Version)
# Erstellt sowohl eine JVM-JAR als auch eine native macOS Binary

echo "========================================="
echo "BitQueen UCI Engine - Build Script"
echo "========================================="
echo ""

# Verzeichnisse
UCI_DIR="/Users/peterbetz/Dokumente/Kotlin_2026/BitQueenUCI"
TARGET_DIR="/Users/peterbetz/Dokumente/Kotlin_2026"

echo "Schritt 1: UCI-Engine (JVM & Native) kompilieren..."
cd "$UCI_DIR"

# Führe Gradle-Build für beide Targets aus
./gradlew jvmJar linkReleaseExecutableMacosArm64 --quiet

BUILD_SUCCESS=false

# 1. JVM JAR prüfen und kopieren
if [ -f "build/libs/BitQueenUCI-jvm-2.2.jar" ]; then
    cp build/libs/BitQueenUCI-jvm-2.2.jar "$TARGET_DIR/BitQueen-UCI-2.2.jar"
    echo "✓ JVM JAR erstellt: $TARGET_DIR/BitQueen-UCI-2.2.jar"
    BUILD_SUCCESS=true
fi

# 2. Native macOS Binary prüfen und kopieren
if [ -f "build/bin/macosArm64/releaseExecutable/BitQueen-UCI.kexe" ]; then
    cp build/bin/macosArm64/releaseExecutable/BitQueen-UCI.kexe "$TARGET_DIR/BitQueen-UCI-macOS"
    chmod +x "$TARGET_DIR/BitQueen-UCI-macOS"
    echo "✓ Native macOS Binary erstellt: $TARGET_DIR/BitQueen-UCI-macOS"
    BUILD_SUCCESS=true
fi

if [ "$BUILD_SUCCESS" = true ]; then
    echo ""
    echo "========================================="
    echo "✓ ERFOLG!"
    echo "========================================="
    echo ""
    echo "Testen der Engine:"
    echo "  JVM:    java -jar $TARGET_DIR/BitQueen-UCI-2.2.jar"
    echo "  Native: $TARGET_DIR/BitQueen-UCI-macOS"
    echo ""
    
    ls -lh "$TARGET_DIR"/BitQueen-UCI*
else
    echo "FEHLER: Build fehlgeschlagen. Bitte prüfen Sie die Fehlermeldungen oben."
    exit 1
fi
