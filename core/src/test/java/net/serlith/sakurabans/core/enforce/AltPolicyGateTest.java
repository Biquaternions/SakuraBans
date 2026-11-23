package net.serlith.sakurabans.core.enforce;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;
import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** The connection limiter and the allow list at login (plan S28). */
class AltPolicyGateTest extends ServiceTest {

    private static final UUID BOB = UUID.fromString("2b1c9e5a-7f3d-4c8e-9a6b-1d2e3f4a5b6c");

    @Override
    protected String configYaml() {
        return "alts:\n  connection-limiter:\n    enabled: true\n    limit: 2\n    window: 10m\n";
    }

    private LoginDecision join(final UUID uuid, final String name) {
        return this.loginGate.check(new LoginAttempt(uuid, name, this.home, null)).join();
    }

    private void seen(final UUID uuid, final String name) {
        this.players.recordConnection(uuid, name, this.now.get(), this.home, null).join();
    }

    @Test
    void tooManyAccountsOnOneAddressAreTurnedAway() {
        this.seen(ALEX, "Alex");
        this.seen(BOB, "Bob");

        Assertions.assertThat(this.join(STEVE, "Steve"))
                .isInstanceOfSatisfying(LoginDecision.Denied.class, denied ->
                        Assertions.assertThat(denied.denial())
                                .isEqualTo(PlayerDenyEvent.Denial.LOGIN_CONNECTION_LIMIT));
        Assertions.assertThat(this.join(ALEX, "Alex"))
                .as("an account already seen there isn't limited")
                .isInstanceOf(LoginDecision.Allowed.class);

        this.now.set(this.now.get().plus(Duration.ofMinutes(11)));
        Assertions.assertThat(this.join(STEVE, "Steve")).as("outside the window")
                .isInstanceOf(LoginDecision.Allowed.class);
    }

    @Test
    void allowListedAddressesAreNotLimited() {
        this.seen(ALEX, "Alex");
        this.seen(BOB, "Bob");
        this.allowList.allow(this.home, MODERATOR, "school").join();

        Assertions.assertThat(this.join(STEVE, "Steve")).isInstanceOf(LoginDecision.Allowed.class);
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
