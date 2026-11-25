package net.serlith.sakurabans.core.notify;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.event.SakuraPunishmentCreatedEvent;
import net.serlith.sakurabans.core.message.Formats;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Discord webhooks against a local HTTP server (plan S37). */
class WebhookServiceTest extends ServiceTest {

    private final List<String> received = new CopyOnWriteArrayList<>();
    /** Status codes to answer with, in order; 204 once empty. */
    private final Deque<Integer> answers = new ArrayDeque<>();
    private HttpServer server;
    private WebhookService webhooks;

    @BeforeEach
    void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),
                0), 0);
        this.server.createContext("/api/webhooks/1/secret-token", exchange -> {
            try (InputStream body = exchange.getRequestBody()) {
                this.received.add(new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
            final Integer status;
            synchronized (this.answers) {
                status = this.answers.poll();
            }
            final int code = status == null ? 204 : status;
            if (code == 429) {
                exchange.getResponseHeaders().add("Retry-After", "0.05");
            }
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
        });
        this.server.start();
        final String url = "http://127.0.0.1:" + this.server.getAddress().getPort()
                + "/api/webhooks/1/secret-token";
        final String yaml =
                """
                enabled: true
                url: "%s"
                routes:
                  ban:
                    title: "Ban #{display_id}"
                    color: "#ff0000"
                    description: "{victim} was banned by {operator}"
                    fields:
                      Reason: "{reason}"
                      Scope: "{scope}"
                  unban:
                    color: "#00ff00"
                    description: "{victim} was unbanned by {revoker}"
                  mute:
                    enabled: false
                """;
        Files.writeString(this.dir.resolve("webhooks.yml"), yaml.formatted(url));
        Assertions.assertThat(this.config.reload()).isInstanceOf(ReloadResult.Reloaded.class);
        this.webhooks = new WebhookService(this.bus, this.config, this.messages,
                this.placeholders, new Formats(this.messages, this.config), this.scopes,
                this.names, this.executors, this.clock, this.log.logger());
        this.webhooks.start();
        this.names.put(STEVE, "Steve");
    }

    @AfterEach
    void stopServer() {
        this.webhooks.stop();
        this.server.stop(0);
    }

    private Punishment punish(final PunishmentType type, final Visibility visibility) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(type,
                        PlayerVictim.of(STEVE), MODERATOR).reason("x-ray \"diamonds\"").build(),
                        PunishOptions.builder().visibility(visibility).build()).join())
                .punishment();
    }

    @Test
    void punishmentsArePostedAsEmbedsOnlyWhereTheyHappened() {
        final Punishment ban = this.punish(PunishmentType.BAN, Visibility.NORMAL);
        eventually(() -> this.received.size() == 1);
        Assertions.assertThat(this.received.getFirst()).contains(
                "\"username\":\"SakuraBans\"",
                "\"title\":\"Ban #" + ban.displayId() + "\"",
                "\"description\":\"Steve was banned by Mod\"",
                "\"color\":16711680",
                "{\"name\":\"Reason\",\"value\":\"x-ray \\\"diamonds\\\"\",\"inline\":true}",
                "{\"name\":\"Scope\",\"value\":\"global\",\"inline\":true}");

        // Another instance's ban, a disabled route, silent and hidden ones: nothing.
        this.bus.post(new SakuraPunishmentCreatedEvent(ban, null, true));
        this.punish(PunishmentType.MUTE, Visibility.NORMAL);
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(ban.id()),
                MODERATOR).visibility(Visibility.SILENT).build()).join();
        this.punish(PunishmentType.BAN, Visibility.HIDDEN);
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ByVictim.of(
                PunishmentType.BAN, PlayerVictim.of(STEVE), null), MODERATOR).build()).join();

        this.punish(PunishmentType.BAN, Visibility.NORMAL);
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ByVictim.of(
                PunishmentType.BAN, PlayerVictim.of(STEVE), null), MODERATOR).build()).join();
        eventually(() -> this.received.size() == 3);
        Assertions.assertThat(this.received.getLast()).contains("unbanned by Mod",
                "\"color\":65280");
        Assertions.assertThat(this.received).hasSize(3);
    }

    @Test
    void rateLimitsAndServerErrorsAreRetriedClientErrorsAreNot() {
        synchronized (this.answers) {
            this.answers.addAll(List.of(429, 500, 204, 400));
        }
        this.punish(PunishmentType.BAN, Visibility.NORMAL);
        // 429, then 500 (retried after 1 s), then delivered.
        eventually(() -> this.received.size() == 3);
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ByVictim.of(
                PunishmentType.BAN, PlayerVictim.of(STEVE), null), MODERATOR).build()).join();
        eventually(() -> this.received.size() == 4);
        eventually(() -> this.webhookWarnings().size() == 1);
        Assertions.assertThat(this.webhookWarnings()).singleElement()
                .satisfies(entry -> Assertions.assertThat(Arrays.deepToString(entry.arguments()))
                        .contains("127.0.0.1", "400").doesNotContain("secret-token"));
    }

    private List<RecordingLogger.Entry> webhookWarnings() {
        return this.log.entries("warn").stream()
                .filter(entry -> entry.format().contains("webhook on")).toList();
    }

    @Test
    void textsAreEscapedAndCut() {
        Assertions.assertThat(WebhookService.quote("a\"b\\c\nd\u0001", 100))
                .isEqualTo("\"a\\\"b\\\\c\\nd\\u0001\"");
        Assertions.assertThat(WebhookService.quote("x".repeat(10), 5)).isEqualTo("\"xxxx…\"");
    }
}
