package net.serlith.sakurabans.core.enforce;

import java.util.List;
import java.util.Set;
import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * S68: a proxy's {@code servers-without-login-check}. "gone" isn't a backend, so connecting
 * there fails and the next server is tried; network-kick is ignored on a proxy.
 */
class ProxyLoginCheckTest extends ServiceTest {

    ProxyLoginCheckTest() {
        super(new FakePlatform(Set.of("lobby", "limbo", "survival"), List.of("lobby")));
    }

    @Override
    protected boolean proxy() {
        return true;
    }

    @Override
    protected String configYaml() {
        return "enforcement:\n  servers-without-login-check: [gone, limbo]\n"
                + "  enforce-server-switch: false\n  network-kick: true\n";
    }

    private LoginAttempt steve() {
        return new LoginAttempt(STEVE, "Steve", this.home, null);
    }

    private void ban() {
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).reason("griefing").build(), PunishOptions.defaults()).join();
    }

    @Test
    void bannedPlayersGetInButOnlyToTheListedServers() {
        final FakeOnlinePlayer staff = this.platform.join(ALEX, "Alex", this.home, null, "lobby")
                .grant(Permissions.NOTIFY_ATTEMPT_JOIN);
        this.ban();
        final int before = staff.messages().size();

        Assertions.assertThat(this.loginGate.check(this.steve()).join())
                .isInstanceOfSatisfying(LoginDecision.Allowed.class, allowed ->
                        Assertions.assertThat(allowed.notice()).isPresent());
        // The first server: sent on to the first listed one, and nobody is told.
        Assertions.assertThat(this.loginGate.checkServer(this.steve(), "lobby", true).join())
                .isInstanceOfSatisfying(LoginDecision.Denied.class, denied -> {
                    Assertions.assertThat(denied.denial())
                            .isEqualTo(PlayerDenyEvent.Denial.SERVER_SWITCH_BANNED);
                    Assertions.assertThat(denied.fallback()).contains("gone");
                });
        Assertions.assertThat(this.loginGate.checkServer(this.steve(), "limbo", true).join())
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(staff.messages()).hasSize(before);
        // Checked although enforce-server-switch is false: the list keeps them there.
        Assertions.assertThat(this.loginGate.checkServer(this.steve(), "survival", false).join())
                .isInstanceOf(LoginDecision.Denied.class);
        eventually(() -> staff.messages().size() > before);
    }

    @Test
    void liveBansMoveThemToTheFirstListedServerThatConnects() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home, null,
                "survival");

        this.ban();

        eventually(() -> steve.currentServer().filter("limbo"::equals).isPresent());
        Assertions.assertThat(steve.disconnectReason()).isEmpty();
        Assertions.assertThat(steve.networkKicked()).as("ignored on a proxy").isFalse();
        Assertions.assertThat(steve.messages()).anySatisfy(message ->
                Assertions.assertThat(plain(message)).contains("You are banned from this network"));
    }

    @Test
    void playersOnListedServersStayThere() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home, null,
                "limbo");

        this.ban();

        eventually(() -> !steve.messages().isEmpty());
        Assertions.assertThat(steve.currentServer()).contains("limbo");
        Assertions.assertThat(steve.disconnectReason()).isEmpty();
    }
}
