package net.serlith.sakurabans.core.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/** The editor site's blob routes, in memory, on a loopback port (protocol section 3). */
public final class FakeEditorSite implements AutoCloseable {

    /** What one request looked like. */
    public record Request(String method, String path, Map<String, List<String>> headers) {
    }

    private final HttpServer server;
    private final Map<String, byte[]> payloads = new ConcurrentHashMap<>();
    private final Map<String, Map.Entry<String, byte[]>> changes = new ConcurrentHashMap<>();
    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();
    private final Map<String, byte[]> results = new ConcurrentHashMap<>();
    private final Map<String, Integer> revisions = new ConcurrentHashMap<>();
    private volatile boolean refusingPuts;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger next = new AtomicInteger();
    private volatile @Nullable String token;
    private volatile int forcedStatus;

    public FakeEditorSite() {
        try {
            this.server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + this.server.getAddress().getPort();
    }

    public FakeEditorSite token(final @Nullable String token) {
        this.token = token;
        return this;
    }

    /** Answers every request with this status (0: behave). */
    public FakeEditorSite status(final int status) {
        this.forcedStatus = status;
        return this;
    }

    public List<Request> requests() {
        return List.copyOf(this.requests);
    }

    public @Nullable byte[] payload(final String id) {
        return this.payloads.get(id);
    }

    /** The current revision of a session's payload. */
    public int revision(final String id) {
        return this.revisions.getOrDefault(id, 0);
    }

    /** {@code PUT}s of payloads fail while true. */
    public FakeEditorSite refusingPuts(final boolean refusing) {
        this.refusingPuts = refusing;
        return this;
    }

    public @Nullable byte[] result(final String code) {
        return this.results.get(code);
    }

    public @Nullable String sessionToken(final String id) {
        return this.sessionTokens.get(id);
    }

    /** What the browser does on save; returns the code. */
    public String saveChanges(final String session, final byte[] blob) {
        final String code = "ABCDEFGH" + "%02d".formatted(this.next.incrementAndGet() % 100)
                .replace('0', 'J').replace('1', 'K');
        this.changes.put(code, Map.entry(session, blob));
        return code;
    }

    private void handle(final HttpExchange exchange) throws IOException {
        final String path = exchange.getRequestURI().getPath();
        this.requests.add(new Request(exchange.getRequestMethod(), path,
                Map.copyOf(exchange.getRequestHeaders())));
        final byte[] body = exchange.getRequestBody().readAllBytes();
        if (this.forcedStatus != 0) {
            this.reply(exchange, this.forcedStatus, "{\"error\":\"forced\",\"message\":\"x\"}");
            return;
        }
        if (exchange.getRequestMethod().equals("POST") && path.equals("/api/sessions")) {
            final String expected = this.token;
            if (expected != null && !("Bearer " + expected).equals(
                    exchange.getRequestHeaders().getFirst("Authorization"))) {
                this.reply(exchange, 401, "{\"error\":\"token\",\"message\":\"bad token\"}");
                return;
            }
            final byte[] random = new byte[16];
            random[0] = (byte) this.next.incrementAndGet();
            final String id = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            this.payloads.put(id, body);
            this.revisions.put(id, 1);
            final String sessionToken = "tok" + id;
            this.sessionTokens.put(id, sessionToken);
            this.reply(exchange, 201, "{\"id\":\"" + id + "\",\"token\":\"" + sessionToken
                    + "\"}");
        } else if (exchange.getRequestMethod().equals("PUT") && path.startsWith("/api/sessions/")) {
            final String id = path.substring("/api/sessions/".length());
            if (!this.payloads.containsKey(id) || this.refusingPuts) {
                this.reply(exchange, 404, "{\"error\":\"not_found\",\"message\":\"gone\"}");
                return;
            }
            if (!("Bearer " + this.sessionTokens.get(id)).equals(
                    exchange.getRequestHeaders().getFirst("Authorization"))) {
                this.reply(exchange, 401, "{\"error\":\"token\",\"message\":\"bad\"}");
                return;
            }
            this.payloads.put(id, body);
            this.reply(exchange, 200, "{\"revision\":" + this.revisions.merge(id, 1, Integer::sum)
                    + "}");
        } else if (exchange.getRequestMethod().equals("POST") && path.startsWith("/api/changes/")
                && path.endsWith("/result")) {
            final String code = path.substring("/api/changes/".length(),
                    path.length() - "/result".length());
            final Map.Entry<String, byte[]> stored = this.changes.get(code);
            if (stored == null) {
                this.reply(exchange, 404, "{\"error\":\"not_found\",\"message\":\"gone\"}");
                return;
            }
            if (!("Bearer " + this.sessionTokens.get(stored.getKey())).equals(
                    exchange.getRequestHeaders().getFirst("Authorization"))) {
                this.reply(exchange, 401, "{\"error\":\"token\",\"message\":\"bad\"}");
                return;
            }
            if (this.results.putIfAbsent(code, body) != null) {
                this.reply(exchange, 409, "{\"error\":\"exists\",\"message\":\"x\"}");
                return;
            }
            this.reply(exchange, 201, "{}");
        } else if (exchange.getRequestMethod().equals("GET")
                && path.startsWith("/api/changes/")) {
            final Map.Entry<String, byte[]> stored =
                    this.changes.get(path.substring("/api/changes/".length()));
            if (stored == null) {
                this.reply(exchange, 404, "{\"error\":\"not_found\",\"message\":\"gone\"}");
                return;
            }
            this.reply(exchange, 200, "{\"session\":\"" + stored.getKey() + "\",\"data\":\""
                    + Base64.getEncoder().encodeToString(stored.getValue()) + "\"}");
        } else {
            this.reply(exchange, 404, "{\"error\":\"not_found\",\"message\":\"no route\"}");
        }
    }

    private void reply(final HttpExchange exchange, final int status, final String json)
            throws IOException {
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        this.server.stop(0);
    }
}
