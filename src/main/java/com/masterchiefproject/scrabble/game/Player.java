package com.masterchiefproject.scrabble.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Player {
    public static final int RACK_SIZE = 7;

    private final String name;
    private final List<Tile> rack = new ArrayList<>(RACK_SIZE);
    private int score;

    public Player(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Player name is required");
        }
        this.name = name.trim();
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
        rack.addAll(tiles);
    }

    Tile tileAt(int index) {
        return rack.get(index);
    }

    Tile removeTileAt(int index) {
        return rack.remove(index);
    }

    void replaceTileAt(int index, Tile tile) {
        rack.set(index, tile);
    }

    int rackPoints() {
        return rack.stream().mapToInt(Tile::points).sum();
    }

    boolean rackEmpty() {
        return rack.isEmpty();
    }
}
