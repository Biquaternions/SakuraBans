package net.serlith.sakurabans.core;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.serlith.sakurabans.api.Capability;
import net.serlith.sakurabans.api.imports.ImportOptions;
import net.serlith.sakurabans.core.admin.SakuraStatsService;
import net.serlith.sakurabans.core.audit.SakuraAuditService;
import net.serlith.sakurabans.core.enforce.SakuraMessageFormatter;
import net.serlith.sakurabans.core.integration.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.player.SakuraPlayerService;
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
                        this.punishments, this.clock), this.playerData);
    }

    @Test
    void unfinishedServicesSayWhenTheyArrive() {
        final SakuraApi api = this.api();

        Assertions.assertThat(api.imports().importPunishments(List.of(),
                        ImportOptions.builder().build(), CONSOLE))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(UnsupportedOperationException.class)
                .withMessageContaining("S38");
        Assertions.assertThat(api.imports()).isEqualTo(api.imports()).hasToString(
                "Unsupported ImportService");
    }

    @Test
    void onlyFinishedCapabilitiesAreListed() {
        Assertions.assertThat(this.api().capabilities())
                .containsExactlyInAnyOrder(Capability.SHADOW_MUTE, Capability.CURSORS,
                        Capability.KINDS, Capability.EVIDENCE,
                        Capability.VOID, Capability.AUDIT, Capability.ROLLBACK,
                        Capability.STATS, Capability.PLAYER_DATA);
        Assertions.assertThat(this.api().punishments()).isSameAs(this.service);
    }
}
