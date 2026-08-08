package com.minedoom.video;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/** Tiny in-process HTTP server for serving resource packs when uploads are blocked. */
public final class LocalPackServer {

    private static LocalPackServer instance;

    private final HttpServer server;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    private LocalPackServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            byte[] data = files.get(path);
            if (data == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public static synchronized LocalPackServer ensure(JavaPlugin plugin, int port) throws IOException {
        if (instance == null) {
            instance = new LocalPackServer(port);
            plugin.getLogger().info("Resource-pack HTTP server on port " + port);
        }
        return instance;
    }

    public void offer(String name, byte[] data) {
        files.put(name, data);
    }

    public void stop() {
        server.stop(0);
    }
}
