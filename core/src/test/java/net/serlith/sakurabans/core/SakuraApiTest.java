package net.serlith.sakurabans.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.core.enforce.SakuraMessageFormatter;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.player.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.player.SakuraPlayerService;
import net.serlith.sakurabans.core.util.BuildConstants;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SakuraApiTest extends ServiceTest {

    private SakuraApi api() {
        return new SakuraApi(this.service, new SakuraPlayerService(this.players, this.clock,
                        (uuid, strictness) -> CompletableFuture.completedFuture(List.of())),
                this.scopes, this.bus, new SakuraIntegrationRegistry(), this.templates,
                this.allowList, new SakuraMessageFormatter(this.enforcementMessages,
                        new Formats(this.messages, this.config)),
                this.playerData);
    }

    @Test
    void theServicesAreTheRealOnes() {
        Assertions.assertThat(this.api().apiVersion()).isEqualTo(BuildConstants.VERSION);
        Assertions.assertThat(this.api().punishments()).isSameAs(this.service);
        Assertions.assertThat(this.api().playerData()).isSameAs(this.playerData);
    }
}
