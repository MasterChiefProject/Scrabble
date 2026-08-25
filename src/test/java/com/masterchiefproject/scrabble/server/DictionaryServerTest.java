package com.masterchiefproject.scrabble.server;

import com.masterchiefproject.scrabble.dictionary.DictionaryManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictionaryServerTest {
    @TempDir
    Path tempDir;

    @Test
    void servesQueriesOnLoopbackAndClosesCleanly() throws Exception {
        Files.writeString(tempDir.resolve("words.txt"), "HELLO WORLD JAVA", StandardCharsets.UTF_8);
        DictionaryManager manager = new DictionaryManager(tempDir);

        DictionaryServer server = new DictionaryServer(0, new DictionaryProtocolHandler(manager));
        server.start();
        try {
            assertTrue(InetAddress.getLoopbackAddress().isLoopbackAddress());
            assertEquals("true", request(server.localPort(), "Q,words.txt,HELLO"));
            assertEquals("false", request(server.localPort(), "C,words.txt,PYTHON"));
            assertEquals("ERROR,Unknown action", request(server.localPort(), "X,words.txt,HELLO"));
        } finally {
            server.close();
        }
        assertFalse(server.isRunning());
    }

    @Test
    void rejectsDictionaryPathOutsideConfiguredRoot() throws Exception {
        Files.writeString(tempDir.resolve("inside.txt"), "HELLO", StandardCharsets.UTF_8);
        Path outside = Files.createTempFile("scrabble-outside-", ".txt");
        Files.writeString(outside, "SECRET", StandardCharsets.UTF_8);
        DictionaryManager manager = new DictionaryManager(tempDir);

        try (DictionaryServer server = new DictionaryServer(0, new DictionaryProtocolHandler(manager))) {
            server.start();
            String response = request(server.localPort(), "Q," + outside + ",SECRET");
            assertEquals("ERROR,Dictionary path is outside the configured root", response);
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void rejectsOversizedRequests() throws Exception {
        DictionaryManager manager = new DictionaryManager(tempDir);
        try (DictionaryServer server = new DictionaryServer(0, new DictionaryProtocolHandler(manager))) {
            server.start();
            String response = request(server.localPort(), "Q," + "A".repeat(DictionaryProtocolHandler.MAX_REQUEST_BYTES + 32));
            assertEquals("ERROR,request too large", response);
        }
    }

    private static String request(int port, String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out.println(request);
            return in.readLine();
        }
    }
}
