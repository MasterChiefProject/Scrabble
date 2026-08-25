package com.masterchiefproject.scrabble.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Mutable player state owned by {@link ScrabbleGame}. */
public final class Player {
    public static final int RACK_SIZE = 7;
    public static final int MAX_NAME_LENGTH = 40;

    private final String name;
    private final List<Tile> rack = new ArrayList<>(RACK_SIZE);
    private int score;

    public Player(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Player name is required");
        }
        String normalized = name.trim();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Player name cannot exceed " + MAX_NAME_LENGTH + " characters");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Player name cannot contain control characters");
        }
        this.name = normalized;
    }

    public String name() {
        return name;
    }

    public int score() {
        return score;
    }

    public List<Tile> rack() {
        return Collections.unmodifiableList(rack);
    }

    void addScore(int amount) {
        score += amount;
    }

    void addTiles(List<Tile> tiles) {
        if (tiles == null || tiles.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Tiles cannot be null");
        }
        if (rack.size() + tiles.size() > RACK_SIZE) {
            throw new IllegalStateException("A rack cannot contain more than " + RACK_SIZE + " tiles");
        }
        rack.addAll(tiles);
    }

    Tile tileAt(int index) {
        return rack.get(index);
    }

    Tile removeTileAt(int index) {
        return rack.remove(index);
    }

    void replaceTileAt(int index, Tile tile) {
        rack.set(index, java.util.Objects.requireNonNull(tile, "tile"));
    }

    int rackPoints() {
        return rack.stream().mapToInt(Tile::points).sum();
    }

    boolean rackEmpty() {
        return rack.isEmpty();
    }
}
