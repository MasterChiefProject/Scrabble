package com.masterchiefproject.scrabble.game;

import com.masterchiefproject.scrabble.dictionary.WordValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rules and scoring engine for a standard 15x15 English Scrabble-style board.
 * The board is independent from player racks and turn order.
 */
public final class Board {
    public static final int SIZE = 15;
    public static final Position CENTER = new Position(7, 7);

    private final Tile[][] grid = new Tile[SIZE][SIZE];
    private final PremiumSquare[][] premiums = createPremiumLayout();
    private int tileCount;

    public Tile tileAt(int row, int col) {
        requireInside(row, col);
        return grid[row][col];
    }

    public PremiumSquare premiumAt(int row, int col) {
        requireInside(row, col);
        return premiums[row][col];
    }

    public int tileCount() {
        return tileCount;
    }

    public boolean isEmpty() {
        return tileCount == 0;
    }

    /** Validates, scores, and atomically applies a move. */
    public MoveResult place(List<Placement> placements, WordValidator validator) {
        Objects.requireNonNull(validator, "validator");
        MoveAnalysis analysis = analyze(placements, validator);
        for (Placement placement : analysis.placements()) {
            Position p = placement.position();
            grid[p.row()][p.col()] = placement.tile();
            tileCount++;
        }
        return new MoveResult(analysis.score(), analysis.words(), analysis.bingo());
    }

    /** Validates and scores without modifying the board. */
    public MoveResult preview(List<Placement> placements, WordValidator validator) {
        MoveAnalysis analysis = analyze(placements, validator);
        return new MoveResult(analysis.score(), analysis.words(), analysis.bingo());
    }

    private MoveAnalysis analyze(List<Placement> rawPlacements, WordValidator validator) {
        if (rawPlacements == null || rawPlacements.isEmpty()) {
            throw new InvalidMoveException("Place at least one tile");
        }
        if (rawPlacements.size() > 7) {
            throw new InvalidMoveException("A move cannot place more than seven rack tiles");
        }

        List<Placement> placements = List.copyOf(rawPlacements);
        Map<Position, Tile> newTiles = new LinkedHashMap<>();
        for (Placement placement : placements) {
            Position p = placement.position();
            if (!inside(p.row(), p.col())) {
                throw new InvalidMoveException("Placement is outside the board: " + p);
            }
            if (grid[p.row()][p.col()] != null) {
                throw new InvalidMoveException("A new tile cannot overwrite an occupied square: " + p);
            }
            if (newTiles.putIfAbsent(p, placement.tile()) != null) {
                throw new InvalidMoveException("Two tiles cannot occupy the same square: " + p);
            }
        }

        boolean sameRow = placements.stream().map(p -> p.position().row()).distinct().count() == 1;
        boolean sameCol = placements.stream().map(p -> p.position().col()).distinct().count() == 1;
        if (placements.size() > 1 && !sameRow && !sameCol) {
            throw new InvalidMoveException("All newly placed tiles must be in one row or one column");
        }

        if (isEmpty()) {
            if (!newTiles.containsKey(CENTER)) {
                throw new InvalidMoveException("The first move must cover the center square");
            }
        } else if (!touchesExisting(newTiles.keySet())) {
            throw new InvalidMoveException("The move must connect to at least one existing tile");
        }

        if (placements.size() > 1) {
            ensureContiguous(placements, newTiles, sameRow);
        }

        List<FormedWord> formedWords = collectFormedWords(placements, newTiles, sameRow, sameCol);
        if (formedWords.isEmpty()) {
            throw new InvalidMoveException("The move must form at least one word of two or more letters");
        }

        List<String> words = new ArrayList<>(formedWords.size());
        int score = 0;
        for (FormedWord word : formedWords) {
            String text = wordText(word, newTiles);
            if (!validator.isValid(text)) {
                throw new InvalidMoveException("Word is not in the configured dictionary: " + text);
            }
            words.add(text);
            score += scoreWord(word, newTiles);
        }

        boolean bingo = placements.size() == 7;
        if (bingo) {
            score += 50;
        }

        return new MoveAnalysis(placements, score, words, bingo);
    }

    private void ensureContiguous(List<Placement> placements, Map<Position, Tile> newTiles, boolean horizontal) {
        if (horizontal) {
            int row = placements.get(0).position().row();
            int min = placements.stream().mapToInt(p -> p.position().col()).min().orElseThrow();
            int max = placements.stream().mapToInt(p -> p.position().col()).max().orElseThrow();
            for (int col = min; col <= max; col++) {
                if (tileConsideringNew(row, col, newTiles) == null) {
                    throw new InvalidMoveException("Placed tiles cannot leave an empty gap");
                }
            }
        } else {
            int col = placements.get(0).position().col();
            int min = placements.stream().mapToInt(p -> p.position().row()).min().orElseThrow();
            int max = placements.stream().mapToInt(p -> p.position().row()).max().orElseThrow();
            for (int row = min; row <= max; row++) {
                if (tileConsideringNew(row, col, newTiles) == null) {
                    throw new InvalidMoveException("Placed tiles cannot leave an empty gap");
                }
            }
        }
    }

    private List<FormedWord> collectFormedWords(
            List<Placement> placements,
            Map<Position, Tile> newTiles,
            boolean sameRow,
            boolean sameCol) {

        Map<String, FormedWord> unique = new LinkedHashMap<>();
        Position origin = placements.get(0).position();

        if (placements.size() == 1) {
            addIfWord(unique, buildWord(origin, 0, 1, newTiles));
            addIfWord(unique, buildWord(origin, 1, 0, newTiles));
        } else if (sameRow) {
            addIfWord(unique, buildWord(origin, 0, 1, newTiles));
            for (Placement placement : placements) {
                addIfWord(unique, buildWord(placement.position(), 1, 0, newTiles));
            }
        } else if (sameCol) {
            addIfWord(unique, buildWord(origin, 1, 0, newTiles));
            for (Placement placement : placements) {
                addIfWord(unique, buildWord(placement.position(), 0, 1, newTiles));
            }
        }

        return new ArrayList<>(unique.values());
    }

    private void addIfWord(Map<String, FormedWord> unique, FormedWord word) {
        if (word.positions().size() < 2) {
            return;
        }
        String key = word.positions().stream()
                .map(p -> p.row() + ":" + p.col())
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
        unique.putIfAbsent(key, word);
    }

    private FormedWord buildWord(Position origin, int dr, int dc, Map<Position, Tile> newTiles) {
        int row = origin.row();
        int col = origin.col();

        while (inside(row - dr, col - dc) && tileConsideringNew(row - dr, col - dc, newTiles) != null) {
            row -= dr;
            col -= dc;
        }

        List<Position> positions = new ArrayList<>();
        while (inside(row, col) && tileConsideringNew(row, col, newTiles) != null) {
            positions.add(new Position(row, col));
            row += dr;
            col += dc;
        }
        return new FormedWord(positions);
    }

    private String wordText(FormedWord word, Map<Position, Tile> newTiles) {
        StringBuilder out = new StringBuilder(word.positions().size());
        for (Position p : word.positions()) {
            out.append(tileConsideringNew(p.row(), p.col(), newTiles).letter());
        }
        return out.toString();
    }

    private int scoreWord(FormedWord word, Map<Position, Tile> newTiles) {
        int letterScore = 0;
        int wordMultiplier = 1;

        for (Position p : word.positions()) {
            Tile tile = tileConsideringNew(p.row(), p.col(), newTiles);
            boolean newlyPlaced = newTiles.containsKey(p);
            if (newlyPlaced) {
                PremiumSquare premium = premiums[p.row()][p.col()];
                letterScore += tile.points() * premium.letterMultiplier();
                wordMultiplier *= premium.wordMultiplier();
            } else {
                letterScore += tile.points();
            }
        }
        return letterScore * wordMultiplier;
    }

    private boolean touchesExisting(Collection<Position> positions) {
        for (Position p : positions) {
            if (existingAt(p.row() - 1, p.col()) || existingAt(p.row() + 1, p.col())
                    || existingAt(p.row(), p.col() - 1) || existingAt(p.row(), p.col() + 1)) {
                return true;
            }
        }
        return false;
    }

    private boolean existingAt(int row, int col) {
        return inside(row, col) && grid[row][col] != null;
    }

    private Tile tileConsideringNew(int row, int col, Map<Position, Tile> newTiles) {
        Tile replacement = newTiles.get(new Position(row, col));
        return replacement != null ? replacement : grid[row][col];
    }

    private static boolean inside(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    private static void requireInside(int row, int col) {
        if (!inside(row, col)) {
            throw new IndexOutOfBoundsException("Board position out of range: " + row + "," + col);
        }
    }

    private static PremiumSquare[][] createPremiumLayout() {
        PremiumSquare[][] result = new PremiumSquare[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                result[row][col] = PremiumSquare.NORMAL;
            }
        }

        mark(result, PremiumSquare.TRIPLE_WORD,
                0,0, 0,7, 0,14, 7,0, 7,14, 14,0, 14,7, 14,14);
        mark(result, PremiumSquare.DOUBLE_WORD,
                1,1, 1,13, 2,2, 2,12, 3,3, 3,11, 4,4, 4,10,
                10,4, 10,10, 11,3, 11,11, 12,2, 12,12, 13,1, 13,13);
        result[CENTER.row()][CENTER.col()] = PremiumSquare.CENTER;
        mark(result, PremiumSquare.TRIPLE_LETTER,
                1,5, 1,9, 5,1, 5,5, 5,9, 5,13, 9,1, 9,5, 9,9, 9,13, 13,5, 13,9);
        mark(result, PremiumSquare.DOUBLE_LETTER,
                0,3, 0,11, 2,6, 2,8, 3,0, 3,7, 3,14,
                6,2, 6,6, 6,8, 6,12, 7,3, 7,11,
                8,2, 8,6, 8,8, 8,12, 11,0, 11,7, 11,14,
                12,6, 12,8, 14,3, 14,11);
        return result;
    }

    private static void mark(PremiumSquare[][] board, PremiumSquare type, int... coordinates) {
        for (int i = 0; i < coordinates.length; i += 2) {
            board[coordinates[i]][coordinates[i + 1]] = type;
        }
    }

    private record FormedWord(List<Position> positions) {
        private FormedWord {
            positions = List.copyOf(positions);
        }
    }

    private record MoveAnalysis(List<Placement> placements, int score, List<String> words, boolean bingo) {
        private MoveAnalysis {
            placements = List.copyOf(placements);
            words = List.copyOf(words);
        }
    }
}
