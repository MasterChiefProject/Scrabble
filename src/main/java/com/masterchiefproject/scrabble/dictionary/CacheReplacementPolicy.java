package com.masterchiefproject.scrabble.dictionary;

public interface CacheReplacementPolicy {
    void record(String word);
    String evict();
    void remove(String word);
}
