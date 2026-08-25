package com.masterchiefproject.scrabble.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Standard English 100-tile distribution, including two blank tiles. */
public final class TileBag {
    public static final int INITIAL_SIZE = 100;

    private final List<Tile> tiles = new ArrayList<>(INITIAL_SIZE);
    private final Random random;

    public TileBag() {
        this(new Random());
    }

    public TileBag(long seed) {
        this(new Random(seed));
    }

    TileBag(Random random) {
        this.random = Objects.requireNonNull(random, "random");
        reset();
    }

    /** Test-only deterministic bag content. The last list element is drawn first. */
    TileBag(List<Tile> initialTiles, long seed) {
        this.random = new Random(seed);
        if (initialTiles == null || initialTiles.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Initial tiles cannot be null");
        }
        tiles.addAll(initialTiles);
    }

    private void reset() {
        tiles.clear();
        add('A', 9); add('B', 2); add('C', 2); add('D', 4);
        add('E', 12); add('F', 2); add('G', 3); add('H', 2);
        add('I', 9); add('J', 1); add('K', 1); add('L', 4);
        add('M', 2); add('N', 6); add('O', 8); add('P', 2);
        add('Q', 1); add('R', 6); add('S', 4); add('T', 6);
        add('U', 4); add('V', 2); add('W', 2); add('X', 1);
        add('Y', 2); add('Z', 1);
        tiles.add(Tile.blankTile());
        tiles.add(Tile.blankTile());
        Collections.shuffle(tiles, random);
    }

    private void add(char letter, int count) {
        for (int i = 0; i < count; i++) {
            tiles.add(Tile.letterTile(letter));
        }
    }

    public int remaining() {
        return tiles.size();
    }

    public boolean isEmpty() {
        return tiles.isEmpty();
    }

    public Tile draw() {
        if (tiles.isEmpty()) {
            return null;
        }
        return tiles.remove(tiles.size() - 1);
    }

    public List<Tile> draw(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        List<Tile> result = new ArrayList<>(Math.min(count, tiles.size()));
        for (int i = 0; i < count && !tiles.isEmpty(); i++) {
            result.add(draw());
        }
        return result;
    }

    /**
     * Draws replacements first, then returns the exchanged tiles to the bag and reshuffles.
     * This prevents a player from immediately drawing back a tile from the same exchange.
     * Callers should enforce the game rule that exchanges require at least seven tiles in the bag.
     */
    public List<Tile> exchange(List<Tile> returnedTiles) {
        if (returnedTiles == null || returnedTiles.isEmpty()) {
            throw new IllegalArgumentException("At least one tile is required for exchange");
        }
        if (tiles.size() < returnedTiles.size()) {
            throw new IllegalStateException("Not enough tiles remain to complete the exchange");
        }

        List<Tile> replacements = draw(returnedTiles.size());
        for (Tile tile : returnedTiles) {
            if (tile == null) {
                throw new IllegalArgumentException("Returned tiles cannot contain null");
            }
            tiles.add(tile.blank() ? Tile.blankTile() : tile);
        }
        Collections.shuffle(tiles, random);
        return replacements;
    }
}
