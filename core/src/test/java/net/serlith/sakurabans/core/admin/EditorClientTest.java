package net.serlith.sakurabans.core.admin;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.TestConfig;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.util.BuildConstants;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorClientTest {

    @TempDir
    private Path dir;

    private final RecordingLogger log = new RecordingLogger();
    private final FakeEditorSite site = new FakeEditorSite();
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
    private EditorClient client;

    private EditorClient client(final String web) {
        final ConfigService config = TestConfig.load(this.dir, "web:\n" + web, this.log);
        this.client = new EditorClient(config, this.worker, this.log.logger());
        return this.client;
    }

    @AfterEach
    void close() {
        if (this.client != null) {
            this.client.stop();
        }
        this.site.close();
        this.worker.close();
    }

    private static <T> T ok(final EditorClient.Response<T> response) {
        Assertions.assertThat(response).isInstanceOf(EditorClient.Response.Ok.class);
        return ((EditorClient.Response.Ok<T>) response).value();
    }

    private static EditorClient.Problem problem(final EditorClient.Response<?> response) {
        Assertions.assertThat(response).isInstanceOf(EditorClient.Response.Failed.class);
        return ((EditorClient.Response.Failed<?>) response).problem();
    }

    @Test
    void uploadsWithTheTokenAndDownloadsChanges() {
        this.site.token("s3cret");
        final EditorClient client = this.client("  url: '" + this.site.url()
                + "/'\n  token: s3cret\n");

        final EditorClient.Uploaded uploaded = ok(client.upload(new byte[] {1, 2, 3}).join());
        final String id = uploaded.id();
        Assertions.assertThat(id).hasSize(22);
        Assertions.assertThat(uploaded.token()).isEqualTo(this.site.sessionToken(id));
        Assertions.assertThat(uploaded.toString()).doesNotContain(uploaded.token());
        Assertions.assertThat(this.site.payload(id)).containsExactly(1, 2, 3);
        final FakeEditorSite.Request upload = this.site.requests().getFirst();
        Assertions.assertThat(upload.path()).isEqualTo("/api/sessions");
        Assertions.assertThat(upload.headers().get("Authorization"))
                .containsExactly("Bearer s3cret");
        Assertions.assertThat(upload.headers().get("User-agent"))
                .containsExactly("SakuraBans/" + BuildConstants.VERSION);

        final String code = this.site.saveChanges(id, new byte[] {9});
        final EditorClient.Download download = ok(client.changes(code.toLowerCase()).join());
        Assertions.assertThat(download.session()).isEqualTo(id);
        Assertions.assertThat(download.blob()).containsExactly(9);
        // The browser's routes never get the token; results carry the session's.
        Assertions.assertThat(this.site.requests().getLast().headers())
                .doesNotContainKey("Authorization");
        Assertions.assertThat(ok(client.postResult(code, uploaded.token(), new byte[] {4})
                .join())).isTrue();
        Assertions.assertThat(this.site.result(code)).containsExactly(4);
        Assertions.assertThat(ok(client.replacePayload(id, uploaded.token(), new byte[] {7})
                .join())).isEqualTo(2);
        Assertions.assertThat(this.site.payload(id)).containsExactly(7);
        Assertions.assertThat(problem(client.replacePayload(id, "wrong-token-0123456",
                new byte[] {8}).join())).isEqualTo(EditorClient.Problem.UNAUTHORIZED);
        Assertions.assertThat(problem(client.replacePayload("BBBBBBBBBBBBBBBBBBBBBB",
                uploaded.token(), new byte[] {8}).join()))
                .isEqualTo(EditorClient.Problem.NOT_FOUND);
        Assertions.assertThat(problem(client.postResult(code, uploaded.token(), new byte[] {5})
                .join())).as("one result per code").isEqualTo(EditorClient.Problem.UNEXPECTED);
        Assertions.assertThat(problem(client.postResult(this.site.saveChanges(id,
                new byte[] {1}), "wrong-token-0123456", new byte[] {5}).join()))
                .isEqualTo(EditorClient.Problem.UNAUTHORIZED);
    }

    @Test
    void failuresAreTyped() {
        this.site.token("right");
        final EditorClient client = this.client("  url: " + this.site.url() + "\n  token: wrong\n");

        Assertions.assertThat(problem(client.upload(new byte[1]).join()))
                .isEqualTo(EditorClient.Problem.UNAUTHORIZED);
        Assertions.assertThat(problem(client.upload(new byte[EditorClient.MAX_BLOB + 1]).join()))
                .isEqualTo(EditorClient.Problem.TOO_LARGE);
        Assertions.assertThat(problem(client.changes("ABCDEFGHJK").join()))
                .isEqualTo(EditorClient.Problem.NOT_FOUND);
        final int requests = this.site.requests().size();
        Assertions.assertThat(problem(client.changes("../sessions").join()))
                .isEqualTo(EditorClient.Problem.NOT_FOUND);
        Assertions.assertThat(this.site.requests()).as("no request for a malformed code")
                .hasSize(requests);
        this.site.status(429);
        Assertions.assertThat(problem(client.upload(new byte[1]).join()))
                .isEqualTo(EditorClient.Problem.RATE_LIMITED);
        this.site.status(413);
        Assertions.assertThat(problem(client.upload(new byte[1]).join()))
                .isEqualTo(EditorClient.Problem.TOO_LARGE);
        this.site.status(500);
        Assertions.assertThat(problem(client.changes("ABCDEFGHJK").join()))
                .isEqualTo(EditorClient.Problem.UNEXPECTED);
        this.site.status(201);
        Assertions.assertThat(problem(client.upload(new byte[1]).join()))
                .as("a body without an id").isEqualTo(EditorClient.Problem.UNEXPECTED);
        Assertions.assertThat(this.log.entries("warn")).extracting(RecordingLogger.Entry::format)
                .noneMatch(line -> line.contains("wrong"));
    }

    @Test
    void anUnreachableOrMissingSiteIsTyped() {
        final String url = this.site.url();
        this.site.close();

        Assertions.assertThat(problem(this.client("  url: " + url + "\n").upload(new byte[1])
                .join())).isEqualTo(EditorClient.Problem.UNREACHABLE);
        this.client.stop();
        Assertions.assertThat(problem(this.client("  url: ''\n").upload(new byte[1]).join()))
                .isEqualTo(EditorClient.Problem.NOT_CONFIGURED);
    }

    @Test
    void refusedUpgradesAreReadFromTheHandshakeResponse() {
        for (final int status : new int[] {400, 401, 404, 429, 503}) {
            final Throwable failure = new java.util.concurrent.CompletionException(
                    new java.net.http.WebSocketHandshakeException(new Response(status)));
            Assertions.assertThat(EditorClient.refusal(failure)).hasValueSatisfying(refused -> {
                Assertions.assertThat(refused.status()).isEqualTo(status);
                Assertions.assertThat(refused.permanent()).isEqualTo(status != 429
                        && status != 503);
            });
        }
        Assertions.assertThat(EditorClient.refusal(new java.io.IOException("down"))).isEmpty();
    }

    /** Just enough of a response for a handshake failure. */
    private record Response(int statusCode) implements java.net.http.HttpResponse<Object> {

        @Override
        public java.net.http.HttpRequest request() {
            return java.net.http.HttpRequest.newBuilder(java.net.URI.create("ws://localhost"))
                    .build();
        }

        @Override
        public java.util.Optional<java.net.http.HttpResponse<Object>> previousResponse() {
            return java.util.Optional.empty();
        }

        @Override
        public java.net.http.HttpHeaders headers() {
            return java.net.http.HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        @Override
        public Object body() {
            return "";
        }

        @Override
        public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
            return java.util.Optional.empty();
        }

        @Override
        public java.net.URI uri() {
            return java.net.URI.create("ws://localhost");
        }

        @Override
        public java.net.http.HttpClient.Version version() {
            return java.net.http.HttpClient.Version.HTTP_1_1;
        }
    }
}
