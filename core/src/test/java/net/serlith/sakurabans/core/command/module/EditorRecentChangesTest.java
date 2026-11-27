package net.serlith.sakurabans.core.command.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** The payload's read-only "Recent changes" (protocol 5.6). */
class EditorRecentChangesTest extends EditorTest {

    private static final String WEBHOOK = "https://discord.example/api/webhooks/1/s3cr3t-t0ken";
    private static final String LONG_TEXT = "<red>Message body that must never travel in the list";

    private void tick() {
        this.now.set(this.now.get().plus(Duration.ofSeconds(10)));
    }

    private void template(final String name) {
        this.templates.create(TemplateBuilder.preset(name, PunishmentType.MUTE)
                .reason("Reason of " + name).duration(TemplateDuration.fixed(Duration.ofHours(1)))
                .build(), CONSOLE).join();
        this.tick();
    }

    private static List<JsonObject> recent(final JsonObject payload) {
        final JsonArray list = payload.getAsJsonArray("recentChanges");
        Assertions.assertThat(list).isNotNull();
        final List<JsonObject> items = new ArrayList<>();
        for (final JsonElement element : list) {
            items.add(element.getAsJsonObject());
        }
        return items;
    }

    @Test
    void absentWithoutChanges() {
        Assertions.assertThat(this.open(this.console, "").payload().has("recentChanges"))
                .isFalse();
    }

    @Test
    void holdsTheTwentyNewestChangesOfSettingsMessagesAndTemplates() {
        for (int i = 0; i < 15; i++) {
            this.template("t" + i);
        }
        this.messageTemplates.change(ServerScope.of("skywars"), Locale.US,
                Map.of("chat.muted", LONG_TEXT), Set.of(), 0, CONSOLE).join();
        this.tick();
        this.settingsService.change(GlobalScope.INSTANCE, Map.of("reasons.default", "Rules",
                "webhooks:url", WEBHOOK), Set.of(), 1, MODERATOR).join();
        this.tick();
        // Not a change of the configuration: never listed.
        this.run(this.console, "ban Steve -r x");
        for (int i = 15; i < 20; i++) {
            this.template("t" + i);
        }

        final JsonObject payload = this.open(this.console, "").payload();

        final List<JsonObject> items = recent(payload);
        Assertions.assertThat(items).hasSize(20);
        Assertions.assertThat(items.stream().map(item -> item.get("at").getAsLong()).toList())
                .isSortedAccordingTo(Comparator.reverseOrder());
        Assertions.assertThat(items.getFirst().get("target").getAsString()).isEqualTo("t19");
        Assertions.assertThat(items.getFirst().get("kind").getAsString()).isEqualTo("templates");
        Assertions.assertThat(items.getFirst().get("action").getAsString()).isEqualTo("create");
        Assertions.assertThat(items.getFirst().get("scope").getAsString()).isEqualTo("global");
        // 5 newer templates, then the settings change, the message, and the older templates.
        final JsonObject settings = items.get(5);
        Assertions.assertThat(settings.get("kind").getAsString()).isEqualTo("settings");
        Assertions.assertThat(settings.get("actor").getAsString()).isEqualTo("Mod");
        Assertions.assertThat(settings.get("action").getAsString()).isEqualTo("update");
        Assertions.assertThat(settings.get("scope").getAsString()).isEqualTo("global");
        Assertions.assertThat(settings.get("target").getAsString()).isEmpty();
        Assertions.assertThat(settings.getAsJsonArray("keys").asList().stream()
                .map(JsonElement::getAsString).toList())
                .containsExactlyInAnyOrder("reasons.default", "webhooks:url");
        final JsonObject message = items.get(6);
        Assertions.assertThat(message.get("kind").getAsString()).isEqualTo("messages");
        Assertions.assertThat(message.get("scope").getAsString()).isEqualTo("server:skywars");
        Assertions.assertThat(message.get("locale").getAsString()).isEqualTo("en_US");
        Assertions.assertThat(message.get("target").getAsString()).isEqualTo("chat.muted");
        Assertions.assertThat(message.get("actor").getAsString()).isEqualTo("Console");
        Assertions.assertThat(items.getLast().get("target").getAsString()).isEqualTo("t2");
        // Nothing of the entries' values, and the ban isn't one of the kinds.
        final String text = payload.getAsJsonArray("recentChanges").toString();
        Assertions.assertThat(text).doesNotContain(WEBHOOK, "s3cr3t", "(secret)", LONG_TEXT,
                "Rules", "Reason of", "BAN");
        Assertions.assertThat(items).allSatisfy(item -> Assertions.assertThat(item.keySet())
                .containsExactly("at", "actor", "action", "kind", "scope", "locale", "target",
                        "keys"));
    }

    @Test
    void variantsAndDeletionsNameTheirScope() {
        this.template("spam");
        this.templates.create(TemplateBuilder.from(this.templates.find("spam").orElseThrow()
                .template()).variant(ServerScope.of("skywars")).build(), CONSOLE).join();
        this.tick();
        this.templates.delete(ServerScope.of("skywars"), "spam", this.templates.find("spam",
                ServerScope.of("skywars")).orElseThrow().version(), CONSOLE).join();

        final List<JsonObject> items = recent(this.open(this.console, "").payload());

        Assertions.assertThat(items.get(0).get("action").getAsString()).isEqualTo("delete");
        Assertions.assertThat(items.get(0).get("scope").getAsString()).isEqualTo("server:skywars");
        Assertions.assertThat(items.get(0).get("target").getAsString()).isEqualTo("spam");
        Assertions.assertThat(items.get(1).get("scope").getAsString()).isEqualTo("server:skywars");
        Assertions.assertThat(items.get(2).get("scope").getAsString()).isEqualTo("global");
    }

    @Test
    void onlyHoldsTheSectionsTheUploaderMayEdit() {
        this.template("spam");
        this.settingsService.change(GlobalScope.INSTANCE, Map.of("reasons.default", "Rules"),
                Set.of(), 1, CONSOLE).join();
        final FakeCommandActor templater = FakeCommandActor.player(MOD, "Tem",
                Permissions.EDITOR, Permissions.ADMIN_TEMPLATES);

        final List<JsonObject> items = recent(this.open(templater, "").payload());

        Assertions.assertThat(items).hasSize(1);
        Assertions.assertThat(items.getFirst().get("kind").getAsString()).isEqualTo("templates");
    }

    @Test
    void freshPayloadAfterApplyShowsTheChange() {
        final FakeCommandActor admin = FakeCommandActor.player(MOD, "Mod", Permissions.EDITOR,
                Permissions.ADMIN_TEMPLATES);
        final Session session = this.open(admin, "");
        Assertions.assertThat(session.payload().has("recentChanges")).isFalse();

        final String code = this.save(session,
                """
                {"format": 1, "templates": [
                   {"op": "create", "scope": "global", "name": "flood",
                    "definition": {"type": "mute", "reason": "Flood", "duration": "1h"}}]}
                """);
        this.apply(admin, code);

        final List<JsonObject> items = recent(this.current(session));
        Assertions.assertThat(items).hasSize(1);
        Assertions.assertThat(items.getFirst().get("actor").getAsString()).isEqualTo("Mod");
        Assertions.assertThat(items.getFirst().get("source")).isNull();
        Assertions.assertThat(items.getFirst().get("target").getAsString()).isEqualTo("flood");
    }
}
