package com.masterchiefproject.scrabble.dictionary;

import java.util.HashSet;
import java.util.Set;

/** Fixed-capacity word cache backed by a configurable replacement policy. */
public final class CacheManager {
    private final int capacity;
    private final CacheReplacementPolicy policy;
    private final Set<String> cache = new HashSet<>();

    public CacheManager(int capacity, CacheReplacementPolicy policy) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.policy = policy;
    }

    public synchronized boolean query(String word) {
        if (!cache.contains(word)) {
            return false;
        }
        policy.record(word);
        return true;
    }

    public synchronized void add(String word) {
        if (cache.contains(word)) {
            policy.record(word);
            return;
        }
        if (cache.size() >= capacity) {
            String victim = policy.evict();
            if (victim != null) {
                cache.remove(victim);
            }
        }
        cache.add(word);
        policy.record(word);
    }

    public synchronized void remove(String word) {
        cache.remove(word);
        policy.remove(word);
    }

    public synchronized int size() {
        return cache.size();
    }
}
