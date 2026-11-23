package net.serlith.sakurabans.core.command.module;

import java.net.InetAddress;
import java.util.UUID;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code /alts} with a strictness, {@code /ipreport} and join notices (plan S29). */
class AltNoticeAndReportTest extends CommandTest {

    private static final UUID BOB = UUID.fromString("2b1c9e5a-7f3d-4c8e-9a6b-1d2e3f4a5b6c");

    private InetAddress other;

    @Override
    protected String configYaml() {
        return "alts:\n  join-notification:\n    threshold: 1\n";
    }

    @BeforeEach
    void fixture() throws Exception {
        this.other = InetAddress.getByName("198.51.100.20");
        this.known(STEVE, "Steve");
        this.known(ALEX, "Alex");
        this.players.recordConnection(ALEX, "Alex", this.now.get(), this.other, null).join();
        this.players.recordConnection(BOB, "Bob", this.now.get(), this.other, null).join();
    }

    @Test
    void altsTakeOptionalStrictness() {
        this.run(this.console, "alts Steve");
        Assertions.assertThat(this.console.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("1 known alts (normal)"));
        Assertions.assertThat(this.console.last()).contains("Alex", "203.0.113.7");

        this.console.clear();
        this.run(this.console, "alts Steve STERN");
        Assertions.assertThat(this.console.texts()).anySatisfy(line ->
                Assertions.assertThat(line).contains("2 known alts (stern)"));
        Assertions.assertThat(this.console.texts())
                .anySatisfy(line -> Assertions.assertThat(line).contains("Bob"));

        this.run(this.console, "alts Bob lenient");
        Assertions.assertThat(this.console.last()).contains("No alts are known for Bob");
    }

    @Test
    void ipReportGroupsOnlinePlayersByAddress() {
        this.run(this.console, "ipreport");
        Assertions.assertThat(this.console.last()).contains("No two online players");

        this.platform.join(STEVE, "Steve", this.home);
        this.platform.join(ALEX, "Alex", this.home);
        this.platform.join(BOB, "Bob", this.other);
        this.run(this.console, "ipreport");

        Assertions.assertThat(this.console.last()).contains("203.0.113.7", "Alex, Steve")
                .doesNotContain("Bob");
    }

    @Test
    void staffAreToldWhenSomeoneJoinsWithPunishedAlts() {
        final FakeOnlinePlayer staff = this.platform.join(UUID.randomUUID(), "Mod",
                this.other).grant(Permissions.ALTS_NOTIFY);
        final LoginAttempt steve = new LoginAttempt(STEVE, "Steve", this.home, null);

        this.loginGate.check(steve).join();
        Assertions.assertThat(staff.messages()).as("Alex isn't punished").isEmpty();

        this.service.punish(DraftPunishment.builder(PunishmentType.MUTE, PlayerVictim.of(ALEX),
                MODERATOR).build(), PunishOptions.defaults()).join();
        this.loginGate.check(steve).join();
        eventually(() -> staff.messages().stream()
                .anyMatch(message -> plain(message).contains("joined with 1 known alts")));

        final int before = staff.messages().size();
        this.loginGate.check(steve).join();
        Assertions.assertThat(staff.messages()).as("once per 30 minutes").hasSize(before);
    }

}
