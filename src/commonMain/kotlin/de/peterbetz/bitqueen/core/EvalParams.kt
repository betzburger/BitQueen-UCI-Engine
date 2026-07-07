package de.peterbetz.bitqueen.core

/**
 * Tunable evaluation parameters for BitQueen 4.2.
 *
 * All fields are `var` so the Texel tuner can mutate them at runtime via the
 * UCI `setoption` interface. ChessEvaluation reads these values on every call.
 *
 * Non-tunable values (phase weights, bitmasks, MATE_SCORE, loop indices) are
 * intentionally NOT moved here — they are structural, not weights.
 */
object EvalParams {

    // ---- Piece Values (Middlegame / Endgame) ----
    var PAWN_VALUE_MG: Int = 82
    var KNIGHT_VALUE_MG: Int = 337
    var BISHOP_VALUE_MG: Int = 365
    var ROOK_VALUE_MG: Int = 477
    var QUEEN_VALUE_MG: Int = 1025

    var PAWN_VALUE_EG: Int = 94
    var KNIGHT_VALUE_EG: Int = 281
    var BISHOP_VALUE_EG: Int = 297
    var ROOK_VALUE_EG: Int = 512
    var QUEEN_VALUE_EG: Int = 936

    // ---- Piece-Square Tables (from White's perspective, a8..h1 order as in ChessEvaluation) ----
    var PST_PAWN_MG: IntArray = intArrayOf(
         0,   0,   0,   0,   0,   0,   0,   0,
        98, 134,  61,  95,  68, 126,  34, -11,
        -6,   7,  26,  31,  65,  56,  25, -20,
       -14,  13,   6,  21,  23,  12,  17, -23,
       -27,  -2,  -5,  12,  17,   6,  10, -25,
       -26,  -4,  -4, -10,   3,   3,  33, -12,
       -35,  -1, -20, -23, -15,  24,  38, -22,
         0,   0,   0,   0,   0,   0,   0,   0
    )
    var PST_PAWN_EG: IntArray = intArrayOf(
         0,   0,   0,   0,   0,   0,   0,   0,
       178, 173, 158, 134, 147, 132, 165, 187,
        94, 100,  85,  67,  56,  53,  82,  84,
        32,  24,  13,   5,  -2,   4,  17,  17,
        13,   9,  -3,  -7,  -7,  -8,   3,  -1,
         4,   7,  -6,   1,   0,  -5,  -1,  -8,
        13,   8,   8,  10,  13,   0,   2,  -7,
         0,   0,   0,   0,   0,   0,   0,   0
    )
    var PST_KNIGHT_MG: IntArray = intArrayOf(
       -167, -89, -34, -49,  61, -97, -15, -107,
        -73, -41,  72,  36,  23,  62,   7,  -17,
        -47,  60,  37,  65,  84, 129,  73,   44,
         -9,  17,  19,  53,  37,  69,  18,   22,
        -13,   4,  16,  13,  28,  19,  21,   -8,
        -23,  -9,  12,  10,  19,  17,  25,  -16,
        -29, -53, -12,  -3,  -1,  18, -14,  -19,
       -105, -21, -58, -33, -17, -28, -19,  -23
    )
    var PST_KNIGHT_EG: IntArray = intArrayOf(
        -58, -38, -13, -28, -31, -27, -63, -99,
        -25,  -8, -25,  -2,  -9, -25, -24, -52,
        -24, -20,  10,   9,  -1,  -9, -19, -41,
        -17,   3,  22,  22,  22,  11,   8, -18,
        -18,  -6,  16,  25,  16,  17,   4, -18,
        -23,  -3,  -1,  15,  10,  -3, -20, -22,
        -42, -20, -10,  -5,  -2, -20, -23, -44,
        -29, -51, -23, -15, -22, -18, -50, -64
    )
    var PST_BISHOP_MG: IntArray = intArrayOf(
        -29,   4, -82, -37, -25, -42,   7,  -8,
        -26,  16, -18, -13,  30,  59,  18, -47,
        -16,  37,  43,  40,  35,  50,  37,  -2,
         -4,   5,  19,  50,  37,  37,   7,  -2,
         -6,  13,  13,  26,  34,  12,  10,   4,
          0,  15,  15,  15,  14,  27,  18,  10,
          4,  15,  16,   0,   7,  21,  33,   1,
        -33,  -3, -14, -21, -13, -12, -39, -21
    )
    var PST_BISHOP_EG: IntArray = intArrayOf(
        -14, -21, -11,  -8, -7,  -9, -17, -24,
         -8,  -4,   7, -12, -3, -13,  -4, -14,
          2,  -8,   0,  -1, -2,   6,   0,   4,
         -3,   9,  12,   9, 14,  10,   3,   2,
         -6,   3,  13,  19,  7,  10,  -3,  -9,
        -12,  -3,   8,  10, 13,   3,  -7, -15,
        -14, -18,  -7,  -1,  4,  -9, -15, -27,
        -23,  -9, -23,  -5, -9, -16,  -5, -17
    )
    var PST_ROOK_MG: IntArray = intArrayOf(
         32,  42,  32,  51, 63,  9,  31,  43,
         27,  32,  58,  62, 80, 67,  26,  44,
         -5,  19,  26,  36, 17, 45,  61,  16,
        -24, -11,   7,  26, 24, 35,  -8, -20,
        -36, -26, -12,  -1,  9, -7,   6, -23,
        -45, -25, -16, -17,  3,  0,  -5, -33,
        -44, -16, -20,  -9, -1, 11,  -6, -71,
        -19, -13,   1,  17, 16,  7, -37, -26
    )
    var PST_ROOK_EG: IntArray = intArrayOf(
        13, 10, 18, 15, 12,  12,   8,   5,
        11, 13, 13, 11, -3,   3,   8,   3,
         7,  7,  7,  5,  4,  -3,  -5,  -3,
         4,  3, 13,  1,  2,   1,  -1,   2,
         3,  5,  8,  4, -5,  -6,  -8, -11,
        -4,  0, -5, -1, -7, -12,  -8, -16,
        -6, -6,  0,  2, -9,  -9, -11,  -3,
        -9,  2,  3, -1, -5, -13,   4, -20
    )
    var PST_QUEEN_MG: IntArray = intArrayOf(
        -28,   0,  29,  12,  59,  44,  43,  45,
        -24, -39,  -5,   1, -16,  57,  28,  54,
        -13, -17,   7,   8,  29,  56,  47,  57,
        -27, -27, -16, -16,  -1,  17,  -2,   1,
         -9, -26, -9, -10,  -2,  -4,   3,  -3,
        -14,   2, -11,  -2,  -5,   2,  14,   5,
        -35,  -8,  11,   2,   8,  15,  -3,   1,
         -1, -18,  -9, -10, -30, -35, -20, -54
    )
    var PST_QUEEN_EG: IntArray = intArrayOf(
         -9,  22,  22,  27,  27,  19,  10,  20,
        -17,  20,  32,  41,  58,  25,  30,   0,
        -20,   6,   9,  49,  47,  35,  19,   9,
          3,  22,  24,  45,  57,  40,  57,  36,
        -18,  28,  19,  47,  31,  34,  39,  23,
        -16, -27,  15,   6,   9,  17,  10,   5,
        -22, -23, -30, -16, -16, -23, -36, -32,
        -33, -28, -22, -43,  -5, -32, -20, -41
    )
    var PST_KING_MG: IntArray = intArrayOf(
        -65,  23,  16, -15, -56, -34,   2,  13,
         29,  -1, -20,  -7,  -8,  -4, -38, -29,
         -9,  24,   2, -16, -20,   6,  22, -22,
        -17, -20, -12, -27, -30, -25, -14, -36,
        -49,  -1, -27, -39, -46, -44, -33, -51,
        -14, -14, -22, -46, -44, -30, -15, -27,
          1,   7,  -8, -64, -43, -16,   9,   8,
        -15,  36,  12, -54,   8, -28,  24,  14
    )
    var PST_KING_EG: IntArray = intArrayOf(
        -74, -35, -18, -18, -11,  15,   4, -17,
        -12,  17,  14,  17,  17,  38,  23,  11,
         10,  17,  23,  15,  20,  45,  44,  13,
         -8,  22,  24,  27,  26,  33,  26,   3,
        -18,  -4,  21,  24,  27,  23,   9, -11,
        -19,  -3,  11,  21,  23,  16,   7,  -9,
        -27, -11,   4,  13,  14,   4,  -5, -17,
        -53, -34, -21, -11, -28, -14, -24, -43
    )

    // ---- Safety Table (40 entries) ----
    var SAFETY_TABLE: IntArray = intArrayOf(
        0, 0, 0, 2, 5, 8, 12, 18, 25, 35,
        45, 55, 65, 75, 85, 95, 105, 115, 125, 135,
        145, 155, 165, 175, 185, 195, 205, 215, 225, 235,
        245, 255, 265, 275, 285, 295, 300, 300, 300, 300
    )

    // King-attack weights (attack units per attacker type)
    var KING_ATTACK_UNITS_KNIGHT: Int = 2
    var KING_ATTACK_UNITS_BISHOP: Int = 2
    var KING_ATTACK_UNITS_ROOK: Int = 3
    var KING_ATTACK_UNITS_QUEEN: Int = 5

    // ---- Pawn structure ----
    var PAWN_ISOLATED: Int = -15
    var PAWN_DOUBLED: Int = -10
    var PAWN_CONNECTED_MG: Int = 15
    var PAWN_CONNECTED_EG: Int = 20
    var PASSED_BONUS_MG: IntArray = intArrayOf(0, 5, 10, 20, 35, 60, 100, 0)
    var PASSED_BONUS_EG: IntArray = intArrayOf(0, 10, 20, 40, 70, 120, 200, 0)

    // ---- Rooks ----
    var ROOK_OPEN_FILE_MG: Int = 20
    var ROOK_OPEN_FILE_EG: Int = 10
    var ROOK_SEMI_OPEN_MG: Int = 10
    var ROOK_SEMI_OPEN_EG: Int = 5
    var ROOK_ON_7TH_MG: Int = 25
    var ROOK_ON_7TH_EG: Int = 40
    var DOUBLED_ROOKS_MG: Int = 15
    var DOUBLED_ROOKS_EG: Int = 25

    // ---- King safety pawn shield / open files ----
    var PAWN_SHIELD_MISSING: Int = -20
    var KING_OPEN_FILE_OWN: Int = 30
    var KING_OPEN_FILE_ADJ: Int = 15
    var KING_SEMI_OPEN_OWN: Int = 15
    var KING_SEMI_OPEN_ADJ: Int = 8

    // ---- Strategic ----
    var BISHOP_PAIR_MG: Int = 40
    var BISHOP_PAIR_EG: Int = 60
    var BACK_RANK_MINOR: Int = -15
    var KNIGHT_OUTPOST: Int = 30

    // ---- Mobility (per-pt indices 1..4 = N,B,R,Q) ----
    var MOB_KNIGHT_MG: Int = 4
    var MOB_BISHOP_MG: Int = 3
    var MOB_ROOK_MG: Int = 2
    var MOB_QUEEN_MG: Int = 1
    var MOB_KNIGHT_EG: Int = 4
    var MOB_BISHOP_EG: Int = 3
    var MOB_ROOK_EG: Int = 4
    var MOB_QUEEN_EG: Int = 2

    // ---- Tropism ----
    var TROPISM_KNIGHT_MG: Int = 3
    var TROPISM_KNIGHT_EG: Int = 2
    var TROPISM_ROOK_MG: Int = 2
    var TROPISM_ROOK_EG: Int = 2
    var TROPISM_QUEEN_MG: Int = 2
    var TROPISM_QUEEN_EG: Int = 1

    // ---- Tempo ----
    var TEMPO: Int = 10

    // ---- Setter (used by UCI setoption) ----
    // Returns true if the parameter name was recognized and set.
    fun setParam(name: String, value: Int): Boolean {
        // Scalars first
        when (name) {
            "PAWN_VALUE_MG" -> { PAWN_VALUE_MG = value; return true }
            "KNIGHT_VALUE_MG" -> { KNIGHT_VALUE_MG = value; return true }
            "BISHOP_VALUE_MG" -> { BISHOP_VALUE_MG = value; return true }
            "ROOK_VALUE_MG" -> { ROOK_VALUE_MG = value; return true }
            "QUEEN_VALUE_MG" -> { QUEEN_VALUE_MG = value; return true }
            "PAWN_VALUE_EG" -> { PAWN_VALUE_EG = value; return true }
            "KNIGHT_VALUE_EG" -> { KNIGHT_VALUE_EG = value; return true }
            "BISHOP_VALUE_EG" -> { BISHOP_VALUE_EG = value; return true }
            "ROOK_VALUE_EG" -> { ROOK_VALUE_EG = value; return true }
            "QUEEN_VALUE_EG" -> { QUEEN_VALUE_EG = value; return true }
            "KING_ATTACK_UNITS_KNIGHT" -> { KING_ATTACK_UNITS_KNIGHT = value; return true }
            "KING_ATTACK_UNITS_BISHOP" -> { KING_ATTACK_UNITS_BISHOP = value; return true }
            "KING_ATTACK_UNITS_ROOK" -> { KING_ATTACK_UNITS_ROOK = value; return true }
            "KING_ATTACK_UNITS_QUEEN" -> { KING_ATTACK_UNITS_QUEEN = value; return true }
            "PAWN_ISOLATED" -> { PAWN_ISOLATED = value; return true }
            "PAWN_DOUBLED" -> { PAWN_DOUBLED = value; return true }
            "PAWN_CONNECTED_MG" -> { PAWN_CONNECTED_MG = value; return true }
            "PAWN_CONNECTED_EG" -> { PAWN_CONNECTED_EG = value; return true }
            "ROOK_OPEN_FILE_MG" -> { ROOK_OPEN_FILE_MG = value; return true }
            "ROOK_OPEN_FILE_EG" -> { ROOK_OPEN_FILE_EG = value; return true }
            "ROOK_SEMI_OPEN_MG" -> { ROOK_SEMI_OPEN_MG = value; return true }
            "ROOK_SEMI_OPEN_EG" -> { ROOK_SEMI_OPEN_EG = value; return true }
            "ROOK_ON_7TH_MG" -> { ROOK_ON_7TH_MG = value; return true }
            "ROOK_ON_7TH_EG" -> { ROOK_ON_7TH_EG = value; return true }
            "DOUBLED_ROOKS_MG" -> { DOUBLED_ROOKS_MG = value; return true }
            "DOUBLED_ROOKS_EG" -> { DOUBLED_ROOKS_EG = value; return true }
            "PAWN_SHIELD_MISSING" -> { PAWN_SHIELD_MISSING = value; return true }
            "KING_OPEN_FILE_OWN" -> { KING_OPEN_FILE_OWN = value; return true }
            "KING_OPEN_FILE_ADJ" -> { KING_OPEN_FILE_ADJ = value; return true }
            "KING_SEMI_OPEN_OWN" -> { KING_SEMI_OPEN_OWN = value; return true }
            "KING_SEMI_OPEN_ADJ" -> { KING_SEMI_OPEN_ADJ = value; return true }
            "BISHOP_PAIR_MG" -> { BISHOP_PAIR_MG = value; return true }
            "BISHOP_PAIR_EG" -> { BISHOP_PAIR_EG = value; return true }
            "BACK_RANK_MINOR" -> { BACK_RANK_MINOR = value; return true }
            "KNIGHT_OUTPOST" -> { KNIGHT_OUTPOST = value; return true }
            "MOB_KNIGHT_MG" -> { MOB_KNIGHT_MG = value; return true }
            "MOB_BISHOP_MG" -> { MOB_BISHOP_MG = value; return true }
            "MOB_ROOK_MG" -> { MOB_ROOK_MG = value; return true }
            "MOB_QUEEN_MG" -> { MOB_QUEEN_MG = value; return true }
            "MOB_KNIGHT_EG" -> { MOB_KNIGHT_EG = value; return true }
            "MOB_BISHOP_EG" -> { MOB_BISHOP_EG = value; return true }
            "MOB_ROOK_EG" -> { MOB_ROOK_EG = value; return true }
            "MOB_QUEEN_EG" -> { MOB_QUEEN_EG = value; return true }
            "TROPISM_KNIGHT_MG" -> { TROPISM_KNIGHT_MG = value; return true }
            "TROPISM_KNIGHT_EG" -> { TROPISM_KNIGHT_EG = value; return true }
            "TROPISM_ROOK_MG" -> { TROPISM_ROOK_MG = value; return true }
            "TROPISM_ROOK_EG" -> { TROPISM_ROOK_EG = value; return true }
            "TROPISM_QUEEN_MG" -> { TROPISM_QUEEN_MG = value; return true }
            "TROPISM_QUEEN_EG" -> { TROPISM_QUEEN_EG = value; return true }
            "TEMPO" -> { TEMPO = value; return true }
        }

        // Indexed array parameters: NAME_<index>
        val under = name.lastIndexOf('_')
        if (under < 0) return false
        val base = name.substring(0, under)
        val idx = name.substring(under + 1).toIntOrNull() ?: return false
        val arr: IntArray = when (base) {
            "PST_PAWN_MG" -> PST_PAWN_MG
            "PST_PAWN_EG" -> PST_PAWN_EG
            "PST_KNIGHT_MG" -> PST_KNIGHT_MG
            "PST_KNIGHT_EG" -> PST_KNIGHT_EG
            "PST_BISHOP_MG" -> PST_BISHOP_MG
            "PST_BISHOP_EG" -> PST_BISHOP_EG
            "PST_ROOK_MG" -> PST_ROOK_MG
            "PST_ROOK_EG" -> PST_ROOK_EG
            "PST_QUEEN_MG" -> PST_QUEEN_MG
            "PST_QUEEN_EG" -> PST_QUEEN_EG
            "PST_KING_MG" -> PST_KING_MG
            "PST_KING_EG" -> PST_KING_EG
            "SAFETY_TABLE" -> SAFETY_TABLE
            "PASSED_BONUS_MG" -> PASSED_BONUS_MG
            "PASSED_BONUS_EG" -> PASSED_BONUS_EG
            else -> return false
        }
        if (idx < 0 || idx >= arr.size) return false
        arr[idx] = value
        return true
    }

    // Returns current value, or null if name is unknown.
    fun getParam(name: String): Int? {
        when (name) {
            "PAWN_VALUE_MG" -> return PAWN_VALUE_MG
            "KNIGHT_VALUE_MG" -> return KNIGHT_VALUE_MG
            "BISHOP_VALUE_MG" -> return BISHOP_VALUE_MG
            "ROOK_VALUE_MG" -> return ROOK_VALUE_MG
            "QUEEN_VALUE_MG" -> return QUEEN_VALUE_MG
            "PAWN_VALUE_EG" -> return PAWN_VALUE_EG
            "KNIGHT_VALUE_EG" -> return KNIGHT_VALUE_EG
            "BISHOP_VALUE_EG" -> return BISHOP_VALUE_EG
            "ROOK_VALUE_EG" -> return ROOK_VALUE_EG
            "QUEEN_VALUE_EG" -> return QUEEN_VALUE_EG
            "KING_ATTACK_UNITS_KNIGHT" -> return KING_ATTACK_UNITS_KNIGHT
            "KING_ATTACK_UNITS_BISHOP" -> return KING_ATTACK_UNITS_BISHOP
            "KING_ATTACK_UNITS_ROOK" -> return KING_ATTACK_UNITS_ROOK
            "KING_ATTACK_UNITS_QUEEN" -> return KING_ATTACK_UNITS_QUEEN
            "PAWN_ISOLATED" -> return PAWN_ISOLATED
            "PAWN_DOUBLED" -> return PAWN_DOUBLED
            "PAWN_CONNECTED_MG" -> return PAWN_CONNECTED_MG
            "PAWN_CONNECTED_EG" -> return PAWN_CONNECTED_EG
            "ROOK_OPEN_FILE_MG" -> return ROOK_OPEN_FILE_MG
            "ROOK_OPEN_FILE_EG" -> return ROOK_OPEN_FILE_EG
            "ROOK_SEMI_OPEN_MG" -> return ROOK_SEMI_OPEN_MG
            "ROOK_SEMI_OPEN_EG" -> return ROOK_SEMI_OPEN_EG
            "ROOK_ON_7TH_MG" -> return ROOK_ON_7TH_MG
            "ROOK_ON_7TH_EG" -> return ROOK_ON_7TH_EG
            "DOUBLED_ROOKS_MG" -> return DOUBLED_ROOKS_MG
            "DOUBLED_ROOKS_EG" -> return DOUBLED_ROOKS_EG
            "PAWN_SHIELD_MISSING" -> return PAWN_SHIELD_MISSING
            "KING_OPEN_FILE_OWN" -> return KING_OPEN_FILE_OWN
            "KING_OPEN_FILE_ADJ" -> return KING_OPEN_FILE_ADJ
            "KING_SEMI_OPEN_OWN" -> return KING_SEMI_OPEN_OWN
            "KING_SEMI_OPEN_ADJ" -> return KING_SEMI_OPEN_ADJ
            "BISHOP_PAIR_MG" -> return BISHOP_PAIR_MG
            "BISHOP_PAIR_EG" -> return BISHOP_PAIR_EG
            "BACK_RANK_MINOR" -> return BACK_RANK_MINOR
            "KNIGHT_OUTPOST" -> return KNIGHT_OUTPOST
            "MOB_KNIGHT_MG" -> return MOB_KNIGHT_MG
            "MOB_BISHOP_MG" -> return MOB_BISHOP_MG
            "MOB_ROOK_MG" -> return MOB_ROOK_MG
            "MOB_QUEEN_MG" -> return MOB_QUEEN_MG
            "MOB_KNIGHT_EG" -> return MOB_KNIGHT_EG
            "MOB_BISHOP_EG" -> return MOB_BISHOP_EG
            "MOB_ROOK_EG" -> return MOB_ROOK_EG
            "MOB_QUEEN_EG" -> return MOB_QUEEN_EG
            "TROPISM_KNIGHT_MG" -> return TROPISM_KNIGHT_MG
            "TROPISM_KNIGHT_EG" -> return TROPISM_KNIGHT_EG
            "TROPISM_ROOK_MG" -> return TROPISM_ROOK_MG
            "TROPISM_ROOK_EG" -> return TROPISM_ROOK_EG
            "TROPISM_QUEEN_MG" -> return TROPISM_QUEEN_MG
            "TROPISM_QUEEN_EG" -> return TROPISM_QUEEN_EG
            "TEMPO" -> return TEMPO
        }
        val under = name.lastIndexOf('_')
        if (under < 0) return null
        val base = name.substring(0, under)
        val idx = name.substring(under + 1).toIntOrNull() ?: return null
        val arr: IntArray = when (base) {
            "PST_PAWN_MG" -> PST_PAWN_MG
            "PST_PAWN_EG" -> PST_PAWN_EG
            "PST_KNIGHT_MG" -> PST_KNIGHT_MG
            "PST_KNIGHT_EG" -> PST_KNIGHT_EG
            "PST_BISHOP_MG" -> PST_BISHOP_MG
            "PST_BISHOP_EG" -> PST_BISHOP_EG
            "PST_ROOK_MG" -> PST_ROOK_MG
            "PST_ROOK_EG" -> PST_ROOK_EG
            "PST_QUEEN_MG" -> PST_QUEEN_MG
            "PST_QUEEN_EG" -> PST_QUEEN_EG
            "PST_KING_MG" -> PST_KING_MG
            "PST_KING_EG" -> PST_KING_EG
            "SAFETY_TABLE" -> SAFETY_TABLE
            "PASSED_BONUS_MG" -> PASSED_BONUS_MG
            "PASSED_BONUS_EG" -> PASSED_BONUS_EG
            else -> return null
        }
        if (idx < 0 || idx >= arr.size) return null
        return arr[idx]
    }

    // List of all tunable parameter names (scalars + expanded array entries).
    fun listParamNames(): List<String> {
        val scalars = listOf(
            "PAWN_VALUE_MG","KNIGHT_VALUE_MG","BISHOP_VALUE_MG","ROOK_VALUE_MG","QUEEN_VALUE_MG",
            "PAWN_VALUE_EG","KNIGHT_VALUE_EG","BISHOP_VALUE_EG","ROOK_VALUE_EG","QUEEN_VALUE_EG",
            "KING_ATTACK_UNITS_KNIGHT","KING_ATTACK_UNITS_BISHOP","KING_ATTACK_UNITS_ROOK","KING_ATTACK_UNITS_QUEEN",
            "PAWN_ISOLATED","PAWN_DOUBLED","PAWN_CONNECTED_MG","PAWN_CONNECTED_EG",
            "ROOK_OPEN_FILE_MG","ROOK_OPEN_FILE_EG","ROOK_SEMI_OPEN_MG","ROOK_SEMI_OPEN_EG",
            "ROOK_ON_7TH_MG","ROOK_ON_7TH_EG","DOUBLED_ROOKS_MG","DOUBLED_ROOKS_EG",
            "PAWN_SHIELD_MISSING","KING_OPEN_FILE_OWN","KING_OPEN_FILE_ADJ","KING_SEMI_OPEN_OWN","KING_SEMI_OPEN_ADJ",
            "BISHOP_PAIR_MG","BISHOP_PAIR_EG","BACK_RANK_MINOR","KNIGHT_OUTPOST",
            "MOB_KNIGHT_MG","MOB_BISHOP_MG","MOB_ROOK_MG","MOB_QUEEN_MG",
            "MOB_KNIGHT_EG","MOB_BISHOP_EG","MOB_ROOK_EG","MOB_QUEEN_EG",
            "TROPISM_KNIGHT_MG","TROPISM_KNIGHT_EG","TROPISM_ROOK_MG","TROPISM_ROOK_EG",
            "TROPISM_QUEEN_MG","TROPISM_QUEEN_EG","TEMPO"
        )
        val arrays = listOf(
            "PST_PAWN_MG" to 64, "PST_PAWN_EG" to 64,
            "PST_KNIGHT_MG" to 64, "PST_KNIGHT_EG" to 64,
            "PST_BISHOP_MG" to 64, "PST_BISHOP_EG" to 64,
            "PST_ROOK_MG" to 64, "PST_ROOK_EG" to 64,
            "PST_QUEEN_MG" to 64, "PST_QUEEN_EG" to 64,
            "PST_KING_MG" to 64, "PST_KING_EG" to 64,
            "SAFETY_TABLE" to SAFETY_TABLE.size,
            "PASSED_BONUS_MG" to PASSED_BONUS_MG.size,
            "PASSED_BONUS_EG" to PASSED_BONUS_EG.size
        )
        val out = ArrayList<String>(scalars.size + arrays.sumOf { it.second })
        out.addAll(scalars)
        for ((base, n) in arrays) for (i in 0 until n) out.add("${base}_$i")
        return out
    }
}
