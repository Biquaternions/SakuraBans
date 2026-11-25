package net.serlith.sakurabans.core.enforce;

import java.net.InetAddress;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** The allow list at login (plan S28). */
class AltPolicyGateTest extends ServiceTest {

    private LoginDecision join(final UUID uuid, final String name) {
        return this.loginGate.check(new LoginAttempt(uuid, name, this.home, null)).join();
    }

    @Test
    void allowListedAddressesSkipAddressBans() throws Exception {
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                AddressVictim.of(this.home), MODERATOR).build(), PunishOptions.defaults()).join();
        Assertions.assertThat(this.join(STEVE, "Steve")).isInstanceOf(LoginDecision.Denied.class);

        this.allowList.allow(InetAddress.getByName("203.0.113.7"), MODERATOR, null).join();

        Assertions.assertThat(this.join(STEVE, "Steve")).isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.published).anySatisfy(packet ->
                Assertions.assertThat(packet).isInstanceOf(
                        SyncPacket.AllowListChangedPacket.class));
    }
}
