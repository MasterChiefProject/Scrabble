package com.masterchiefproject.scrabble.server;

import com.masterchiefproject.scrabble.dictionary.DictionaryManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Implements the original line protocol: Q,file1,...,word and C,file1,...,word. */
public final class BookScrabbleHandler implements ClientHandler {
    private final DictionaryManager manager;

    public BookScrabbleHandler() {
        this(DictionaryManager.get());
    }

    public BookScrabbleHandler(DictionaryManager manager) {
        this.manager = manager;
    }

    @Override
    public void handleClient(InputStream input, OutputStream output) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(output, true, StandardCharsets.UTF_8);

        String line = reader.readLine();
        if (line == null || line.isBlank()) {
            writer.println("ERROR,empty request");
            return;
        }

        String[] fields = Arrays.stream(line.split(","))
                .map(String::trim)
                .toArray(String[]::new);
        if (fields.length < 3) {
            writer.println("ERROR,expected action,dictionary,word");
            return;
        }

        String action = fields[0].toUpperCase();
        String[] args = Arrays.copyOfRange(fields, 1, fields.length);
        try {
            boolean result = switch (action) {
                case "Q" -> manager.query(args);
                case "C" -> manager.challenge(args);
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            };
            writer.println(result);
        } catch (IllegalArgumentException e) {
            writer.println("ERROR," + e.getMessage());
        }
    }
}
