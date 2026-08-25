package com.masterchiefproject.scrabble.dictionary;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictionaryTest {
    @Test
    void performsExactCaseInsensitiveValidationAndParsesWhitespace() throws Exception {
        String words = "HELLO WORLD\nJAVA\tMAVEN\n";
        Dictionary dictionary = new Dictionary(new ByteArrayInputStream(words.getBytes(StandardCharsets.UTF_8)));

        assertTrue(dictionary.isValid("hello"));
        assertTrue(dictionary.query("WORLD"));
        assertTrue(dictionary.challenge("maven"));
        assertFalse(dictionary.isValid("WORLDD"));
        assertFalse(dictionary.isValid("hello!"));
        assertEquals(4, dictionary.size());
    }

    @Test
    void lruReadsRefreshRecency() {
        CacheManager cache = new CacheManager(2, new LRU());
        cache.add("A");
        cache.add("B");
        assertTrue(cache.query("A"));
        cache.add("C");

        assertTrue(cache.query("A"));
        assertFalse(cache.query("B"));
        assertTrue(cache.query("C"));
    }

    @Test
    void lfuUsesDeterministicOldestTieBreak() {
        CacheManager cache = new CacheManager(2, new LFU());
        cache.add("A");
        cache.add("B");
        cache.add("C");

        assertFalse(cache.query("A"));
        assertTrue(cache.query("B"));
        assertTrue(cache.query("C"));
    }

    @Test
    void cacheRejectsInvalidConstructionAndNullKeys() {
        assertThrows(IllegalArgumentException.class, () -> new CacheManager(0, new LRU()));
        assertThrows(NullPointerException.class, () -> new CacheManager(1, null));
        CacheManager cache = new CacheManager(1, new LRU());
        assertThrows(NullPointerException.class, () -> cache.add(null));
    }
}
