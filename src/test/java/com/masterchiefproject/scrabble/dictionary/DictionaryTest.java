package com.masterchiefproject.scrabble.dictionary;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DictionaryTest {
    @Test
    void performsExactCaseInsensitiveValidation() throws Exception {
        String words = "HELLO\nWORLD\nJAVA\n";
        Dictionary dictionary = new Dictionary(new ByteArrayInputStream(words.getBytes(StandardCharsets.UTF_8)));

        assertTrue(dictionary.isValid("hello"));
        assertTrue(dictionary.query("WORLD"));
        assertFalse(dictionary.isValid("WORLDD"));
        assertFalse(dictionary.isValid("hello!"));
        assertEquals(3, dictionary.size());
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
}
