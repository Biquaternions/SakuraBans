package net.serlith.sakurabans.core.command.module;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.core.admin.EditorSocket;
import net.serlith.sakurabans.core.admin.EditorWatcher;
import net.serlith.sakurabans.core.admin.FakeEditorSockets;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Live apply (protocol section 9) against the in-memory socket seam. */
class EditorWatcherTest extends EditorTest {

    private static final UUID ALEX_UUID = UUID.randomUUID();
    private static final String HELLO = "{\"type\":\"hello\",\"browsers\":1,\"pending\":[]}";

    private FakeCommandActor alex;

    @Override
    protected EditorWatcher.Timing editorTiming() {
        return new EditorWatcher.Timing(Duration.ofMillis(30), Duration.ofHours(1),
                Duration.ofMinutes(2), List.of(Duration.ofMillis(10), Duration.ofMillis(20)));
    }

    @BeforeEach
    void creator() {
        this.alex = FakeCommandActor.player(ALEX_UUID, "Alex", Permissions.EDITOR,
                Permissions.ADMIN_TEMPLATES, Permissions.ADMIN_MESSAGES);
    }

    private FakeOnlinePlayer online() {
        return this.platform.online(ALEX_UUID).orElseThrow();
    }

    private Session watched() {
        final Session session = this.open(this.alex, "");
        final FakeEditorSockets.Connection connection = this.editorSockets.last();
        Assertions.assertThat(connection.session()).isEqualTo(session.id());
        Assertions.assertThat(connection.token()).isEqualTo(this.site.sessionToken(session.id()));
        return session;
    }

    private static String create(final String name) {
        return
                """
                {"format": 1, "templates": [{"op": "create", "name": "%s",
                  "definition": {"type": "mute", "reason": "R", "ladder": ["1h"]}}]}
                """.formatted(name);
    }

    private boolean watching() {
        return this.editorWatcher.watched(PlayerOperator.of(ALEX_UUID, "Alex")).isPresent();
    }

    @Test
    void savedChangesApplyWithoutCommandsAndTheCreatorIsTold() {
        final Session session = this.watched();
        final FakeEditorSockets.Connection socket = this.editorSockets.last();
        socket.frame(HELLO);
        final String code = this.save(session, create("live"));

        socket.frame("{\"type\":\"changes\",\"code\":\"" + code + "\"}");

        eventually(() -> this.templates.find("live").isPresent());
        eventually(() -> lines(this.online()).size() >= 3);
        Assertions.assertThat(lines(this.online())).containsSubsequence(
                "Applied changes saved in the editor session you opened:",
                "Template live: created", "Applied 1, unchanged 0, conflicts 0.");
        eventually(() -> this.site.result(code) != null);
        final JsonObject result = this.result(session, code);
        Assertions.assertThat(result.get("by").getAsString()).isEqualTo("live");
        Assertions.assertThat(result.get("outcome").getAsString()).isEqualTo("applied");
        eventually(() -> socket.sent().contains("{\"type\":\"ping\"}"));

        // Pong and frames of newer sites change nothing.
        socket.frame("{\"type\":\"pong\"}");
        socket.frame("{\"type\":\"party\",\"code\":\"" + code + "\"}");
        socket.frame("not json");
        Assertions.assertThat(this.watching()).isTrue();
    }

    @Test
    void pendingCodesApplyInOrderAndRepeatsAreHarmless() {
        final Session session = this.watched();
        final String first = this.save(session, create("ladder"));
        final String second = this.save(session,
                """
                {"format": 1, "templates": [{"op": "update", "name": "ladder",
                  "expectedVersion": 1,
                  "definition": {"type": "mute", "reason": "Second", "ladder": ["1h"]}}]}
                """);

        this.editorSockets.last().frame("{\"type\":\"hello\",\"browsers\":1,\"pending\":[\""
                + first + "\",\"" + second + "\"]}");
        eventually(() -> this.templates.find("ladder").map(template -> template.reason()
                .equals("Second")).orElse(false));

        // A notice for a code a command applied already: nothing more to say.
        final String third = this.save(session, create("third"));
        this.apply(this.console, third);
        Assertions.assertThat(lines(this.online())).contains(
                "CONSOLE applied changes from the editor session you opened:");
        final int told = this.online().messages().size();
        this.editorSockets.last().frame("{\"type\":\"changes\",\"code\":\"" + third + "\"}");
        this.editorSockets.last().frame("{\"type\":\"changes\",\"code\":\"" + first + "\"}");
        this.editorSockets.last().frame("{\"type\":\"browsers\",\"count\":2}");
        eventually(() -> this.site.requests().stream().filter(request -> request.path()
                .equals("/api/changes/" + first)).count() == 2);
        this.editorWatcher.idle().join();
        Assertions.assertThat(this.online().messages()).as("the console's apply told the"
                + " creator already").hasSize(told);
    }

    @Test
    void theCreatorsPermissionsDecideAtApplyTime() {
        final Session session = this.watched();
        this.editorSockets.last().frame(HELLO);
        final String code = this.save(session,
                """
                {"format": 1, "settings": [{"scope": "global", "expectedVersion": 1,
                 "set": {"reasons.default": "x"}}]}
                """);

        this.editorSockets.last().frame("{\"type\":\"changes\",\"code\":\"" + code + "\"}");

        eventually(() -> lines(this.online()).contains(
                "Nothing was changed: you may not change settings."));
        eventually(() -> this.site.result(code) != null);
        Assertions.assertThat(this.result(session, code).get("outcome").getAsString())
                .isEqualTo("refused");
        Assertions.assertThat(this.settingsService.overrides()).isEmpty();
    }

    @Test
    void watchingStopsWithoutBrowsers() {
        this.watched();
        this.editorSockets.last().frame("{\"type\":\"hello\",\"browsers\":0,\"pending\":[]}");
        this.editorWatcher.idle().join();
        this.now.set(this.now.get().plus(Duration.ofSeconds(119)));
        this.editorWatcher.check();
        Assertions.assertThat(this.watching()).isTrue();
        this.now.set(this.now.get().plus(Duration.ofSeconds(1)));
        this.editorWatcher.check();
        Assertions.assertThat(this.watching()).isFalse();
        Assertions.assertThat(this.editorSockets.last().closedWith())
                .isEqualTo(EditorSocket.NORMAL);

        this.watched();
        final FakeEditorSockets.Connection socket = this.editorSockets.last();
        socket.frame(HELLO);
        this.editorWatcher.idle().join();
        this.now.set(this.now.get().plus(Duration.ofMinutes(5)));
        this.editorWatcher.check();
        Assertions.assertThat(this.watching()).as("a browser is connected").isTrue();
        socket.frame("{\"type\":\"browsers\",\"count\":0}");
        this.editorWatcher.idle().join();
        this.now.set(this.now.get().plus(Duration.ofMinutes(1)));
        this.editorWatcher.check();
        Assertions.assertThat(this.watching()).isTrue();
        this.now.set(this.now.get().plus(Duration.ofMinutes(1)));
        this.editorWatcher.check();
        Assertions.assertThat(this.watching()).isFalse();
    }

    @Test
    void watchingStopsWhenTheCreatorLeavesTheSessionExpiresOrTheServerStops() {
        this.watched();
        this.editorSockets.last().frame(HELLO);
        this.editorWatcher.idle().join();
        this.platform.leave(ALEX_UUID);
        this.editorWatcher.check();
        Assertions.assertThat(this.watching()).isFalse();
        Assertions.assertThat(this.editorSockets.last().closedWith())
                .isEqualTo(EditorSocket.NORMAL);

        this.watched();
        final FakeEditorSockets.Connection older = this.editorSockets.last();
        this.watched();
        Assertions.assertThat(older.closedWith()).as("replaced by a newer session")
                .isEqualTo(EditorSocket.NORMAL);
        Assertions.assertThat(this.editorSockets.last().closedWith()).isZero();

        final FakeEditorSockets.Connection current = this.editorSockets.last();
        current.frame(HELLO);
        this.editorWatcher.idle().join();
        this.now.set(this.now.get().plus(Duration.ofHours(24)));
        this.editorWatcher.check();
        Assertions.assertThat(current.closedWith()).isEqualTo(EditorSocket.NORMAL);

        this.watched();
        this.editorWatcher.stop();
        Assertions.assertThat(this.editorSockets.last().closedWith())
                .isEqualTo(EditorSocket.NORMAL);
        Assertions.assertThat(this.watching()).isFalse();
    }

    @Test
    void settingsDecideWhetherToWatch() throws Exception {
        this.watched();
        Files.writeString(this.dir.resolve("config.yml"), "web:\n  url: '" + this.site.url()
                + "'\n  token: other\n");
        this.config.reload();
        this.editorWatcher.check();
        Assertions.assertThat(this.watching()).as("the token changed").isFalse();

        Files.writeString(this.dir.resolve("config.yml"), "web:\n  url: '" + this.site.url()
                + "'\n  token: other\n  live-apply: false\n");
        this.config.reload();
        this.site.token("other");
        final int connections = this.editorSockets.connections().size();
        this.open(this.alex, "");
        Assertions.assertThat(this.editorSockets.connections()).hasSize(connections);
        Assertions.assertThat(lines(this.alex)).noneMatch(line -> line.contains("by itself"));
        Assertions.assertThat(this.watching()).isFalse();
    }

    @Test
    void droppedSocketsReconnectUnlessReplacedOrExpired() throws Exception {
        this.watched();
        this.editorSockets.last().drop(EditorSocket.ABNORMAL);
        eventually(() -> this.editorSockets.connections().size() == 2);
        this.editorSockets.refusing(true);
        this.editorSockets.last().drop(EditorSocket.ABNORMAL);
        Thread.sleep(100);
        this.editorSockets.refusing(false);
        eventually(() -> this.editorSockets.connections().size() == 3);
        Assertions.assertThat(this.log.entries("warn")).hasSizeLessThanOrEqualTo(2);

        this.editorSockets.last().drop(EditorSocket.REPLACED);
        Thread.sleep(100);
        Assertions.assertThat(this.editorSockets.connections()).hasSize(3);
        Assertions.assertThat(this.watching()).isFalse();

        this.watched();
        final int connections = this.editorSockets.connections().size();
        this.editorSockets.last().drop(EditorSocket.EXPIRED);
        Thread.sleep(100);
        Assertions.assertThat(this.editorSockets.connections()).hasSize(connections);
        Assertions.assertThat(this.watching()).isFalse();
    }

    @Test
    void refusedUpgradesStopOrRetryByStatus() throws Exception {
        for (final int status : new int[] {400, 401, 404}) {
            this.editorSockets.refuseWith(status);
            final int before = this.editorSockets.attempts();
            this.open(this.alex, "");
            Thread.sleep(100);
            Assertions.assertThat(this.editorSockets.attempts()).as("%s", status)
                    .isEqualTo(before + 1);
            Assertions.assertThat(this.watching()).isFalse();
        }
        this.editorSockets.refuseWith(429);
        final int before = this.editorSockets.attempts();
        this.open(this.alex, "");
        eventually(() -> this.editorSockets.attempts() >= before + 3);
        Assertions.assertThat(this.watching()).isTrue();
        this.editorSockets.refuseWith(0);
        eventually(() -> !this.editorSockets.connections().isEmpty()
                && this.editorSockets.last().closedWith() == 0);
    }
}
