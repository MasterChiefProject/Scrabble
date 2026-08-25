package com.masterchiefproject.scrabble.game;

import com.masterchiefproject.scrabble.dictionary.Dictionary;

import java.util.List;

/** Minimal console entry point for verifying the packaged engine and dictionary. */
public final class GameDemo {
    private GameDemo() {
    }

    public static void main(String[] args) throws Exception {
        Dictionary dictionary = Dictionary.fromResource("/dictionary/words.txt");
        ScrabbleGame game = new ScrabbleGame(List.of("Player 1", "Player 2"), dictionary);

        System.out.println("ScrabbleGame engine ready");
        System.out.println("Dictionary words: " + dictionary.size());
        System.out.println("Tiles remaining after initial racks: " + game.bagRemaining());
        System.out.println("Current player: " + game.currentPlayer().name());
        System.out.println("Rack: " + game.currentPlayer().rack().stream().map(Tile::display).toList());
    }
}
