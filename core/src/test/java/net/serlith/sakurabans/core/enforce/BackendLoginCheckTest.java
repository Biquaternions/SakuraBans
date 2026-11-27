package net.serlith.sakurabans.core.enforce;

import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.event.SakuraPlayerDenyEvent;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** A backend with {@code enforcement.login-check: false} keeps banned players in. */
class BackendLoginCheckTest extends ServiceTest {

    @Override
    protected String configYaml() {
        return "enforcement:\n  login-check: false\n  network-kick: true\n";
    }

    private void ban() {
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).reason("griefing").build(), PunishOptions.defaults()).join();
    }

    @Test
    void bannedPlayersAreLetInToldAndStaffHearNothing() {
        final FakeOnlinePlayer staff = this.platform.join(ALEX, "Alex", this.home)
                .grant(Permissions.NOTIFY_ATTEMPT_JOIN);
        this.ban();
        final int before = staff.messages().size();

        final LoginDecision decision = this.loginGate.check(
                new LoginAttempt(STEVE, "Steve", this.home, null)).join();

        Assertions.assertThat(decision).isInstanceOfSatisfying(LoginDecision.Allowed.class,
                allowed -> Assertions.assertThat(allowed.notice()).hasValueSatisfying(notice ->
                        Assertions.assertThat(plain(notice))
                                .contains("You are banned from this network", "griefing")));
        Assertions.assertThat(this.events(SakuraPlayerDenyEvent.class)).isEmpty();
        Assertions.assertThat(staff.messages()).as("no tried-to-join notice").hasSize(before);
    }

    @Test
    void bansOnOnlinePlayersTellThemInsteadOfKicking() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);

        this.ban();

        eventually(() -> steve.messages().stream()
                .anyMatch(message -> plain(message).contains("You are banned from this network")));
        Assertions.assertThat(steve.disconnectReason()).isEmpty();
        Assertions.assertThat(steve.networkKicked()).as("login-check false wins").isFalse();
    }

    @Test
    void mutesStillApply() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);
        this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home, null)).join();
        this.service.punish(DraftPunishment.builder(PunishmentType.MUTE, PlayerVictim.of(STEVE),
                MODERATOR).build(), PunishOptions.defaults()).join();

        eventually(() -> this.chatGate.checkChat(steve, "hello") instanceof ChatDecision.Denied);
    }
}
