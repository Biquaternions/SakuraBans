package net.serlith.sakurabans.core;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.Capability;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.imports.ImportOptions;
import net.serlith.sakurabans.core.admin.SakuraStatsService;
import net.serlith.sakurabans.core.audit.SakuraAuditService;
import net.serlith.sakurabans.core.enforce.SakuraMessageFormatter;
import net.serlith.sakurabans.core.importer.ImportSources;
import net.serlith.sakurabans.core.importer.SakuraImportService;
import net.serlith.sakurabans.core.integration.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.player.SakuraPlayerService;
import net.serlith.sakurabans.core.punish.ActionCheck;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.storage.repository.StatsRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SakuraApiTest extends ServiceTest {

    private SakuraApi api() {
        return SakuraApi.create(this.service, new SakuraPlayerService(this.players, this.clock,
                        (uuid, strictness) -> CompletableFuture.completedFuture(List.of())),
                this.scopes, this.bus, new SakuraIntegrationRegistry(), this.allowList,
                this.templates, this.kinds, this.authorization,
                new SakuraAuditService(new AuditRepository(this.storage)),
                new SakuraMessageFormatter(this.enforcementMessages,
                        new Formats(this.messages, this.config)),
                new SakuraStatsService(new StatsRepository(this.storage, this.allowList),
                        this.punishments, this.clock), this.playerData,
                new SakuraImportService(this.importJobs(ImportSources.standard()),
                        ActionCheck.ALLOW_ALL));
    }

    @Test
    void importsAreTheRealService() {
        Assertions.assertThat(this.api().imports().importPunishments(List.of(),
                        ImportOptions.builder().build(), CONSOLE))
                .succeedsWithin(Duration.ofSeconds(5))
                .isInstanceOf(AdminResult.Ok.class);
    }

    @Test
    void onlyFinishedCapabilitiesAreListed() {
        Assertions.assertThat(this.api().capabilities())
                .containsExactlyInAnyOrder(Capability.SHADOW_MUTE, Capability.CURSORS,
                        Capability.KINDS, Capability.EVIDENCE,
                        Capability.VOID, Capability.AUDIT, Capability.ROLLBACK,
                        Capability.STATS, Capability.PLAYER_DATA, Capability.IMPORT);
        Assertions.assertThat(this.api().punishments()).isSameAs(this.service);
    }
}
