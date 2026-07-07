package de.peterbetz.bitqueen.core

import kotlin.math.abs
import kotlin.math.max

/**
 * The core move generator using Bitboards.
 * Port from Swift: ChessBitboardMoveGenerator
 */
object ChessBitboardMoveGenerator {

    // MARK: - Precomputed Tables
    private val knightAttacks = ULongArray(64)
    private val kingAttacks = ULongArray(64)

    // Rays [Square][Direction 0-7]
    // Directions: 0:N, 1:NE, 2:E, 3:SE, 4:S, 5:SW, 6:W, 7:NW
    private val rays = Array(64) { ULongArray(8) }

    init {
        precomputeLeapers()
        precomputeRays()
    }

    // MARK: - API

    fun generateMoves(state: ChessBitboardGameState): List<BitboardChessMove> {
        val moves = ArrayList<BitboardChessMove>(50)

        val us = if (state.whiteToMove) state.whiteOccupied else state.blackOccupied
        val them = if (state.whiteToMove) state.blackOccupied else state.whiteOccupied
        val occupied = us or them

        // 1. Pawns
        generatePawnMoves(state, us, them, occupied, moves)

        // 2. Knights
        val knights = if (state.whiteToMove) state.wN else state.bN
        var kn = knights
        while (true) {
            val (newBb, from) = kn.popLSB()
            kn = newBb
            if (from == null) break
            
            val attacks = ChessBitboard(knightAttacks[from!!]) and us.inv()
            var att = attacks
            while (true) {
                val (newAtt, to) = att.popLSB()
                att = newAtt
                if (to == null) break
                
                val isCap = them.isSet(to!!)
                moves.add(BitboardChessMove(from, to, if (isCap) ChessMoveFlag.CAPTURE else ChessMoveFlag.QUIET))
            }
        }

        // 3. Kings
        val king = if (state.whiteToMove) state.wK else state.bK
        val kSq = king.lsbIndex
        if (kSq != null) {
            val attacks = ChessBitboard(kingAttacks[kSq]) and us.inv()
            var att = attacks
            while (true) {
                val (newAtt, to) = att.popLSB()
                att = newAtt
                if (to == null) break
                
                val isCap = them.isSet(to!!)
                moves.add(BitboardChessMove(kSq, to, if (isCap) ChessMoveFlag.CAPTURE else ChessMoveFlag.QUIET))
            }

            // Castling
            generateCastling(state, occupied, moves)
        }

        // 4. Sliders (Bishops, Rooks, Queens)
        val bishops = if (state.whiteToMove) state.wB else state.bB
        val rooks = if (state.whiteToMove) state.wR else state.bR
        val queens = if (state.whiteToMove) state.wQ else state.bQ

        // Bishops & Queens (Diagonal)
        var bq = bishops or queens
        while (true) {
            val (newBq, from) = bq.popLSB()
            bq = newBq
            if (from == null) break
            
            val attacks = getBishopAttacks(from!!, occupied) and us.inv()
            var att = attacks
            while (true) {
                val (newAtt, to) = att.popLSB()
                att = newAtt
                if (to == null) break
                
                val isCap = them.isSet(to!!)
                moves.add(BitboardChessMove(from, to, if (isCap) ChessMoveFlag.CAPTURE else ChessMoveFlag.QUIET))
            }
        }

        // Rooks & Queens (Orthogonal)
        var rq = rooks or queens
        while (true) {
            val (newRq, from) = rq.popLSB()
            rq = newRq
            if (from == null) break
            
            val attacks = getRookAttacks(from!!, occupied) and us.inv()
            var att = attacks
            while (true) {
                val (newAtt, to) = att.popLSB()
                att = newAtt
                if (to == null) break
                
                val isCap = them.isSet(to!!)
                moves.add(BitboardChessMove(from, to, if (isCap) ChessMoveFlag.CAPTURE else ChessMoveFlag.QUIET))
            }
        }

        return moves
    }

    // MARK: - Logic

    private fun generatePawnMoves(
        state: ChessBitboardGameState,
        us: ChessBitboard,
        them: ChessBitboard,
        occupied: ChessBitboard,
        moves: MutableList<BitboardChessMove>
    ) {
        val white = state.whiteToMove
        val pawns = if (white) state.wP else state.bP
        val singlePushOffset = if (white) 8 else -8

        var p = pawns
        while (true) {
            val (newP, from) = p.popLSB()
            p = newP
            if (from == null) break
            
            val rank = from!! / 8
            val isPromoRank = if (white) (rank == 6) else (rank == 1)

            // 1. Single Push
            val toOne = from + singlePushOffset
            if (toOne in 0..63 && !occupied.isSet(toOne)) {
                if (isPromoRank) {
                    addPromotions(from, toOne, false, moves)
                } else {
                    moves.add(BitboardChessMove(from, toOne, ChessMoveFlag.QUIET))

                    // 2. Double Push
                    val startRank = if (white) 1 else 6
                    if (rank == startRank) {
                        val toTwo = from + (singlePushOffset * 2)
                        if (!occupied.isSet(toTwo)) {
                            moves.add(BitboardChessMove(from, toTwo, ChessMoveFlag.DOUBLE_PAWN_PUSH))
                        }
                    }
                }
            }

            // 3. Captures
            val attacks = getPawnAttacks(from, white)

            // Normal Captures
            var capTargets = attacks and them
            while (true) {
                val (newCap, to) = capTargets.popLSB()
                capTargets = newCap
                if (to == null) break
                
                if (isPromoRank) {
                    addPromotions(from, to!!, true, moves)
                } else {
                    moves.add(BitboardChessMove(from, to!!, ChessMoveFlag.CAPTURE))
                }
            }

            // En Passant
            state.enPassantTarget?.let { epSq ->
                if (attacks.isSet(epSq)) {
                    moves.add(BitboardChessMove(from, epSq, ChessMoveFlag.EP_CAPTURE))
                }
            }
        }
    }

    private fun addPromotions(from: Int, to: Int, capture: Boolean, moves: MutableList<BitboardChessMove>) {
        if (capture) {
            moves.add(BitboardChessMove(from, to, ChessMoveFlag.PROMO_QUEEN_CAPTURE))
            moves.add(BitboardChessMove(from, to, ChessMoveFlag.PROMO_ROOK_CAPTURE))
            moves.add(BitboardChessMove(from, to, ChessMoveFlag.PROMO_BISHOP_CAPTURE))
            moves.add(BitboardChessMove(from, to, ChessMoveFlag.PROMO_KNIGHT_CAPTURE))
        } else {
            moves.add(BitboardChessMove(from, to, ChessMoveFlag.PROMO_QUEEN))
            moves.add(BitboardChessMove(from, to, ChessMoveFlag.PROMO_ROOK))
            moves.add(BitboardChessMove(from, to, ChessMoveFlag.PROMO_BISHOP))
            moves.add(BitboardChessMove(from, to, ChessMoveFlag.PROMO_KNIGHT))
        }
    }

    fun getKingAttacks(sq: Int): ChessBitboard {
        return ChessBitboard(kingAttacks[sq])
    }

    fun getKnightAttacks(sq: Int): ChessBitboard {
        return ChessBitboard(knightAttacks[sq])
    }

    fun getPawnAttacks(from: Int, white: Boolean): ChessBitboard {
        var bb = 0uL
        val file = from % 8
        val rank = from / 8

        val forwardRank = if (white) rank + 1 else rank - 1
        if (forwardRank < 0 || forwardRank > 7) return ChessBitboard(bb)

        // Left Capture (File - 1)
        if (file > 0) {
            bb = bb or (1uL shl (forwardRank * 8 + (file - 1)))
        }
        // Right Capture (File + 1)
        if (file < 7) {
            bb = bb or (1uL shl (forwardRank * 8 + (file + 1)))
        }
        return ChessBitboard(bb)
    }

    private fun generateCastling(state: ChessBitboardGameState, occupied: ChessBitboard, moves: MutableList<BitboardChessMove>) {
        val white = state.whiteToMove
        val kingSideBit = if (white) 0 else 2
        val queenSideBit = if (white) 1 else 3

        val rank = if (white) 0 else 7
        val kSq = rank * 8 + 4

        // King Side
        if ((state.castlingRights and (1 shl kingSideBit)) != 0) {
            if (!occupied.isSet(kSq + 1) && !occupied.isSet(kSq + 2)) {
                if (!isSquareAttacked(kSq, !white, state) &&
                    !isSquareAttacked(kSq + 1, !white, state) &&
                    !isSquareAttacked(kSq + 2, !white, state)
                ) {
                    moves.add(BitboardChessMove(kSq, kSq + 2, ChessMoveFlag.KING_CASTLE))
                }
            }
        }

        // Queen Side
        if ((state.castlingRights and (1 shl queenSideBit)) != 0) {
            if (!occupied.isSet(kSq - 1) && !occupied.isSet(kSq - 2) && !occupied.isSet(kSq - 3)) {
                if (!isSquareAttacked(kSq, !white, state) &&
                    !isSquareAttacked(kSq - 1, !white, state) &&
                    !isSquareAttacked(kSq - 2, !white, state)
                ) {
                    moves.add(BitboardChessMove(kSq, kSq - 2, ChessMoveFlag.QUEEN_CASTLE))
                }
            }
        }
    }

    fun isSquareAttacked(sq: Int, byWhite: Boolean, state: ChessBitboardGameState): Boolean {
        // Pawn attacks
        val pawnAttacks = getPawnAttacks(sq, !byWhite)
        val pawns = if (byWhite) state.wP else state.bP
        if ((pawnAttacks and pawns).rawValue != 0uL) return true

        // Knight
        val knights = if (byWhite) state.wN else state.bN
        if ((ChessBitboard(knightAttacks[sq]) and knights).rawValue != 0uL) return true

        // King
        val kings = if (byWhite) state.wK else state.bK
        if ((ChessBitboard(kingAttacks[sq]) and kings).rawValue != 0uL) return true
        
        val occupied = state.allOccupied

        // Bishop/Queen
        val bq = if (byWhite) (state.wB or state.wQ) else (state.bB or state.bQ)
        if (bq.rawValue != 0uL) {
             if ((getBishopAttacks(sq, occupied) and bq).rawValue != 0uL) return true
        }

        // Rook/Queen
        val rq = if (byWhite) (state.wR or state.wQ) else (state.bR or state.bQ)
        if (rq.rawValue != 0uL) {
             if ((getRookAttacks(sq, occupied) and rq).rawValue != 0uL) return true
        }

        return false
    }

    // MARK: - Slider Helpers

    fun getBishopAttacks(from: Int, occupied: ChessBitboard): ChessBitboard {
        var attacks: ULong = 0uL
        for (dir in arrayOf(1, 3, 5, 7)) {
            attacks = attacks or getRayAttacks(from, dir, occupied.rawValue)
        }
        return ChessBitboard(attacks)
    }

    fun getRookAttacks(from: Int, occupied: ChessBitboard): ChessBitboard {
        var attacks: ULong = 0uL
        for (dir in arrayOf(0, 2, 4, 6)) {
            attacks = attacks or getRayAttacks(from, dir, occupied.rawValue)
        }
        return ChessBitboard(attacks)
    }

    fun getRayAttacks(sq: Int, dir: Int, occupied: ULong): ULong {
        val ray = rays[sq][dir]
        val blocker = ray and occupied
        if (blocker == 0uL) return ray

        val isPositiveDir = (dir == 0 || dir == 1 || dir == 2 || dir == 7)

        if (isPositiveDir) {
            // LSB isolation: x & -x used to work for Signed, for Unsigned we can use takeLowestOneBit() or similar
            // Java/Kotlin Long.lowestOneBit works on Signed Long interpretations.
            // ULong doesn't have takeLowestOneBit directly in common stdlib maybe?
            // Actually: blocker.takeLowestOneBit() exists in 1.5+.
            val firstBlocker = blocker.takeLowestOneBit() 
            val idx = firstBlocker.countTrailingZeroBits()
            return ray xor rays[idx][dir]
        } else {
            // MSB
            // takeHighestOneBit()
            val firstBlocker = blocker.takeHighestOneBit()
            val idx = firstBlocker.countTrailingZeroBits() // Index of the MSB
            return ray xor rays[idx][dir]
        }
    }

    // MARK: - Precomputation

    private fun precomputeLeapers() {
        val knightOffsets = intArrayOf(6, 10, 15, 17, -6, -10, -15, -17)
        val kingOffsets = intArrayOf(1, 7, 8, 9, -1, -7, -8, -9)

        for (sq in 0 until 64) {
            // Knight
            var kn: ULong = 0uL
            for (off in knightOffsets) {
                val to = sq + off
                if (to in 0..63 && distance(sq, to) <= 2) {
                    kn = kn or (1uL shl to)
                }
            }
            knightAttacks[sq] = kn

            // King
            var ki: ULong = 0uL
            for (off in kingOffsets) {
                val to = sq + off
                if (to in 0..63 && distance(sq, to) == 1) {
                    ki = ki or (1uL shl to)
                }
            }
            kingAttacks[sq] = ki
        }
    }

    private fun precomputeRays() {
        val offsets = intArrayOf(8, 9, 1, -7, -8, -9, -1, 7)

        for (sq in 0 until 64) {
            for ((i, off) in offsets.withIndex()) {
                var ray: ULong = 0uL
                var curr = sq
                while (true) {
                    val file = curr % 8
                    val dest = curr + off

                    if (dest < 0 || dest >= 64) break

                    val destFile = dest % 8
                    if (abs(file - destFile) > 1) break

                    ray = ray or (1uL shl dest)
                    curr = dest
                }
                rays[sq][i] = ray
            }
        }
    }

    private fun distance(a: Int, b: Int): Int {
        val ra = a / 8; val fa = a % 8
        val rb = b / 8; val fb = b % 8
        return max(abs(ra - rb), abs(fa - fb))
    }
}
