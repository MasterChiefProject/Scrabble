package com.masterchiefproject.scrabble.server;

import com.masterchiefproject.scrabble.dictionary.DictionaryManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** Implements the line protocol: Q,file1,...,word and C,file1,...,word. */
public final class DictionaryProtocolHandler implements ClientHandler {
    public static final int MAX_REQUEST_BYTES = 8_192;
    public static final int MAX_DICTIONARIES_PER_REQUEST = 16;

    private final DictionaryManager manager;

    public DictionaryProtocolHandler() {
        this(DictionaryManager.get());
    }

    public DictionaryProtocolHandler(DictionaryManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public void handleClient(InputStream input, OutputStream output) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        PrintWriter writer = new PrintWriter(output, true, StandardCharsets.UTF_8);

        String line;
        try {
            line = readLimitedLine(input, MAX_REQUEST_BYTES);
        } catch (IllegalArgumentException e) {
            writer.println("ERROR," + safeError(e.getMessage()));
            return;
        }

        if (line == null || line.isBlank()) {
            writer.println("ERROR,empty request");
            return;
        }

        String[] fields = Arrays.stream(line.split(",", -1))
                .map(String::trim)
                .toArray(String[]::new);
        if (fields.length < 3) {
            writer.println("ERROR,expected action,dictionary,word");
            return;
        }
        if (fields.length - 2 > MAX_DICTIONARIES_PER_REQUEST) {
            writer.println("ERROR,too many dictionaries");
            return;
        }

        String action = fields[0].toUpperCase(Locale.ROOT);
        String[] args = Arrays.copyOfRange(fields, 1, fields.length);
        try {
            boolean result = switch (action) {
                case "Q" -> manager.query(args);
                case "C" -> manager.challenge(args);
                default -> throw new IllegalArgumentException("Unknown action");
            };
            writer.println(result);
        } catch (IllegalArgumentException e) {
            writer.println("ERROR," + safeError(e.getMessage()));
        } catch (IOException e) {
            writer.println("ERROR,dictionary unavailable");
        }
    }

    private static String readLimitedLine(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 256));
        while (true) {
            int value = input.read();
            if (value == -1 || value == '\n') {
                if (buffer.size() == 0 && value == -1) {
                    return null;
                }
                break;
            }
            if (value == '\r') {
                continue;
            }
            if (buffer.size() >= maxBytes) {
                throw new IllegalArgumentException("request too large");
            }
            buffer.write(value);
        }

        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(buffer.toByteArray()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("request must be valid UTF-8", e);
        }
    }

    private static String safeError(String message) {
        if (message == null || message.isBlank()) {
            return "invalid request";
        }
        return message.replace(',', ';').replace('\r', ' ').replace('\n', ' ');
    }
}
