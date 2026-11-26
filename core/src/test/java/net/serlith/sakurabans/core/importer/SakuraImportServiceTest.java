package net.serlith.sakurabans.core.importer;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.imports.ImportOptions;
import net.serlith.sakurabans.api.imports.ImportReport;
import net.serlith.sakurabans.api.imports.ImportedPunishment;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.storage.Lease;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** {@code ImportService} of the API (plan S38). */
class SakuraImportServiceTest extends ImportTest {

    private SakuraImportService service() {
        return new SakuraImportService(this.jobs(),
                (actor, action) -> this.actionCheck.check(actor, action));
    }

    private ImportedPunishment mute(final String id) {
        return ImportedPunishment.builder(PunishmentType.MUTE, PlayerVictim.of(STEVE),
                        PlayerOperator.of(MOD, "Mod"), this.now.get().minus(Duration.ofDays(1)))
                .expiresAt(this.now.get().plus(Duration.ofDays(1)))
                .reason("spam")
                .externalId("hydra:mutes:" + id)
                .build();
    }

    @Test
    void importsAndReports() {
        final AdminResult<ImportReport> result = this.service().importPunishments(
                List.of(this.mute("1")), ImportOptions.builder().source("import:hydra").build(),
                MODERATOR).join();

        Assertions.assertThat(result).isInstanceOfSatisfying(AdminResult.Ok.class, ok ->
                Assertions.assertThat(((ImportReport) ok.value()).imported()).isEqualTo(1));
        Assertions.assertThat(this.count(SakuraTables.ACTIVE_PUNISHMENTS)).isEqualTo(1);
        Assertions.assertThat(this.count(SakuraTables.AUDIT_LOG,
                SakuraTables.AUDIT_LOG.SUBJECT_ID.eq("import:hydra"))).isEqualTo(1);
    }

    @Test
    void theActorNeedsTheImportPermission() {
        this.actionCheck = (actor, action) -> CompletableFuture.completedFuture(
                action.equals(Action.Manage.of(Action.Manage.Area.IMPORT))
                        ? Decision.deny(Decision.Denial.NO_PERMISSION, null)
                        : Decision.allow());

        Assertions.assertThat(this.service().importPunishments(List.of(this.mute("1")),
                        ImportOptions.builder().build(), MODERATOR).join())
                .isInstanceOf(AdminResult.Forbidden.class);
        Assertions.assertThat(this.count(SakuraTables.PUNISHMENTS)).isZero();
    }

    @Test
    void sourcesThatAreNoActorSourceAreRefused() {
        Assertions.assertThat(this.service().importPunishments(List.of(this.mute("1")),
                        ImportOptions.builder().source("Not A Source").build(), MODERATOR)
                        .join())
                .isInstanceOf(AdminResult.ValidationFailed.class);
    }

    @Test
    void onlyOneImportRunsOnTheNetwork() {
        new Lease(this.storage, UUID.randomUUID(), this.clock)
                .tryAcquire(ImportJobs.LEASE, Duration.ofMinutes(5)).join();

        Assertions.assertThat(this.service().importPunishments(List.of(this.mute("1")),
                        ImportOptions.builder().build(), MODERATOR).join())
                .isInstanceOfSatisfying(AdminResult.ValidationFailed.class, failed ->
                        Assertions.assertThat(failed.errors().toString())
                                .contains("another import is running"));
    }
}
