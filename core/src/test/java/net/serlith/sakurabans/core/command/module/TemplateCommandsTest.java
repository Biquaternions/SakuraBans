package net.serlith.sakurabans.core.command.module;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code --template} and reason shortcuts in punish commands, and /sb templates (plan S31). */
class TemplateCommandsTest extends CommandTest {

    @Override
    protected String configYaml() {
        return
                """
                reasons:
                  shortcuts:
                    griefing: Griefing
                """;
    }

    @BeforeEach
    void fixture() {
        this.known(STEVE, "Steve");
        this.templates.create(TemplateBuilder.preset("hacking", PunishmentType.BAN)
                .reason("Hacking")
                .duration(TemplateDuration.fixed(Duration.ofDays(7)))
                .build(), CONSOLE).join();
        this.templates.create(TemplateBuilder.preset("secret", PunishmentType.BAN)
                .reason("Reason")
                .permissionRequired(true).build(), CONSOLE).join();
    }

    private List<Punishment> all() {
        return this.service.query(PunishmentQuery.builder().build()).join().items();
    }

    @Test
    void templatesDecideDurationAndDefaultReason() {
        this.run(this.console, "ban Steve --template hacking -r caught on camera");

        Assertions.assertThat(this.all()).singleElement().satisfies(ban -> {
            Assertions.assertThat(ban.reason()).isEqualTo("caught on camera");
            Assertions.assertThat(ban.expiresAt()).hasValue(this.now.get()
                    .plus(Duration.ofDays(7)));
            Assertions.assertThat(ban.template()).isPresent();
        });
    }

    @Test
    void wrongTypesPermissionsAndShortcutsAreHandled() {
        this.run(this.console, "mute Steve -t hacking");
        Assertions.assertThat(this.console.last()).contains("hacking is a ban template");

        final FakeCommandActor mod = FakeCommandActor.player(UUID.randomUUID(), "Mod")
                .grant(Permissions.punish(PunishmentType.BAN));
        this.run(mod, "ban Steve --template secret");
        Assertions.assertThat(mod.last()).contains("can't use the template secret");
        mod.grant(Permissions.template("secret"));
        this.run(mod, "ban Steve --template secret");
        Assertions.assertThat(this.all()).hasSize(1);

        this.run(this.console, "warn Steve --template griefing -r near spawn");
        Assertions.assertThat(this.all()).filteredOn(p -> p.type() == PunishmentType.WARN)
                .singleElement().satisfies(warn -> Assertions.assertThat(warn.reason())
                        .isEqualTo("Griefing near spawn"));
    }

    @Test
    void templatesAreListedImportedAndExported() throws Exception {
        this.run(this.console, "sakurabans templates list");
        Assertions.assertThat(this.console.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("hacking", "ban", "7d"));

        Files.writeString(this.dir.resolve("templates.yml"),
                """
                templates:
                  spam:
                    type: mute
                    reason: Reason
                    duration: 1h
                  misconduct:
                    type: ladder
                    reason: Reason
                    steps: [warn, kick, ban 7d]
                """);
        this.run(this.console, "sakurabans templates import");
        Assertions.assertThat(this.console.last()).contains("--confirm");
        Assertions.assertThat(this.templates.template("spam")).isEmpty();
        this.run(this.console, "sakurabans templates import --confirm");
        Assertions.assertThat(this.console.last()).contains("2 new, 0 updated");
        Assertions.assertThat(this.templates.template("spam")).isPresent();
        this.console.clear();
        this.run(this.console, "sakurabans templates list");
        Assertions.assertThat(this.console.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("misconduct", "ladder",
                        "warn → kick → ban 7d"));

        this.run(this.console, "sakurabans templates export");
        Assertions.assertThat(this.console.last()).contains("Exported 4 templates to exports/");
        try (var files = Files.list(this.dir.resolve("exports"))) {
            Assertions.assertThat(files).hasSize(1);
        }
    }

    @Test
    void listImportAndExportTakeScopes() throws Exception {
        this.templates.create(TemplateBuilder.preset("hacking", PunishmentType.BAN)
                .reason("Hacking in Skywars").variant(ServerScope.of("skywars")).build(),
                CONSOLE).join();
        this.run(this.console, "sakurabans templates list");
        Assertions.assertThat(this.console.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("variants: server:skywars"));
        this.console.clear();
        this.run(this.console, "sakurabans templates list server:skywars");
        Assertions.assertThat(this.console.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("variants in server:skywars", "(1)"));
        Assertions.assertThat(this.console.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("hacking", "Hacking in Skywars"));
        this.run(this.console, "sakurabans templates list category:none");
        Assertions.assertThat(this.console.last()).contains("No template has a variant in"
                + " category:none");

        this.run(this.console, "sakurabans templates export server:skywars");
        Assertions.assertThat(this.console.last()).contains("Exported 1 templates");
        this.run(this.console, "sakurabans templates export");
        Assertions.assertThat(this.console.last()).as("none means global")
                .contains("Exported 2 templates");
        this.run(this.console, "sakurabans templates export --all");
        Assertions.assertThat(this.console.last()).contains("Exported 3 templates");

        Files.writeString(this.dir.resolve("templates.yml"),
                """
                templates:
                  spam: {type: mute, reason: Spamming, duration: 1h}
                variants:
                  category:games:
                    spam: {type: mute, reason: Spamming in games}
                """);
        this.run(this.console, "sakurabans templates import category:games");
        this.run(this.console, "sakurabans templates import category:games --confirm");
        Assertions.assertThat(this.console.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("needs one"));
        this.run(this.console, "sakurabans templates import --all");
        this.run(this.console, "sakurabans templates import --all --confirm");
        Assertions.assertThat(this.console.last()).contains("2 new, 0 updated");
        Assertions.assertThat(this.templates.find("spam", CategoryScope.of("games"))
                .orElseThrow().template().reason()).isEqualTo("Spamming in games");
    }
}
