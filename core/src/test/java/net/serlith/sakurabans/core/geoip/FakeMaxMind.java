package net.serlith.sakurabans.core.geoip;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;
import org.jspecify.annotations.Nullable;

/**
 * MaxMind's download service as the plan describes it: basic auth on
 * {@code /geoip/databases/GeoLite2-Country/download}, {@code HEAD} with {@code Last-Modified},
 * the checksum at {@code suffix=tar.gz.sha256}, and the archive as a redirect to a storage
 * path with a signed query.
 */
final class FakeMaxMind implements AutoCloseable {

    static final String ACCOUNT = "424242";
    static final String KEY = "Geo_Licence-Key_0123456789";
    static final String SIGNATURE = "X-Amz-Signature=storage-signature-secret";

    record Request(String method, String path, @Nullable String query,
            @Nullable String authorization, @Nullable String userAgent) {
    }

    /** {@code data} null: a directory. */
    record TarEntry(String name, byte @Nullable [] data) {
    }

    private final HttpServer server;
    final List<Request> requests = new CopyOnWriteArrayList<>();
    volatile int headStatus = 200;
    volatile int getStatus = 200;
    volatile @Nullable Instant lastModified = Instant.parse("2026-11-24T10:00:00Z");
    volatile byte[] archive = archive(GeoIpTestFiles.database());
    volatile @Nullable String checksum;
    volatile boolean redirect = true;

    FakeMaxMind() {
        try {
            this.server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    URI base() {
        return URI.create("http://127.0.0.1:" + this.server.getAddress().getPort()
                + "/geoip/databases/");
    }

    String expectedAuthorization() {
        return "Basic " + Base64.getEncoder().encodeToString(
                (ACCOUNT + ":" + KEY).getBytes(StandardCharsets.UTF_8));
    }

    List<Request> gets() {
        return this.requests.stream().filter(request -> request.method().equals("GET")).toList();
    }

    private void handle(final HttpExchange exchange) throws IOException {
        final URI uri = exchange.getRequestURI();
        final String method = exchange.getRequestMethod();
        this.requests.add(new Request(method, uri.getPath(), uri.getRawQuery(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("User-Agent")));
        try (exchange) {
            if (uri.getPath().equals("/storage/GeoLite2-Country.tar.gz")) {
                this.send(exchange, 200, this.archive, method);
                return;
            }
            if (!uri.getPath().equals("/geoip/databases/GeoLite2-Country/download")) {
                this.send(exchange, 404, new byte[0], method);
                return;
            }
            if (!this.expectedAuthorization().equals(
                    exchange.getRequestHeaders().getFirst("Authorization"))) {
                this.send(exchange, 401, new byte[0], method);
                return;
            }
            final String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
            if (method.equals("HEAD")) {
                this.send(exchange, this.headStatus, new byte[0], method);
                return;
            }
            if (this.getStatus != 200) {
                this.send(exchange, this.getStatus, new byte[0], method);
                return;
            }
            if (query.equals("suffix=tar.gz.sha256")) {
                final String sum = this.checksum != null ? this.checksum : sha256(this.archive);
                this.send(exchange, 200, (sum + "  GeoLite2-Country_20261124.tar.gz\n")
                        .getBytes(StandardCharsets.US_ASCII), method);
                return;
            }
            if (this.redirect) {
                exchange.getResponseHeaders().add("Location",
                        "/storage/GeoLite2-Country.tar.gz?" + SIGNATURE);
                this.send(exchange, 302, new byte[0], method);
                return;
            }
            this.send(exchange, 200, this.archive, method);
        }
    }

    private void send(final HttpExchange exchange, final int status, final byte[] body,
            final String method) throws IOException {
        final Instant modified = this.lastModified;
        if (modified != null && status == 200) {
            exchange.getResponseHeaders().add("Last-Modified", DateTimeFormatter
                    .RFC_1123_DATE_TIME.format(modified.atOffset(ZoneOffset.UTC)));
        }
        if (method.equals("HEAD") || body.length == 0) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close() {
        this.server.stop(0);
    }

    static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A tar.gz as MaxMind ships it: a folder, a copyright file, then the database. */
    static byte[] archive(final byte[] database) {
        return archive(List.of(
                new TarEntry("GeoLite2-Country_20261124/", null),
                new TarEntry("GeoLite2-Country_20261124/COPYRIGHT.txt",
                        "Database and Contents Copyright (c) MaxMind, Inc.\n"
                                .getBytes(StandardCharsets.US_ASCII)),
                new TarEntry("GeoLite2-Country_20261124/GeoLite2-Country.mmdb", database)));
    }

    static byte[] archive(final List<TarEntry> entries) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            for (final TarEntry entry : entries) {
                final byte[] data = entry.data() == null ? new byte[0] : entry.data();
                gzip.write(header(entry.name(), data.length, entry.data() == null));
                gzip.write(data);
                gzip.write(new byte[(512 - data.length % 512) % 512]);
            }
            gzip.write(new byte[1024]);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static byte[] header(final String name, final long size, final boolean directory) {
        final byte[] header = new byte[512];
        put(header, 0, name);
        put(header, 100, "0000644\0");
        put(header, 108, "0000000\0");
        put(header, 116, "0000000\0");
        put(header, 124, String.format("%011o\0", size));
        put(header, 136, String.format("%011o\0", 1795651200L));
        header[156] = (byte) (directory ? '5' : '0');
        put(header, 257, "ustar\0");
        put(header, 263, "00");
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        long sum = 0;
        for (final byte b : header) {
            sum += Byte.toUnsignedInt(b);
        }
        put(header, 148, String.format("%06o\0 ", sum));
        return header;
    }

    private static void put(final byte[] header, final int offset, final String text) {
        final byte[] value = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(value, 0, header, offset, value.length);
    }
}
