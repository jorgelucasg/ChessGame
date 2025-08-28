package controller;

import javax.swing.JOptionPane;
import model.board.Board;
import model.board.Position;
import model.pieces.*;

public class Game {
    private Board board;
    private boolean isWhiteTurn;
    private boolean isGameOver;
    private Piece selectedPiece;

    public Game() {
        board = new Board();
        isWhiteTurn = true;
        isGameOver = false;
        setupPieces();
    }

    private void setupPieces() {
        // Peças brancas
        board.placePiece(new Rook(board, true), new Position(7, 0));
        board.placePiece(new Knight(board, true), new Position(7, 1));
        board.placePiece(new Bishop(board, true), new Position(7, 2));
        board.placePiece(new Queen(board, true), new Position(7, 3));
        board.placePiece(new King(board, true), new Position(7, 4));
        board.placePiece(new Bishop(board, true), new Position(7, 5));
        board.placePiece(new Knight(board, true), new Position(7, 6));
        board.placePiece(new Rook(board, true), new Position(7, 7));
        for (int col = 0; col < 8; col++) {
            board.placePiece(new Pawn(board, true), new Position(6, col));
        }

        // Peças pretas
        board.placePiece(new Rook(board, false), new Position(0, 0));
        board.placePiece(new Knight(board, false), new Position(0, 1));
        board.placePiece(new Bishop(board, false), new Position(0, 2));
        board.placePiece(new Queen(board, false), new Position(0, 3));
        board.placePiece(new King(board, false), new Position(0, 4));
        board.placePiece(new Bishop(board, false), new Position(0, 5));
        board.placePiece(new Knight(board, false), new Position(0, 6));
        board.placePiece(new Rook(board, false), new Position(0, 7));
        for (int col = 0; col < 8; col++) {
            board.placePiece(new Pawn(board, false), new Position(1, col));
        }
    }

    public Board getBoard() {
        return board;
    }

    public boolean isWhiteTurn() {
        return isWhiteTurn;
    }

    public boolean isGameOver() {
        return isGameOver;
    }

    public Piece getSelectedPiece() {
        return selectedPiece;
    }

    public void selectPiece(Position position) {
        Piece piece = board.getPieceAt(position);
        if (piece != null && piece.isWhite() == isWhiteTurn) {
            selectedPiece = piece;
        }
    }

    public boolean movePiece(Position destination) {
        if (selectedPiece == null || isGameOver) return false;

        Position from = selectedPiece.getPosition();

        // --- TENTA ROQUE (antes de qualquer movimento normal) ---
        if (selectedPiece instanceof King &&
            Math.abs(destination.getColumn() - from.getColumn()) == 2) {
            boolean ok = attemptCastling((King) selectedPiece, from, destination);
            if (ok) {
                // Depois do roque bem-sucedido:
                checkGameStatus();
                isWhiteTurn = !isWhiteTurn;
                selectedPiece = null;
                return true;
            } else {
                return false;
            }
        }

        // --- Movimento normal ---
        if (!selectedPiece.canMoveTo(destination)) return false;
        if (moveCausesCheck(selectedPiece, destination)) return false;

        Piece capturedPiece = board.getPieceAt(destination);

        // Executa
        board.removePiece(from);
        board.placePiece(selectedPiece, destination);

        // Marca que a peça se moveu
        selectedPiece.setHasMoved(true);

        // Condições especiais (promoção; en passant real exigiria histórico de lances)
        handlePromotionIfAny(selectedPiece, destination);

        // Status
        checkGameStatus();

        isWhiteTurn = !isWhiteTurn;
        selectedPiece = null;
        return true;
    }

    // =========================
    //      CASTLING OFICIAL
    // =========================
    private boolean attemptCastling(King king, Position from, Position destination) {
        // Regras: rei/torre nunca moveram, caminho livre, rei não está em xeque,
        // e não passa/termina em casa atacada.
        if (king.hasMoved()) return false;

        int row = from.getRow();
        int colFrom = from.getColumn();
        int colTo = destination.getColumn();
        int dir = (colTo > colFrom) ? 1 : -1; // roque pequeno (dir=1) ou grande (dir=-1)

        int rookCol = (dir == 1) ? 7 : 0;
        Position rookPos = new Position(row, rookCol);
        Piece rook = board.getPieceAt(rookPos);

        if (!(rook instanceof Rook)) return false;
        if (rook.hasMoved()) return false;

        // 1) Caminho entre rei e torre deve estar livre (exclui o rei e a torre)
        for (int c = colFrom + dir; c != rookCol; c += dir) {
            if (board.getPieceAt(new Position(row, c)) != null) return false;
        }

        // 2) Rei não pode estar em xeque na posição inicial
        if (isSquareAttacked(from, !king.isWhite())) return false;

        // 3) Rei não pode passar por casa atacada nem terminar em casa atacada
        int midCol = colFrom + dir; // casa intermediária por onde o rei passa
        Position midPos = new Position(row, midCol);
        if (isSquareAttacked(midPos, !king.isWhite())) return false;
        if (isSquareAttacked(destination, !king.isWhite())) return false;

        // --- Executa roque: mover rei e torre ---
        // Mover rei
        board.removePiece(from);
        board.placePiece(king, destination);
        king.setHasMoved(true);

        // Mover torre
        int newRookCol = (dir == 1) ? (colTo - 1) : (colTo + 1);
        Position newRookPos = new Position(row, newRookCol);
        board.removePiece(rookPos);
        board.placePiece(rook, newRookPos);
        rook.setHasMoved(true);

        return true;
    }

    // =========================
    //    PROMOÇÃO DE PEÃO
    // =========================
    private void handlePromotionIfAny(Piece piece, Position destination) {
        if (!(piece instanceof Pawn)) return;

        if ((piece.isWhite() && destination.getRow() == 0) ||
            (!piece.isWhite() && destination.getRow() == 7)) {

            String[] options = {"Rainha", "Torre", "Bispo", "Cavalo"};
            int choice = JOptionPane.showOptionDialog(
                    null,
                    "Escolha uma peça para promoção:",
                    "Promoção de Peão",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]
            );

            Piece newPiece;
            switch (choice) {
                case 1: newPiece = new Rook(board, piece.isWhite()); break;
                case 2: newPiece = new Bishop(board, piece.isWhite()); break;
                case 3: newPiece = new Knight(board, piece.isWhite()); break;
                default: newPiece = new Queen(board, piece.isWhite());
            }

            board.removePiece(destination);
            board.placePiece(newPiece, destination);
        }
    }

    /**
     * Simula o movimento e verifica se deixa o rei do lado que move em xeque.
     * Assume que Piece.canMoveTo não considera segurança do rei.
     */
    private boolean moveCausesCheck(Piece piece, Position destination) {
        Position from = piece.getPosition();
        Piece captured = board.getPieceAt(destination);

        // Simula
        board.removePiece(from);
        if (captured != null) board.removePiece(destination);
        board.placePiece(piece, destination);

        boolean inCheck;
        try {
            Position myKing = findKingPosition(piece.isWhite());
            if (myKing == null) return true; // posição inválida
            inCheck = isSquareAttacked(myKing, !piece.isWhite());
        } finally {
            // Desfaz
            board.removePiece(destination);
            board.placePiece(piece, from);
            if (captured != null) board.placePiece(captured, destination);
        }
        return inCheck;
    }

    private Position findKingPosition(boolean whiteKing) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Position p = new Position(r, c);
                Piece pc = board.getPieceAt(p);
                if (pc instanceof King && pc.isWhite() == whiteKing) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * Verdadeiro se alguma peça de 'byWhite' ataca 'square' (sem checar segurança do rei).
     */
    private boolean isSquareAttacked(Position square, boolean byWhite) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Position from = new Position(r, c);
                Piece p = board.getPieceAt(from);
                if (p == null || p.isWhite() != byWhite) continue;
                if (from.equals(square)) continue;
                if (p.canMoveTo(square)) return true;
            }
        }
        return false;
    }

    private void checkGameStatus() {
        // Verifica o lado que acabou de jogar colocou o oponente em xeque/xeque-mate/empate
        boolean sideJustMovedIsWhite = !isWhiteTurn; // pois o turno alterna após o movimento
        boolean opponentIsWhite = !sideJustMovedIsWhite;

        Position opponentKing = findKingPosition(opponentIsWhite);
        if (opponentKing == null) {
            isGameOver = true;
            JOptionPane.showMessageDialog(null,
                    (sideJustMovedIsWhite ? "Brancas" : "Pretas") + " vencem por xeque-mate!");
            return;
        }

        boolean kingInCheck = isSquareAttacked(opponentKing, sideJustMovedIsWhite);

        // Checa se o oponente tem algum lance legal
        boolean anyLegalMove = false;
        outer:
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Position from = new Position(r, c);
                Piece p = board.getPieceAt(from);
                if (p == null || p.isWhite() != opponentIsWhite) continue;

                for (Position to : p.getPossibleMoves()) {
                    if (!moveCausesCheck(p, to)) {
                        anyLegalMove = true;
                        break outer;
                    }
                }
            }
        }

        if (!anyLegalMove) {
            isGameOver = true;
            if (kingInCheck) {
                JOptionPane.showMessageDialog(null,
                        (sideJustMovedIsWhite ? "Brancas" : "Pretas") + " vencem por xeque-mate!");
            } else {
                JOptionPane.showMessageDialog(null, "Empate por afogamento!");
            }
        } else if (kingInCheck) {
            JOptionPane.showMessageDialog(null,
                    (opponentIsWhite ? "Rei branco" : "Rei preto") + " está em xeque!");
        }
    }
}
