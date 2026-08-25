package com.masterchiefproject.scrabble.server;

import com.masterchiefproject.scrabble.dictionary.DictionaryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MyServerTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearDictionaries() {
        DictionaryManager.get().clear();
    }

    @Test
    void servesQueryAndClosesCleanly() throws Exception {
        Path words = tempDir.resolve("words.txt");
        Files.writeString(words, "HELLO WORLD JAVA");

        MyServer server = new MyServer(0, new BookScrabbleHandler());
        server.start();
        try {
            assertEquals("true", request(server.localPort(), "Q," + words + ",HELLO"));
            assertEquals("false", request(server.localPort(), "C," + words + ",PYTHON"));
        } finally {
            server.close();
        }
        assertFalse(server.isRunning());
    }

    private static String request(int port, String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println(request);
            return in.readLine();
        }
    }
}
