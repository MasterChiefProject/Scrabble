package com.masterchiefproject.scrabble.dictionary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazily loads and reuses file dictionaries for the Q/C server protocol.
 * File access is restricted to explicitly configured real root directories.
 */
public final class DictionaryManager {
    private final Map<Path, Dictionary> dictionaries = new ConcurrentHashMap<>();
    private final List<Path> allowedRoots;

    public DictionaryManager(Path... allowedRoots) {
        if (allowedRoots == null || allowedRoots.length == 0) {
            throw new IllegalArgumentException("At least one dictionary root is required");
        }
        this.allowedRoots = Arrays.stream(allowedRoots)
                .map(Objects::requireNonNull)
                .map(DictionaryManager::requireRealDirectory)
                .distinct()
                .toList();
    }

    /** Convenience manager for the repository's bundled development dictionary directory. */
    public static DictionaryManager get() {
        return DefaultHolder.INSTANCE;
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
            Path path = resolveAllowedPath(args[i]);
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

    private Path resolveAllowedPath(String value) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Dictionary path is required");
        }

        Path raw = Path.of(value.trim());
        Path candidate = raw.isAbsolute()
                ? raw.toAbsolutePath().normalize()
                : allowedRoots.get(0).resolve(raw).normalize();

        if (!Files.isRegularFile(candidate)) {
            throw new IOException("Dictionary file does not exist or is not a regular file");
        }

        Path realPath = candidate.toRealPath();
        boolean allowed = allowedRoots.stream().anyMatch(realPath::startsWith);
        if (!allowed) {
            throw new IllegalArgumentException("Dictionary path is outside the configured root");
        }
        return realPath;
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

    private static Path requireRealDirectory(Path root) {
        try {
            Path real = root.toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(real)) {
                throw new IllegalArgumentException("Dictionary root is not a directory: " + root);
            }
            return real;
        } catch (IOException e) {
            throw new IllegalArgumentException("Dictionary root does not exist or cannot be resolved: " + root, e);
        }
    }

    private static final class DefaultHolder {
        private static final DictionaryManager INSTANCE =
                new DictionaryManager(Path.of("src/main/resources/dictionary"));
    }
}
