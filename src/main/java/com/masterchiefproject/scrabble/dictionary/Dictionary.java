package com.masterchiefproject.scrabble.dictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Exact word dictionary with positive/negative caches and a Bloom filter pre-check.
 * Bloom false positives are always verified against the exact set.
 */
public final class Dictionary implements WordValidator {
    private final Set<String> words;
    private final CacheManager positiveCache = new CacheManager(512, new LRU());
    private final CacheManager negativeCache = new CacheManager(256, new LFU());
    private final BloomFilter bloomFilter;

    public Dictionary(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            this.words = loadWords(input);
        }
        this.bloomFilter = buildBloom(words);
    }

    public Dictionary(InputStream input) throws IOException {
        this.words = loadWords(input);
        this.bloomFilter = buildBloom(words);
    }

    public static Dictionary fromResource(String resourcePath) throws IOException {
        InputStream input = Dictionary.class.getResourceAsStream(resourcePath);
        if (input == null) {
            throw new IOException("Dictionary resource not found: " + resourcePath);
        }
        try (input) {
            return new Dictionary(input);
        }
    }

    private static Set<String> loadWords(InputStream input) throws IOException {
        Set<String> result = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                for (String token : line.split("\\s+")) {
                    String word = normalize(token);
                    if (!word.isEmpty()) {
                        result.add(word);
                    }
                }
            }
        }
        return Set.copyOf(result);
    }

    private static BloomFilter buildBloom(Set<String> words) {
        int bits = Math.max(8192, words.size() * 12);
        BloomFilter filter = new BloomFilter(bits, "MD5", "SHA-256");
        words.forEach(filter::add);
        return filter;
    }

    @Override
    public boolean isValid(String word) {
        String normalized = normalize(word);
        if (normalized.isEmpty()) {
            return false;
        }
        if (positiveCache.query(normalized)) {
            return true;
        }
        if (negativeCache.query(normalized)) {
            return false;
        }
        if (!bloomFilter.mightContain(normalized)) {
            negativeCache.add(normalized);
            return false;
        }

        boolean exists = words.contains(normalized);
        if (exists) {
            positiveCache.add(normalized);
        } else {
            negativeCache.add(normalized);
        }
        return exists;
    }

    /** Exact lookup. Kept for compatibility with the original coursework terminology. */
    public boolean challenge(String word) {
        return isValid(word);
    }

    /** Exact cached lookup. Kept for compatibility with the original coursework terminology. */
    public boolean query(String word) {
        return isValid(word);
    }

    public int size() {
        return words.size();
    }

    private static String normalize(String word) {
        if (word == null) {
            return "";
        }
        String normalized = word.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z]+") ? normalized : "";
    }
}
