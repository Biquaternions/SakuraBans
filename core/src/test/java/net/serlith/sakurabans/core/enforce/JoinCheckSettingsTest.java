package net.serlith.sakurabans.core.enforce;

import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.concurrent.LifecycleState;
import net.serlith.sakurabans.core.model.LoginAttempt;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** {@code enforcement.deny-banned-joins: false}: another instance turns banned players away. */
class JoinCheckSettingsTest extends ServiceTest {

    @Override
    protected String configYaml() {
        return "enforcement:\n  deny-banned-joins: false\n";
    }

    private LoginAttempt steve() {
        return new LoginAttempt(STEVE, "Steve", this.home, null);
    }

    @Test
    void bannedPlayersGetInButTheirMutesStillLoad() {
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).build(), PunishOptions.defaults()).join();

        Assertions.assertThat(this.loginGate.check(this.steve()).join())
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.names.uuid("steve")).contains(STEVE);
    }

    @Test
    void joinsAreRefusedWhileSakuraBansIsntRunning() {
        this.lifecycle.advance(LifecycleState.RUNNING, LifecycleState.STOPPING);

        Assertions.assertThat(this.loginGate.check(this.steve()).join())
                .isInstanceOfSatisfying(LoginDecision.Denied.class, denied ->
                        Assertions.assertThat(denied.denial())
                                .isEqualTo(PlayerDenyEvent.Denial.LOGIN_CHECK_FAILED));
    }
}
