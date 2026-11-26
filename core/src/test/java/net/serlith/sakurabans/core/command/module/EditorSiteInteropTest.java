package net.serlith.sakurabans.core.command.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.core.admin.EditorClient;
import net.serlith.sakurabans.core.admin.EditorSeal;
import net.serlith.sakurabans.core.admin.EditorService;
import net.serlith.sakurabans.core.admin.EditorSocket;
import net.serlith.sakurabans.core.admin.EditorWatcher;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.storage.repository.EditorSessionRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The real {@link net.serlith.sakurabans.core.admin.EditorClient}, seal, service and watcher
 * against a running editor site (the built {@code web/} app), with a small browser in the test.
 * Opt in with {@code -PeditorSite=http://127.0.0.1:<port>}; skipped otherwise.
 */
@EnabledIfSystemProperty(named = "sakurabans.editorSite", matches = ".+")
class EditorSiteInteropTest extends CommandTest {

    private static final String SITE = System.getProperty("sakurabans.editorSite", "");
    private static final Pattern LINK =
            Pattern.compile("/editor/([A-Za-z0-9_-]{22})#([A-Za-z0-9_-]{43})");
    private static final Duration WAIT = Duration.ofSeconds(15);

    // HTTP/1.1 as the plugin uses it: Node refuses the JDK's h2c upgrade (see EditorClient).
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).build();
    private final AtomicInteger connects = new AtomicInteger();
    private final List<Browser> browsers = new CopyOnWriteArrayList<>();
    private long sequence = System.currentTimeMillis();
    private FakeCommandActor alex;

    @Override
    protected String configYaml() {
        return "web:\n  url: '" + SITE + "'\nalts:\n  join-notification:\n    threshold: 4\n";
    }

    @Override
    protected EditorSocket.Connector editorConnector() {
        return (session, token, listener) -> {
            this.connects.incrementAndGet();
            return this.editorClient.connect(session, token, listener);
        };
    }

    @Override
    protected EditorWatcher.Timing editorTiming() {
        return new EditorWatcher.Timing(Duration.ofSeconds(1), Duration.ofHours(1),
                Duration.ofSeconds(2), List.of(Duration.ofMillis(200), Duration.ofMillis(500)));
    }

    @BeforeEach
    void creator() {
        this.alex = FakeCommandActor.player(MOD, "Mod", Permissions.EDITOR,
                Permissions.ADMIN_TEMPLATES, Permissions.ADMIN_MESSAGES);
    }

    @AfterEach
    void closeBrowsers() {
        this.browsers.forEach(browser -> browser.socket().abort());
        this.http.close();
    }

    private static void waitFor(final String what, final BooleanSupplier condition) {
        final long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            Assertions.assertThat(System.nanoTime()).as(what).isLessThan(deadline);
            try {
                Thread.sleep(25);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    // The browser

    private record Session(String id, byte[] key) {
    }

    private record Browser(WebSocket socket, List<JsonObject> frames, AtomicInteger closed) {

        boolean saw(final String json) {
            final JsonObject frame = JsonParser.parseString(json).getAsJsonObject();
            return this.frames.contains(frame);
        }
    }

    private Session open(final FakeCommandActor actor) {
        actor.clear();
        this.run(actor, "sb editor");
        final Matcher link = LINK.matcher(String.join("\n", actor.texts()));
        Assertions.assertThat(link.find()).as(actor.texts() + " " + this.log.entries("warn")
                .stream().map(entry -> entry.format()).toList()).isTrue();
        return new Session(link.group(1), Base64.getUrlDecoder().decode(link.group(2)));
    }

    private String payload(final Session session) throws Exception {
        final HttpResponse<byte[]> response = this.http.send(HttpRequest.newBuilder(
                URI.create(SITE + "/api/sessions/" + session.id())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        Assertions.assertThat(response.statusCode()).isEqualTo(200);
        return EditorSeal.open(session.key(), EditorSeal.Role.PAYLOAD, response.body());
    }

    private String save(final Session session, final String changes) throws Exception {
        final String json = changes.replaceFirst("\\{", "{\"sequence\": " + ++this.sequence
                + ", ");
        final HttpResponse<String> response = this.http.send(HttpRequest.newBuilder(
                URI.create(SITE + "/api/sessions/" + session.id() + "/changes"))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(new EditorSeal(new SecureRandom())
                        .seal(session.key(), EditorSeal.Role.CHANGES, json)))
                .build(), HttpResponse.BodyHandlers.ofString());
        Assertions.assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return JsonParser.parseString(response.body()).getAsJsonObject().get("code")
                .getAsString();
    }

    private JsonObject result(final Session session, final String code) throws Exception {
        final HttpResponse<byte[]> response = this.http.send(HttpRequest.newBuilder(
                URI.create(SITE + "/api/changes/" + code + "/result")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        Assertions.assertThat(response.statusCode()).isEqualTo(200);
        return JsonParser.parseString(EditorSeal.open(session.key(), EditorSeal.Role.RESULT,
                response.body())).getAsJsonObject();
    }

    private Browser browser(final Session session) {
        final List<JsonObject> frames = new CopyOnWriteArrayList<>();
        final AtomicInteger closed = new AtomicInteger();
        final WebSocket socket = this.http.newWebSocketBuilder().buildAsync(
                URI.create(SITE.replaceFirst("^http", "ws") + "/api/sessions/" + session.id()
                        + "/socket?role=browser"), new WebSocket.Listener() {
                            private final StringBuilder partial = new StringBuilder();

                            @Override
                            public CompletionStage<?> onText(final WebSocket webSocket,
                                    final CharSequence data, final boolean last) {
                                this.partial.append(data);
                                if (last) {
                                    frames.add(JsonParser.parseString(this.partial.toString())
                                            .getAsJsonObject());
                                    this.partial.setLength(0);
                                }
                                webSocket.request(1);
                                return null;
                            }

                            @Override
                            public CompletionStage<?> onClose(final WebSocket webSocket,
                                    final int code, final String reason) {
                                closed.set(code);
                                return null;
                            }
                        }).join();
        final Browser browser = new Browser(socket, frames, closed);
        this.browsers.add(browser);
        return browser;
    }

    private boolean watching() {
        return this.editorWatcher.watched(PlayerOperator.of(MOD, "Mod")).isPresent();
    }

    private String token(final Session session) {
        return new EditorSessionRepository(this.storage).find(session.id()).join().orElseThrow()
                .token();
    }

    private FakeOnlinePlayer online() {
        return this.platform.online(MOD).orElseThrow();
    }

    private static String create(final String name) {
        return
                """
                {"format": 1, "templates": [{"op": "create", "name": "%s",
                  "definition": {"type": "mute", "reason": "R", "ladder": ["1h"]}}]}
                """.formatted(name);
    }

    @Test
    void uploadLiveApplyCommandApplyAndReplay() throws Exception {
        // a. The upload: the browser opens what the plugin built.
        final Session session = this.open(this.alex);
        Assertions.assertThat(JsonParser.parseString(this.payload(session))).isEqualTo(
                this.editor.payload(PlayerOperator.of(MOD, "Mod"), EnumSet.of(
                        EditorService.Section.MESSAGES, EditorService.Section.TEMPLATES),
                        this.now.get()).json());

        // b. Both sockets: the browser hears that the plugin is there.
        waitFor("the plugin connects", () -> this.connects.get() == 1);
        final Browser browser = this.browser(session);
        waitFor("the browser hears of the plugin", () -> browser.saw(
                "{\"type\":\"hello\",\"plugin\":true}") || browser.saw(
                "{\"type\":\"plugin\",\"connected\":true}"));
        this.now.set(this.now.get().plus(Duration.ofSeconds(5)));
        this.editorWatcher.idle().join();
        this.editorWatcher.check();
        Assertions.assertThat(this.watching()).as("a browser is connected").isTrue();

        // c. Live apply.
        final String live = this.save(session, create("interop_live"));
        waitFor("the live apply", () -> this.templates.find("interop_live").isPresent());
        waitFor("the result notice", () -> browser.saw("{\"type\":\"result\",\"code\":\""
                + live + "\"}"));
        final JsonObject liveResult = this.result(session, live);
        Assertions.assertThat(liveResult.get("by").getAsString()).isEqualTo("live");
        Assertions.assertThat(liveResult.get("outcome").getAsString()).isEqualTo("applied");
        Assertions.assertThat(liveResult.getAsJsonArray("changes").get(0).getAsJsonObject()
                .get("action").getAsString()).isEqualTo("created");
        Assertions.assertThat(EditorTest.lines(this.online())).contains(
                "Applied changes saved in the editor session you opened:",
                "Template interop_live: created");

        // d. The command, with the watcher gone.
        this.editorWatcher.stop();
        final String command = this.save(session, create("interop_command"));
        Thread.sleep(300);
        Assertions.assertThat(this.templates.find("interop_command")).isEmpty();
        final List<String> lines = EditorTest.lines(this.applied(command));
        Assertions.assertThat(lines).contains("Template interop_command: created");
        waitFor("the command's result notice", () -> browser.saw(
                "{\"type\":\"result\",\"code\":\"" + command + "\"}"));
        Assertions.assertThat(this.result(session, command).get("by").getAsString())
                .isEqualTo("command");

        // e. A replay is refused.
        Assertions.assertThat(EditorTest.lines(this.applied(command)))
                .containsExactly("These changes were already applied; save again in the"
                        + " editor to send new ones.");
    }

    @Test
    void scopesPublishSecretsAndMixedLaddersRoundTrip() throws Exception {
        this.alex.grant(Permissions.ADMIN_SETTINGS);
        final Session session = this.open(this.alex);
        waitFor("the plugin connects", () -> this.connects.get() == 1);
        this.editorWatcher.stop();
        final String secret = "https://discord.example/api/webhooks/7/interop-secret";

        final String code = this.save(session,
                """
                {"format": 1,
                 "settings": [
                   {"scope": "global", "expectedVersion": 1,
                    "publish": {"server": "lobby", "keys": ["alts.join-notification.threshold"]}},
                   {"scope": "server:skywars", "expectedVersion": 0,
                    "set": {"reasons.default": "Skywars rules", "webhooks:url": "%s"}}],
                 "messages": [{"scope": "category:minigames", "locale": "en_US",
                   "expectedVersion": 0, "set": {"chat.muted": "<red>Minigames</red>"}}],
                 "templates": [{"op": "create", "name": "interop_mixed",
                   "definition": {"type": "mute", "reason": "Mixed",
                                  "ladder": ["kick", "mute 1h", "ban 7d"]}}]}
                """.formatted(secret));
        final List<String> lines = EditorTest.lines(this.applied(code));

        Assertions.assertThat(lines).contains(
                "Setting alts.join-notification.threshold: published from lobby (2 → 4)",
                "Setting reasons.default in server:skywars: No reason specified → Skywars rules",
                "Setting webhooks:url in server:skywars: set (hidden)",
                "Message chat.muted (en_US) in category:minigames: set to <red>Minigames</red>",
                "Template interop_mixed: created", "Applied 5, unchanged 0, conflicts 0.");
        final JsonObject result = this.result(session, code);
        Assertions.assertThat(result.getAsJsonArray("changes").asList()).extracting(change ->
                change.getAsJsonObject().get("scope").getAsString() + " "
                        + change.getAsJsonObject().get("action").getAsString())
                .containsExactly("global published", "server:skywars set",
                        "server:skywars set", "category:minigames set", " created");
        Assertions.assertThat(result + String.join("\n", lines)).doesNotContain("interop-secret");

        // The fresh snapshot shows all of it, the secret only as set.
        final Snapshot fresh = this.snapshot(session);
        Assertions.assertThat(fresh.revision()).isEqualTo(2);
        Assertions.assertThat(fresh.payload().toString()).doesNotContain("interop-secret");
        final JsonObject skywars = fresh.payload().getAsJsonObject("settings")
                .getAsJsonArray("scopes").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(scope -> scope.get("scope").getAsString().equals("server:skywars"))
                .findFirst().orElseThrow();
        Assertions.assertThat(skywars.get("version").getAsLong()).isOne();
        Assertions.assertThat(skywars.getAsJsonObject("overrides").get("webhooks:url")
                .getAsBoolean()).isTrue();
        Assertions.assertThat(fresh.payload().getAsJsonObject("settings")
                .getAsJsonArray("scopes").get(0).getAsJsonObject().getAsJsonObject("overrides")
                .get("alts.join-notification.threshold").getAsLong()).isEqualTo(4);
        Assertions.assertThat(fresh.payload().getAsJsonObject("messages")
                .getAsJsonArray("scopes").asList()).anySatisfy(scope -> Assertions
                .assertThat(scope.getAsJsonObject().get("scope").getAsString())
                .isEqualTo("category:minigames"));
        Assertions.assertThat(fresh.payload().getAsJsonObject("templates")
                .getAsJsonArray("items").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(item -> item.get("name").getAsString().equals("interop_mixed"))
                .findFirst().orElseThrow().getAsJsonObject("definition")
                .getAsJsonArray("ladder")).hasToString("[\"kick\",\"1h\",\"ban 7d\"]");
    }

    private FakeCommandActor applied(final String code) {
        this.alex.clear();
        this.run(this.alex, "sb applyedits " + code);
        return this.alex;
    }

    @Test
    void refusedSocketsAreNotRetried() throws Exception {
        final Session session = this.open(this.alex);
        waitFor("the plugin connects", () -> this.connects.get() == 1);
        this.editorWatcher.stop();

        // The JDK reports a refused upgrade as a handshake failure with the response.
        final Throwable wrong = Assertions.catchThrowable(() -> this.editorClient.connect(
                session.id(), "wrong-token-0123456789abcdef", new Ignore()).join());
        Assertions.assertThat(wrong).hasCauseInstanceOf(EditorSocket.Refused.class);
        Assertions.assertThat(((EditorSocket.Refused) wrong.getCause()).status()).isEqualTo(401);
        final Throwable unknown = Assertions.catchThrowable(() -> this.editorClient.connect(
                "AAAAAAAAAAAAAAAAAAAAAA", this.token(session), new Ignore()).join());
        Assertions.assertThat(((EditorSocket.Refused) unknown.getCause()).status())
                .isEqualTo(404);

        for (final String token : List.of("wrong-token-0123456789abcdef", "")) {
            final String id = token.isEmpty() ? "AAAAAAAAAAAAAAAAAAAAAA" : session.id();
            final int before = this.connects.get();
            this.editorWatcher.watch(id, token.isEmpty() ? this.token(session) : token,
                    PlayerOperator.of(MOD, "Mod"), this.now.get());
            Thread.sleep(1500);
            Assertions.assertThat(this.connects.get()).as(id).isEqualTo(before + 1);
            Assertions.assertThat(this.watching()).isFalse();
        }
    }

    @Test
    void stopRulesAndReplacementAgainstTheSite() throws Exception {
        final Session session = this.open(this.alex);
        waitFor("the plugin connects", () -> this.connects.get() == 1);
        final Browser browser = this.browser(session);
        waitFor("hello", () -> !browser.frames().isEmpty());
        browser.socket().sendClose(WebSocket.NORMAL_CLOSURE, "tab closed").join();
        Thread.sleep(500);
        this.editorWatcher.idle().join();
        this.now.set(this.now.get().plus(Duration.ofSeconds(3)));
        this.editorWatcher.check();
        Assertions.assertThat(this.watching()).as("no browser for the grace period").isFalse();

        // Another plugin socket for the same session replaces the watcher's (4000): no retry.
        final Session second = this.open(this.alex);
        waitFor("the plugin connects", () -> this.connects.get() == 2);
        final EditorSocket intruder = this.editorClient.connect(second.id(), this.token(second),
                new Ignore()).join();
        waitFor("the watcher hears it was replaced", () -> !this.watching());
        Thread.sleep(1000);
        Assertions.assertThat(this.connects.get()).isEqualTo(2);
        intruder.close(EditorSocket.NORMAL, "done");
    }

    @Test
    void pendingCodesArriveTogetherAndApplyInOrder() throws Exception {
        final Session session = this.open(this.alex);
        waitFor("the plugin connects", () -> this.connects.get() == 1);
        this.editorWatcher.stop();
        final List<String> codes = List.of(this.save(session, create("pending_a")),
                this.save(session, create("pending_b")), this.save(session, create("pending_c")),
                this.save(session, create("pending_d")));

        this.editorWatcher.watch(session.id(), this.token(session), PlayerOperator.of(MOD,
                "Mod"), this.now.get());

        waitFor("all pending codes", () -> this.templates.find("pending_d").isPresent());
        for (final String code : codes) {
            waitFor("a result for " + code, () -> this.resultExists(code));
        }
        Assertions.assertThat(EditorTest.lines(this.online()).stream()
                .filter(line -> line.startsWith("Template pending_")).toList())
                .containsExactly("Template pending_a: created", "Template pending_b: created",
                        "Template pending_c: created", "Template pending_d: created");
    }

    /** The current payload and its {@code X-Revision}. */
    private record Snapshot(long revision, JsonObject payload) {

        long version(final String template) {
            return this.payload.getAsJsonObject("templates").getAsJsonArray("items").asList()
                    .stream().map(item -> item.getAsJsonObject())
                    .filter(item -> item.get("name").getAsString().equals(template))
                    .findFirst().orElseThrow().get("version").getAsLong();
        }
    }

    private Snapshot snapshot(final Session session) throws Exception {
        final HttpResponse<byte[]> response = this.http.send(HttpRequest.newBuilder(
                URI.create(SITE + "/api/sessions/" + session.id())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        Assertions.assertThat(response.statusCode()).isEqualTo(200);
        return new Snapshot(Long.parseLong(response.headers().firstValue("X-Revision")
                .orElseThrow()), JsonParser.parseString(EditorSeal.open(session.key(),
                EditorSeal.Role.PAYLOAD, response.body())).getAsJsonObject());
    }

    private static String update(final String name, final long version, final String reason) {
        return
                """
                {"format": 1, "templates": [{"op": "update", "name": "%s",
                  "expectedVersion": %d,
                  "definition": {"type": "mute", "reason": "%s", "ladder": ["1h"]}}]}
                """.formatted(name, version, reason);
    }

    @Test
    void appliesRefreshTheSnapshotSoTheNextSaveDoesNotConflict() throws Exception {
        final Session session = this.open(this.alex);
        waitFor("the plugin connects", () -> this.connects.get() == 1);
        final Browser browser = this.browser(session);
        Assertions.assertThat(this.snapshot(session).revision()).isEqualTo(1);

        // Live: apply, refreshed snapshot, a second save on its versions.
        final String created = this.save(session, create("rebased"));
        waitFor("the result", () -> browser.saw("{\"type\":\"result\",\"code\":\""
                + created + "\"}"));
        Assertions.assertThat(this.result(session, created).get("payloadRevision").getAsLong())
                .isEqualTo(2);
        final Snapshot second = this.snapshot(session);
        Assertions.assertThat(second.revision()).isEqualTo(2);
        final String live = this.save(session, update("rebased", second.version("rebased"),
                "Live"));
        waitFor("the second result", () -> browser.saw("{\"type\":\"result\",\"code\":\""
                + live + "\"}"));
        final JsonObject liveResult = this.result(session, live);
        Assertions.assertThat(liveResult.getAsJsonObject("summary").get("conflicts").getAsInt())
                .isZero();
        Assertions.assertThat(liveResult.get("payloadRevision").getAsLong()).isEqualTo(3);
        Assertions.assertThat(this.templates.find("rebased").orElseThrow().reason())
                .isEqualTo("Live");

        // By command, the same.
        this.editorWatcher.stop();
        final Snapshot third = this.snapshot(session);
        Assertions.assertThat(third.revision()).isEqualTo(3);
        final String command = this.save(session, update("rebased", third.version("rebased"),
                "Command"));
        Assertions.assertThat(EditorTest.lines(this.applied(command)))
                .contains("Applied 1, unchanged 0, conflicts 0.");
        Assertions.assertThat(this.snapshot(session).revision()).isEqualTo(4);
        Assertions.assertThat(this.templates.find("rebased").orElseThrow().reason())
                .isEqualTo("Command");

        // Only the session's token may replace its payload.
        final byte[] blob = new EditorSeal(new SecureRandom()).seal(session.key(),
                EditorSeal.Role.PAYLOAD, "{}");
        Assertions.assertThat(this.editorClient.replacePayload(session.id(),
                "wrong-token-0123456789abcdef", blob).join()).isEqualTo(
                        new EditorClient.Response.Failed<>(EditorClient.Problem.UNAUTHORIZED));
        Assertions.assertThat(this.snapshot(session).revision()).isEqualTo(4);
    }

    private boolean resultExists(final String code) {
        try {
            return this.http.send(HttpRequest.newBuilder(URI.create(SITE + "/api/changes/" + code
                    + "/result")).GET().build(), HttpResponse.BodyHandlers.discarding())
                    .statusCode() == 200;
        } catch (final Exception e) {
            return false;
        }
    }

    private static final class Ignore implements EditorSocket.Listener {

        @Override
        public void text(final String text) {
        }

        @Override
        public void closed(final int code, final String reason) {
        }
    }
}
