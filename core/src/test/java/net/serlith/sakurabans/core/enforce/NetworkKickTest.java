package net.serlith.sakurabans.core.enforce;

import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** S68: {@code enforcement.network-kick} on a backend asks for a network-wide disconnect. */
class NetworkKickTest extends ServiceTest {

    @Override
    protected String configYaml() {
        return "enforcement:\n  network-kick: true\n";
    }

    private void punish(final PunishmentType type) {
        this.service.punish(DraftPunishment.builder(type, PlayerVictim.of(STEVE), MODERATOR)
                .build(), PunishOptions.defaults()).join();
    }

    @Test
    void bansDisconnectFromTheNetwork() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);

        this.punish(PunishmentType.BAN);

        eventually(() -> steve.disconnectReason().isPresent());
        Assertions.assertThat(steve.networkKicked()).isTrue();
        Assertions.assertThat(plain(steve.disconnectReason().orElseThrow()))
                .contains("Expiration: permanent");
    }

    @Test
    void kicksStayLocal() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);

        this.punish(PunishmentType.KICK);

        eventually(() -> steve.disconnectReason().isPresent());
        Assertions.assertThat(steve.networkKicked()).isFalse();
    }
}
