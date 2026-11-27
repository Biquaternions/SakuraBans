package net.serlith.sakurabans.core.notify;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.configuration.ReloadResult;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Limits and webhook keys are scoped settings; webhook URLs are secrets. */
class WebhookOverridesTest extends CommandTest {

    private static final String FILE_TOKEN = "file-token-1a2b";
    private static final String ROUTE_TOKEN = "route-token-3c4d";

    private final List<String> received = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private WebhookService webhooks;
    private String routeUrl;

    @BeforeEach
    void startServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),
                0), 0);
        for (final String token : List.of(FILE_TOKEN, ROUTE_TOKEN)) {
            this.server.createContext("/api/webhooks/1/" + token, exchange -> {
                try (InputStream body = exchange.getRequestBody()) {
                    this.received.add(token + " " + new String(body.readAllBytes(),
                            StandardCharsets.UTF_8));
                }
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
        }
        this.server.start();
        final String base = "http://127.0.0.1:" + this.server.getAddress().getPort()
                + "/api/webhooks/1/";
        this.routeUrl = base + ROUTE_TOKEN;
        Files.writeString(this.dir.resolve("webhooks.yml"),
                "enabled: true\nurl: \"" + base + FILE_TOKEN + "\"\n");
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

    private void set(final Scope scope, final String key, final Object value) {
        Assertions.assertThat(this.settingsService.change(scope, Map.of(key, value), Set.of(),
                this.settingsService.version(scope), CONSOLE).join())
                .as(key).isInstanceOf(AdminResult.Ok.class);
    }

    private void ban() {
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).reason("x").build(), PunishOptions.defaults()).join();
    }

    @Test
    void routeKeysAreFlatAndScoped() {
        this.set(ServerScope.of("lobby"), "webhooks:routes.ban.url", this.routeUrl);
        this.set(GlobalScope.INSTANCE, "webhooks:routes.ban.title", "Banned!");
        this.set(GlobalScope.INSTANCE, "webhooks:routes.ban.fields",
                Map.of("Who", "{victim}"));

        this.ban();
        eventually(() -> this.received.size() == 1);
        Assertions.assertThat(this.received.getFirst()).startsWith(ROUTE_TOKEN)
                .contains("\"title\":\"Banned!\"", "{\"name\":\"Who\",\"value\":\"Steve\"");
        // The other routes keep their defaults.
        Assertions.assertThat(this.config.webhooks().routes()).hasSizeGreaterThan(5);

        this.set(GlobalScope.INSTANCE, "limits:reduce-to-limit", false);
        Assertions.assertThat(this.config.limits().reduceToLimit()).isFalse();
        Assertions.assertThat(this.settingsService.change(ServerScope.of("lobby"),
                Map.of("webhooks:routes.ban.color", "red"), Set.of(), 1, CONSOLE).join())
                .isInstanceOfSatisfying(AdminResult.ValidationFailed.class, failed ->
                        Assertions.assertThat(failed.errors().toString())
                                .contains("webhooks:routes.ban.color"));
    }

    @Test
    void secretsAreNeverShownLoggedOrAudited() {
        this.set(ServerScope.of("lobby"), "webhooks:routes.ban.url", this.routeUrl);
        Assertions.assertThat(this.settingsService.change(GlobalScope.INSTANCE,
                Map.of("webhooks:url", "not a url"), Set.of(), this.settingsService.version(),
                CONSOLE).join()).isInstanceOfSatisfying(AdminResult.ValidationFailed.class,
                        failed -> Assertions.assertThat(failed.errors().toString())
                                .doesNotContain("not a url"));
        this.ban();
        eventually(() -> this.received.size() == 1);

        final List<String> said = new ArrayList<>();
        for (final String command : List.of("sb settings list server:lobby",
                "sb settings list --effective", "sb settings publish", "sb reload",
                "sb settings reset webhooks:routes.ban.url server:lobby")) {
            this.console.clear();
            this.run(this.console, command);
            said.addAll(this.console.texts());
        }
        Assertions.assertThat(said).anySatisfy(line -> Assertions.assertThat(line)
                .contains("webhooks:routes.ban.url", "(set, hidden)"));
        final List<String> audit = this.read(dsl -> dsl.select(SakuraTables.AUDIT_LOG.OLD_VALUE,
                SakuraTables.AUDIT_LOG.NEW_VALUE).from(SakuraTables.AUDIT_LOG).fetch(
                        row -> row.value1() + " " + row.value2()));
        Assertions.assertThat(audit).anySatisfy(row -> Assertions.assertThat(row)
                .contains("(secret)"));
        final List<String> logged = new ArrayList<>();
        for (final String level : List.of("trace", "debug", "info", "warn", "error")) {
            for (final RecordingLogger.Entry entry : this.log.entries(level)) {
                logged.add(entry.format() + Arrays.deepToString(entry.arguments()));
            }
        }
        for (final String token : List.of(FILE_TOKEN, ROUTE_TOKEN)) {
            Assertions.assertThat(said).noneSatisfy(line -> Assertions.assertThat(line)
                    .contains(token));
            Assertions.assertThat(audit).noneSatisfy(row -> Assertions.assertThat(row)
                    .contains(token));
            Assertions.assertThat(logged).noneSatisfy(line -> Assertions.assertThat(line)
                    .contains(token));
        }
    }
}
