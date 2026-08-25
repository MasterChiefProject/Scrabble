package com.masterchiefproject.scrabble.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TileBagTest {
    @Test
    void standardBagContainsOneHundredTilesAndTwoBlanks() {
        TileBag bag = new TileBag(1L);
        List<Tile> all = bag.draw(TileBag.INITIAL_SIZE);

        assertEquals(100, all.size());
        assertEquals(2, all.stream().filter(Tile::blank).count());
        assertEquals(9, all.stream().filter(tile -> !tile.blank() && tile.letter() == 'A').count());
        assertEquals(0, bag.remaining());
    }

    @Test
    void exchangeDrawsReplacementBeforeReturningOldTile() {
        TileBag bag = new TileBag(List.of(
                Tile.letterTile('H'), Tile.letterTile('I'), Tile.letterTile('J'),
                Tile.letterTile('K'), Tile.letterTile('L'), Tile.letterTile('M'), Tile.letterTile('N')), 3L);

        List<Tile> replacement = bag.exchange(List.of(Tile.letterTile('A')));

        assertEquals('N', replacement.get(0).letter());
        assertEquals(7, bag.remaining());
        assertFalse(replacement.stream().anyMatch(tile -> tile.letter() == 'A'));
    }
}
