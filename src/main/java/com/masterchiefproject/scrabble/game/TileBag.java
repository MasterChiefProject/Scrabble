package com.masterchiefproject.scrabble.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        this.random = random;
        reset();
    }

    private void reset() {
        tiles.clear();
        add('A', 1, 9); add('B', 3, 2); add('C', 3, 2); add('D', 2, 4);
        add('E', 1, 12); add('F', 4, 2); add('G', 2, 3); add('H', 4, 2);
        add('I', 1, 9); add('J', 8, 1); add('K', 5, 1); add('L', 1, 4);
        add('M', 3, 2); add('N', 1, 6); add('O', 1, 8); add('P', 3, 2);
        add('Q', 10, 1); add('R', 1, 6); add('S', 1, 4); add('T', 1, 6);
        add('U', 1, 4); add('V', 4, 2); add('W', 4, 2); add('X', 8, 1);
        add('Y', 4, 2); add('Z', 10, 1);
        tiles.add(Tile.blankTile());
        tiles.add(Tile.blankTile());
        Collections.shuffle(tiles, random);
    }

    private void add(char letter, int points, int count) {
        for (int i = 0; i < count; i++) {
            tiles.add(new Tile(letter, points, false));
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
     * Returns tiles to the bag, reshuffles, and draws the same number.
     * Callers should enforce the game rule that exchanges require at least seven tiles in the bag.
     */
    public List<Tile> exchange(List<Tile> returnedTiles) {
        if (returnedTiles == null || returnedTiles.isEmpty()) {
            throw new IllegalArgumentException("At least one tile is required for exchange");
        }
        for (Tile tile : returnedTiles) {
            if (tile.blank()) {
                tiles.add(Tile.blankTile());
            } else {
                tiles.add(tile);
            }
        }
        Collections.shuffle(tiles, random);
        return draw(returnedTiles.size());
    }
}
