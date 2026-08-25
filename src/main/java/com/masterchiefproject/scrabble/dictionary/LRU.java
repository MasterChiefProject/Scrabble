package com.masterchiefproject.scrabble.dictionary;

import java.util.LinkedHashSet;

/** Least-recently-used replacement policy. Reads refresh recency. */
public final class LRU implements CacheReplacementPolicy {
    private final LinkedHashSet<String> order = new LinkedHashSet<>();

    @Override
    public void record(String word) {
        order.remove(word);
        order.add(word);
    }

    @Override
    public String evict() {
        if (order.isEmpty()) {
            return null;
        }
        String oldest = order.iterator().next();
        order.remove(oldest);
        return oldest;
    }

    @Override
    public void remove(String word) {
        order.remove(word);
    }
}
