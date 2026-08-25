package com.masterchiefproject.scrabble.game;

/** Premium square types used by the standard 15x15 English board. */
public enum PremiumSquare {
    NORMAL(1, 1, ""),
    DOUBLE_LETTER(2, 1, "DL"),
    TRIPLE_LETTER(3, 1, "TL"),
    DOUBLE_WORD(1, 2, "DW"),
    TRIPLE_WORD(1, 3, "TW"),
    CENTER(1, 2, "★");

    private final int letterMultiplier;
    private final int wordMultiplier;
    private final String label;

    PremiumSquare(int letterMultiplier, int wordMultiplier, String label) {
        this.letterMultiplier = letterMultiplier;
        this.wordMultiplier = wordMultiplier;
        this.label = label;
    }

    public int letterMultiplier() {
        return letterMultiplier;
    }

    public int wordMultiplier() {
        return wordMultiplier;
    }

    public String label() {
        return label;
    }
}
