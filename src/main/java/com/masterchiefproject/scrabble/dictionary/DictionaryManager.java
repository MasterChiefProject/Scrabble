package com.masterchiefproject.scrabble.dictionary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Lazily loads and reuses file dictionaries for the original Q/C server protocol. */
public final class DictionaryManager {
    private static final DictionaryManager INSTANCE = new DictionaryManager();
    private final Map<Path, Dictionary> dictionaries = new ConcurrentHashMap<>();

    private DictionaryManager() {
    }

    public static DictionaryManager get() {
        return INSTANCE;
    }

    public boolean query(String... args) throws IOException {
        return lookup(false, args);
    }

    public boolean challenge(String... args) throws IOException {
        return lookup(true, args);
    }

    private boolean lookup(boolean challenge, String... args) throws IOException {
        if (args == null || args.length < 2) {
            throw new IllegalArgumentException("Expected at least one dictionary file and one word");
        }
        String word = args[args.length - 1];
        for (int i = 0; i < args.length - 1; i++) {
            Path path = Path.of(args[i]).toAbsolutePath().normalize();
            Dictionary dictionary = getOrLoad(path);
            boolean found = challenge ? dictionary.challenge(word) : dictionary.query(word);
            if (found) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return dictionaries.size();
    }

    public void clear() {
        dictionaries.clear();
    }

    private Dictionary getOrLoad(Path path) throws IOException {
        Dictionary existing = dictionaries.get(path);
        if (existing != null) {
            return existing;
        }
        Dictionary loaded = new Dictionary(path);
        Dictionary raced = dictionaries.putIfAbsent(path, loaded);
        return raced != null ? raced : loaded;
    }
}
