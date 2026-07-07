# BitQueen UCI Engine - Build-Anleitung

## Problem

Der automatische Gradle-Build erfordert eine spezifische Java-Version (JDK 17), die möglicherweise nicht auf Ihrem System konfiguriert ist.

## Lösung 1: Build mit IntelliJ IDEA (EMPFOHLEN)

### Schritt 1: Projekt öffnen
1. Öffnen Sie IntelliJ IDEA
2. File → Open
3. Wählen Sie den Ordner: `/Users/peterbetz/Dokumente/Kotlin_2026/BitQueenUCI`
4. Warten Sie, bis Gradle das Projekt synchronisiert hat

### Schritt 2: JAR erstellen
1. Öffnen Sie das Gradle-Panel (rechte Seite)
2. BitQueenUCI → Tasks → build → jar
3. Doppelklick auf "jar"
4. Warten Sie, bis der Build abgeschlossen ist

### Schritt 3: JAR finden und kopieren
Die fertige JAR-Datei finden Sie hier:
```
BitQueenUCI/build/libs/BitQueen-UCI-2.2.jar
```

Kopieren Sie diese nach:
```
/Users/peterbetz/Dokumente/Kotlin_2026/BitQueen-UCI-2.2.jar
```

## Lösung 2: Build mit System-Gradle

Falls Sie Gradle installiert haben:

```bash
cd /Users/peterbetz/Dokumente/Kotlin_2026/BitQueenUCI
gradle wrapper --gradle-version=8.5
./gradlew clean jar
cp build/libs/BitQueen-UCI-2.2.jar /Users/peterbetz/Dokumente/Kotlin_2026/
```

## Lösung 3: Manuelle Kompilierung (Fortgeschrittene)

Falls Sie den Kotlin-Compiler installiert haben:

```bash
cd /Users/peterbetz/Dokumente/Kotlin_2026/BitQueenUCI

# Kotlin-Dateien kompilieren
find src/main/kotlin -name "*.kt" > sources.txt
kotlinc @sources.txt -include-runtime -d BitQueen-UCI-2.2.jar

# JAR kopieren
cp BitQueen-UCI-2.2.jar /Users/peterbetz/Dokumente/Kotlin_2026/
```

## Lösung 4: Vorkompilierte JAR verwenden

Ich erstelle eine vorkompilierte JAR-Datei im Hauptprojekt, die Sie direkt verwenden können.

## Testen der Engine

Sobald Sie die JAR haben:

```bash
cd /Users/peterbetz/Dokumente/Kotlin_2026
java -jar BitQueen-UCI-2.2.jar
```

Dann eingeben:
```
uci
isready
position startpos
go movetime 1000
quit
```

## Alternative: Engine im Hauptprojekt verwenden

Das Schach_KMP-Projekt enthält bereits die vollständige BitQueen Engine 2.2.
Sie können diese direkt im Desktop-Modus verwenden, ohne eine separate UCI-Engine zu erstellen.

## Fehlersuche

### "Cannot find Java 17"
Lösung: Verwenden Sie IntelliJ IDEA, das automatisch das richtige JDK verwendet.

### "Gradle build failed"
Lösung: Verwenden Sie die IntelliJ IDEA-Methode (siehe Lösung 1).

### "kotlinc not found"
Lösung: Installieren Sie IntelliJ IDEA oder verwenden Sie die vorkompilierte JAR.

## Kontakt

Bei Problemen kontaktieren Sie bitte den Entwickler.

**Autor**: Peter Betz  
**Version**: 2.2  
**Datum**: 2026-01-19
