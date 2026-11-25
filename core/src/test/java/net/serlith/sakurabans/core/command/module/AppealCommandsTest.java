package net.serlith.sakurabans.core.command.module;

import java.time.Duration;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** /appeal and /appeals (plan S37d). */
class AppealCommandsTest extends CommandTest {

    @Test
    void playersAppealAndStaffDecide() {
        this.known(STEVE, "Steve");
        this.names.put(STEVE, "Steve");
        final Punishment mute = ((PunishResult.Success) this.service.punish(DraftPunishment
                .builder(PunishmentType.MUTE, PlayerVictim.of(STEVE), MODERATOR)
                .duration(Duration.ofDays(1)).build(), PunishOptions.defaults()).join())
                .punishment();
        final FakeCommandActor steve = FakeCommandActor.player(STEVE, "Steve",
                Permissions.APPEAL);
        final FakeCommandActor alex = FakeCommandActor.player(ALEX, "Alex", Permissions.APPEAL);

        this.run(alex, "appeal " + mute.id() + " free me");
        Assertions.assertThat(alex.last()).contains("your own punishments");
        this.run(steve, "appeal #" + mute.id() + " I didn't say that");
        Assertions.assertThat(steve.last()).contains("was sent");

        this.run(this.console, "appeals list");
        Assertions.assertThat(String.join("\n", this.console.texts()))
                .contains("Open appeals (1", "I didn't say that", "Steve");
        this.run(this.console, "appeals reject 1 the log shows it");
        Assertions.assertThat(this.console.last()).contains("Appeal 1: reject");
        this.run(this.console, "appeals list");
        Assertions.assertThat(this.console.last()).contains("no open appeals");
        Assertions.assertThat(this.service.byId(mute.id()).join().orElseThrow().end()).isEmpty();
    }
}
