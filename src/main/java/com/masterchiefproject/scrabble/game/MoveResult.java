package com.masterchiefproject.scrabble.game;

import java.util.List;

public record MoveResult(int score, List<String> words, boolean bingo) {
    public MoveResult {
        words = List.copyOf(words);
    }
}
