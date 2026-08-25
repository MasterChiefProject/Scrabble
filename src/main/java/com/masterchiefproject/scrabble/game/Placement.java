package com.masterchiefproject.scrabble.game;

import java.util.Objects;

public record Placement(Position position, Tile tile) {
    public Placement {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(tile, "tile");
        if (tile.blank() && tile.letter() == '?') {
            throw new IllegalArgumentException("A blank tile must be assigned a letter before placement");
        }
    }
}
