package com.juniverse.gatewaydemo.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copia simulada de un microservicio para probar el gateway: registra lo que recibe y responde según la ruta.
 * <ul>
 *   <li>{@code /products/flaky} responde 503 si la copia está configurada como defectuosa;</li>
 *   <li>{@code /products/slow} tarda 3 segundos;</li>
 *   <li>cualquier otra ruta responde 200 (GET) o 201 (POST) con un JSON que dice qué copia respondió.</li>
 * </ul>
 */
final class StubService implements AutoCloseable {

    /** Petición recibida por la copia. */
    record Received(String method, String path, Map<String, String> headers) {
    }

    final String name;
    final boolean faulty;
    final AtomicInteger hits = new AtomicInteger();
    final List<Received> received = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    StubService(String name, boolean faulty) throws IOException {
        this.name = name;
        this.faulty = faulty;
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        // Con varios hilos: si no, la ruta lenta (3 s) bloquearía las demás peticiones a esta misma copia y se confundiría con un fallo del gateway
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            try (InputStream body = exchange.getRequestBody()) {
                body.readAllBytes();
            }
            Map<String, String> headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
            String path = exchange.getRequestURI().getPath();
            received.add(new Received(exchange.getRequestMethod(), path, headers));
            if (path.equals("/products/slow")) {
                try {
                    Thread.sleep(3_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            int status = path.equals("/products/flaky") && faulty ? 503 : exchange.getRequestMethod().equals("POST") ? 201 : 200;
            byte[] bytes = ("{\"instance\":\"" + name + "\",\"path\":\"" + path + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("X-Served-By", name);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    String uri() {
        return "http://localhost:" + port();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
