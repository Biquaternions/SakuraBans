package net.serlith.sakurabans.core.command.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.RecordingLogger;
import net.serlith.sakurabans.core.admin.EditorSeal;
import net.serlith.sakurabans.core.admin.FakeEditorSite;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.configuration.TemplateFiles;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.AdminResult;
import net.serlith.sakurabans.core.model.TemplateDefinition;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class EditorCommandsTest extends EditorTest {

    private static final Path FIXTURES = Path.of(System.getProperty(
            "sakurabans.protocolFixtures", "../protocol/fixtures"));

    @Override
    protected String configYaml() {
        return super.configYaml() + "alts:\n  join-notification:\n    threshold: 3\n";
    }

    @Test
    void theUploadHoldsTheSectionsTheSenderMayEdit() {
        final JsonObject all = this.open(this.console, "").payload();
        Assertions.assertThat(all.getAsJsonObject("meta").getAsJsonArray("sections"))
                .hasToString("[\"settings\",\"messages\",\"templates\"]");
        Assertions.assertThat(all.getAsJsonObject("meta").get("platform").getAsString())
                .isEqualTo("paper");
        Assertions.assertThat(all.getAsJsonObject("settings").getAsJsonArray("fields").asList())
                .extracting(field -> field.getAsJsonObject().get("key").getAsString())
                .contains("reasons.default", "scopes.categories")
                .doesNotContain("server.name", "web.url", "web.token", "web.live-apply",
                        "commands.labels");
        Assertions.assertThat(all.getAsJsonObject("messages").getAsJsonArray("locales").asList())
                .extracting(locale -> locale.getAsJsonObject().get("locale").getAsString())
                .contains("en_US");
        Assertions.assertThat(this.count(SakuraTables.EDITOR_SESSIONS)).isOne();
        Assertions.assertThat(this.site.requests().getFirst().headers().get("Authorization"))
                .containsExactly("Bearer " + TOKEN);
        Assertions.assertThat(lines(this.console)).last().isEqualTo("While the editor stays"
                + " open, what you save there applies here by itself.");

        final UUID uuid = UUID.randomUUID();
        final FakeCommandActor templater = FakeCommandActor.player(uuid, "Tem",
                Permissions.EDITOR, Permissions.ADMIN_TEMPLATES);
        final JsonObject templates = this.open(templater, "").payload();
        Assertions.assertThat(templates.keySet())
                .containsExactly("format", "meta", "templates", "lookups");
        Assertions.assertThat(templates.getAsJsonObject("meta").getAsJsonObject("uploader")
                .get("uuid").getAsString()).isEqualTo(uuid.toString());

        templater.clear();
        this.run(templater, "sb editor settings");
        Assertions.assertThat(templater.last()).contains("may not edit");
        this.run(templater, "sb editor plugins");
        Assertions.assertThat(templater.last()).contains("'plugins' is not a section");
        final FakeCommandActor nobody = FakeCommandActor.player(UUID.randomUUID(), "No",
                Permissions.EDITOR);
        this.run(nobody, "sb editor");
        Assertions.assertThat(nobody.last()).contains("may not edit");
    }

    @Test
    void eachSectionAppliesAndTheSenderSeesEveryChange() {
        this.templates.create(template("spam", "Spamming"), CONSOLE).join();
        this.templates.create(template("old", "Old"), CONSOLE).join();
        this.settingsService.update(Map.of("enforcement.address-strictness", "STRICT"),
                this.settingsService.version(), CONSOLE).join();
        final Session session = this.open(this.console, "");
        final String longReason = "x".repeat(100);

        final String code = this.save(session,
                """
                {"format": 1,
                 "settings": [{"scope": "global", "expectedVersion": %d,
                              "set": {"reasons.default": "%s",
                                      "alts.join-notification.threshold": 4},
                              "reset": ["enforcement.address-strictness"]}],
                 "messages": [{"scope": "global", "locale": "fr_FR", "expectedVersion": 0,
                               "set": {"chat.muted": "<red>Muet : <reason></red>"}}],
                 "templates": [
                   {"op": "create", "name": "advertising",
                    "definition": {"type": "mute", "reason": "Ads", "ladder": ["1d"]}},
                   {"op": "update", "name": "spam", "expectedVersion": %d,
                    "definition": {"type": "mute", "reason": "Spam", "ladder": ["1h", "1d"]}},
                   {"op": "delete", "name": "old", "expectedVersion": %d}]}
                """.formatted(this.settingsService.version(), longReason, this.version("spam"),
                this.version("old")));
        final List<String> lines = this.apply(this.console, code);

        Assertions.assertThat(lines).containsExactly(
                "Setting reasons.default: No reason specified → " + "x".repeat(59) + "…",
                "Setting alts.join-notification.threshold: 2 → 4",
                "Setting enforcement.address-strictness: reset, now NORMAL",
                "Message chat.muted (fr_FR): set to <red>Muet : <reason></red>",
                "Template advertising: created",
                "Template spam: changed reason (Spamming → Spam), ladder",
                "Template old: deleted",
                "Applied 7, unchanged 0, conflicts 0.");
        Assertions.assertThat(this.config.settings().reasons().defaultReason())
                .isEqualTo(longReason);
        Assertions.assertThat(this.config.settings().enforcement().addressStrictness().name())
                .isEqualTo("NORMAL");
        Assertions.assertThat(this.messages.find("chat.muted", Locale.FRANCE))
                .contains("<red>Muet : <reason></red>");
        Assertions.assertThat(this.templates.find("advertising")).isPresent();
        Assertions.assertThat(this.templates.find("old")).isEmpty();
        Assertions.assertThat(this.templates.find("spam").orElseThrow().reason())
                .isEqualTo("Spam");
        final List<String> sources = this.read(dsl -> dsl.selectDistinct(
                SakuraTables.AUDIT_LOG.SOURCE).from(SakuraTables.AUDIT_LOG)
                .fetch(SakuraTables.AUDIT_LOG.SOURCE));
        Assertions.assertThat(sources).contains("web");
        final JsonObject result = this.result(session, code);
        Assertions.assertThat(result.get("by").getAsString()).isEqualTo("command");
        Assertions.assertThat(result.getAsJsonArray("changes").get(0).getAsJsonObject()
                .get("detail").getAsString()).isEqualTo("No reason specified → " + longReason);

        // Another save that changes nothing is unchanged.
        Assertions.assertThat(this.apply(this.console, this.save(session,
                """
                {"format": 1, "settings": [{"scope": "global", "expectedVersion": 99,
                 "set": {"alts.join-notification.threshold": 4}}]}
                """))).containsExactly("Unchanged: settings alts.join-notification.threshold",
                "Applied 0, unchanged 1, conflicts 0.");
    }

    @Test
    void playersSeeTheLinesInChatAndConflictsLeaveTheRestApplied() {
        this.templates.create(template("spam", "Spamming"), CONSOLE).join();
        final FakeCommandActor admin = FakeCommandActor.player(MOD, "Mod", Permissions.EDITOR,
                Permissions.ADMIN_TEMPLATES, Permissions.ADMIN_MESSAGES);
        final Session session = this.open(admin, "");
        final long stale = this.version("spam");
        this.templates.update(template("spam", "Changed meanwhile"), stale, CONSOLE).join();

        final String code = this.save(session,
                """
                {"format": 1,
                 "messages": [{"scope": "global", "locale": "en_US", "expectedVersion": 0,
                               "set": {"chat.muted": "<b>Quiet</b> <reason>"}}],
                 "templates": [
                   {"op": "update", "name": "spam", "expectedVersion": %d,
                    "definition": {"type": "mute", "reason": "Mine", "ladder": ["1h"]}},
                   {"op": "create", "name": "flood",
                    "definition": {"type": "mute", "reason": "Flood", "ladder": ["1h"]}}]}
                """.formatted(stale));
        final List<String> lines = this.apply(admin, code);

        Assertions.assertThat(lines).containsExactly(
                "Message chat.muted (en_US): set to <b>Quiet</b> <reason>",
                "Conflict: templates spam changed meanwhile; not applied.",
                "Template flood: created",
                "Applied 2, unchanged 0, conflicts 1.",
                "Open a new session with /sb editor to see what changed meanwhile.");
        Assertions.assertThat(this.templates.find("spam").orElseThrow().reason())
                .isEqualTo("Changed meanwhile");
        Assertions.assertThat(this.templates.find("flood")).isPresent();
        // The sender is the creator: told once, in the reply.
        Assertions.assertThat(lines(this.platform.online(MOD).orElseThrow())).isEmpty();
        Assertions.assertThat(this.result(session, code).getAsJsonArray("changes").get(1)
                .getAsJsonObject().get("action").getAsString()).isEqualTo("conflict");
    }

    @Test
    void theCreatorIsToldWhenSomeoneElseApplies() {
        final UUID uuid = UUID.randomUUID();
        final FakeCommandActor creator = FakeCommandActor.player(uuid, "Alex",
                Permissions.EDITOR, Permissions.ADMIN_TEMPLATES);
        final Session session = this.open(creator, "");
        final FakeOnlinePlayer online = this.platform.online(uuid).orElseThrow();
        this.platform.leave(uuid);
        final String first = this.save(session,
                """
                {"format": 1, "templates": [{"op": "create", "name": "first",
                  "definition": {"type": "mute", "reason": "One", "ladder": ["1h"]}}]}
                """);

        // Offline: nobody to tell but the console that ran it.
        Assertions.assertThat(this.apply(this.console, first))
                .containsExactly("Template first: created", "Applied 1, unchanged 0, conflicts 0.");
        Assertions.assertThat(lines(online)).isEmpty();

        final FakeOnlinePlayer back = this.platform.join(uuid, "Alex", this.home);
        final String second = this.save(session,
                """
                {"format": 1, "templates": [{"op": "create", "name": "second",
                  "definition": {"type": "mute", "reason": "Two", "ladder": ["1h"]}}]}
                """);
        this.apply(this.console, second);
        Assertions.assertThat(lines(back)).containsExactly(
                "CONSOLE applied changes from the editor session you opened:",
                "Template second: created", "Applied 1, unchanged 0, conflicts 0.");

        // A console creator hears it in the console.
        final Session own = this.open(this.console, "templates");
        final FakeCommandActor other = FakeCommandActor.player(MOD, "Mod", Permissions.EDITOR,
                Permissions.ADMIN_TEMPLATES);
        final int before = this.platform.consoleMessages().size();
        this.apply(other, this.save(own,
                """
                {"format": 1, "templates": [{"op": "delete", "name": "second",
                  "expectedVersion": 1}]}
                """));
        Assertions.assertThat(strip(this.platform.consoleMessages().subList(before,
                this.platform.consoleMessages().size()).stream().map(EditorTest::plain)
                .toList())).containsExactly(
                        "Mod applied changes from the editor session you opened:",
                        "Template second: deleted", "Applied 1, unchanged 0, conflicts 0.");
    }

    @Test
    void anInvalidChangeSetChangesNothingAndListsItsPaths() {
        this.templates.create(template("spam", "Spamming"), CONSOLE).join();
        final Session session = this.open(this.console, "");

        final String code = this.save(session,
                """
                {"format": 1,
                 "settings": [{"scope": "global", "expectedVersion": 1,
                              "set": {"reasons.default": "fine", "reasons.allow-newlines": "yes",
                                      "alts.join-notification.threshold": 0,
                                      "server.name": "x", "no.such": 1},
                              "reset": ["reasons.default"]}],
                 "messages": [{"scope": "global", "locale": "en_US", "expectedVersion": 0,
                               "set": {"no.key": "x", "chat.muted": ""}}],
                 "templates": [
                   {"op": "create", "name": "advertising",
                    "definition": {"type": "mute", "reason": "Ads", "ladder": ["1d", "soon"]}},
                   {"op": "delete", "name": "spam", "expectedVersion": 0}]}
                """);
        final List<String> lines = this.apply(this.console, code);

        Assertions.assertThat(lines.getFirst()).isEqualTo("Nothing was changed; the changes"
                + " have 9 errors:");
        Assertions.assertThat(lines.subList(1, lines.size())).extracting(line -> line
                .substring(2, line.indexOf(':'))).containsExactlyInAnyOrder(
                        "settings[0].set.reasons.allow-newlines",
                        "settings[0].set.alts.join-notification.threshold",
                        "settings[0].set.server.name", "settings[0].set.no.such",
                        "settings[0].reset[0]", "messages[0].set.no.key",
                        "messages[0].set.chat.muted", "templates[0].definition.ladder[1]",
                        "templates[1].expectedVersion");
        Assertions.assertThat(this.settingsService.overrides()).isEmpty();
        Assertions.assertThat(this.messageTemplates.overrides(GlobalScope.INSTANCE)).isEmpty();
        Assertions.assertThat(this.templates.find("advertising")).isEmpty();
        Assertions.assertThat(this.templates.find("spam")).isPresent();
        final JsonObject result = this.result(session, code);
        Assertions.assertThat(result.get("outcome").getAsString()).isEqualTo("refused");
        Assertions.assertThat(result.getAsJsonArray("errors")).hasSize(9);
        Assertions.assertThat(result.getAsJsonArray("changes")).isEmpty();
    }

    @Test
    void theSettingsMustStillLoadAsWhole() {
        final Session session = this.open(this.console, "settings");

        final List<String> lines = this.apply(this.console, this.save(session,
                """
                {"format": 1, "settings": [{"scope": "global", "expectedVersion": 1,
                 "set": {"display.timezone": "Mars/Olympus"}}]}
                """));

        Assertions.assertThat(lines.getFirst()).startsWith("Nothing was changed");
        Assertions.assertThat(lines.get(1)).startsWith("- settings[0].set.display.timezone:");
        Assertions.assertThat(this.settingsService.overrides()).isEmpty();
    }

    @Test
    void blobsCodesSessionsFormatsAndReplaysThatDontFitAreRefused() {
        final Session session = this.open(this.console, "");
        final String good = "{\"format\": 1, \"settings\": [{\"scope\": \"global\","
                + " \"expectedVersion\": 1, \"set\": {\"reasons.default\": \"x\"}}]}";

        final byte[] tampered = new EditorSeal(new SecureRandom())
                .seal(session.key(), EditorSeal.Role.CHANGES, "{\"sequence\": 1}");
        tampered[tampered.length - 1] ^= 1;
        Assertions.assertThat(this.apply(this.console,
                this.site.saveChanges(session.id(), tampered)))
                .containsExactly("Those changes can't be read; nothing was changed.");
        final byte[] asPayload = new EditorSeal(new SecureRandom())
                .seal(session.key(), EditorSeal.Role.PAYLOAD, good);
        Assertions.assertThat(this.apply(this.console,
                this.site.saveChanges(session.id(), asPayload)))
                .containsExactly("Those changes can't be read; nothing was changed.");

        Assertions.assertThat(this.apply(this.console, "ZZZZZZZZZZ").getFirst())
                .startsWith("There are no saved changes with that code");
        Assertions.assertThat(this.apply(this.console, "nope").getFirst())
                .startsWith("There are no saved changes with that code");

        final Session other = new Session("CCCCCCCCCCCCCCCCCCCCCC", session.key(),
                session.payload());
        Assertions.assertThat(this.apply(this.console, this.save(other, good)).getFirst())
                .startsWith("Those changes belong to an editor session this network doesn't");

        Assertions.assertThat(this.apply(this.console, this.save(session,
                "{\"format\": 2}"))).containsExactly("Those changes are in format 2, which"
                + " this SakuraBans version doesn't read; nothing was changed.");

        // Each change set applies once; an older one after a newer one doesn't either.
        final String older = this.save(session, good);
        final String code = this.save(session, good.replace("\"x\"", "\"y\""));
        Assertions.assertThat(this.apply(this.console, code)).last().asString()
                .startsWith("Applied 1");
        Assertions.assertThat(this.apply(this.console, code)).containsExactly("These changes"
                + " were already applied; save again in the editor to send new ones.");
        Assertions.assertThat(this.apply(this.console, older)).first().asString()
                .startsWith("These changes were already applied");
        Assertions.assertThat(this.config.settings().reasons().defaultReason()).isEqualTo("y");

        final String late = this.save(session, good);
        this.now.set(this.now.get().plus(Duration.ofHours(25)));
        Assertions.assertThat(this.apply(this.console, late).getFirst())
                .startsWith("Those changes belong to an editor session");
    }

    @Test
    void twoAppliesOfOneCodeApplyItOnce() {
        final Session session = this.open(this.console, "");
        final String code = this.save(session,
                """
                {"format": 1, "templates": [{"op": "create", "name": "once",
                  "definition": {"type": "mute", "reason": "Once", "ladder": ["1h"]}}]}
                """);
        final FakeCommandActor first = FakeCommandActor.theConsole();
        final FakeCommandActor second = FakeCommandActor.theConsole();

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> this.run(first, "sb applyedits " + code)),
                CompletableFuture.runAsync(() -> this.run(second, "sb applyedits " + code)))
                .join();

        final List<String> all = new ArrayList<>(lines(first));
        all.addAll(lines(second));
        Assertions.assertThat(all).filteredOn(line -> line.equals("Template once: created"))
                .hasSize(1);
        Assertions.assertThat(all).filteredOn(line -> line.startsWith("These changes were"
                + " already applied")).hasSize(1);
    }

    @Test
    void sectionsTheSenderMayNotChangeRefuseEverythingUntilAllowed() {
        final FakeCommandActor templater = FakeCommandActor.player(UUID.randomUUID(), "Tem",
                Permissions.EDITOR, Permissions.ADMIN_TEMPLATES);
        final Session session = this.open(templater, "");
        final String code = this.save(session,
                """
                {"format": 1, "settings": [{"scope": "global", "expectedVersion": 1,
                 "set": {"reasons.default": "x"}}],
                 "templates": [{"op": "create", "name": "ads",
                   "definition": {"type": "mute", "reason": "Ads", "ladder": ["1d"]}}]}
                """);

        Assertions.assertThat(this.apply(templater, code))
                .containsExactly("Nothing was changed: you may not change settings.");
        Assertions.assertThat(this.templates.find("ads")).isEmpty();

        // A refusal doesn't use up the code: with the permission, it applies.
        templater.grant(Permissions.ADMIN_SETTINGS);
        Assertions.assertThat(this.apply(templater, code)).last().asString()
                .isEqualTo("Applied 2, unchanged 0, conflicts 0.");
    }

    @Test
    void withoutAnAddressTheEditorIsOff() throws Exception {
        Files.writeString(this.dir.resolve("config.yml"), "web:\n  url: ''\n");
        this.config.reload();

        this.run(this.console, "sb editor");
        Assertions.assertThat(lines(this.console).getLast())
                .startsWith("The web editor isn't set up");
        this.run(this.console, "sb applyedits ABCDEFGHJK");
        Assertions.assertThat(this.console.last()).contains("/sb settings list");
        Assertions.assertThat(this.site.requests()).isEmpty();
    }

    @Test
    void theTokensNeverShow() throws Exception {
        final Session session = this.open(this.console, "");
        final String sessionToken = this.site.sessionToken(session.id());
        this.apply(this.console, this.save(session, "{\"format\": 1}"));
        this.site.token("another");
        this.run(this.console, "sb editor");
        Assertions.assertThat(this.console.last()).contains("check web.token");
        this.site.status(500);
        this.run(this.console, "sb applyedits ABCDEFGHJK");
        this.run(this.console, "sb settings list");
        this.run(this.console, "sb status");

        for (final String secret : List.of(TOKEN, sessionToken)) {
            Assertions.assertThat(String.join("\n", this.console.texts())).doesNotContain(secret);
            for (final String level : List.of("trace", "debug", "info", "warn", "error")) {
                Assertions.assertThat(this.log.entries(level))
                        .extracting(RecordingLogger.Entry::format)
                        .noneMatch(line -> line.contains(secret));
            }
        }
        Assertions.assertThat(this.settingsService.settings().values())
                .doesNotContainKey("web.token");
    }

    @Test
    void messageTextsAreShownLiterally() {
        final Session session = this.open(this.console, "messages");

        Assertions.assertThat(this.apply(this.console, this.save(session,
                """
                {"format": 1, "messages": [{"scope": "global", "locale": "en_US", "expectedVersion": 0,
                 "set": {"chat.muted": "<click:run_command:'/op'><rainbow>Hi</rainbow>"}}]}
                """))).first().asString().isEqualTo("Message chat.muted (en_US): set to"
                + " <click:run_command:'/op'><rainbow>Hi</rainbow>");
        Assertions.assertThat(Set.copyOf(this.messageTemplates.overrides(GlobalScope.INSTANCE)
                .get(Locale.US)))
                .containsExactly("chat.muted");
    }

    @Test
    void theFixtureChangeSetApplies() throws Exception {
        // The state payload-v1.json describes: global settings at version 3, the minigames
        // category at 1, skywars at 2, global en_US at 2, hacking at 4; lifesteal changed
        // since (version 2), so deleting version 1 conflicts as in result-v1.json. This
        // server is lobby, whose file sets the alt threshold to 3.
        this.settingsService.update(Map.of("enforcement.address-strictness", "STRICT",
                "webhooks:enabled", true, "webhooks:url",
                "https://discord.example/api/webhooks/1/global"), 1, CONSOLE).join();
        this.settingsService.update(Map.of("scopes.categories",
                Map.of("minigames", List.of("bedwars-1", "skywars"))), 2, CONSOLE).join();
        final Scope skywars = ServerScope.of("skywars");
        this.settingsService.change(CategoryScope.of("minigames"), Map.of("reasons.default",
                "Breaking the minigame rules"), Set.of(), 0, CONSOLE).join();
        this.settingsService.change(skywars, Map.of("enforcement.mute-command-blacklist",
                List.of("me", "say", "tell", "shout")), Set.of(), 0, CONSOLE).join();
        this.settingsService.change(skywars, Map.of("webhooks:routes.ban.title",
                "Skywars ban"), Set.of(), 1, CONSOLE).join();
        Assertions.assertThat(this.settingsService.version()).isEqualTo(3);
        Assertions.assertThat(this.settingsService.version(skywars)).isEqualTo(2);
        this.messageTemplates.update(Locale.US, Map.of("screen.ban", "<red>Banned</red>"), 0,
                CONSOLE).join();
        this.messageTemplates.update(Locale.US, Map.of("screen.ban",
                "<red>Banned from Example Network.</red>"), 1, CONSOLE).join();
        final JsonObject payload = JsonParser.parseString(Files.readString(
                FIXTURES.resolve("payload-v1.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        for (final var item : payload.getAsJsonObject("templates").getAsJsonArray("items")) {
            final String name = item.getAsJsonObject().get("name").getAsString();
            final boolean changed = name.equals("lifesteal");
            final long target = changed ? 2 : item.getAsJsonObject().get("version").getAsLong();
            final TemplateDefinition definition = TemplateFiles.definition(name,
                    plain(item.getAsJsonObject().get("definition")), name).templates().getFirst();
            this.templates.create(definition, CONSOLE).join();
            for (long version = 1; version < target; version++) {
                final TemplateDefinition next = version == target - 1 && !changed ? definition
                        : definition.toBuilder().reason("v" + version).build();
                Assertions.assertThat(this.templates.update(next, version, CONSOLE).join())
                        .isInstanceOf(AdminResult.Ok.class);
            }
        }
        Assertions.assertThat(this.version("hacking")).isEqualTo(4);
        final FakeCommandActor alex = FakeCommandActor.player(UUID.fromString(
                "ec561538-f3fd-461d-aff5-086b22154bce"), "Alex", Permissions.EDITOR,
                Permissions.ADMIN_SETTINGS, Permissions.ADMIN_MESSAGES,
                Permissions.ADMIN_TEMPLATES);
        final Session session = this.open(alex, "");
        final String changes = Files.readString(FIXTURES.resolve("changes-v1.json"),
                StandardCharsets.UTF_8);

        final String code = this.save(session, changes);
        final List<String> lines = this.apply(alex, code);

        Assertions.assertThat(lines).containsExactly(
                "Setting scopes.categories: {minigames: [bedwars-1, skywars]} → {minigames:"
                        + " [bedwars-1, bedwars-2, skywars]}",
                "Setting enforcement.address-strictness: reset, now NORMAL",
                "Setting alts.join-notification.threshold: published from lobby (2 → 3)",
                "Setting reasons.default in server:skywars: Breaking the minigame rules →"
                        + " Breaking the Skywars rules",
                "Setting webhooks:url in server:skywars: set (hidden)",
                "Setting webhooks:routes.ban.title in server:skywars: reset, now Ban",
                "Setting limits:groups in server:bedwars-1: {} → {helper: {permission:"
                        + " sakurabans.group.helper, max-duration…",
                "Message chat.muted (en_US): set to <red>Muted: <reason></red>",
                "Message screen.ban (en_US): reset",
                "Message screen.ban (en_US) in server:skywars: set to <red>Banned from"
                        + " Skywars.</red>",
                "Template advertising: created",
                "Template hacking: changed reason (Unfair advantage (hacked client) → Cheating),"
                        + " ladder, ladder-expiry, message, step-messages",
                "Conflict: templates lifesteal changed meanwhile; not applied.",
                "Applied 12, unchanged 0, conflicts 1.",
                "Open a new session with /sb editor to see what changed meanwhile.");
        Assertions.assertThat(String.join("\n", lines)).doesNotContain("discord.example");
        final JsonObject result = this.result(session, code);
        System.out.println("FIXTURE RESULT\n" + new GsonBuilder().setPrettyPrinting()
                .disableHtmlEscaping().create().toJson(result));
        final JsonObject expected = JsonParser.parseString(Files.readString(
                FIXTURES.resolve("result-v1.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        // The fixture shows a live apply; this one ran by command.
        expected.addProperty("by", "command");
        Assertions.assertThat(result.get("payloadRevision").getAsLong()).as("the first PUT")
                .isEqualTo(2);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    private static Object plain(final JsonElement element) {
        return new Gson().fromJson(element, Object.class);
    }

    private int request(final String method, final String path) {
        final List<FakeEditorSite.Request> requests =
                this.site.requests();
        for (int i = 0; i < requests.size(); i++) {
            if (requests.get(i).method().equals(method) && requests.get(i).path().equals(path)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void anApplyRefreshesTheSnapshotBeforeItsResult() {
        final Session session = this.open(this.console, "templates");
        final String first = this.save(session,
                """
                {"format": 1, "templates": [{"op": "create", "name": "fresh",
                  "definition": {"type": "mute", "reason": "One", "ladder": ["1h"]}}]}
                """);
        this.apply(this.console, first);

        Assertions.assertThat(this.site.revision(session.id())).isEqualTo(2);
        Assertions.assertThat(this.request("PUT", "/api/sessions/" + session.id()))
                .isNotNegative().isLessThan(this.request("POST", "/api/changes/" + first
                        + "/result"));
        Assertions.assertThat(this.result(session, first).get("payloadRevision").getAsLong())
                .isEqualTo(2);
        // The browser re-bases on the snapshot and saves on its versions: no conflict.
        final long version = this.current(session).getAsJsonObject("templates")
                .getAsJsonArray("items").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(item -> item.get("name").getAsString().equals("fresh")).findFirst()
                .orElseThrow().get("version").getAsLong();
        Assertions.assertThat(this.apply(this.console, this.save(session,
                """
                {"format": 1, "templates": [{"op": "update", "name": "fresh",
                  "expectedVersion": %d,
                  "definition": {"type": "mute", "reason": "Two", "ladder": ["1h"]}}]}
                """.formatted(version)))).contains("Applied 1, unchanged 0, conflicts 0.");
        Assertions.assertThat(this.site.revision(session.id())).isEqualTo(3);

        // A refused change set uploads nothing.
        final String refused = this.save(session,
                """
                {"format": 1, "templates": [{"op": "delete", "name": "fresh",
                  "expectedVersion": 0}]}
                """);
        this.apply(this.console, refused);
        Assertions.assertThat(this.site.revision(session.id())).isEqualTo(3);
        Assertions.assertThat(this.result(session, refused).get("payloadRevision").isJsonNull())
                .isTrue();

        // A failed upload leaves the apply standing, with no revision.
        this.site.refusingPuts(true);
        final String standing = this.save(session,
                """
                {"format": 1, "templates": [{"op": "create", "name": "standing",
                  "definition": {"type": "mute", "reason": "S", "ladder": ["1h"]}}]}
                """);
        Assertions.assertThat(this.apply(this.console, standing)).contains(
                "Template standing: created");
        Assertions.assertThat(this.result(session, standing).get("payloadRevision")
                .isJsonNull()).isTrue();
        Assertions.assertThat(this.log.entries("debug")).extracting(RecordingLogger.Entry::format)
                .anyMatch(line -> line.contains("didn't take the fresh payload"));
    }

    @Test
    void anOfflineCreatorsSnapshotHoldsOnlySectionsTheSenderMayEditToo() {
        final UUID uuid = UUID.randomUUID();
        final FakeCommandActor creator = FakeCommandActor.player(uuid, "Alex",
                Permissions.EDITOR, Permissions.ADMIN_TEMPLATES, Permissions.ADMIN_MESSAGES);
        final Session session = this.open(creator, "");
        final FakeCommandActor templater = FakeCommandActor.player(MOD, "Mod",
                Permissions.EDITOR, Permissions.ADMIN_TEMPLATES);
        final String code = this.save(session,
                """
                {"format": 1, "templates": [{"op": "create", "name": "offline",
                  "definition": {"type": "mute", "reason": "O", "ladder": ["1h"]}}]}
                """);
        this.platform.leave(uuid);

        this.apply(templater, code);

        final JsonObject snapshot = this.current(session);
        Assertions.assertThat(snapshot.getAsJsonObject("meta").getAsJsonArray("sections"))
                .hasToString("[\"templates\"]");
        Assertions.assertThat(snapshot.getAsJsonObject("meta").getAsJsonObject("uploader")
                .get("uuid").getAsString()).isEqualTo(uuid.toString());

        // Online, the creator's own permissions decide.
        this.platform.join(uuid, "Alex", this.home).grant(Permissions.EDITOR,
                Permissions.ADMIN_TEMPLATES, Permissions.ADMIN_MESSAGES);
        this.apply(templater, this.save(session,
                """
                {"format": 1, "templates": [{"op": "delete", "name": "offline",
                  "expectedVersion": 1}]}
                """));
        Assertions.assertThat(this.current(session).getAsJsonObject("meta")
                .getAsJsonArray("sections")).hasToString("[\"messages\",\"templates\"]");
    }
}
