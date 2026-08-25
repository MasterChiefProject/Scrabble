package com.masterchiefproject.scrabble.game;

import com.masterchiefproject.scrabble.dictionary.WordValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void connectedWordCanReuseExistingTileWithoutReusingPremium() {
        Board board = new Board();
        board.place(word("HORN", 7, 5, false), ALL);

        MoveResult result = board.place(List.of(
                p('A', 6, 7),
                p('E', 8, 7)), ALL);

        assertEquals(List.of("ARE"), result.words());
        assertEquals(3, result.score(), "The center double-word premium must not be reused");
    }

    @Test
    void oneTileCanCreateAndScoreTwoCrossWords() {
        Board board = new Board();
        board.place(word("HORN", 7, 5, false), ALL);
        board.place(List.of(p('F', 5, 7), p('A', 6, 7), p('M', 8, 7)), ALL);

        MoveResult result = board.place(List.of(p('B', 6, 6)), ALL);

        assertEquals(List.of("BA", "BO"), result.words());
        assertEquals(14, result.score());
    }

    @Test
    void blankTileScoresZeroWhileStillApplyingWordPremium() {
        Board board = new Board();
        MoveResult result = board.place(List.of(
                new Placement(new Position(7, 6), Tile.blankTile().assignBlank('C')),
                p('A', 7, 7),
                p('T', 7, 8)), ALL);

        assertEquals(List.of("CAT"), result.words());
        assertEquals(4, result.score());
    }

    @Test
    void invalidDictionaryWordDoesNotMutateBoard() {
        Board board = new Board();
        WordValidator onlyHello = value -> value.equals("HELLO");

        assertThrows(InvalidMoveException.class,
                () -> board.place(word("HORN", 7, 5, false), onlyHello));
        assertEquals(0, board.tileCount());
    }

    @Test
    void rejectsDisconnectedOverwritingAndGappedMoves() {
        Board board = new Board();
        board.place(word("HORN", 7, 5, false), ALL);

        assertThrows(InvalidMoveException.class,
                () -> board.place(word("CAT", 0, 0, false), ALL));
        assertThrows(InvalidMoveException.class,
                () -> board.place(List.of(p('X', 7, 5)), ALL));
        assertThrows(InvalidMoveException.class,
                () -> board.place(List.of(p('A', 6, 4), p('B', 6, 6)), ALL));
    }

    @Test
    void sevenNewTilesReceiveBingoBonus() {
        Board board = new Board();
        MoveResult result = board.place(word("EXAMPLE", 7, 4, false), ALL);
        assertTrue(result.bingo());
        assertTrue(result.score() >= 50);
    }

    @Test
    void previewIsAtomicAndDoesNotMutateBoard() {
        Board board = new Board();
        MoveResult preview = board.preview(word("HORN", 7, 5, false), ALL);
        assertEquals(14, preview.score());
        assertEquals(0, board.tileCount());
    }

    private static List<Placement> word(String value, int row, int col, boolean vertical) {
        List<Placement> out = new ArrayList<>();
        for (int i = 0; i < value.length(); i++) {
            int r = row + (vertical ? i : 0);
            int c = col + (vertical ? 0 : i);
            out.add(p(value.charAt(i), r, c));
        }
        return out;
    }

    private static Placement p(char letter, int row, int col) {
        return new Placement(new Position(row, col), Tile.letterTile(letter));
    }
}
