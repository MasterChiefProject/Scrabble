package com.masterchiefproject.scrabble.game;

/**
 * A placement requested from the current player's rack.
 * assignedLetter is used only when rackIndex references a blank tile.
 */
public record PlacementRequest(int rackIndex, int row, int col, Character assignedLetter) {
    public PlacementRequest(int rackIndex, int row, int col) {
        this(rackIndex, row, col, null);
    }
}
