package net.serlith.sakurabans.core.command.module;

import java.time.Duration;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.enforce.LoginDecision;
import net.serlith.sakurabans.core.model.LoginAttempt;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** S69: the decay at login, with ON_JOIN, and in /checkban and the lists. */
class IpBanDecayCommandsTest extends CommandTest {

    @Override
    protected String configYaml() {
        return "enforcement:\n  ip-ban-decay: 30d\nalts:\n  auto-ban: ON_JOIN\n";
    }

    @Test
    void decayedCompositesTurnAwayTheirPlayerOnlyAndSaySo() {
        this.known(STEVE, "Steve");
        this.known(ALEX, "Alex");
        // Banned while ON_BAN isn't listening, so it can't copy the ban to Alex. Accepting
        // right after the ban raced with ON_BAN's asynchronous read of the acceptance.
        this.autoBans.stop();
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                CompositeVictim.of(STEVE, this.home), MODERATOR).reason("xray").build(),
                PunishOptions.defaults()).join();
        this.autoBans.start();
        this.autoBans.accept().join();
        this.run(this.console, "checkban Steve");
        Assertions.assertThat(String.join("\n", this.console.texts()))
                .doesNotContain("address part decayed");

        this.now.set(this.now.get().plus(Duration.ofDays(31)));

        Assertions.assertThat(this.loginGate.check(
                        new LoginAttempt(ALEX, "Alex", this.home, null)).join())
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.loginGate.check(
                        new LoginAttempt(STEVE, "Steve", this.home, null)).join())
                .isInstanceOf(LoginDecision.Denied.class);
        Assertions.assertThat(this.service.query(PunishmentQuery.builder()
                        .victim(PlayerVictim.of(ALEX)).build()).join().items())
                .as("ON_JOIN follows the decay").isEmpty();
        this.console.clear();
        this.run(this.console, "checkban Steve");
        Assertions.assertThat(String.join("\n", this.console.texts()))
                .contains("address part decayed");
        this.console.clear();
        this.run(this.console, "history Steve");
        Assertions.assertThat(String.join("\n", this.console.texts()))
                .contains("address part decayed");
    }
}
