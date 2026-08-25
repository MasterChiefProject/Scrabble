package com.masterchiefproject.scrabble.dictionary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;

/** Small deterministic Bloom filter used as a fast negative pre-check. */
public final class BloomFilter {
    private final BitSet bits;
    private final int bitCount;
    private final MessageDigest[] digests;

    public BloomFilter(int bitCount, String... algorithms) {
        if (bitCount < 64) {
            throw new IllegalArgumentException("bitCount must be at least 64");
        }
        if (algorithms == null || algorithms.length == 0) {
            throw new IllegalArgumentException("At least one digest algorithm is required");
        }
        this.bitCount = bitCount;
        this.bits = new BitSet(bitCount);
        this.digests = new MessageDigest[algorithms.length];
        try {
            for (int i = 0; i < algorithms.length; i++) {
                digests[i] = MessageDigest.getInstance(algorithms[i]);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unsupported digest algorithm", e);
        }
    }

    public synchronized void add(String word) {
        byte[] input = word.getBytes(StandardCharsets.UTF_8);
        for (MessageDigest digest : digests) {
            byte[] hash = digest.digest(input);
            bits.set(index(hash, 0));
            if (hash.length >= 8) {
                bits.set(index(hash, 4));
            }
        }
    }

    public synchronized boolean mightContain(String word) {
        byte[] input = word.getBytes(StandardCharsets.UTF_8);
        for (MessageDigest digest : digests) {
            byte[] hash = digest.digest(input);
            if (!bits.get(index(hash, 0))) {
                return false;
            }
            if (hash.length >= 8 && !bits.get(index(hash, 4))) {
                return false;
            }
        }
        return true;
    }

    private int index(byte[] hash, int offset) {
        int value = ((hash[offset] & 0xff) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        return Math.floorMod(value, bitCount);
    }
}
