package com.masterchiefproject.scrabble.game;

import java.util.Locale;

/** Immutable standard English Scrabble tile. */
public record Tile(char letter, int points, boolean blank) {
    public Tile {
        letter = Character.toUpperCase(letter);
        if (blank) {
            if (points != 0) {
                throw new IllegalArgumentException("Blank tiles must be worth zero points");
            }
            if (letter != '?' && (letter < 'A' || letter > 'Z')) {
                throw new IllegalArgumentException("A blank must be unassigned '?' or assigned A-Z");
            }
        } else {
            if (letter < 'A' || letter > 'Z') {
                throw new IllegalArgumentException("Tile letter must be A-Z");
            }
            int expected = pointsForLetter(letter);
            if (points != expected) {
                throw new IllegalArgumentException(
                        "Incorrect point value for " + letter + ": expected " + expected + ", got " + points);
            }
        }
    }

    public static Tile letterTile(char letter) {
        char normalized = Character.toUpperCase(letter);
        return new Tile(normalized, pointsForLetter(normalized), false);
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

    public static int pointsForLetter(char letter) {
        return switch (Character.toUpperCase(letter)) {
            case 'A', 'E', 'I', 'L', 'N', 'O', 'R', 'S', 'T', 'U' -> 1;
            case 'D', 'G' -> 2;
            case 'B', 'C', 'M', 'P' -> 3;
            case 'F', 'H', 'V', 'W', 'Y' -> 4;
            case 'K' -> 5;
            case 'J', 'X' -> 8;
            case 'Q', 'Z' -> 10;
            default -> throw new IllegalArgumentException("Tile letter must be A-Z");
        };
    }
}
