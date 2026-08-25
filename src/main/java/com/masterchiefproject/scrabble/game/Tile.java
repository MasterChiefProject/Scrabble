package com.masterchiefproject.scrabble.game;

import java.util.Locale;

/** Immutable letter tile. A rack blank uses '?' and receives an assigned letter when placed. */
public record Tile(char letter, int points, boolean blank) {
    public Tile {
        letter = Character.toUpperCase(letter);
        if (points < 0) {
            throw new IllegalArgumentException("Tile points cannot be negative");
        }
        if (blank) {
            if (points != 0) {
                throw new IllegalArgumentException("Blank tiles must be worth zero points");
            }
            if (letter != '?' && (letter < 'A' || letter > 'Z')) {
                throw new IllegalArgumentException("A blank must be unassigned '?' or assigned A-Z");
            }
        } else if (letter < 'A' || letter > 'Z') {
            throw new IllegalArgumentException("Tile letter must be A-Z");
        }
    }

    public static Tile blankTile() {
        return new Tile('?', 0, true);
    }

    public Tile assignBlank(char assignedLetter) {
        if (!blank) {
            throw new IllegalStateException("Only a blank tile can be assigned a letter");
        }
        char normalized = Character.toUpperCase(assignedLetter);
        if (normalized < 'A' || normalized > 'Z') {
            throw new IllegalArgumentException("Blank assignment must be A-Z");
        }
        return new Tile(normalized, 0, true);
    }

    public String display() {
        return String.valueOf(letter).toUpperCase(Locale.ROOT);
    }
}
