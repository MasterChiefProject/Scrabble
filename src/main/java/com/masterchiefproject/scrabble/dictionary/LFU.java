package com.masterchiefproject.scrabble.dictionary;

import java.util.HashMap;
import java.util.Map;

/** Least-frequently-used replacement policy with deterministic age tie-breaking. */
public final class LFU implements CacheReplacementPolicy {
    private record Entry(int frequency, long order) {}

    private final Map<String, Entry> entries = new HashMap<>();
    private long sequence;

    @Override
    public void record(String word) {
        Entry current = entries.get(word);
        if (current == null) {
            entries.put(word, new Entry(1, sequence++));
        } else {
            entries.put(word, new Entry(current.frequency() + 1, current.order()));
        }
    }

    @Override
    public String evict() {
        String candidate = null;
        Entry candidateEntry = null;
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            if (candidateEntry == null
                    || item.getValue().frequency() < candidateEntry.frequency()
                    || (item.getValue().frequency() == candidateEntry.frequency()
                        && item.getValue().order() < candidateEntry.order())) {
                candidate = item.getKey();
                candidateEntry = item.getValue();
            }
        }
        if (candidate != null) {
            entries.remove(candidate);
        }
        return candidate;
    }

    @Override
    public void remove(String word) {
        entries.remove(word);
    }
}
