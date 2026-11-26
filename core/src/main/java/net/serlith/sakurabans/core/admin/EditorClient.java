package net.serlith.sakurabans.core.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.util.BuildConstants;
import org.jspecify.annotations.Nullable;

/** The editor site's blob routes (protocol section 3). */
public final class EditorClient {

    /** The site's limit per blob, after sealing. */
    public static final int MAX_BLOB = 1024 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern SESSION_ID = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private static final Pattern CODE = Pattern.compile("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{10}");

    public enum Problem {
        NOT_CONFIGURED,
        UNREACHABLE,
        UNAUTHORIZED,
        TOO_LARGE,
        RATE_LIMITED,
        NOT_FOUND,
        UNEXPECTED
    }

    public sealed interface Response<T> {

        record Ok<T>(T value) implements Response<T> {
        }

        record Failed<T>(Problem problem) implements Response<T> {
        }
    }

    public record Download(String session, byte[] blob) {
    }

    /** {@code token} is the session's secret toward the site: never log or show it. */
    public record Uploaded(String id, String token) {

        @Override
        public String toString() {
            return "Uploaded[id=" + this.id + "]";
        }
    }

    private final ConfigService config;
    private final Executor worker;
    private final ComponentLogger logger;
    private final AtomicReference<@Nullable HttpClient> client = new AtomicReference<>();

    public EditorClient(final ConfigService config, final Executor worker,
            final ComponentLogger logger) {
        this.config = config;
        this.worker = worker;
        this.logger = logger;
    }

    public void stop() {
        final HttpClient current = this.client.getAndSet(null);
        if (current != null) {
            current.close();
        }
    }

    public static boolean validCode(final String code) {
        return CODE.matcher(code.toUpperCase(Locale.ROOT)).matches();
    }

    /** Uploads a sealed payload; the value is the session the site made. */
    public CompletableFuture<Response<Uploaded>> upload(final byte[] blob) {
        final Settings.Web web = this.config.settings().web();
        if (web.url() == null) {
            return failed(Problem.NOT_CONFIGURED);
        }
        if (blob.length > MAX_BLOB) {
            return failed(Problem.TOO_LARGE);
        }
        final HttpRequest.Builder request = this.request(web.url(), "/api/sessions")
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(blob));
        if (web.token() != null) {
            request.header("Authorization", "Bearer " + web.token());
        }
        return this.send(request.build(), 201, true, json(body -> {
            final String id = string(body, "id");
            final String token = string(body, "token");
            return id != null && SESSION_ID.matcher(id).matches() && token != null
                    && TOKEN.matcher(token).matches() ? new Uploaded(id, token) : null;
        }));
    }

    /** Downloads a sealed change set by the code the site showed. */
    public CompletableFuture<Response<Download>> changes(final String code) {
        final Settings.Web web = this.config.settings().web();
        if (web.url() == null) {
            return failed(Problem.NOT_CONFIGURED);
        }
        if (!validCode(code)) {
            return failed(Problem.NOT_FOUND);
        }
        final HttpRequest request = this.request(web.url(),
                "/api/changes/" + code.toUpperCase(Locale.ROOT)).GET().build();
        return this.send(request, 200, true, json(body -> {
            final String session = string(body, "session");
            final String data = string(body, "data");
            if (session == null || data == null || !SESSION_ID.matcher(session).matches()) {
                return null;
            }
            try {
                return new Download(session, Base64.getDecoder().decode(data));
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }));
    }

    /** Posts a sealed result for a change set; failures are only for the caller to log. */
    public CompletableFuture<Response<Boolean>> postResult(final String code, final String token,
            final byte[] blob) {
        final Settings.Web web = this.config.settings().web();
        if (web.url() == null) {
            return failed(Problem.NOT_CONFIGURED);
        }
        if (!validCode(code) || blob.length > MAX_BLOB) {
            return failed(validCode(code) ? Problem.TOO_LARGE : Problem.NOT_FOUND);
        }
        final HttpRequest request = this.request(web.url(), "/api/changes/"
                        + code.toUpperCase(Locale.ROOT) + "/result")
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(blob)).build();
        return this.send(request, 201, false, body -> true);
    }

    /** Replaces a session's payload with a fresh one; the value is its revision (section 10). */
    public CompletableFuture<Response<Long>> replacePayload(final String session,
            final String token, final byte[] blob) {
        final Settings.Web web = this.config.settings().web();
        if (web.url() == null) {
            return failed(Problem.NOT_CONFIGURED);
        }
        if (!SESSION_ID.matcher(session).matches()) {
            return failed(Problem.NOT_FOUND);
        }
        if (blob.length > MAX_BLOB) {
            return failed(Problem.TOO_LARGE);
        }
        final HttpRequest request = this.request(web.url(), "/api/sessions/" + session)
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(blob)).build();
        return this.send(request, 200, false, json(body -> {
            final JsonElement revision = body.get("revision");
            if (revision == null || !revision.isJsonPrimitive()
                    || !revision.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            final long value = revision.getAsLong();
            return value >= 1 ? value : null;
        }));
    }

    /** Opens the plugin's socket of a session (protocol section 9). */
    public CompletableFuture<EditorSocket> connect(final String session, final String token,
            final EditorSocket.Listener listener) {
        final Settings.Web web = this.config.settings().web();
        if (web.url() == null || !SESSION_ID.matcher(session).matches()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "the editor isn't set up"));
        }
        final String base = web.url().toString().replaceFirst("^http", "ws");
        final HttpClient http;
        try {
            http = this.http();
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return http.newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", "SakuraBans/" + BuildConstants.VERSION)
                .buildAsync(URI.create(base + "/api/sessions/" + session + "/socket?role=plugin"),
                        new Frames(listener))
                .<EditorSocket>thenApplyAsync(Socket::new, this.worker)
                .exceptionallyComposeAsync(failure -> CompletableFuture.failedFuture(
                        refusal(failure).<Throwable>map(refused -> refused).orElse(failure)),
                        this.worker);
    }

    /** The status of a refused upgrade, which the JDK reports as a handshake failure. */
    static Optional<EditorSocket.Refused> refusal(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof WebSocketHandshakeException handshake
                    && handshake.getResponse() != null) {
                return Optional.of(new EditorSocket.Refused(
                        handshake.getResponse().statusCode()));
            }
        }
        return Optional.empty();
    }

    /** Joins text frames that arrive in parts, and keeps asking for the next one. */
    private static final class Frames implements WebSocket.Listener {

        private final EditorSocket.Listener listener;
        private final StringBuilder partial = new StringBuilder();

        private Frames(final EditorSocket.Listener listener) {
            this.listener = listener;
        }

        @Override
        public void onOpen(final WebSocket socket) {
            socket.request(1);
        }

        @Override
        public @Nullable CompletionStage<?> onText(final WebSocket socket,
                final CharSequence data, final boolean last) {
            this.partial.append(data);
            if (last) {
                final String text = this.partial.toString();
                this.partial.setLength(0);
                this.listener.text(text);
            }
            socket.request(1);
            return null;
        }

        @Override
        public @Nullable CompletionStage<?> onBinary(final WebSocket socket,
                final ByteBuffer data, final boolean last) {
            socket.request(1);
            return null;
        }

        @Override
        public @Nullable CompletionStage<?> onClose(final WebSocket socket, final int code,
                final String reason) {
            this.listener.closed(code, reason);
            return null;
        }

        @Override
        public void onError(final WebSocket socket, final Throwable error) {
            this.listener.closed(EditorSocket.ABNORMAL, error.toString());
        }
    }

    /** Sends one frame at a time, as {@link WebSocket} requires. */
    private static final class Socket implements EditorSocket {

        private final WebSocket socket;
        private CompletableFuture<?> sending = CompletableFuture.completedFuture(null);

        private Socket(final WebSocket socket) {
            this.socket = socket;
        }

        @Override
        public synchronized void send(final String text) {
            this.sending = this.sending.handle((ignored, failure) -> null)
                    .thenCompose(ignored -> this.socket.sendText(text, true));
        }

        @Override
        public synchronized void close(final int code, final String reason) {
            this.sending = this.sending.handle((ignored, failure) -> null)
                    .thenCompose(ignored -> this.socket.sendClose(code, reason))
                    .whenComplete((ignored, failure) -> this.socket.abort());
        }
    }

    @FunctionalInterface
    private interface BodyReader<T> {
        @Nullable T read(byte[] body);
    }

    @FunctionalInterface
    private interface JsonReader<T> {
        @Nullable T read(JsonObject body);
    }

    private static <T> BodyReader<T> json(final JsonReader<T> reader) {
        return body -> {
            try {
                final JsonElement json = JsonParser.parseString(
                        new String(body, StandardCharsets.UTF_8));
                return json.isJsonObject() ? reader.read(json.getAsJsonObject()) : null;
            } catch (final JsonParseException e) {
                return null;
            }
        };
    }

    private HttpRequest.Builder request(final URI base, final String path) {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "SakuraBans/" + BuildConstants.VERSION);
        // Over plain http the JDK would try an h2c upgrade, which the editor site (Node)
        // answers with 405; over https, HTTP/2 is negotiated without one.
        return "http".equals(base.getScheme())
                ? request.version(HttpClient.Version.HTTP_1_1) : request;
    }

    private <T> CompletableFuture<Response<T>> send(final HttpRequest request,
            final int success, final boolean loud, final BodyReader<T> reader) {
        final HttpClient http;
        try {
            http = this.http();
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        // The JDK completes sendAsync's future on the common pool whatever the client's
        // executor is: every step after it runs on the worker.
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .<Response<T>>thenApplyAsync(response -> {
                    final int status = response.statusCode();
                    if (status == success) {
                        final T value = reader.read(response.body());
                        if (value != null) {
                            return new Response.Ok<>(value);
                        }
                        if (loud) {
                            this.logger.warn("The editor site at {} answered {} with an"
                                    + " unexpected body", request.uri().getHost(), status);
                        }
                        return new Response.Failed<>(Problem.UNEXPECTED);
                    }
                    return new Response.Failed<>(this.problem(request, status, loud));
                }, this.worker)
                .exceptionallyAsync(failure -> {
                    if (loud) {
                        this.logger.warn("The editor site at {} can't be reached: {}",
                                request.uri().getHost(), failure.getCause()
                                        instanceof IOException io ? io.toString()
                                        : failure.toString());
                    }
                    return new Response.Failed<>(Problem.UNREACHABLE);
                }, this.worker);
    }

    private Problem problem(final HttpRequest request, final int status, final boolean loud) {
        return switch (status) {
            case 401, 403 -> Problem.UNAUTHORIZED;
            case 404, 410 -> Problem.NOT_FOUND;
            case 413 -> Problem.TOO_LARGE;
            case 429 -> Problem.RATE_LIMITED;
            default -> {
                if (loud) {
                    this.logger.warn("The editor site at {} answered {}",
                            request.uri().getHost(), status);
                }
                yield Problem.UNEXPECTED;
            }
        };
    }

    private static @Nullable String string(final JsonObject body, final String key) {
        final JsonElement value = body.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private HttpClient http() {
        final HttpClient current = this.client.get();
        if (current != null) {
            return current;
        }
        final HttpClient created = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(this.worker)
                .build();
        if (this.client.compareAndSet(null, created)) {
            return created;
        }
        created.close();
        return this.http();
    }

    private static <T> CompletableFuture<Response<T>> failed(final Problem problem) {
        return CompletableFuture.completedFuture(new Response.Failed<>(problem));
    }
}
