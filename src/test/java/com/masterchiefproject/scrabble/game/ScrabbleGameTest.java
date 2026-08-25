package com.masterchiefproject.scrabble.game;

import com.masterchiefproject.scrabble.dictionary.WordValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScrabbleGameTest {
    @Test
    void sixScorelessTurnsEndTheGame() {
        ScrabbleGame game = new ScrabbleGame(List.of("Player 1", "Player 2"), WordValidator.acceptAll(), 42L);
        for (int i = 0; i < ScrabbleGame.MAX_SCORELESS_TURNS; i++) {
            game.passTurn();
        }
        assertTrue(game.isGameOver());
    }

    @Test
    void startsWithSevenTilesPerPlayer() {
        ScrabbleGame game = new ScrabbleGame(List.of("One", "Two"), WordValidator.acceptAll(), 1L);
        assertEquals(7, game.players().get(0).rack().size());
        assertEquals(7, game.players().get(1).rack().size());
        assertEquals(86, game.bagRemaining());
    }
}
