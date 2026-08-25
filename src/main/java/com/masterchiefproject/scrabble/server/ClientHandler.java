package com.masterchiefproject.scrabble.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@FunctionalInterface
public interface ClientHandler {
    void handleClient(InputStream input, OutputStream output) throws IOException;

    default void close() throws IOException {
    }
}
