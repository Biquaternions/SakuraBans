package net.serlith.sakurabans.core.command.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.ServerScope;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** S52: scoped blocks, publish and secrets through the editor (protocol 5 and 6). */
class EditorScopesTest extends EditorTest {

    private static final String URL = "https://discord.example/api/webhooks/9/hidden-token";

    @Override
    protected String configYaml() {
        return super.configYaml() + "alts:\n  join-notification:\n    threshold: 3\n";
    }

    private static JsonObject find(final JsonElement array, final String key,
            final String value) {
        for (final JsonElement element : array.getAsJsonArray()) {
            if (element.getAsJsonObject().get(key).getAsString().equals(value)) {
                return element.getAsJsonObject();
            }
        }
        throw new AssertionError(value + " not in " + array);
    }

    @Test
    void thePayloadCarriesScopesServersAndNoSecretValue() {
        this.settingsService.change(ServerScope.of("skywars"), Map.of("webhooks:url", URL,
                "reasons.default", "Skywars"), Set.of(), 0, CONSOLE).join();
        this.messageTemplates.change(ServerScope.of("skywars"), Locale.US, Map.of("chat.muted",
                "Hush"), Set.of(), 0, CONSOLE).join();

        final JsonObject payload = this.open(this.console, "").payload();

        Assertions.assertThat(payload.toString()).doesNotContain("hidden-token");
        final JsonObject settings = payload.getAsJsonObject("settings");
        final JsonObject url = find(settings.get("fields"), "key", "webhooks:url");
        Assertions.assertThat(url.get("group").getAsString()).isEqualTo("webhooks");
        Assertions.assertThat(url.get("secret").getAsBoolean()).isTrue();
        Assertions.assertThat(url.get("level").getAsString()).isEqualTo("SCOPED");
        Assertions.assertThat(find(settings.get("fields"), "key",
                "enforcement.address-strictness").get("level").getAsString())
                .isEqualTo("GLOBAL");
        Assertions.assertThat(settings.getAsJsonArray("scopes").get(0).getAsJsonObject()
                .get("scope").getAsString()).isEqualTo("global");
        final JsonObject skywars = find(settings.get("scopes"), "scope", "server:skywars");
        Assertions.assertThat(skywars.get("version").getAsLong()).isOne();
        Assertions.assertThat(skywars.getAsJsonObject("overrides").get("webhooks:url")
                .getAsBoolean()).isTrue();
        final JsonObject self = settings.getAsJsonArray("servers").get(0).getAsJsonObject();
        Assertions.assertThat(self.get("name").getAsString()).isEqualTo("lobby");
        Assertions.assertThat(self.getAsJsonObject("fileValues").get("webhooks:url")
                .getAsBoolean()).isFalse();
        Assertions.assertThat(self.getAsJsonObject("fileValues")
                .get("alts.join-notification.threshold").getAsLong()).isEqualTo(3);
        final JsonObject messages = payload.getAsJsonObject("messages");
        Assertions.assertThat(find(messages.get("scopes"), "scope", "server:skywars")
                .getAsJsonObject("overrides").get("chat.muted").getAsString()).isEqualTo("Hush");
        Assertions.assertThat(find(messages.get("locales"), "locale", "en_US")
                .getAsJsonObject("fileTexts").has("chat.muted")).isTrue();
    }

    @Test
    void errorPathsFollowTheChangeSet() {
        final Session session = this.open(this.console, "");

        final List<String> lines = this.apply(this.console, this.save(session,
                """
                {"format": 1,
                 "settings": [
                   {"scope": "global", "expectedVersion": 1, "set": {},
                    "publish": {"server": "lobby",
                                "keys": ["reasons.default", "server.name", "no.such"]}},
                   {"scope": "server:skywars", "expectedVersion": 0,
                    "set": {"enforcement.address-strictness": "STRICT"},
                    "publish": {"server": "lobby", "keys": []}},
                   {"scope": "local", "expectedVersion": 0}],
                 "messages": [
                   {"scope": "global", "locale": "en_US", "expectedVersion": 0},
                   {"scope": "category:hubs", "locale": "en_US", "expectedVersion": 0,
                    "reset": ["no.key"]}]}
                """));

        Assertions.assertThat(lines.subList(1, lines.size())).extracting(line -> line
                .substring(2, line.indexOf(": "))).containsExactlyInAnyOrder(
                        "settings[0].publish.keys[1]", "settings[0].publish.keys[2]",
                        "settings[1].publish", "settings[2].scope", "messages[1].reset[0]");

        final List<String> level = this.apply(this.console, this.save(session,
                """
                {"format": 1, "settings": [{"scope": "server:skywars", "expectedVersion": 0,
                  "set": {"enforcement.address-strictness": "STRICT"}}]}
                """));
        Assertions.assertThat(level).element(1).asString()
                .startsWith("- settings[0].set.enforcement.address-strictness:");
    }

    @Test
    void anUnnamedServerCantBePublishedFrom() {
        final Session session = this.open(this.console, "");

        Assertions.assertThat(this.apply(this.console, this.save(session,
                """
                {"format": 1, "settings": [{"scope": "global", "expectedVersion": 1,
                  "publish": {"server": "", "keys": ["reasons.default"]}}]}
                """))).element(1).asString().startsWith("- settings[0].publish.server:")
                .contains("server.name");
    }

    @Test
    void overridingHereWithTheInheritedValueIsSetAndSecretsStayHidden() {
        this.settingsService.change(GlobalScope.INSTANCE, Map.of("reasons.default", "Global"),
                Set.of(), 1, CONSOLE).join();
        final Session session = this.open(this.console, "");
        final String pin =
                """
                {"format": 1, "settings": [{"scope": "server:lobby", "expectedVersion": %d,
                  "set": {"reasons.default": "Global", "webhooks:url": "%s"}}]}
                """;

        final String code = this.save(session, pin.formatted(0, URL));
        Assertions.assertThat(this.apply(this.console, code)).containsExactly(
                "Setting reasons.default in server:lobby: Global → Global",
                "Setting webhooks:url in server:lobby: set (hidden)",
                "Applied 2, unchanged 0, conflicts 0.");
        Assertions.assertThat(this.settingsService.overrides(ServerScope.of("lobby")))
                .containsOnlyKeys("reasons.default", "webhooks:url");
        Assertions.assertThat(this.result(session, code).toString())
                .doesNotContain("hidden-token");

        Assertions.assertThat(this.apply(this.console, this.save(session, pin.formatted(1,
                URL)))).containsExactly("Unchanged: settings reasons.default in server:lobby",
                "Unchanged: settings webhooks:url in server:lobby",
                "Applied 0, unchanged 2, conflicts 0.");
    }
}
