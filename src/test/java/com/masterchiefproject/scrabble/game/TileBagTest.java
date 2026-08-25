package com.masterchiefproject.scrabble.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TileBagTest {
    @Test
    void usesStandardHundredTileDistributionIncludingBlanks() {
        TileBag bag = new TileBag(1234L);
        List<Tile> tiles = bag.draw(100);

        assertEquals(100, tiles.size());
        assertEquals(0, bag.remaining());
        assertEquals(9, tiles.stream().filter(t -> t.letter() == 'A').count());
        assertEquals(12, tiles.stream().filter(t -> t.letter() == 'E').count());
        assertEquals(2, tiles.stream().filter(Tile::blank).count());
    }
}
