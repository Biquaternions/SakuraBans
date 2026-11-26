package net.serlith.sakurabans.core.configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.model.SakuraTemplateInfo;
import net.serlith.sakurabans.core.model.TemplateDefinition;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code templates.yml}: the shipped seed, errors with paths, and exports (plan S31). */
class TemplateFilesTest {

    @TempDir
    Path dir;

    private TemplateFiles.Read read(final String yaml) throws Exception {
        Files.writeString(this.dir.resolve(TemplateFiles.FILE), yaml);
        return new TemplateFiles(this.dir).read();
    }

    @Test
    void theShippedFileIsValid() {
        final TemplateFiles.Read read = new TemplateFiles(this.dir).read();

        Assertions.assertThat(read.errors()).isEmpty();
        Assertions.assertThat(read.templates()).extracting(TemplateDefinition::name)
                .contains("hacking", "spam");
        Assertions.assertThat(this.dir.resolve(TemplateFiles.FILE)).exists();
    }

    @Test
    void everyProblemIsReportedWithItsPath() throws Exception {
        final TemplateFiles.Read read = this.read(
                """
                templates:
                  good:
                    type: mute
                    reason: Reason
                    ladder: [1h, permanent]
                    step-actions:
                      2: ["say {victim}"]
                  bad:
                    type: ban
                    reason: Reason
                    ladder: [1h, soon]
                    ladder-expiry: later
                    address-shared: maybe
                    groups: {cheating: lots}
                    step-actions:
                      9: [say hi]
                    colour: red
                  "Not A Name!":
                    type: ban
                    reason: Reason
                    ladder: [1d]
                """);

        Assertions.assertThat(read.templates()).extracting(TemplateDefinition::name)
                .containsExactly("good");
        Assertions.assertThat(read.templates().getFirst().ladder().get(1).actions())
                .containsExactly("say {victim}");
        Assertions.assertThat(read.errors()).extracting(ConfigIssue::path).contains(
                "templates.bad.ladder[1]", "templates.bad.ladder-expiry",
                "templates.bad.address-shared", "templates.bad.groups.cheating",
                "templates.bad.step-actions.9", "templates.bad.colour",
                "templates.Not A Name!.name");
    }

    @Test
    void theTypeAndItsModifiersAreChecked() throws Exception {
        final TemplateFiles.Read read = this.read(
                """
                templates:
                  elimination:
                    type: ban
                    reason: Out of hearts
                    ladder: [3d]
                    scope: category:lifesteal
                    ip: true
                  quiet:
                    type: mute
                    reason: Spam
                    ladder: [1h]
                    shadow: true
                  untyped:
                    reason: Reason
                    ladder: [1d]
                  wrong:
                    type: jail
                    reason: Reason
                    ladder: [1d]
                  loud:
                    type: ban
                    reason: Reason
                    ladder: [1d]
                    shadow: true
                  here:
                    type: ban
                    reason: Reason
                    ladder: [1d]
                    scope: local
                    strictness: very
                """);

        Assertions.assertThat(read.templates()).extracting(TemplateDefinition::name)
                .containsExactly("elimination", "quiet");
        final TemplateDefinition elimination = read.templates().getFirst();
        Assertions.assertThat(elimination.type()).isEqualTo(PunishmentType.BAN);
        Assertions.assertThat(elimination.scope()).isEqualTo(CategoryScope.of("lifesteal"));
        Assertions.assertThat(elimination.ip()).isTrue();
        Assertions.assertThat(read.templates().get(1).shadow()).isTrue();
        Assertions.assertThat(read.errors()).extracting(ConfigIssue::path)
                .containsExactlyInAnyOrder("templates.untyped.type", "templates.wrong.type",
                        "templates.loud.shadow", "templates.here.scope",
                        "templates.here.strictness");
    }

    @Test
    void exportsReadBackTheSame() throws Exception {
        final TemplateDefinition definition = TemplateDefinition.builder("hacking",
                        PunishmentType.MUTE)
                .ip(true)
                .shadow(true)
                .scope(CategoryScope.of("survival"))
                .reason("Hacking")
                .ladder(List.of(
                        TemplateStep.of(Duration.ofDays(1),
                                List.of("say {victim}")),
                        TemplateStep.of(null)))
                .ladderExpiry(Duration.ofDays(90))
                .addressShared(true)
                .permissionRequired(true)
                .actions(List.of("say once"))
                .groups(Map.of("cheating", 2))
                .build();
        final SakuraTemplateInfo info = SakuraTemplateInfo.of(definition, 4);

        final Path file = new TemplateFiles(this.dir).export(List.of(info),
                Instant.parse("2026-11-24T10:00:00Z"));

        Assertions.assertThat(file.getFileName().toString())
                .isEqualTo("templates-20261124-100000.yml");
        Files.copy(file, this.dir.resolve(TemplateFiles.FILE));
        final TemplateFiles.Read read = new TemplateFiles(this.dir).read();
        Assertions.assertThat(read.errors()).isEmpty();
        Assertions.assertThat(read.templates()).containsExactly(definition);
        Assertions.assertThat(SakuraTemplateInfo.of(read.templates().getFirst(), 4).summary())
                .isEqualTo(info.summary());
    }
}
