package net.serlith.sakurabans.core;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.serlith.sakurabans.api.Capability;
import net.serlith.sakurabans.api.audit.AuditQuery;
import net.serlith.sakurabans.core.integration.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.player.SakuraPlayerService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SakuraApiTest extends ServiceTest {

    private SakuraApi api() {
        return SakuraApi.create(this.service, new SakuraPlayerService(this.players, this.clock,
                        (uuid, strictness) -> CompletableFuture.completedFuture(List.of())),
                this.scopes, this.bus, new SakuraIntegrationRegistry(), this.allowList,
                this.templates, this.kinds, this.authorization);
    }

    @Test
    void unfinishedServicesSayWhenTheyArrive() {
        final SakuraApi api = this.api();

        Assertions.assertThat(api.audit().query(
                        AuditQuery.builder().build()))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(UnsupportedOperationException.class)
                .withMessageContaining("S35");
        Assertions.assertThatThrownBy(() -> api.formatter().formatDuration(Duration.ofHours(1),
                        Locale.ROOT))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThat(api.audit()).isEqualTo(api.audit()).hasToString(
                "Unsupported AuditService");
    }

    @Test
    void onlyFinishedCapabilitiesAreListed() {
        Assertions.assertThat(this.api().capabilities())
                .containsExactlyInAnyOrder(Capability.SHADOW_MUTE, Capability.CURSORS,
                        Capability.TEMPLATE_EDITING, Capability.KINDS);
        Assertions.assertThat(this.api().punishments()).isSameAs(this.service);
    }
}
