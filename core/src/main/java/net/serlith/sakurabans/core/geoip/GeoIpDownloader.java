package net.serlith.sakurabans.core.geoip;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.Nullable;

public final class GeoIpDownloader {

    public static final URI MAXMIND = URI.create("https://download.maxmind.com/geoip/databases/");
    public static final String EDITION = "GeoLite2-Country";
    public static final long MAX_ARCHIVE = 64L * 1024 * 1024;
    public static final long MAX_DATABASE = 128L * 1024 * 1024;

    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_CHECKSUM = 4096;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);
    private static final String USER_AGENT = "SakuraBans";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    public sealed interface Result {

        record Downloaded(Path file, @Nullable Instant lastModified) implements Result {
        }

        record NotNewer() implements Result {
        }

        record Failed(String reason) implements Result {
        }
    }

    private final URI base;
    private final long maxArchive;
    private final long maxDatabase;
    private final Executor worker;
    private final AtomicReference<@Nullable HttpClient> client = new AtomicReference<>();

    public GeoIpDownloader(final URI base, final Executor worker) {
        this(base, MAX_ARCHIVE, MAX_DATABASE, worker);
    }

    GeoIpDownloader(final URI base, final long maxArchive, final long maxDatabase,
            final Executor worker) {
        Preconditions.checkArgument(base.getPath().endsWith("/"), "the base URL must end in /");
        this.base = base;
        this.maxArchive = maxArchive;
        this.maxDatabase = maxDatabase;
        this.worker = worker;
    }

    public void stop() {
        final HttpClient current = this.client.getAndSet(null);
        if (current != null) {
            current.close();
        }
    }

    public Result download(final String accountId, final String licenseKey,
            final @Nullable Instant localModified, final Path directory) {
        final String authorization = "Basic " + Base64.getEncoder().encodeToString(
                (accountId + ":" + licenseKey).getBytes(StandardCharsets.UTF_8));
        final URI archive = this.base.resolve(EDITION + "/download?suffix=tar.gz");
        final URI checksum = this.base.resolve(EDITION + "/download?suffix=tar.gz.sha256");
        Path packed = null;
        Path unpacked = null;
        try {
            // Not followed: a presigned storage URL is signed for GET only. A redirect that
            // carries no date means the archive is fetched to compare.
            final HttpResponse<Void> head = this.http().send(this.request(archive, "HEAD",
                    authorization, SHORT_TIMEOUT), HttpResponse.BodyHandlers.discarding());
            if (head.statusCode() != 200 && !REDIRECTS.contains(head.statusCode())) {
                return failed(head.statusCode());
            }
            final Instant published = lastModified(head).orElse(null);
            if (localModified != null && published != null
                    && !published.isAfter(localModified)) {
                return new Result.NotNewer();
            }
            final HttpResponse<InputStream> sum = this.send(checksum, "GET", authorization,
                    SHORT_TIMEOUT, HttpResponse.BodyHandlers.ofInputStream());
            final String expected;
            try (InputStream body = sum.body()) {
                if (sum.statusCode() != 200) {
                    return failed(sum.statusCode());
                }
                expected = checksum(body.readNBytes(MAX_CHECKSUM));
            }
            if (expected == null) {
                return new Result.Failed("MaxMind's checksum isn't a SHA-256, so the download"
                        + " can't be checked");
            }
            final HttpResponse<InputStream> got = this.send(archive, "GET", authorization,
                    DOWNLOAD_TIMEOUT, HttpResponse.BodyHandlers.ofInputStream());
            Files.createDirectories(directory);
            packed = Files.createTempFile(directory, EDITION, ".tar.gz.part");
            final String actual;
            try (InputStream body = got.body()) {
                if (got.statusCode() != 200) {
                    return failed(got.statusCode());
                }
                if (got.headers().firstValueAsLong("Content-Length").orElse(0) > this.maxArchive) {
                    return new Result.Failed("the archive is larger than " + this.maxArchive
                            + " bytes");
                }
                actual = this.save(body, packed);
                if (actual == null) {
                    return new Result.Failed("the archive is larger than " + this.maxArchive
                            + " bytes");
                }
            }
            if (!actual.equalsIgnoreCase(expected)) {
                return new Result.Failed("the archive doesn't match MaxMind's checksum");
            }
            unpacked = Files.createTempFile(directory, EDITION, ".mmdb.part");
            final boolean found;
            try (InputStream tar = new GZIPInputStream(Files.newInputStream(packed));
                    OutputStream out = Files.newOutputStream(unpacked)) {
                found = TarReader.extract(tar, name -> name.endsWith(".mmdb"), out,
                        this.maxDatabase);
            }
            if (!found) {
                return new Result.Failed("the archive holds no .mmdb file");
            }
            final Path result = unpacked;
            unpacked = null;
            return new Result.Downloaded(result, published);
        } catch (final IOException e) {
            return new Result.Failed(describe(e));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result.Failed("the download was interrupted");
        } finally {
            deleteQuietly(packed);
            deleteQuietly(unpacked);
        }
    }

    private @Nullable String save(final InputStream body, final Path file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = new DigestInputStream(body, digest);
                OutputStream out = Files.newOutputStream(file)) {
            final byte[] buffer = new byte[16384];
            long total = 0;
            int n;
            while ((n = in.read(buffer)) >= 0) {
                total += n;
                if (total > this.maxArchive) {
                    return null;
                }
                out.write(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private <T> HttpResponse<T> send(final URI uri, final String method,
            final String authorization, final Duration timeout,
            final HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
        URI current = uri;
        boolean first = true;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            final HttpResponse<T> response = this.http().send(this.request(current, method,
                    first ? authorization : null, timeout), handler);
            if (!REDIRECTS.contains(response.statusCode())) {
                return response;
            }
            close(response);
            final String location = response.headers().firstValue("Location").orElse(null);
            if (location == null) {
                throw new IOException("MaxMind answered a redirect without a location");
            }
            final URI next;
            try {
                next = current.resolve(location.strip());
            } catch (final IllegalArgumentException e) {
                throw new IOException("MaxMind redirected to an address that can't be read");
            }
            final boolean secure = "https".equals(next.getScheme())
                    || "http".equals(next.getScheme()) && "http".equals(current.getScheme());
            if (!secure || next.getHost() == null) {
                throw new IOException("MaxMind redirected to an address that isn't https");
            }
            current = next;
            first = false;
        }
        throw new IOException("MaxMind redirected more than " + MAX_REDIRECTS + " times");
    }

    private HttpRequest request(final URI uri, final String method,
            final @Nullable String authorization, final Duration timeout) {
        final HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        // Over plain http (tests) the JDK would try an h2c upgrade first.
        return "http".equals(uri.getScheme())
                ? request.version(HttpClient.Version.HTTP_1_1).build() : request.build();
    }

    private static void close(final HttpResponse<?> response) {
        if (response.body() instanceof InputStream stream) {
            try {
                stream.close();
            } catch (final IOException e) {
                // The connection is dropped either way.
            }
        }
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

    private static Optional<Instant> lastModified(final HttpResponse<?> response) {
        return response.headers().firstValue("Last-Modified").flatMap(text -> {
            try {
                return Optional.of(ZonedDateTime.parse(text.strip(),
                        DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (final DateTimeParseException e) {
                return Optional.empty();
            }
        });
    }

    static @Nullable String checksum(final byte[] body) {
        final String text = new String(body, StandardCharsets.US_ASCII).strip();
        final String token = text.isEmpty() ? "" : text.split("\\s+", 2)[0];
        return SHA256.matcher(token).matches() ? token.toLowerCase(Locale.ROOT) : null;
    }

    private static Result failed(final int status) {
        final String reason = switch (status) {
            case 401, 403 -> "MaxMind refused the account ID or licence key (" + status + ")";
            case 404 -> "MaxMind has no " + EDITION + " download for this account (404)";
            case 429 -> "MaxMind's download limit for this account is reached (429)";
            default -> "MaxMind answered " + status;
        };
        return new Result.Failed(reason);
    }

    private static String describe(final IOException e) {
        final String message = e.getMessage();
        return "the download failed: " + e.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : " (" + message.strip() + ")");
    }

    private static void deleteQuietly(final @Nullable Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (final IOException e) {
                // GeoIpService deletes leftover .part files at the next start.
            }
        }
    }
}
