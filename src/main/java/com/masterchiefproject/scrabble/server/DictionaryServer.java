package com.masterchiefproject.scrabble.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Small bounded TCP server with deterministic shutdown and local-only defaults. */
public final class DictionaryServer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(DictionaryServer.class.getName());
    private static final int DEFAULT_MAX_CLIENTS = 16;
    private static final int DEFAULT_QUEUE_CAPACITY = 64;
    private static final int DEFAULT_SOCKET_TIMEOUT_MS = 5_000;

    private final InetAddress bindAddress;
    private final int port;
    private final ClientHandler handler;
    private final int maxClients;
    private final int socketTimeoutMillis;

    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private ThreadPoolExecutor clients;

    public DictionaryServer(int port, ClientHandler handler) {
        this(InetAddress.getLoopbackAddress(), port, handler, DEFAULT_MAX_CLIENTS, DEFAULT_SOCKET_TIMEOUT_MS);
    }

    public DictionaryServer(
            InetAddress bindAddress,
            int port,
            ClientHandler handler,
            int maxClients,
            int socketTimeoutMillis) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        if (maxClients < 1) {
            throw new IllegalArgumentException("maxClients must be positive");
        }
        if (socketTimeoutMillis < 1) {
            throw new IllegalArgumentException("socketTimeoutMillis must be positive");
        }
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        this.port = port;
        this.handler = Objects.requireNonNull(handler, "handler");
        this.maxClients = maxClients;
        this.socketTimeoutMillis = socketTimeoutMillis;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(bindAddress, port), DEFAULT_QUEUE_CAPACITY);
        serverSocket = socket;

        clients = new ThreadPoolExecutor(
                maxClients,
                maxClients,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(DEFAULT_QUEUE_CAPACITY),
                daemonThreadFactory("scrabble-server-client"),
                new ThreadPoolExecutor.AbortPolicy());

        running = true;
        acceptThread = new Thread(this::acceptLoop, "scrabble-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public boolean isRunning() {
        return running;
    }

    public int localPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? port : socket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(socketTimeoutMillis);
                try {
                    clients.execute(() -> serve(socket));
                } catch (RejectedExecutionException e) {
                    closeQuietly(socket);
                    LOGGER.warning("Client rejected because the server is at capacity");
                }
            } catch (SocketException e) {
                if (running) {
                    LOGGER.log(Level.WARNING, "Server socket error", e);
                }
            } catch (IOException e) {
                if (running) {
                    LOGGER.log(Level.WARNING, "Accept failed", e);
                }
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            handler.handleClient(socket.getInputStream(), socket.getOutputStream());
        } catch (IOException e) {
            if (running) {
                LOGGER.log(Level.FINE, "Client handling failed", e);
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
                    clients.awaitTermination(1, TimeUnit.SECONDS);
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
                acceptThread.join(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            acceptThread = null;
        }

        if (closeFailure != null) {
            throw closeFailure;
        }
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + '-' + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Best effort while rejecting excess clients.
        }
    }
}
