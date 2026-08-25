package com.masterchiefproject.scrabble.game;

import com.masterchiefproject.scrabble.dictionary.WordValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrabbleGameTest {
    @Test
    void supportsTwoToFourUniquePlayers() {
        assertEquals(2, new ScrabbleGame(List.of("One", "Two"), WordValidator.acceptAll(), 1L).players().size());
        assertEquals(4, new ScrabbleGame(List.of("One", "Two", "Three", "Four"), WordValidator.acceptAll(), 1L).players().size());
        assertThrows(IllegalArgumentException.class,
                () -> new ScrabbleGame(List.of("Only one"), WordValidator.acceptAll(), 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new ScrabbleGame(List.of("One", " one "), WordValidator.acceptAll(), 1L));
    }

    @Test
    void startsWithSevenTilesPerPlayerAndRotatesTurns() {
        ScrabbleGame game = new ScrabbleGame(List.of("One", "Two"), WordValidator.acceptAll(), 1L);
        assertEquals(7, game.players().get(0).rack().size());
        assertEquals(7, game.players().get(1).rack().size());
        assertEquals(86, game.bagRemaining());

        game.passTurn();
        assertEquals(1, game.currentPlayerIndex());
        game.passTurn();
        assertEquals(0, game.currentPlayerIndex());
    }

    @Test
    void rejectsDuplicateRackIndexAndNullPlacementRequest() {
        ScrabbleGame game = new ScrabbleGame(List.of("One", "Two"), WordValidator.acceptAll(), 2L);
        assertThrows(InvalidMoveException.class,
                () -> game.playMove(List.of(new PlacementRequest(0, 7, 7), new PlacementRequest(0, 7, 8))));

        List<PlacementRequest> requests = new ArrayList<>();
        requests.add(null);
        assertThrows(InvalidMoveException.class, () -> game.playMove(requests));
    }

    @Test
    void sixScorelessTurnsEndTheGameAndDeductRackValues() {
        ScrabbleGame game = new ScrabbleGame(List.of("Player 1", "Player 2"), WordValidator.acceptAll(), 42L);
        int initialRackValue = game.players().stream()
                .flatMap(player -> player.rack().stream())
                .mapToInt(Tile::points)
                .sum();

        for (int i = 0; i < ScrabbleGame.MAX_SCORELESS_TURNS; i++) game.passTurn();

        assertTrue(game.isGameOver());
        assertEquals(-initialRackValue, game.players().stream().mapToInt(Player::score).sum());
        assertThrows(IllegalStateException.class, game::passTurn);
    }

    @Test
    void goingOutAwardsOpponentsRemainingRackPoints() {
        List<Tile> initial = new ArrayList<>();
        // Player 2 is drawn second. These seven tiles remain after Player 1 draws their rack.
        initial.addAll(List.of(
                Tile.letterTile('A'), Tile.letterTile('A'), Tile.letterTile('A'), Tile.letterTile('A'),
                Tile.letterTile('A'), Tile.letterTile('A'), Tile.letterTile('A')));
        // Last element is drawn first, so reverse EXAMPLE for Player 1's rack.
        initial.addAll(List.of(
                Tile.letterTile('E'), Tile.letterTile('L'), Tile.letterTile('P'), Tile.letterTile('M'),
                Tile.letterTile('A'), Tile.letterTile('X'), Tile.letterTile('E')));

        ScrabbleGame game = new ScrabbleGame(
                List.of("One", "Two"), WordValidator.acceptAll(), new TileBag(initial, 1L));
        assertEquals(0, game.bagRemaining());

        List<PlacementRequest> requests = new ArrayList<>();
        for (int i = 0; i < 7; i++) requests.add(new PlacementRequest(i, 7, 4 + i));
        MoveResult result = game.playMove(requests);

        assertTrue(result.bingo());
        assertTrue(game.isGameOver());
        assertTrue(game.players().get(0).score() > result.score(), "Finisher should receive opponent rack points");
        assertEquals(-7, game.players().get(1).score());
    }
}
