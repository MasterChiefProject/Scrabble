package com.masterchiefproject.scrabble.server;

import com.masterchiefproject.scrabble.dictionary.DictionaryManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/** Command-line launcher for the local dictionary protocol server. */
public final class DictionaryServerMain {
    private DictionaryServerMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length >= 1 ? Integer.parseInt(args[0]) : 6_000;
        Path dictionaryRoot = args.length >= 2 ? Path.of(args[1]) : Path.of("src/main/resources/dictionary");
        if (!Files.isDirectory(dictionaryRoot)) {
            throw new IllegalArgumentException("Dictionary root is not a directory: " + dictionaryRoot.toAbsolutePath());
        }

        DictionaryManager manager = new DictionaryManager(dictionaryRoot);
        DictionaryServer server = new DictionaryServer(port, new DictionaryProtocolHandler(manager));
        CountDownLatch shutdown = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
            } catch (Exception ignored) {
                // JVM is already shutting down.
            } finally {
                shutdown.countDown();
            }
        }, "scrabble-server-shutdown"));

        server.start();
        System.out.printf("Dictionary server listening on 127.0.0.1:%d, root=%s%n",
                server.localPort(), dictionaryRoot.toAbsolutePath().normalize());
        shutdown.await();
    }
}
