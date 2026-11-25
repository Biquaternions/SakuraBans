package net.serlith.sakurabans.core.configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
                    kind: mute
                    reason: Reason
                    ladder: [1h, permanent]
                    step-actions:
                      2: ["say {victim}"]
                  bad:
                    kind: ban
                    reason: Reason
                    ladder: [1h, soon]
                    ladder-expiry: later
                    address-shared: maybe
                    groups: {cheating: lots}
                    step-actions:
                      9: [say hi]
                    colour: red
                  "Not A Name!":
                    kind: ban
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
    void exportsReadBackTheSame() throws Exception {
        final TemplateDefinition definition = TemplateDefinition.builder("hacking", "ban")
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
        final SakuraTemplateInfo info = SakuraTemplateInfo.of(definition, PunishmentType.BAN, 4);

        final Path file = new TemplateFiles(this.dir).export(List.of(info),
                Instant.parse("2026-11-24T10:00:00Z"));

        Assertions.assertThat(file.getFileName().toString())
                .isEqualTo("templates-20261124-100000.yml");
        Files.copy(file, this.dir.resolve(TemplateFiles.FILE));
        final TemplateFiles.Read read = new TemplateFiles(this.dir).read();
        Assertions.assertThat(read.errors()).isEmpty();
        Assertions.assertThat(SakuraTemplateInfo.of(read.templates().getFirst(),
                PunishmentType.BAN, 4).summary()).isEqualTo(info.summary());
    }
}
