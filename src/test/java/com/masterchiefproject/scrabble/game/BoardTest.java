package com.masterchiefproject.scrabble.game;

import com.masterchiefproject.scrabble.dictionary.WordValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {
    private static final WordValidator ALL = WordValidator.acceptAll();

    @Test
    void firstWordMustCoverCenterAndScoresPremiums() {
        Board board = new Board();
        MoveResult result = board.place(word("HORN", 7, 5, false), ALL);

        assertEquals(14, result.score());
        assertEquals(List.of("HORN"), result.words());
        assertEquals('H', board.tileAt(7, 5).letter());
        assertEquals('N', board.tileAt(7, 8).letter());
    }

    @Test
    void connectedWordCanReuseExistingTile() {
        Board board = new Board();
        board.place(word("HORN", 7, 5, false), ALL);

        List<Placement> farm = List.of(
                p('F', 4, 5, 7),
                p('A', 1, 6, 7),
                p('M', 3, 8, 7));
        MoveResult result = board.place(farm, ALL);

        assertEquals(9, result.score());
        assertEquals(List.of("FARM"), result.words());
    }

    @Test
    void invalidDictionaryWordDoesNotMutateBoard() {
        Board board = new Board();
        WordValidator onlyHello = word -> word.equals("HELLO");

        assertThrows(InvalidMoveException.class,
                () -> board.place(word("HORN", 7, 5, false), onlyHello));
        assertEquals(0, board.tileCount());
    }

    @Test
    void rejectsDisconnectedAndOverwritingMoves() {
        Board board = new Board();
        board.place(word("HORN", 7, 5, false), ALL);

        assertThrows(InvalidMoveException.class,
                () -> board.place(word("CAT", 0, 0, false), ALL));
        assertThrows(InvalidMoveException.class,
                () -> board.place(List.of(p('X', 8, 7, 5)), ALL));
    }

    @Test
    void sevenNewTilesReceiveBingoBonus() {
        Board board = new Board();
        MoveResult result = board.place(word("EXAMPLE", 7, 4, false), ALL);
        assertTrue(result.bingo());
        assertTrue(result.score() >= 50);
    }

    private static List<Placement> word(String word, int row, int col, boolean vertical) {
        List<Placement> out = new ArrayList<>();
        for (int i = 0; i < word.length(); i++) {
            int r = row + (vertical ? i : 0);
            int c = col + (vertical ? 0 : i);
            out.add(p(word.charAt(i), points(word.charAt(i)), r, c));
        }
        return out;
    }

    private static Placement p(char letter, int points, int row, int col) {
        return new Placement(new Position(row, col), new Tile(letter, points, false));
    }

    private static int points(char letter) {
        return switch (letter) {
            case 'B', 'C', 'M', 'P' -> 3;
            case 'D', 'G' -> 2;
            case 'F', 'H', 'V', 'W', 'Y' -> 4;
            case 'K' -> 5;
            case 'J', 'X' -> 8;
            case 'Q', 'Z' -> 10;
            default -> 1;
        };
    }
}
