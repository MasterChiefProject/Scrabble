package com.masterchiefproject.scrabble.dictionary;

@FunctionalInterface
public interface WordValidator {
    boolean isValid(String word);

    static WordValidator acceptAll() {
        return word -> true;
    }
}
