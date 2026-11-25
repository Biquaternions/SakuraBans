package net.serlith.sakurabans.core.command.module;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.template.TemplateDefinition;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
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
        this.templates.create(TemplateDefinition.builder("hacking", "ban").reason("Hacking")
                .ladder(List.of(TemplateStep.of(Duration.ofDays(7)), TemplateStep.of(null)))
                .build(), CONSOLE).join();
        this.templates.create(TemplateDefinition.builder("secret", "ban").reason("Reason")
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
                Assertions.assertThat(line).contains("hacking", "ban", "7d → permanent"));

        Files.writeString(this.dir.resolve("templates.yml"),
                """
                templates:
                  spam:
                    kind: mute
                    reason: Reason
                    ladder: [1h]
                """);
        this.run(this.console, "sakurabans templates import");
        Assertions.assertThat(this.console.last()).contains("--confirm");
        Assertions.assertThat(this.templates.template("spam")).isEmpty();
        this.run(this.console, "sakurabans templates import --confirm");
        Assertions.assertThat(this.console.last()).contains("1 new, 0 updated");
        Assertions.assertThat(this.templates.template("spam")).isPresent();

        this.run(this.console, "sakurabans templates export");
        Assertions.assertThat(this.console.last()).contains("Exported 3 templates to exports/");
        try (var files = Files.list(this.dir.resolve("exports"))) {
            Assertions.assertThat(files).hasSize(1);
        }
    }
}
