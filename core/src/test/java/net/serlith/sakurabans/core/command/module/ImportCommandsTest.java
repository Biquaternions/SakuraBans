package net.serlith.sakurabans.core.command.module;

import java.time.Duration;
import java.util.List;
import net.serlith.sakurabans.api.imports.ImportedPunishment;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.importer.FakeSource;
import net.serlith.sakurabans.core.importer.ImportSources;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** /sb import (plan S38). */
class ImportCommandsTest extends CommandTest {

    private final FakeSource source = new FakeSource();

    @Override
    protected ImportSources importSources() {
        for (final String id : List.of("1", "2")) {
            this.source.row(reader -> reader.punishment(ImportedPunishment.builder(
                            PunishmentType.BAN, PlayerVictim.of(STEVE), ConsoleOperator.INSTANCE,
                            this.now.get().minus(Duration.ofDays(1)))
                    .reason("&cbye")
                    .externalId(FakeSource.ID + ":bans:" + id)
                    .build()));
        }
        return new ImportSources(List.of(this.source));
    }

    @Test
    void unknownSourcesListTheKnownOnes() {
        this.run(this.console, "sb import nope");

        Assertions.assertThat(this.console.last())
                .contains("There is no import source nope", "self, fake");
    }

    @Test
    void anImportShowsWhatItReadsAndRunsOnceConfirmed() {
        this.run(this.console, "sb import fake");
        Assertions.assertThat(this.console.last()).contains("reads rows 2", "--confirm");
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();

        this.run(this.console, "sb import fake --confirm");

        Assertions.assertThat(this.console.texts()).anySatisfy(text ->
                Assertions.assertThat(text).contains("Importing from fake"));
        eventually(() -> this.console.last().contains("Import from fake done"));
        Assertions.assertThat(this.console.last()).contains("2 punishments imported",
                "0 already there");
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isEqualTo(2);
    }

    @Test
    void dryRunsNeedNoConfirmationAndStoreNothing() {
        this.run(this.console, "sb import fake --dry-run");

        eventually(() -> this.console.last().contains("Dry run of fake"));
        Assertions.assertThat(this.console.last()).contains("2 punishments would be imported");
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
    }

    @Test
    void playersGetTheReportAndTheConsoleGetsCopies() {
        final FakeCommandActor admin = FakeCommandActor.player(MOD, "Mod",
                Permissions.ADMIN_IMPORT);
        this.run(admin, "sb import fake");
        this.run(admin, "sb import fake --confirm");

        eventually(() -> admin.last().contains("Import from fake done"));
        eventually(() -> this.platform.consoleMessages().stream()
                .anyMatch(message -> plain(message).contains("Import from fake done")));
    }

    @Test
    void sourcesWithoutSettingsSayWhatToSet() {
        this.run(this.console, "sb import self --confirm");

        Assertions.assertThat(this.console.last())
                .contains("Set self.jdbc-url in import.yml first.");
    }

    @Test
    void importingNeedsThePermission() {
        final FakeCommandActor player = FakeCommandActor.player(ALEX, "Alex");

        this.run(player, "sb import fake");

        Assertions.assertThat(player.last()).doesNotContain("reads");
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
    }
}
