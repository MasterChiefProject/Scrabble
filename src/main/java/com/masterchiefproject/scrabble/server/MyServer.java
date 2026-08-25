package com.masterchiefproject.scrabble.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Small concurrent TCP server with deterministic shutdown. */
public final class MyServer implements AutoCloseable {
    private final int port;
    private final ClientHandler handler;

    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private ExecutorService clients;

    public MyServer(int port, ClientHandler handler) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        this.port = port;
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        serverSocket = new ServerSocket(port);
        clients = Executors.newCachedThreadPool();
        running = true;
        acceptThread = new Thread(this::acceptLoop, "scrabble-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public boolean isRunning() {
        return running;
    }

    /** Useful when constructed with port 0 for tests. */
    public int localPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? port : socket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                clients.submit(() -> serve(socket));
            } catch (SocketException e) {
                if (running) {
                    System.err.println("Server socket error: " + e.getMessage());
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            handler.handleClient(socket.getInputStream(), socket.getOutputStream());
        } catch (IOException e) {
            if (running) {
                System.err.println("Client handling failed: " + e.getMessage());
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (!running && serverSocket == null) {
            return;
        }
        running = false;
        IOException closeFailure = null;

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                closeFailure = e;
            }
            serverSocket = null;
        }

        if (clients != null) {
            clients.shutdown();
            try {
                if (!clients.awaitTermination(2, TimeUnit.SECONDS)) {
                    clients.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                clients.shutdownNow();
            }
            clients = null;
        }

        try {
            handler.close();
        } catch (IOException e) {
            if (closeFailure == null) {
                closeFailure = e;
            }
        }

        if (acceptThread != null) {
            try {
                acceptThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            acceptThread = null;
        }

        if (closeFailure != null) {
            throw closeFailure;
        }
    }
}
