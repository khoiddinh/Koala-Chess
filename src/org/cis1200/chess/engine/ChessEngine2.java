package org.cis1200.chess.engine;
import org.cis1200.chess.engine.ChessBoard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import static org.cis1200.chess.engine.MoveGenerationPrecompute.*;

public class ChessEngine2 {


    private static final int MAX = Integer.MAX_VALUE;
    private static final int MIN = Integer.MIN_VALUE;
    private static final int MAX_SEARCH_DEPTH = 6;

    public int nodesSearched = 0;
    public int pruneAmount = 0;

    private static final int OPENING_PHASE_THRESHOLD = 5900;
    private static final int ENDGAME_PHASE_THRESHOLD = 500;

    private static final int[][] PIECE_SQUARE_TABLE = {
            {-30, -40, -40, -50, -50, -40, -40, -30, // king
                    -30, -37, -43, -49, -50, -39, -40, -30,
                    -32, -41, -40, -46, -49, -40, -46, -30,
                    -32, -38, -39, -52, -54, -39, -39, -30,
                    -20, -33, -29, -42, -44, -29, -30, -19,
                    -10, -18, -17, -20, -22, -21, -20, -13,
                    14,  18,  -10,  -10,   -14,  -10,  15,  14,
                    21,  35,  -11,   6,   1,  -14,  32,  22},
            {-25,  -9, -11,  -3,  17, -13, -10, -17, // queen
                    -4,  -6,   4,  -5,  -1,   6,   4,  -5,
                    -8,  -5,   2,   0,   7,   6,  -4,  -5,
                    0,  -4,   7,  -1,   7,  11,   0,   1,
                    -6,   4,   7,   1,  -1,   2,  -6,  -2,
                    -15,  11,  11,  11,   4,  11,   6, -15,
                    -5,  -6,   1,  -6,   3,  -3,   3, -10,
                    -15,  -4, -13,  -8,  -3, -16,  -8, -24},
            {5,  -2,   6,   2,  -2,  -6,   4,  -2, // rook
                    8,  13,  11,  15,  11,  15,  16,   4,
                    -6,   3,   3,   6,   1,  -2,   3,  -5,
                    -10,   5,  -4,  -4,  -1,  -6,   3,  -2,
                    -4,   3,   5,  -2,   4,   1,  -5,   1,
                    0,   1,   1,  -3,   5,   6,   1,  -9,
                    -10,  -1,  -4,   0,   5,  -6,  -6,  -9,
                    -1,  -2,  -6,   9,   9,   5,   4,  -5,},
            {-16, -15, -12,  -5, -10, -12, -10, -20, // bishop
                    -13,   5,   6,   1,  -6,  -5,   3,  -6,
                    -16,   6,  -1,  16,   7,  -1,  -6,  -5,
                    -14,  -1,  11,  14,   4,  10,  11, -13,
                    -4,   5,  12,  16,   4,   6,   2, -16,
                    -15,   4,  14,   8,  16,   4,  16, -15,
                    -5,   6,   6,   6,   3,   6,   9,  -7,
                    -14,  -4, -15,  -4,  -9,  -4, -12, -14},
            {-55, -40, -30, -28, -26, -30, -40, -50, // knight
                    -37, -15, 0, -6, 4, 3, -17, -40,
                    -25, 5, 16, 12, 11, 6, 6, -29,
                    -24, 5, 21, 14, 18, 9, 11, -26,
                    -36, -5, 9, 23, 24, 21, 2, -24,
                    -32, -1, 4, 19, 20, 4, 11, -25,
                    -38, -22, 4, -1, 8, -5, -18, -34,
                    -50, -46, -32, -24, -36, -25, -34, -50},
            {0, 0, 0, 0, 0, 0, 0, 0, // pawn
            -4, 68, 61, 47, 47, 49, 45, -1,
            6, 16, 25, 33, 24, 24, 14, -6,
            0, -1, 9, 28, 20, 8, -1, 11,
            6, 4, 6, 14, 14, -5, 6, -6,
            -1, -8, -4, 4, 2, -12, -1, 5,
            5, 16, 16, -14, -14, 13, 15, 8,
            0, 0, 0, 0, 0, 0, 0, 0}
    };
    private static final int[][] PIECE_SQUARE_TABLE_ENDGAME = {
            {-50, -40, -30, -20, -20, -30, -40, -50, // king
            -30, -18, -15,   6,   3,  -6, -24, -30,
            -35, -16,  20,  32,  34,  14, -11, -30,
            -34,  -5,  24,  35,  34,  35, -16, -35,
            -36,  -7,  31,  34,  34,  34, -12, -31,
            -30,  -7,  14,  33,  36,  16, -13, -33,
            -36, -27,   5,   2,   5,  -1, -31, -33,
            -48, -26, -26, -26, -28, -25, -30, -51},
            {-21,  -7,  -6,   1,  -8, -15, -10, -16, // queen
            -4,  -5,   3,  -4,   2,   6,   3, -10,
            -13,  -2,   7,   2,   6,  10,  -4,  -6,
            -1,  -4,   3,   1,   8,   8,  -2,  -2,
            0,   6,   8,   1,  -1,   1,   0,  -3,
            -11,  10,   6,   3,   7,   9,   4, -10,
            -12,  -6,   5,   0,   0,  -5,   4, -10,
            -20,  -6,  -7,  -7,  -4, -12,  -9, -20},
            {5,  -6,   1,  -4,  -4,  -6,   6,  -3, // rook
            -6,   4,   2,   5,  -1,   3,   4, -15,
            -15,   3,   3,   0,  -1,  -6,   5,  -9,
            -16,   6,   0,  -6,  -3,  -3,  -4,  -4,
            -15,   6,   2,  -6,   6,   0,  -6, -10,
            -6,  -1,   3,  -2,   6,   5,   0, -15,
            -8,  -4,   1,  -4,   3,  -5,  -6,  -5,
            1,   0,  -2,   1,   1,   4,   2,   0},
            {-14, -13,  -4,  -7, -14,  -9, -16, -20, // bishop
            -11,   6,   3,  -6,   4,  -3,   5,  -4,
            -11,  -3,   5,  15,   4,  -1,  -5, -10,
            -7,  -1,  11,  16,   5,  11,   7, -13,
            -4,   4,  10,  16,   6,  12,   4, -16,
            -4,   4,  11,  12,  10,   7,   7, -12,
            -11,   7,   6,   6,  -3,   2,   1,  -7,
            -15,  -4, -11,  -4, -10, -10,  -6, -17},
            {-50, -40, -30, -24, -24, -35, -40, -50, // knight
            -38, -17,   6,  -5,   5,  -4, -15, -40,
            -24,   3,  15,   9,  15,  10,  -6, -26,
            -29,   5,  21,  17,  18,   9,  10, -28,
            -36,  -5,  18,  16,  14,  20,   5, -26,
            -32,   7,   5,  20,  11,  15,   9, -27,
            -43, -20,   5,  -1,   5,   1, -22, -40,
            -50, -40, -32, -27, -30, -25, -35, -50},
            {0,   0,   0,   0,   0,   0,   0,   0, // pawn
            -4, 174, 120,  94,  85,  98,  68,   4,
            6,  48,  44,  45,  31,  38,  37,  -6,
            -6,  -4,  -1,  -6,   2,  -1,  -2,  -2,
            2,   2,   5,  -3,   0,  -5,   4,  -3,
            -2,   0,   1,   5,   0,  -1,   0,   1,
            -2,   5,   6,  -6,   0,   3,   4,  -4,
            0,   0,   0,   0,   0,   0,   0,   0}
    };

    private static final int[][] WHITE_MATERIAL_WEIGHTS = {
            {20001, 888, 488, 319, 308, 89}, // opening material
            {19998, 853, 497, 331, 319, 96} // endgame material
    };
    private static final int[][] BLACK_MATERIAL_WEIGHTS = {
            {20002, 888, 492, 323, 307, 92}, // opening material
            {20000, 845, 501, 334, 318, 102} // endgame material
    };
    public ChessEngine2() {

    }


    // game stage either 0 == opening, 1 = endgame (midGame is avg)
    int getGamePhaseScore(ChessBoard board) {
        int gamePhaseScore = 0;

        for (int piece = 1; piece <= 4; piece++) { // white
            gamePhaseScore += Long.bitCount(board.whiteBitBoards[piece])
                    * WHITE_MATERIAL_WEIGHTS[0][piece];
        }
        for (int piece = 1; piece <= 4; piece++) { // black
            gamePhaseScore += Long.bitCount(board.blackBitBoards[piece])
                    * BLACK_MATERIAL_WEIGHTS[0][piece];
        }

        return gamePhaseScore;
    }
    // MVV-LVA scoring for captures
    // Higher victimVal and lower attackerVal means a bigger score
    private int mvvLvaScore(int attackerPiece, int victimPiece) {
        int attackerVal = WHITE_MATERIAL_WEIGHTS[0][attackerPiece];
        int victimVal = WHITE_MATERIAL_WEIGHTS[0][victimPiece];
        return (victimVal * 100) - attackerVal;
    }


    // Extract move info (same method from your code)
    private MoveInfo decodeMove(int move) {
        MoveInfo mi = new MoveInfo();
        mi.source = move & 0b111111;
        mi.target = (move & (0b111111 << 6)) >>> 6;
        mi.piece = (move & (0b111 << 12)) >>> 12;
        mi.capture = ((move & (1 << 15)) >>> 15) == 1;
        mi.pieceCaptured = (move & (0b111 << 16)) >>> 16;
        mi.promotion = ((move & (1 << 19)) >>> 19) == 1;
        mi.promotionPiece = (move & (0b11 << 20)) >>> 20;
        mi.castleMove = ((move & (1 << 22)) >>> 22) == 1;
        mi.castleDirection = (move & (1 << 23)) >>> 23;
        mi.castleState = (move & (0b1111 << 24)) >>> 24;
        mi.enPassant = ((move & (1 << 28)) >>> 28) == 1;
        return mi;
    }

    private static class MoveInfo {
        int source;
        int target;
        int piece;
        boolean capture;
        int pieceCaptured;
        boolean promotion;
        int promotionPiece;
        boolean castleMove;
        int castleDirection;
        int castleState;
        boolean enPassant;
    }

    // Score move combining TT move, captures (MVV-LVA), killer, history
    private int scoreMove(int move, int depth, boolean isWhiteTurn) {
        MoveInfo mi = decodeMove(move);
        int score = 0;
        if (mi.castleMove) {
            score += 10000;
        }

        // captures via MVV-LVA
        if (mi.capture) {
            score += 50 * mvvLvaScore(mi.piece, mi.pieceCaptured);
            if (WHITE_MATERIAL_WEIGHTS[0][mi.pieceCaptured] < WHITE_MATERIAL_WEIGHTS[0][mi.piece]) {
                score /= 5;
            }

        }

        if (isWhiteTurn) {
            score += 10 * PIECE_SQUARE_TABLE[mi.piece][mi.target];
        } else {
            score += 10 * PIECE_SQUARE_TABLE[mi.piece][63-mi.target];
        }
        return score;
    }

    // Sort moves using our improved heuristic
    private void orderMoves(ArrayList<Integer> moves, int depth, boolean isWhiteTurn) {
        // Precompute scores
        HashMap<Integer, Integer> scores = new HashMap<>();
        for (int m : moves) {
            scores.put(m, scoreMove(m, depth, isWhiteTurn));
        }
        moves.sort((a, b) -> Integer.compare(scores.get(b), scores.get(a)));
    }

    // eval board: + nums good for white, - nums good for black
    private int evalBoard(ChessBoard board, boolean isWhiteToMove) {
        int gamePhaseScore = getGamePhaseScore(board);
        int gamePhase = -1;

        if (gamePhaseScore > OPENING_PHASE_THRESHOLD) {
            gamePhase = 0;
        }
        else if (gamePhaseScore < ENDGAME_PHASE_THRESHOLD) {
            gamePhase = 1; // endgame
        } else {
            gamePhase = 2; // midgame
        }
        int score = 0;
        int scoreOpening = 0;
        int scoreEndgame = 0;

        for (int piece = 0; piece < 6; piece++) {
            // white eval
            long pieceMask = board.whiteBitBoards[piece];
            while (pieceMask != 0) {
                int pos = board.getPosOfLeastSigBit(pieceMask);
                scoreOpening += WHITE_MATERIAL_WEIGHTS[0][piece];
                scoreEndgame += WHITE_MATERIAL_WEIGHTS[1][piece];
                scoreOpening += PIECE_SQUARE_TABLE[piece][pos];
                scoreEndgame += PIECE_SQUARE_TABLE_ENDGAME[piece][pos];
                pieceMask ^= startingBitBoards[pos];
            }
            // black eval
            pieceMask = board.blackBitBoards[piece];
            while (pieceMask != 0) {
                int pos = board.getPosOfLeastSigBit(pieceMask);
                scoreOpening -= BLACK_MATERIAL_WEIGHTS[0][piece];
                scoreEndgame -= BLACK_MATERIAL_WEIGHTS[1][piece];

                scoreOpening -= PIECE_SQUARE_TABLE[piece][63-pos];
                scoreEndgame -= PIECE_SQUARE_TABLE_ENDGAME[piece][63-pos];
                pieceMask ^= startingBitBoards[pos];
            }

        }
        if (gamePhase == 2) { // midgame
            score = (
                    scoreOpening * gamePhaseScore +
                            scoreEndgame * (OPENING_PHASE_THRESHOLD - gamePhaseScore)
            ) / OPENING_PHASE_THRESHOLD;
        }
        else if (gamePhase == 0) { // opening
            score = scoreOpening;
        } else { // endgame
            score = scoreEndgame;
        }
        return board.isWhiteTurn() ? score : - score;

    }

    public int getBestMove(ChessBoard board) {
        int[] result = negamax(board, 0, board.isWhiteTurn(), MIN, MAX);
        return result[0];
    }

    /**
     * negamax with move ordering.
     * returns [bestMove, score].
     */

    private int[] negamax(ChessBoard board, int depth, boolean isWhiteToMove, int alpha, int beta) {
        nodesSearched++;
        ArrayList<Integer> moves = board.getLegalPossibleMoves();
        int gameState = board.checkWinner(moves);
        if (gameState == 1) { // white win
            return new int[]{-1, MAX - depth};
        } else if (gameState == -1) { // black win
            return new int[]{-1, MIN + depth};
        } else if (gameState == 2) { // draw
            return new int[]{-1, 0};
        } else if (depth == MAX_SEARCH_DEPTH) {
            return new int[]{-1, evalBoard(board, isWhiteToMove)};
        }

        if (moves.isEmpty()) {
            // No moves
            return new int[]{-1, evalBoard(board, isWhiteToMove)};
        }

        // Order moves with heuristics
        orderMoves(moves, depth, isWhiteToMove);

        int bestMove = -1;
        int bestScore = MIN;

        for (int move : moves) {
            board.makeMove(move);
            // Negamax formulation: flip perspective
            int[] result = negamax(board, depth + 1, !isWhiteToMove, -beta, -alpha);
            int score = -result[1];
            board.undoLastMove();

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
                if (score > alpha) {
                    pruneAmount++;
                    alpha = score;
                    if (alpha >= beta) {
                        return new int[]{bestMove, bestScore};
                    }
                }
            }
        }
        return new int[]{bestMove, bestScore};
    }
}