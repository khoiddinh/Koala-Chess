package engine;

import java.util.HashSet;
import java.util.Stack;
import java.util.ArrayList;

import static engine.MoveGenerationPrecompute.*;

import static engine.BitBoardFunctions.getPosOfLeastSigBit;
import static engine.BitBoardFunctions.orBitBoardArray;

public class ChessBoard {
    // BIT BOARD CONSTANTS
    private static final long HEAD_INT = 0x8000000000000000L; // int that represents one at the 64th position
    private static final long LEFT_SIDE_BOARD = 0x8080808080808080L;
    private static final long RIGHT_SIDE_BOARD = 0x101010101010101L;
    private static final long TOP_SIDE_BOARD = 0xFF00000000000000L;
    private static final long BOTTOM_SIDE_BOARD = 0xFFL;

    public static final char EMPTY_SQUARE = '.'; // char representation if no piece is there

    private static final long WHITE_RIGHT_CASTLE_MASK = 0x6L;
    private static final long WHITE_LEFT_CASTLE_MASK = 0x30L;

    // NOTE: absolute right according to white POV
    private static final long BLACK_RIGHT_CASTLE_MASK = 0x600000000000000L;
    private static final long BLACK_LEFT_CASTLE_MASK = 0x3000000000000000L;


    // DEFAULT BOARD POSITION
    // bitboard is defined top right to bottom right: 100000000 -> 100 \n 000 \n 000 if 3x3
    final long whiteKingBoard = 0x8;
    final long blackKingBoard = 0x0800000000000000L;
    final long whiteQueenBoard = 0x10;
    final long blackQueenBoard = 0x1000000000000000L;
    final long whiteRookBoard = 0x81;
    final long blackRookBoard = 0x8100000000000000L;
    final long whiteKnightBoard = 0x42;
    final long blackKnightBoard = 0x4200000000000000L;
    final long whiteBishopBoard = 0x24;
    final long blackBishopBoard = 0x2400000000000000L;
    final long whitePawnBoard = 0xFF00; // 0xFF -> 0xFF00 (each 0 adds 16 (2^4) to move up one row is 16*16 or 00)
    final long blackPawnBoard = 0xFF000000000000L;

    public long[] whiteBitBoards = new long[]{
            whiteKingBoard,
            whiteQueenBoard,
            whiteRookBoard,
            whiteBishopBoard,
            whiteKnightBoard,
            whitePawnBoard
    };
    public long[] blackBitBoards = new long[]{
            blackKingBoard,
            blackQueenBoard,
            blackRookBoard,
            blackBishopBoard,
            blackKnightBoard,
            blackPawnBoard
    };
    private boolean isWhiteTurn = true;
    private CastleState castleState = new CastleState();
    public Stack<Move> moveStack = new Stack<>();


    private static final MoveGenerationPrecompute precompute = new MoveGenerationPrecompute();


    public ChessBoard() {
    }

    // public access methods
    public boolean isWhiteTurn() {
        return isWhiteTurn;
    }

    // -1 = black win, 0 = no winner, 1 = white win, 2 = draw
    public int checkWinner(ArrayList<Move> moves) {
        long whitePieces = orBitBoardArray(whiteBitBoards);
        long blackPieces = orBitBoardArray(blackBitBoards);
        if (isWhiteTurn && moves.isEmpty()) {
            if (isKingInCheck(whiteBitBoards[0], whitePieces, blackPieces)) {
                return -1;
            } else { // stalemate, no other moves but king not in check
                return 2;
            }
        } else if (!isWhiteTurn && moves.isEmpty()) {
            if (isKingInCheck(blackBitBoards[0], whitePieces, blackPieces)) {
                return 1;
            }
        }
        return 0;
    }

    public char[][] getBoardArray() {
        char[][] returnBoard = new char[8][8];
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                returnBoard[row][col] = EMPTY_SQUARE;
                // do white first
                char[] whitePieceMap = new char[]{'K', 'Q', 'R','B','N','P'};
                for (int piece = 0; piece < 6; piece++) {
                    // check if piece is at this pos
                    if ((whiteBitBoards[piece] & startingBitBoards[row*8+col]) != 0) {
                        returnBoard[row][col] = whitePieceMap[piece];
                        break;
                    }
                }
                char[] blackPieceMap = new char[]{'k', 'q', 'r', 'b', 'n', 'p'};
                for (int piece = 0; piece < 6; piece++) {
                    // check if piece is at this pos
                    if ((blackBitBoards[piece] & startingBitBoards[row*8+col]) != 0) {
                        returnBoard[row][col] = blackPieceMap[piece];
                        break;
                    }
                }
            }
        }
        return returnBoard;
    }

    // get a list of 2 element arrays: [source pos, target pos]
    public int[][] getMovePairs() {
        ArrayList<Move> moves = getLegalPossibleMoves();
        int[][] movePairs = new int[moves.size()][];
        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            movePairs[i] = new int[]{move.source, move.target};
        }
        return movePairs;
    }

    // switches the turn of the game
    public void switchTurn() {
        isWhiteTurn = !isWhiteTurn;
    }

    // gets binary trace of piece attacks
    // NOTE: doesn't handle enPassant, castle, or promotion
    private long getTraceOfPiece(int pos, int piece, long friendlyBitBoard, long opponentBitBoard) {
        long moveMask = 0;
        long blockerBitBoard = friendlyBitBoard | opponentBitBoard;
        switch (piece) {
            case 0: // king
                moveMask = kingAttackMasks[pos];
                break;
            case 1: // queen
                moveMask = precompute.getSlidingMagicAttack(pos, blockerBitBoard, 1);
                break;
            case 2: // rook
                moveMask = precompute.getSlidingMagicAttack(pos, blockerBitBoard, 2);
                break;
            case 3: // bishop
                moveMask = precompute.getSlidingMagicAttack(pos, blockerBitBoard, 3);
                break;
            case 4: // knight
                moveMask = knightAttackMasks[pos];
                break;
            case 5: // pawn
                // returns both moves and attacks
                // does not handle enPassant (handled in getPossibleLegalMoves)
                if (isWhiteTurn) {
                    if ((whitePawnMoveMasks[pos] & blockerBitBoard) == 0) { // no overlap in opponent piece
                        // can double push potentially
                        moveMask = whitePawnMoveMasks[pos]; // only can go there if opponent not there
                    } else { // check single move
                        // get rid of any second row moves or if there is an opponent there do nothing
                        moveMask = whitePawnMoveMasks[pos] & ~blockerBitBoard & ~(BOTTOM_SIDE_BOARD << 24);
                    }
                    moveMask |= (whitePawnAttackMasks[pos] & blockerBitBoard); // only can go there if takes
                } else { // black pawn moves
                    if ((blackPawnMoveMasks[pos] & blockerBitBoard) == 0) { // no overlap in opponent piece
                        // can double push potentially
                        moveMask = blackPawnMoveMasks[pos]; // only can go there if opponent not there
                    } else { // check single move
                        // get rid of any second row moves or if there is an opponent there do nothing
                        moveMask = blackPawnMoveMasks[pos] & ~blockerBitBoard & ~(TOP_SIDE_BOARD >>> 24);
                    }
                    moveMask |= (blackPawnAttackMasks[pos] & opponentBitBoard);
                }
                break;
        }
        moveMask ^= (moveMask & friendlyBitBoard);
        return moveMask;
    }

    public boolean isSquareAttacked(int pos, long friendly, long opponentBitBoard) {
        long blockerBitBoard = friendly | opponentBitBoard;

        // array of mask of how each piece could attack pos
        long[] potentialAttacks = new long[6];
        for (int piece = 0; piece < 6; piece++) {
            switch (piece) {
                case 0: // king
                    potentialAttacks[piece] = kingAttackMasks[pos];
                    break;
                case 1: // queen
                case 2: // rook
                case 3: // bishop
                    // potentialAttacks[piece] = getSlidingAttackWithBlockers(pos, blockerBitBoard, piece);
                    potentialAttacks[piece] = precompute.getSlidingMagicAttack(pos, blockerBitBoard, piece);
                    break;
                case 4: // knight
                    potentialAttacks[piece] = knightAttackMasks[pos];
                    break;
                case 5: // pawn
                    if (isWhiteTurn && ((startingBitBoards[pos] & TOP_MASK) == 0)) { // not on top row
                        if ((startingBitBoards[pos] & LEFT_MASK) == 0) { // not on left side
                            potentialAttacks[piece] |= startingBitBoards[pos-9];
                        }
                        if ((startingBitBoards[pos] & RIGHT_MASK) == 0) { // not on right side
                            potentialAttacks[piece] |= startingBitBoards[pos-7];
                        }
                    } else if (!isWhiteTurn && (((startingBitBoards[pos] & BOTTOM_MASK) == 0))) {                         // not on bottom row;
                        if (((startingBitBoards[pos] & LEFT_MASK) == 0)) { // not on left side
                            potentialAttacks[piece] |= startingBitBoards[pos + 7];
                        }
                        if (((startingBitBoards[pos] & RIGHT_MASK) == 0)) { // not on right side
                            potentialAttacks[piece] |= startingBitBoards[pos + 9];
                        }
                    }
                    break;
            }
        }
        long[] opponentBitBoardList = isWhiteTurn ? blackBitBoards : whiteBitBoards;
        for (int piece = 0; piece < 6; piece++) {
            // check if each piece is on the attack directions to pos
            if ((opponentBitBoardList[piece] & potentialAttacks[piece]) != 0) { // piece attacking that square
                return true;
            }
        }
        return false;
    }

    // gets if king is currently in check (includes but doesn't distinguish mate)
    public boolean isKingInCheck(long kingBitBoard, long friendlyBitBoard, long opponentBitBoard) {
        int kingPos = getPosOfLeastSigBit(kingBitBoard);
        return isSquareAttacked(kingPos, friendlyBitBoard, opponentBitBoard);
    }

    private int findPieceAtPos(int pos, boolean findWhitePiece) {
        long[] bitBoardList = findWhitePiece ? whiteBitBoards : blackBitBoards;
        long posMask = startingBitBoards[pos];
        for (int piece = 0; piece < 6; piece++) {
            if ((posMask & bitBoardList[piece]) != 0) {
                return piece;
            }
        }
        throw new IllegalArgumentException("No piece at Pos");
    }

    private void filterLegalMoves(ArrayList<Move> moves) {
        long[] bitBoardList = isWhiteTurn ? whiteBitBoards : blackBitBoards;
        for (int i = moves.size() - 1; i >= 0; i--) {
            Move move = moves.get(i);
            makeMove(move);
            switchTurn(); // because make move switches turn,
            // but we want to check king check with respect to previous color
            // check friendly after switch turn because eval same side
            long[] friendlyBitBoardList = isWhiteTurn ? whiteBitBoards : blackBitBoards;
            long[] opponentBitBoardList = isWhiteTurn ? blackBitBoards : whiteBitBoards;
            long friendlyBitBoard = orBitBoardArray(friendlyBitBoardList);
            long opponentBitBoard = orBitBoardArray(opponentBitBoardList);
            if (isKingInCheck(bitBoardList[0], friendlyBitBoard, opponentBitBoard)) { // legal
                moves.remove(i);
            }
            switchTurn(); // switch turn back
            undoLastMove();
        }
    }

    public ArrayList<Move> getLegalPossibleMoves() {
        long[] bitBoardList = isWhiteTurn ? whiteBitBoards : blackBitBoards;

        long opposingBitBoard = orBitBoardArray(
                isWhiteTurn ? blackBitBoards : whiteBitBoards);
        long friendlyBitBoard = orBitBoardArray(bitBoardList);
        long blockerBitBoard = friendlyBitBoard | opposingBitBoard;

        ArrayList<Move> possibleMoves = new ArrayList<>();

        for (int piece = 0; piece < bitBoardList.length; piece++) { // loop over each piece bitboard
            long pieceBitBoard = bitBoardList[piece]; // bitboard with just this piece (could be multiple pieces)
            while (pieceBitBoard != 0) {
                // check piece bitBoardList[i] at pos for moves
                int pos = getPosOfLeastSigBit(pieceBitBoard);
                // get move mask (trace attack) of this piece at this pos
                long moveMask = getTraceOfPiece(pos, piece, friendlyBitBoard, opposingBitBoard);

                // CHECK AND ADD SPECIAL MOVES
                // add king castling
                if (piece == 0) {
                    // check if white can right castle
                    if (isWhiteTurn && castleState.whiteCanRightCastle &&
                            ((blockerBitBoard & WHITE_RIGHT_CASTLE_MASK) == 0) &&
                            !isSquareAttacked(60, friendlyBitBoard, opposingBitBoard) && // can't castle in check
                            !isSquareAttacked(61, friendlyBitBoard, opposingBitBoard) &&
                            !isSquareAttacked(62, friendlyBitBoard, opposingBitBoard)) {
                        possibleMoves.add(
                                new Move(60, 62,piece, false,
                                        0, false, 0,
                                        true, true, castleState.copy(),
                                        false)
                        );
                    }
                    // check if white can left castle
                    if (isWhiteTurn && castleState.whiteCanLeftCastle &&
                            ((blockerBitBoard & WHITE_LEFT_CASTLE_MASK) == 0) &&
                            !isSquareAttacked(60, friendlyBitBoard, opposingBitBoard) &&
                            !isSquareAttacked(59, friendlyBitBoard, opposingBitBoard) &&
                            !isSquareAttacked(58, friendlyBitBoard, opposingBitBoard)) {
                        possibleMoves.add(
                                new Move(60, 58, piece, false,
                                        0, false, 0,
                                        true, false, castleState.copy(),
                                        false)
                        );
                    }
                    // check if black can right castle
                    if (!isWhiteTurn && castleState.blackCanRightCastle &&
                            ((blockerBitBoard & BLACK_RIGHT_CASTLE_MASK) == 0) &&
                            !isSquareAttacked(4, friendlyBitBoard, opposingBitBoard) &&
                            !isSquareAttacked(5, friendlyBitBoard, opposingBitBoard) &&
                            !isSquareAttacked(6, friendlyBitBoard, opposingBitBoard)) {
                        possibleMoves.add(
                                new Move(4, 6,piece, false, 0,
                                        false, 0,
                                        true, true, castleState.copy(),
                                        false)
                        );
                    }
                    // check if black can left castle
                    if (!isWhiteTurn && castleState.blackCanLeftCastle &&
                            ((blockerBitBoard & BLACK_LEFT_CASTLE_MASK) == 0) &&
                            !isSquareAttacked(4, friendlyBitBoard, opposingBitBoard) &&
                            !isSquareAttacked(3, friendlyBitBoard, opposingBitBoard) &&
                            !isSquareAttacked(2, friendlyBitBoard, opposingBitBoard)) {
                        possibleMoves.add(
                                new Move(4, 2,piece,
                                        false, 0,
                                        false, 0,
                                        true, false, castleState.copy(),
                                        false)
                        );
                    }
                }

                // handle pawn enPassant ONLY
                // adds additional enPassant moves
                if (piece == 5) {
                    if (!moveStack.isEmpty()) {
                        Move prevMove = moveStack.peek();
                        if (prevMove.piece == 5 &&
                                (Math.abs(prevMove.source-prevMove.target) == 16)) { // if pawn and two square move
                            if (((startingBitBoards[pos] & LEFT_SIDE_BOARD) == 0)) { // if pawn not on left edge
                                // calculate left side enPassant
                                if (pos-1 == prevMove.target) { // if prev move directly left of curr pawn pos
                                    possibleMoves.add(
                                            new Move(pos, isWhiteTurn ? pos-9 : pos + 7, 5,
                                            true, 5,
                                            false, 0,
                                            false, true, castleState.copy(),
                                            true));
                                }
                            }
                            if (((startingBitBoards[pos] & RIGHT_SIDE_BOARD) == 0)) { // if pawn not on right edge
                                // calculate right side enPassant
                                if (pos+1 == prevMove.target) { // if prev move directly left of curr pawn pos
                                    possibleMoves.add(
                                            new Move(pos, isWhiteTurn ? pos-7 : pos + 9, 5,
                                            true, 5,
                                            false, 0,
                                            false, true, castleState.copy(),
                                            true));
                                }
                            }
                        }
                    }
                }

                // ADD REGULAR MOVES
                while (moveMask != 0) {
                    int targetPos = getPosOfLeastSigBit(moveMask);

                    // capture logic
                    boolean isCaptureMove = (startingBitBoards[targetPos] & opposingBitBoard) != 0;
                    int capturedPiece = 0;
                    if (isCaptureMove) { // if capturing a piece, find the piece we're capturing
                        capturedPiece = findPieceAtPos(targetPos, !isWhiteTurn); // if white turn, find black piece
                    }

                    // pawn promotion logic to add promotion flag along with non queen promotions
                    // capture flag even in the case of promotion is already handled in the above block
                    boolean isPromotion = false;
                    if (piece == 5) {
                        if (isWhiteTurn && ((startingBitBoards[targetPos] & TOP_MASK) != 0)) { // white promotion
                            for (int promotionPiece = 1; promotionPiece < 4; promotionPiece++) { // queen added by default
                                possibleMoves.add(
                                        new Move(pos, targetPos, piece,
                                        isCaptureMove, capturedPiece,
                                        true, promotionPiece,
                                        false, true, castleState.copy(),
                                        false)
                                );
                            }
                            isPromotion = true;
                        }
                        else if (!isWhiteTurn && ((startingBitBoards[targetPos] & BOTTOM_MASK) != 0)) { // black promotion
                            for (int promotionPiece = 1; promotionPiece < 4; promotionPiece++) { // queen added by default
                                possibleMoves.add(
                                        new Move(pos, targetPos, piece,
                                        isCaptureMove, capturedPiece,
                                        true, promotionPiece,
                                        false, true, castleState.copy(),
                                        false));
                            }
                            isPromotion = true;
                        }
                    }

                    // king can't move into check functionality
                    if (!(piece == 0 && isSquareAttacked(targetPos, friendlyBitBoard, opposingBitBoard)) &&
                            !(piece == 5 && isCaptureMove && ((Math.abs(targetPos-pos) % 8) == 0))) { // pawn can't capture on same column

                        // if promotion, queen added by default, block above adds rest
                        possibleMoves.add(
                                new Move(pos, targetPos, piece,
                                isCaptureMove, capturedPiece,
                                isPromotion, 0,
                                false, true,
                                castleState.copy(), false)
                        );
                    }
                    moveMask ^= startingBitBoards[targetPos]; // remove this move from move mask
                    // cont: (the moves that we still have to convert and encode)
                }
                pieceBitBoard ^= startingBitBoards[pos]; // remove piece from bitboard and process next
            }
        }
        filterLegalMoves(possibleMoves);
        return possibleMoves;
    }

    // plays move, updates bitboards, and switches turn
    // assumes valid move
    // TODO: UNIT TESTS
    public void makeMove(Move move) {


        long[] bitBoardList = isWhiteTurn ? whiteBitBoards : blackBitBoards;
        long[] opponentBitBoardList = isWhiteTurn ? blackBitBoards : whiteBitBoards;
        // remove piece at source location
        bitBoardList[move.piece] ^= startingBitBoards[move.source];
        // add at new target location
        bitBoardList[move.piece] |= startingBitBoards[move.target];
        // if capture move, update the capture bitboard in opponent bitboard
        if (move.isCaptureMove && !move.isEnPassantMove) { // don't handle enPassant b/c target isn't loc of enemy pawn
            opponentBitBoardList[move.pieceCaptured] ^= startingBitBoards[move.target]; // remove captured piece
        }
        if (move.isEnPassantMove) {
            // if white turn, enPassant pawn is below (+) if black, enPassant pawn is above
            opponentBitBoardList[5] ^=
                    startingBitBoards[isWhiteTurn ? move.target + 8 : move.target - 8];
        }
        // if promotion, replace the pawn (that we already moved) with the promoted piece
        if (move.isPromotionMove) {
            bitBoardList[move.piece] ^= startingBitBoards[move.target]; // remove pawn
            bitBoardList[move.promotionPiece] |= startingBitBoards[move.target]; // replace with promoted piece
        }
        // if castleMove, move the rook since we already moved the king above
        if (move.isCastleMove) {
            if (move.rightCastleDirection) { // right castle
                bitBoardList[2] ^= startingBitBoards[isWhiteTurn ? 63 : 7];
                bitBoardList[2] |= startingBitBoards[isWhiteTurn ? 61 : 5];
            } else { // left castle
                bitBoardList[2] ^= startingBitBoards[isWhiteTurn ? 56 : 0];
                bitBoardList[2] |= startingBitBoards[isWhiteTurn ? 59 : 3];
            }
            // update the castle fields
            if (isWhiteTurn) {
                castleState.whiteCanLeftCastle = false;
                castleState.whiteCanRightCastle = false;
            } else { // black
                castleState.blackCanLeftCastle = false;
                castleState.blackCanRightCastle = false;
            }
        }

        // if move isn't a castle but could affect castle state
        // (if you move the king or rook)
        if ((move.piece == 0 || move.piece == 2) && !move.isCastleMove) {
            switch (move.piece) {
                case 0: // king
                    if (isWhiteTurn) { // if king moves, castle not valid
                        castleState.whiteCanLeftCastle = false;
                        castleState.whiteCanRightCastle = false;
                    } else {
                        castleState.blackCanLeftCastle = false;
                        castleState.blackCanRightCastle = false;
                    }
                    break;
                case 2: // rook
                    if (isWhiteTurn) {
                        if (move.source == 63) { // white right rook
                            castleState.whiteCanRightCastle = false;
                        } else if (move.source == 56) { // white left rook
                            castleState.whiteCanLeftCastle = false;
                        }
                    } else { // black turn
                        if (move.source == 7) { // black right rook
                            castleState.blackCanRightCastle = false;
                        } else if (move.source == 0) { // black left rook
                            castleState.blackCanLeftCastle = false;
                        }
                    }
                    break;
            }
        }

        // add move to moveStack
        moveStack.add(move);

        // switch turn
        switchTurn();
    }

    public void undoLastMove() {
        Move move = moveStack.pop();
        switchTurn(); // switch turn first

        long[] bitBoardList = isWhiteTurn ? whiteBitBoards : blackBitBoards;
        long[] opponentBitBoardList = isWhiteTurn ? blackBitBoards : whiteBitBoards;
        // if promotion, remove promoted piece from target
        if (move.isPromotionMove) {
            bitBoardList[move.promotionPiece] ^= startingBitBoards[move.target];
        } else { // otherwise
            // remove piece from target
            bitBoardList[move.piece] ^= startingBitBoards[move.target];
        }
        // add it back to the original place
        bitBoardList[move.piece] |= startingBitBoards[move.source];
        // if captured, add the enemy piece back to its spot
        if (move.isCaptureMove && !move.isEnPassantMove) { // don't consider enPassant, handle replace below in the enPassant block
            opponentBitBoardList[move.pieceCaptured] |= startingBitBoards[move.target];
        }
        // put the captured pawn back, already moved capturing pawn back
        if (move.isEnPassantMove) {
            // if white turn, enPassant pawn is below (+) if black, enPassant pawn is above
            opponentBitBoardList[5] |= startingBitBoards[isWhiteTurn ? move.target + 8 : move.target - 8];
        }

        // if castle, move rook back and fix castle fields
        if (move.isCastleMove) {
            if (move.rightCastleDirection) { // right castle
                bitBoardList[2] ^= startingBitBoards[isWhiteTurn ? 61 : 5];
                bitBoardList[2] |= startingBitBoards[isWhiteTurn ? 63 : 7];
            } else { // left castle
                bitBoardList[2] ^= startingBitBoards[isWhiteTurn ? 59 : 3];
                bitBoardList[2] |= startingBitBoards[isWhiteTurn ? 56 : 0];
            }

        }
        // fix the castle fields (regardless of if castle move
        // since could be rook or king move that changed it last move)
        castleState = move.castleState.copy();
    }

    public void reset() {
        // DEFAULT BOARD POSITION
        // bitboard is defined top right to bottom right: 100000000 -> 100 \n 000 \n 000 if 3x3
        final long whiteKingBoard = 0x8;
        final long blackKingBoard = 0x0800000000000000L;
        final long whiteQueenBoard = 0x10;
        final long blackQueenBoard = 0x1000000000000000L;
        final long whiteRookBoard = 0x81;
        final long blackRookBoard = 0x8100000000000000L;
        final long whiteKnightBoard = 0x42;
        final long blackKnightBoard = 0x4200000000000000L;
        final long whiteBishopBoard = 0x24;
        final long blackBishopBoard = 0x2400000000000000L;
        final long whitePawnBoard = 0xFF00; // 0xFF -> 0xFF00 (each 0 adds 16 (2^4) to move up one row is 16*16 or 00)
        final long blackPawnBoard = 0xFF000000000000L;

        // CASTLING DEFAULTS
        castleState = new CastleState();

        whiteBitBoards = new long[]{
                whiteKingBoard,
                whiteQueenBoard,
                whiteRookBoard,
                whiteBishopBoard,
                whiteKnightBoard,
                whitePawnBoard
        };

        blackBitBoards = new long[]{
                blackKingBoard,
                blackQueenBoard,
                blackRookBoard,
                blackBishopBoard,
                blackKnightBoard,
                blackPawnBoard
        };
        isWhiteTurn = true;
        moveStack = new Stack<Move>();
    }

    // returns a printable string of the current board
    @Override
    public String toString() {
        long[] bitboards = {
                whiteBitBoards[0], blackBitBoards[0],
                whiteBitBoards[1], blackBitBoards[1],
                whiteBitBoards[2], blackBitBoards[2],
                whiteBitBoards[3], blackBitBoards[3],
                whiteBitBoards[4], blackBitBoards[4],
                whiteBitBoards[5], blackBitBoards[5]
        };
        char[] symbols = {
                'K', 'k', // White King, Black King
                'Q', 'q', // White Queen, Black Queen
                'R', 'r', // White Rook, Black Rook
                'B', 'b', // White Bishop, Black Bishop
                'N', 'n', // White Knight, Black Knight
                'P', 'p'  // White Pawn, Black Pawn
        };
        StringBuilder returnString = new StringBuilder();
        for (int piecePos = 0; piecePos < 64; piecePos++) { // loop over piece position in string
            char symbolAtPos = EMPTY_SQUARE;
            for (int i = 0; i < bitboards.length; i++) {
                long bitboard = bitboards[i];
                char symbol = symbols[i];
                if ((bitboard & HEAD_INT) != 0) { // check if there is a piece at that position
                    if (symbolAtPos != EMPTY_SQUARE) { // sanity check to make sure there aren't two pieces at same pos
                        throw new RuntimeException("Two Pieces found at the same square");
                    }
                    symbolAtPos = symbol;
                }
                bitboards[i] = bitboard << 1; // left shift bitboard regardless of if there is a piece
            }
            returnString.append(symbolAtPos);
            if ((piecePos+1)%8 == 0) { // append newline after every row
                returnString.append('\n');
            }
            else {
                returnString.append(" "); // if not an end row character add spacing for looks
            }
        }
        return "Current Turn: " + (isWhiteTurn ? "White" : "Black") + "\n" + returnString;
    }

    public String highlightPieceMoves(int pos) {
        int[][] movePairs = getMovePairs();
        HashSet<Integer> moveTargetsFromPos = new HashSet<>();
        for (int[] movePair : movePairs) {
            if (movePair[0] == pos) { // if move piece at pos
                moveTargetsFromPos.add(movePair[1]); // add target
            }
        }

        long[] bitboards = {
                whiteBitBoards[0], blackBitBoards[0],
                whiteBitBoards[1], blackBitBoards[1],
                whiteBitBoards[2], blackBitBoards[2],
                whiteBitBoards[3], blackBitBoards[3],
                whiteBitBoards[4], blackBitBoards[4],
                whiteBitBoards[5], blackBitBoards[5]
        };
        char[] symbols = {
                'K', 'k', // White King, Black King
                'Q', 'q', // White Queen, Black Queen
                'R', 'r', // White Rook, Black Rook
                'B', 'b', // White Bishop, Black Bishop
                'N', 'n', // White Knight, Black Knight
                'P', 'p'  // White Pawn, Black Pawn
        };
        StringBuilder returnString = new StringBuilder();
        for (int piecePos = 0; piecePos < 64; piecePos++) { // loop over piece position in string
            char symbolAtPos = EMPTY_SQUARE;
            for (int i = 0; i < bitboards.length; i++) {
                long bitboard = bitboards[i];
                char symbol = symbols[i];
                if ((bitboard & HEAD_INT) != 0) { // check if there is a piece at that position
                    if (symbolAtPos != EMPTY_SQUARE) { // sanity check to make sure there aren't two pieces at same pos
                        throw new RuntimeException("Two Pieces found at the same square");
                    }
                    symbolAtPos = symbol;

                }
                bitboards[i] = bitboard << 1; // left shift bitboard regardless of if there is a piece
            }

            if (moveTargetsFromPos.contains(piecePos)) {
                symbolAtPos = 'X';
            }

            returnString.append(symbolAtPos);
            if ((piecePos+1) % 8 == 0) { // append newline after every row
                returnString.append('\n');
            }
            else {
                returnString.append(" "); // if not an end row character add spacing for looks
            }
        }
        return "Current Turn: " + (isWhiteTurn ? "White" : "Black") + "\n" + returnString;
    }
}
