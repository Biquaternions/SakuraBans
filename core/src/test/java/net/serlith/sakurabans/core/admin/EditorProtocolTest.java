package net.serlith.sakurabans.core.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.serlith.sakurabans.core.configuration.SettingsSchema;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.util.Scopes;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The plugin side of {@code protocol/fixtures}: the contract with the web app. */
class EditorProtocolTest {

    private static final Path FIXTURES =
            Path.of(System.getProperty("sakurabans.protocolFixtures", "../protocol/fixtures"));

    private static String fixture(final String name) throws IOException {
        return Files.readString(FIXTURES.resolve(name), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> map(final Object... entries) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, @Nullable Object> plainObject(final JsonElement element) {
        return (Map<String, @Nullable Object>) EditorJson.plain(element);
    }

    /**
     * The content of {@code payload-v1.json}, built with the plugin's model. Every field's
     * key, group, type, description, options, bounds, level and secret flag come from the
     * plugin's schema; the values are the fixture's data.
     */
    @SuppressWarnings("unchecked")
    static EditorPayload fixturePayload() throws IOException {
        final JsonObject fixture = JsonParser.parseString(fixture("payload-v1.json"))
                .getAsJsonObject();
        final JsonObject settingsJson = fixture.getAsJsonObject("settings");
        final List<EditorPayload.Setting> fields = new ArrayList<>();
        for (final JsonElement element : settingsJson.getAsJsonArray("fields")) {
            final JsonObject field = element.getAsJsonObject();
            final SettingsSchema.Entry entry = SettingsSchema.entry(field.get("key")
                    .getAsString()).orElseThrow();
            fields.add(new EditorPayload.Setting(entry.key(), entry.group(), entry.type(),
                    entry.description(), entry.options(), entry.min(), entry.max(),
                    EditorJson.plain(field.get("default")), entry.level().name(),
                    entry.secret()));
        }
        final List<EditorPayload.SettingScope> scopes = new ArrayList<>();
        for (final JsonElement element : settingsJson.getAsJsonArray("scopes")) {
            final JsonObject scope = element.getAsJsonObject();
            scopes.add(new EditorPayload.SettingScope(scope.get("scope").getAsString(),
                    scope.get("version").getAsLong(), plainObject(scope.get("overrides"))));
        }
        final List<EditorPayload.Server> servers = new ArrayList<>();
        for (final JsonElement element : settingsJson.getAsJsonArray("servers")) {
            final JsonObject server = element.getAsJsonObject();
            servers.add(new EditorPayload.Server(server.get("id").getAsString(),
                    server.get("name").getAsString(),
                    (List<String>) (List<?>) EditorJson.plain(server.get("categories")),
                    server.get("platform").getAsString(),
                    server.get("pluginVersion").getAsString(),
                    server.get("lastSeenAt").getAsLong(),
                    plainObject(server.get("fileValues"))));
        }
        final String banScreen = "<red>You are banned.</red>\n<gray>Reason:</gray> <reason>";
        final String muted = "<red>You are muted: <reason></red>";
        final Map<String, String> fileTexts = new LinkedHashMap<>();
        fileTexts.put("screen.ban", banScreen);
        fileTexts.put("chat.muted", muted);
        final EditorPayload.Messages messages = new EditorPayload.Messages(List.of(
                new EditorPayload.Message("screen.ban", "The screen a banned player sees",
                        List.of("reason", "operator", "expires", "id"), banScreen),
                new EditorPayload.Message("chat.muted", "Shown to a muted player who chats",
                        List.of("reason", "expires"), muted)),
                List.of(new EditorPayload.LocaleTexts("en_US", fileTexts)), List.of(
                        new EditorPayload.MessageScope("global", "en_US", 2, Map.of(
                                "screen.ban", "<red>Banned from Example Network.</red>\n"
                                        + "<gray>Reason:</gray> <reason>")),
                        new EditorPayload.MessageScope("category:minigames", "en_US", 1,
                                Map.of("screen.ban", "<red>Banned from the minigames.</red>"
                                        + "\n<gray>Reason:</gray> <reason>"))));
        // The definitions go through the same mapping as templates.yml, both ways.
        final List<EditorPayload.Template> templates = new ArrayList<>();
        for (final JsonElement item : fixture.getAsJsonObject("templates")
                .getAsJsonArray("items")) {
            final JsonObject object = item.getAsJsonObject();
            final String scope = object.get("scope").getAsString();
            final String name = object.get("name").getAsString();
            final long version = object.get("version").getAsLong();
            final TemplateFiles.Read read = TemplateFiles.definition(name,
                    EditorJson.plain(object.get("definition")), "templates." + name,
                    Scopes.parse(scope).orElseThrow());
            Assertions.assertThat(read.errors()).isEmpty();
            templates.add(new EditorPayload.Template(scope, name, version, TemplateFiles.tree(
                    read.templates().getFirst())));
        }
        return new EditorPayload(
                new EditorPayload.Meta("1.0.0", "paper", "lobby", "Alex",
                        UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce"),
                        1795651200000L, List.of("settings", "messages", "templates")),
                new EditorPayload.Settings(fields, scopes, servers), messages,
                new EditorPayload.Templates(templates),
                new EditorPayload.Lookups(List.of("bedwars-1", "lobby", "skywars"),
                        List.of("minigames", "pvp"), List.of("en_US")));
    }

    @Test
    void thePayloadModelSerialisesToTheFixture() throws IOException {
        Assertions.assertThat(fixturePayload().json())
                .isEqualTo(JsonParser.parseString(fixture("payload-v1.json")));
    }

    @Test
    void absentSectionsAreLeftOut() throws IOException {
        final EditorPayload full = fixturePayload();
        final JsonObject json = new EditorPayload(new EditorPayload.Meta("1.0.0", "velocity", "",
                "Console", null, 1, List.of("templates")), null, null, full.templates(),
                full.lookups()).json();

        Assertions.assertThat(json.keySet())
                .containsExactly("format", "meta", "templates", "lookups");
        Assertions.assertThat(json.getAsJsonObject("meta").getAsJsonObject("uploader")
                .get("uuid").isJsonNull()).isTrue();
    }

    @Test
    void theChangeSetFixtureParses() throws IOException {
        final EditorChanges.Parsed parsed = EditorChanges.parse(fixture("changes-v1.json"));

        Assertions.assertThat(parsed).isInstanceOf(EditorChanges.Parsed.Read.class);
        final EditorChanges changes = ((EditorChanges.Parsed.Read) parsed).changes();
        Assertions.assertThat(changes.format()).isOne();
        Assertions.assertThat(changes.sequence()).isEqualTo(1795651260000L);
        Assertions.assertThat(changes.settings()).containsExactly(
                new EditorChanges.SettingsChange("global", 3, Map.of("scopes.categories",
                        Map.of("minigames", List.of("bedwars-1", "bedwars-2", "skywars"))),
                        List.of("enforcement.address-strictness"), new EditorChanges.Publish(
                                "lobby", List.of("alts.join-notification.threshold"))),
                new EditorChanges.SettingsChange("server:skywars", 2, map(
                        "reasons.default", "Breaking the Skywars rules",
                        "webhooks:url", "https://discord.example/api/webhooks/1/abc"),
                        List.of("webhooks:routes.ban.title"), null),
                new EditorChanges.SettingsChange("server:bedwars-1", 0, Map.of("limits:groups",
                        Map.of("helper", map("permission", "sakurabans.group.helper",
                                "max-duration", Map.of("ban", "3d")))), List.of(), null));
        Assertions.assertThat(changes.messages()).containsExactly(
                new EditorChanges.LocaleChange("global", "en_US", 2,
                        Map.of("chat.muted", "<red>Muted: <reason></red>"),
                        List.of("screen.ban")),
                new EditorChanges.LocaleChange("server:skywars", "en_US", 0,
                        Map.of("screen.ban", "<red>Banned from Skywars.</red>"), List.of()));
        Assertions.assertThat(changes.templates()).containsExactly(
                new EditorChanges.TemplateChange.Create("global", "advertising", map("type", "mute",
                        "reason", "Advertising", "duration", "1d",
                        "message", "<red>No advertising.</red>", "display", "dialog")),
                new EditorChanges.TemplateChange.Update("global", "hacking", 4, map(
                        "type", "ladder",
                        "reason", "Cheating", "steps", List.of("ban 14d", "ban permanent"),
                        "address-shared", true, "groups", Map.of("cheating", 2L))),
                new EditorChanges.TemplateChange.Update("server:skywars", "spam", 1, map(
                        "type", "mute", "reason", "Spamming in Skywars", "duration", "2d",
                        "message", "<red>Muted in Skywars for spamming.</red>")),
                new EditorChanges.TemplateChange.Delete("global", "lifesteal", 1));
    }

    @Test
    void theResultModelSerialisesToTheFixture() throws IOException {
        final List<EditorService.Change> changes = List.of(
                new EditorService.Change.SettingSet("global", "scopes.categories",
                        Map.of("minigames", List.of("bedwars-1", "skywars")),
                        Map.of("minigames", List.of("bedwars-1", "bedwars-2", "skywars"))),
                new EditorService.Change.SettingReset("global", "enforcement.address-strictness",
                        "NORMAL"),
                new EditorService.Change.SettingPublished("alts.join-notification.threshold",
                        "lobby", 2L, 3L),
                new EditorService.Change.SettingSet("server:skywars", "reasons.default",
                        "Breaking the minigame rules", "Breaking the Skywars rules"),
                new EditorService.Change.SettingSet("server:skywars", "webhooks:url", false,
                        "https://discord.example/api/webhooks/1/abc"),
                new EditorService.Change.SettingReset("server:skywars",
                        "webhooks:routes.ban.title", "Ban"),
                new EditorService.Change.SettingSet("server:bedwars-1", "limits:groups",
                        Map.of(), Map.of("helper", map("permission", "sakurabans.group.helper",
                                "max-duration", Map.of("ban", "3d")))),
                new EditorService.Change.MessageSet("global", "en_US", "chat.muted",
                        "<red>Muted: <reason></red>"),
                new EditorService.Change.MessageReset("global", "en_US", "screen.ban"),
                new EditorService.Change.MessageSet("server:skywars", "en_US", "screen.ban",
                        "<red>Banned from Skywars.</red>"),
                new EditorService.Change.TemplateCreated("global", "advertising"),
                new EditorService.Change.TemplateUpdated("global", "hacking", List.of(
                        new EditorService.FieldChange("reason", "Unfair advantage", "Cheating"),
                        new EditorService.FieldChange("steps", null, null),
                        new EditorService.FieldChange("expiry", "1y", null),
                        new EditorService.FieldChange("message", "x", null))),
                new EditorService.Change.TemplateUpdated("server:skywars", "spam", List.of(
                        new EditorService.FieldChange("duration", "1d", "2d"))),
                new EditorService.Change.Conflict(EditorService.Section.TEMPLATES, "global",
                        "lifesteal", EditorService.Change.Why.CHANGED, null));
        final EditorService.Applied.Done done = new EditorService.Applied.Done(changes);
        final EditorResult result = new EditorResult(1795651260000L, true, true,
                changes.stream().map(EditorService::entry).toList(), List.of(), done.applied(),
                done.unchanged(), done.conflicts(), 2L);

        Assertions.assertThat(result.json())
                .isEqualTo(JsonParser.parseString(fixture("result-v1.json")));
    }

    @Test
    void malformedChangeSetsListTheirPaths() {
        final EditorChanges.Parsed parsed = EditorChanges.parse(
                """
                {"format": 1, "sequence": 1, "settings": [{"set": []}], "messages": [{"locale": 5}],
                 "templates": [{"op": "rename", "name": "x"}], "extra": true}
                """);

        Assertions.assertThat(parsed).isInstanceOfSatisfying(EditorChanges.Parsed.Invalid.class,
                invalid -> Assertions.assertThat(invalid.errors())
                        .extracting(error -> error.path()).containsExactlyInAnyOrder("extra",
                                "settings[0].scope", "settings[0].expectedVersion",
                                "settings[0].set", "messages[0].scope", "messages[0].locale",
                                "messages[0].expectedVersion", "templates[0].op",
                                "templates[0].scope"));
        Assertions.assertThat(EditorChanges.parse("{\"format\": 2}"))
                .isEqualTo(new EditorChanges.Parsed.OtherFormat(2));
        Assertions.assertThat(EditorChanges.parse("not json"))
                .isInstanceOf(EditorChanges.Parsed.Invalid.class);
    }

    @Test
    void theFixtureBlobsOpenToTheFixtureFiles() throws Exception {
        final JsonObject crypto = JsonParser.parseString(fixture("crypto-v1.json"))
                .getAsJsonObject();
        final byte[] key = Base64.getUrlDecoder().decode(crypto.get("key").getAsString());
        for (final String role : List.of("payload", "changes", "result")) {
            final JsonObject entry = crypto.getAsJsonObject(role);
            final String opened = EditorSeal.open(key, EditorSeal.Role.valueOf(
                            role.toUpperCase(Locale.ROOT)),
                    Base64.getDecoder().decode(entry.get("blob").getAsString()));
            Assertions.assertThat(opened).as(role)
                    .isEqualTo(fixture(entry.get("plaintextFile").getAsString()));
        }
    }

    @Test
    void sealedBlobsOpenOnlyWithTheirKeyAndRole() throws Exception {
        final EditorSeal seal = new EditorSeal(new SecureRandom());
        final byte[] key = seal.newKey();
        final byte[] blob = seal.seal(key, EditorSeal.Role.CHANGES, "{\"format\":1,\"é\":\"€\"}");

        Assertions.assertThat(EditorSeal.open(key, EditorSeal.Role.CHANGES, blob))
                .isEqualTo("{\"format\":1,\"é\":\"€\"}");
        Assertions.assertThat(seal.seal(key, EditorSeal.Role.CHANGES, "x"))
                .as("a fresh nonce").isNotEqualTo(seal.seal(key, EditorSeal.Role.CHANGES, "x"));
        final byte[] flipped = blob.clone();
        flipped[flipped.length - 20] ^= 1;
        final byte[] version = blob.clone();
        version[0] = 2;
        for (final Runnable refused : List.<Runnable>of(
                () -> open(seal.newKey(), EditorSeal.Role.CHANGES, blob),
                () -> open(key, EditorSeal.Role.PAYLOAD, blob),
                () -> open(key, EditorSeal.Role.CHANGES,
                        Arrays.copyOf(blob, blob.length - 1)),
                () -> open(key, EditorSeal.Role.CHANGES, Arrays.copyOf(blob, 10)),
                () -> open(key, EditorSeal.Role.CHANGES, flipped),
                () -> open(key, EditorSeal.Role.CHANGES, version))) {
            Assertions.assertThatThrownBy(refused::run).hasCauseInstanceOf(
                    EditorSeal.SealException.class);
        }
    }

    private static void open(final byte[] key, final EditorSeal.Role role, final byte[] blob) {
        try {
            EditorSeal.open(key, role, blob);
        } catch (final EditorSeal.SealException e) {
            throw new IllegalStateException(e);
        }
    }
}
