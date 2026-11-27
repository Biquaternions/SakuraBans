package net.serlith.sakurabans.core.configuration;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.PresetTemplate;
import net.serlith.sakurabans.api.template.TemplateDisplay;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.model.TemplateKind;
import net.serlith.sakurabans.core.model.TemplateSteps;
import net.serlith.sakurabans.core.model.TemplateSummaries;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code templates.yml}: the shipped seed, both shapes, errors with paths, exports (D37). */
class TemplateFilesTest {

    @TempDir
    Path dir;

    private TemplateFiles.Read read(final String yaml) throws Exception {
        Files.writeString(this.dir.resolve(TemplateFiles.FILE), yaml);
        return new TemplateFiles(this.dir).read();
    }

    private static TemplateInfo ladder() {
        return TemplateBuilder.ladder("misconduct")
                .ip(true)
                .shadow(true)
                .scope(CategoryScope.of("survival"))
                .reason("Misconduct")
                .steps(List.of(
                        TemplateStep.of(PunishmentType.WARN, TemplateDuration.open()),
                        TemplateStep.of(PunishmentType.KICK, TemplateDuration.open(), null,
                                "<red>Kicked</red>", List.of()),
                        TemplateStep.of(PunishmentType.MUTE,
                                TemplateDuration.fixed(Duration.ofDays(1))),
                        TemplateStep.none(List.of("say {victim} is on probation")),
                        TemplateStep.of(PunishmentType.WARN,
                                TemplateDuration.fixed(Duration.ofDays(30)), "Last warning",
                                null, List.of("say {victim} {step}")),
                        TemplateStep.of(PunishmentType.BAN, TemplateDuration.permanent())))
                .expiry(Duration.ofDays(90))
                .addressShared(true)
                .permissionRequired(true)
                .actions(List.of("say once"))
                .groups(Map.of("cheating", 2))
                .message("<red>Template</red>")
                .display(TemplateDisplay.DIALOG)
                .build();
    }

    private static TemplateInfo preset() {
        return TemplateBuilder.preset("spam", PunishmentType.MUTE)
                .shadow(true)
                .reason("Spamming")
                .duration(TemplateDuration.fixed(Duration.ofHours(1)))
                .groups(Map.of("chat", 1))
                .actions(List.of("say {victim} {duration}"))
                .message("<red>Muted for spam</red>")
                .build();
    }

    @Test
    void theShippedFileIsValid() {
        final TemplateFiles.Read read = new TemplateFiles(this.dir).read();

        Assertions.assertThat(read.errors()).isEmpty();
        Assertions.assertThat(read.templates()).extracting(TemplateInfo::name)
                .contains("hacking", "spam", "language", "inappropriate-name", "misconduct");
        Assertions.assertThat(read.templates()).extracting(TemplateKind::of)
                .contains(TemplateKind.BAN, TemplateKind.MUTE, TemplateKind.WARN,
                        TemplateKind.KICK, TemplateKind.LADDER);
        final TemplateInfo misconduct = read.templates().stream()
                .filter(template -> template.name().equals("misconduct")).findFirst()
                .orElseThrow();
        Assertions.assertThat(((LadderTemplate) misconduct).steps())
                .extracting(step -> step.type().orElseThrow())
                .containsExactly(PunishmentType.WARN, PunishmentType.KICK, PunishmentType.MUTE,
                        PunishmentType.BAN, PunishmentType.BAN);
        Assertions.assertThat(this.dir.resolve(TemplateFiles.FILE)).exists();
    }

    @Test
    void bothShapesRead() throws Exception {
        final TemplateFiles.Read read = this.read(
                """
                templates:
                  ladder:
                    type: ladder
                    reason: Reason
                    steps:
                      - warn
                      - kick
                      - warn 30d
                      - {step: none, actions: ["say {victim}"]}
                      - {step: "mute 1h", reason: Again, message: "<red>Muted</red>"}
                      - ban permanent
                    expiry: 30d
                  open:
                    type: warn
                    reason: Reason
                  fixed:
                    type: ban
                    reason: Reason
                    duration: 7d
                  forever:
                    type: mute
                    reason: Reason
                    duration: permanent
                """);

        Assertions.assertThat(read.errors()).isEmpty();
        Assertions.assertThat(TemplateKind.of(read.templates().getFirst()))
                .isEqualTo(TemplateKind.LADDER);
        final LadderTemplate ladder = (LadderTemplate) read.templates().getFirst();
        Assertions.assertThat(ladder.expiry()).contains(Duration.ofDays(30));
        Assertions.assertThat(ladder.steps().get(0).duration())
                .isEqualTo(TemplateDuration.open());
        Assertions.assertThat(ladder.steps().get(2).duration().fixedLength())
                .contains(Duration.ofDays(30));
        Assertions.assertThat(ladder.steps().get(3).type()).isEmpty();
        Assertions.assertThat(ladder.steps().get(3).actions()).containsExactly("say {victim}");
        Assertions.assertThat(ladder.steps().get(4).reason()).contains("Again");
        Assertions.assertThat(ladder.steps().get(4).message()).contains("<red>Muted</red>");
        Assertions.assertThat(ladder.steps().get(5).duration())
                .isEqualTo(TemplateDuration.permanent());
        // A ladder has no duration of its own: open.
        Assertions.assertThat(read.templates()).extracting(template ->
                        template instanceof PresetTemplate preset ? preset.duration()
                                : TemplateDuration.open())
                .containsExactly(TemplateDuration.open(), TemplateDuration.open(),
                        TemplateDuration.fixed(Duration.ofDays(7)), TemplateDuration.permanent());
    }

    @Test
    void everyProblemIsReportedWithItsPath() throws Exception {
        final TemplateFiles.Read read = this.read(
                """
                templates:
                  good:
                    type: mute
                    reason: Reason
                    duration: 1h
                  bad:
                    type: ladder
                    reason: Reason
                    steps: [ban 1h, ban soon, 1d, mute, kick 1d, none, jail 1d,
                            {step: warn, colour: red}, {reason: x}]
                    expiry: later
                    address-shared: maybe
                    groups: {cheating: lots}
                    colour: red
                  "Not A Name!":
                    type: ban
                    reason: Reason
                """);

        Assertions.assertThat(read.templates()).extracting(TemplateInfo::name)
                .containsExactly("good");
        Assertions.assertThat(read.errors()).extracting(ConfigIssue::path)
                .containsExactlyInAnyOrder("templates.bad.steps[1]", "templates.bad.steps[2]",
                "templates.bad.steps[3]", "templates.bad.steps[4]",
                "templates.bad.steps[5].actions",
                "templates.bad.steps[6]", "templates.bad.steps[7].colour",
                "templates.bad.steps[8].step", "templates.bad.expiry",
                "templates.bad.address-shared", "templates.bad.groups.cheating",
                "templates.bad.colour", "templates.Not A Name!.name");
    }

    @Test
    void theOldKeysNameTheirReplacement() throws Exception {
        final TemplateFiles.Read read = this.read(
                """
                templates:
                  old:
                    type: ban
                    reason: Reason
                    ladder: [1d, permanent]
                    ladder-expiry: 30d
                    step-actions:
                      2: [say hi]
                    step-messages:
                      2: "<red>Bye</red>"
                """);

        Assertions.assertThat(read.templates()).isEmpty();
        Assertions.assertThat(read.errors()).extracting(ConfigIssue::path)
                .containsExactlyInAnyOrder("templates.old.ladder", "templates.old.ladder-expiry",
                        "templates.old.step-actions", "templates.old.step-messages");
        Assertions.assertThat(read.errors()).extracting(ConfigIssue::message)
                .anySatisfy(message -> Assertions.assertThat(message)
                        .contains("type: ladder").contains("steps"))
                .anySatisfy(message -> Assertions.assertThat(message).contains("'expiry'"));
    }

    @Test
    void eachShapeKeepsToItsKeys() throws Exception {
        final TemplateFiles.Read read = this.read(
                """
                templates:
                  elimination:
                    type: ban
                    reason: Out of hearts
                    duration: 3d
                    scope: category:lifesteal
                    ip: true
                  quiet:
                    type: mute
                    reason: Spam
                    shadow: true
                  untyped:
                    reason: Reason
                  wrong:
                    type: jail
                    reason: Reason
                  loud:
                    type: ban
                    reason: Reason
                    shadow: true
                  stepped:
                    type: ban
                    reason: Reason
                    steps: [ban 1d]
                    expiry: 1d
                    address-shared: true
                  timed-kick:
                    type: kick
                    reason: Reason
                    duration: 1d
                  timed-ladder:
                    type: ladder
                    reason: Reason
                    duration: 1d
                    steps: [kick]
                  empty-ladder:
                    type: ladder
                    reason: Reason
                  loud-ladder:
                    type: ladder
                    reason: Reason
                    shadow: true
                    steps: [ban 1d]
                  here:
                    type: ban
                    reason: Reason
                    scope: local
                    strictness: very
                """);

        Assertions.assertThat(read.templates()).extracting(TemplateInfo::name)
                .containsExactly("elimination", "quiet");
        final TemplateInfo elimination = read.templates().getFirst();
        Assertions.assertThat(TemplateKind.of(elimination)).isEqualTo(TemplateKind.BAN);
        Assertions.assertThat(elimination.scope()).contains(CategoryScope.of("lifesteal"));
        Assertions.assertThat(elimination.ip()).isTrue();
        Assertions.assertThat(read.templates().get(1).shadow()).isTrue();
        Assertions.assertThat(read.errors()).extracting(ConfigIssue::path)
                .containsExactlyInAnyOrder("templates.untyped.type", "templates.wrong.type",
                        "templates.loud.shadow", "templates.stepped.steps",
                        "templates.stepped.expiry", "templates.stepped.address-shared",
                        "templates.timed-kick.duration", "templates.timed-ladder.duration",
                        "templates.empty-ladder.steps", "templates.loud-ladder.shadow",
                        "templates.here.scope", "templates.here.strictness");
    }

    @Test
    void displayIsDialogOnlyWhereTheTemplateTellsAnOnlinePlayer() throws Exception {
        final TemplateFiles.Read read = this.read(
                """
                templates:
                  quiet:
                    type: mute
                    reason: Spam
                    display: dialog
                  caution:
                    type: warn
                    reason: Language
                    display: Dialog
                  conduct:
                    type: ladder
                    reason: Conduct
                    steps: [kick, warn, ban 1d]
                    display: dialog
                  plain:
                    type: ban
                    reason: Reason
                    display: chat
                  banned:
                    type: ban
                    reason: Reason
                    display: dialog
                  kicked:
                    type: kick
                    reason: Reason
                    display: dialog
                  hushed:
                    type: mute
                    reason: Reason
                    shadow: true
                    display: dialog
                  bans-only:
                    type: ladder
                    reason: Reason
                    steps: [kick, ban 1d]
                    display: dialog
                  popup:
                    type: mute
                    reason: Reason
                    display: popup
                """);

        Assertions.assertThat(read.templates()).extracting(TemplateInfo::name,
                        TemplateInfo::display)
                .containsExactly(Assertions.tuple("quiet", TemplateDisplay.DIALOG),
                        Assertions.tuple("caution", TemplateDisplay.DIALOG),
                        Assertions.tuple("conduct", TemplateDisplay.DIALOG),
                        Assertions.tuple("plain", TemplateDisplay.CHAT));
        Assertions.assertThat(read.errors()).extracting(ConfigIssue::path, ConfigIssue::message)
                .containsExactlyInAnyOrder(
                        Assertions.tuple("templates.banned.display",
                                "only a mute or a warning is shown as a dialog"),
                        Assertions.tuple("templates.kicked.display",
                                "only a mute or a warning is shown as a dialog"),
                        Assertions.tuple("templates.hushed.display",
                                "a shadow mute shows the player nothing"),
                        Assertions.tuple("templates.bans-only.display",
                                "needs a mute or warn step"),
                        Assertions.tuple("templates.popup.display",
                                "\"popup\" is not a display (chat or dialog)"));
        // The default is left out of the tree, a dialog is written after the message.
        Assertions.assertThat(TemplateFiles.tree(read.templates().get(3)))
                .doesNotContainKey("display");
        Assertions.assertThat(TemplateFiles.tree(ladder())
                .keySet()).endsWith("message", "display");
        Assertions.assertThat(TemplateSummaries.summary(read.templates().getFirst()))
                .endsWith(" display=dialog");
    }

    @Test
    void templateDurationsAreWholeSeconds() throws Exception {
        final TemplateFiles.Read read = this.read(
                """
                templates:
                  second:
                    type: mute
                    reason: Reason
                    duration: 1s
                  half:
                    type: mute
                    reason: Reason
                    duration: 500ms
                  fraction:
                    type: ban
                    reason: Reason
                    duration: 1500ms
                  step:
                    type: ladder
                    reason: Reason
                    steps: [mute 90500ms]
                  expiry:
                    type: ladder
                    reason: Reason
                    steps: [kick]
                    expiry: 1500ms
                """);

        Assertions.assertThat(read.templates()).extracting(TemplateInfo::name)
                .containsExactly("second");
        Assertions.assertThat(read.errors()).extracting(ConfigIssue::path, ConfigIssue::message)
                .containsExactlyInAnyOrder(
                        Assertions.tuple("templates.half.duration", "\"500ms\" is under one"
                                + " second: a template's durations are whole seconds"),
                        Assertions.tuple("templates.fraction.duration", "\"1500ms\" is not"
                                + " whole seconds: a template's durations are whole seconds"),
                        Assertions.tuple("templates.step.steps[0]", "\"90500ms\" is not"
                                + " whole seconds: a template's durations are whole seconds"),
                        Assertions.tuple("templates.expiry.expiry", "must be whole seconds"));
        Assertions.assertThatThrownBy(() -> TemplateBuilder.ladder("x").reason("R")
                        .steps(List.of(TemplateStep.of(PunishmentType.KICK,
                                TemplateDuration.open())))
                        .expiry(Duration.ofMillis(500)).build())
                .hasMessageContaining("expiry");
    }

    @Test
    void variantsAreReadWrittenAndReadBack() throws Exception {
        Files.writeString(this.dir.resolve(TemplateFiles.FILE),
                """
                templates:
                  spam: {type: mute, reason: Spamming, duration: 1h, groups: {chat: 1}}
                variants:
                  server:skywars:
                    spam: {type: mute, reason: Spamming in Skywars, duration: 1d}
                  category:games:
                    spam: {type: mute, reason: Games, groups: {chat: 1}}
                  global:
                    spam: {type: mute, reason: Again}
                  nowhere:
                    spam: {type: mute, reason: Lost}
                """);
        final TemplateFiles.Read read = new TemplateFiles(this.dir).read();

        Assertions.assertThat(read.errors()).extracting(ConfigIssue::path).containsExactly(
                "variants.category:games.spam.groups", "variants.global",
                "variants.nowhere");
        final TemplateInfo variant = TemplateBuilder.preset("spam", PunishmentType.MUTE)
                .reason("Spamming in Skywars")
                .duration(TemplateDuration.fixed(Duration.ofDays(1)))
                .variant(ServerScope.of("skywars")).build();
        Assertions.assertThat(read.templates()).hasSize(2).contains(variant);

        final Path file = new TemplateFiles(this.dir).export(read.templates(),
                Instant.parse("2026-11-26T10:00:00Z"));
        Assertions.assertThat(Files.readString(file)).contains("variants:", "server:skywars:");
        Files.copy(file, this.dir.resolve(TemplateFiles.FILE),
                StandardCopyOption.REPLACE_EXISTING);
        Assertions.assertThat(new TemplateFiles(this.dir).read().templates())
                .containsExactlyInAnyOrderElementsOf(read.templates());

        // The editor's JSON definition is the same tree, with the variant from the item.
        final Gson gson = new Gson();
        final Object json = gson.fromJson(gson.toJson(TemplateFiles.tree(variant)),
                Object.class);
        Assertions.assertThat(TemplateFiles.definition("spam", json, "templates[0].definition",
                ServerScope.of("skywars")).templates()).containsExactly(variant);
        Assertions.assertThat(TemplateFiles.definition("spam", Map.of("type", "mute",
                "reason", "R", "groups", Map.of("chat", 1)), "templates[0].definition",
                ServerScope.of("skywars")).errors()).extracting(ConfigIssue::path)
                .containsExactly("templates[0].definition.groups");
    }

    @Test
    void exportsReadBackTheSame() throws Exception {
        final TemplateInfo ladder = ladder();
        final TemplateInfo preset = preset();

        final Path file = new TemplateFiles(this.dir).export(List.of(ladder, preset),
                Instant.parse("2026-11-24T10:00:00Z"));

        Assertions.assertThat(file.getFileName().toString())
                .isEqualTo("templates-20261124-100000.yml");
        Files.copy(file, this.dir.resolve(TemplateFiles.FILE));
        final TemplateFiles.Read read = new TemplateFiles(this.dir).read();
        Assertions.assertThat(read.errors()).isEmpty();
        Assertions.assertThat(read.templates()).containsExactly(ladder(), preset());
        Assertions.assertThat(TemplateSummaries.summary(read.templates().getFirst()))
                .isEqualTo(TemplateSummaries.summary(ladder));
    }

    @Test
    void theJsonDefinitionIsTheSameTree() {
        for (final TemplateInfo definition : List.of(ladder(), preset())) {
            final Map<String, Object> tree = TemplateFiles.tree(definition);
            final Gson gson = new Gson();
            final Object json = gson.fromJson(gson.toJson(tree), Object.class);

            final TemplateFiles.Read read = TemplateFiles.definition(definition.name(), json,
                    "templates[0].definition", null);

            Assertions.assertThat(read.errors()).isEmpty();
            Assertions.assertThat(read.templates()).containsExactly(definition);
        }
        Assertions.assertThat(TemplateFiles.tree(ladder()))
                .containsKeys("type", "steps", "expiry", "address-shared")
                .doesNotContainKey("duration");
        Assertions.assertThat(TemplateFiles.tree(ladder())
                .get("steps")).asInstanceOf(InstanceOfAssertFactories.LIST).containsExactly("warn",
                        Map.of("step", "kick", "message", "<red>Kicked</red>"), "mute 1d",
                        Map.of("step", "none", "actions", List.of("say {victim} is on probation")),
                        Map.of("step", "warn 30d", "reason", "Last warning",
                                "actions", List.of("say {victim} {step}")),
                        "ban permanent");
        Assertions.assertThat(TemplateFiles.definition("x", Map.of("type", "ban",
                "reason", "R", "steps", List.of("ban 1d")), "templates[2].definition", null)
                .errors()).extracting(ConfigIssue::path)
                .containsExactly("templates[2].definition.steps");
    }

    @Test
    void stepTextsParse() {
        Assertions.assertThat(List.of("warn", "kick", "none", "mute 1h", "ban permanent",
                "Mute 1H", "warn 30d")).extracting(text -> {
                    final TemplateSteps.Parsed parsed = TemplateSteps.parse(text);
                    return parsed.type() + " " + TemplateSteps.format(parsed.duration())
                            .orElse("open") + " " + parsed.error();
                }).containsExactly("WARN open null", "KICK open null", "null open null",
                        "MUTE 1h null", "BAN permanent null", "MUTE 1h null", "WARN 30d null");
        Assertions.assertThat(List.of("7d", "permanent", "kick 1h", "ban", "mute", "mute soon",
                        "ban 1d 2d", "jail 1d", "none 1d", ""))
                .allSatisfy(text -> Assertions.assertThat(TemplateSteps.parse(text).error())
                        .as(text).isNotNull());
    }
}
