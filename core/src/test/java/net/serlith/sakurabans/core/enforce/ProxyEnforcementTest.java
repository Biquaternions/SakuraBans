package net.serlith.sakurabans.core.enforce;

import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.platform.PlatformType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** A proxy: server bans apply to the players on that backend; mutes are left to it (D4). */
class ProxyEnforcementTest extends ServiceTest {

    @Override
    protected PlatformType platformType() {
        return PlatformType.VELOCITY;
    }

    private void punish(final PunishmentType type, final String server) {
        this.service.punish(DraftPunishment.builder(type, PlayerVictim.of(STEVE), MODERATOR)
                .scope(ServerScope.of(server)).build(), PunishOptions.defaults()).join();
    }

    private LoginAttempt steve() {
        return new LoginAttempt(STEVE, "Steve", this.home, null);
    }

    @Test
    void serverBansBlockSwitchingThereButNotJoining() {
        this.punish(PunishmentType.BAN, "survival");

        Assertions.assertThat(this.loginGate.check(this.steve()).join())
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.loginGate.checkServer(this.steve(), "survival").join())
                .isInstanceOfSatisfying(LoginDecision.Denied.class, denied ->
                        Assertions.assertThat(denied.denial())
                                .isEqualTo(PlayerDenyEvent.Denial.SERVER_SWITCH_BANNED));
        Assertions.assertThat(this.loginGate.checkServer(this.steve(), "lobby").join())
                .isInstanceOf(LoginDecision.Allowed.class);
    }

    @Test
    void proxiesLeaveMutesToTheBackends() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home, null,
                "survival");
        this.loginGate.check(this.steve()).join();

        this.punish(PunishmentType.MUTE, "survival");

        Assertions.assertThat(this.mutes.loaded(STEVE)).isFalse();
        Assertions.assertThat(steve.messages()).isEmpty();
    }

    @Test
    void serverBansKickOnlyPlayersOnThatServer() {
        final FakeOnlinePlayer onSurvival = this.platform.join(STEVE, "Steve", this.home, null,
                "survival");
        this.punish(PunishmentType.BAN, "creative");
        Assertions.assertThat(onSurvival.disconnectReason()).isEmpty();

        this.punish(PunishmentType.KICK, "survival");
        Assertions.assertThat(onSurvival.disconnectReason()).isPresent();
    }
}
