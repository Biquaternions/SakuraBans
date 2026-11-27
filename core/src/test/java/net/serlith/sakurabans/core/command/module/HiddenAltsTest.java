package net.serlith.sakurabans.core.command.module;

import java.net.InetAddress;
import java.util.UUID;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.Permissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code alts.hidden-players} in /alts, /ipreport and the join notification. */
class HiddenAltsTest extends CommandTest {

    private static final UUID BOB = UUID.fromString("2b1c9e5a-7f3d-4c8e-9a6b-1d2e3f4a5b6c");
    private static final UUID CARL = UUID.fromString("3c2d0f6b-8a4e-4d9f-ab7c-2e3f4a5b6c7d");

    private InetAddress office;

    @Override
    protected String configYaml() {
        return "alts:\n  hidden-players: [bob, 198.51.100.0/24]\n"
                + "  join-notification:\n    threshold: 1\n    only-if-punished: false\n";
    }

    @BeforeEach
    void fixture() throws Exception {
        this.office = InetAddress.getByName("198.51.100.20");
        this.known(STEVE, "Steve");
        // Alex shares Steve's home address, Bob is hidden by name, Carl only shares the
        // hidden office address with Steve.
        this.players.recordConnection(STEVE, "Steve", this.now.get(), this.office, null).join();
        this.players.recordConnection(ALEX, "Alex", this.now.get(), this.home, null).join();
        this.players.recordConnection(BOB, "Bob", this.now.get(), this.home, null).join();
        this.players.recordConnection(CARL, "Carl", this.now.get(), this.office, null).join();
    }

    private FakeCommandActor mod(final String... extra) {
        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod", Permissions.ALTS_CHECK,
                Permissions.ALTS_IPREPORT, Permissions.VIEW_IPS);
        return mod.grant(extra);
    }

    @Test
    void altsLeaveOutHiddenAccountsAndAddresses() {
        final FakeCommandActor mod = this.mod();
        this.run(mod, "alts Steve");
        final String shown = String.join("\n", mod.texts());
        Assertions.assertThat(shown).contains("Alex", "1 known alts").doesNotContain("Bob", "Carl",
                "198.51.100.20");

        final FakeCommandActor viewer = this.mod(Permissions.ALTS_VIEW_HIDDEN);
        this.run(viewer, "alts Steve");
        Assertions.assertThat(String.join("\n", viewer.texts())).contains("Alex", "Bob", "Carl");
    }

    @Test
    void ipReportLeavesOutHiddenPlayersAndAddresses() {
        this.platform.join(STEVE, "Steve", this.home);
        this.platform.join(ALEX, "Alex", this.home);
        this.platform.join(BOB, "Bob", this.home);
        this.platform.join(CARL, "Carl", this.office);
        this.platform.join(UUID.randomUUID(), "Dan", this.office);

        final FakeCommandActor mod = this.mod();
        this.run(mod, "ipreport");
        Assertions.assertThat(String.join("\n", mod.texts())).contains("Alex, Steve")
                .doesNotContain("Bob", "Carl", "198.51.100.20");

        final FakeCommandActor viewer = this.mod(Permissions.ALTS_VIEW_HIDDEN);
        this.run(viewer, "ipreport");
        Assertions.assertThat(String.join("\n", viewer.texts())).contains("Alex, Bob, Steve",
                "Carl, Dan");
    }

    @Test
    void theJoinNoticeShowsOnlyVisibleAltsAndNothingAboutHiddenPlayers() {
        final FakeOnlinePlayer staff = this.platform.join(UUID.randomUUID(), "Staff",
                this.office).grant(Permissions.ALTS_NOTIFY);
        final FakeOnlinePlayer viewer = this.platform.join(UUID.randomUUID(), "Viewer",
                this.office).grant(Permissions.ALTS_NOTIFY, Permissions.ALTS_VIEW_HIDDEN);

        this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home, null)).join();
        eventually(() -> !viewer.messages().isEmpty() && !staff.messages().isEmpty());
        Assertions.assertThat(plain(staff.messages().getLast())).contains("Alex")
                .doesNotContain("Bob", "Carl");
        Assertions.assertThat(plain(viewer.messages().getLast())).contains("Alex", "Bob",
                "Carl");

        this.service.punish(DraftPunishment.builder(PunishmentType.MUTE, PlayerVictim.of(ALEX),
                MODERATOR).build(), PunishOptions.defaults()).join();
        final int staffBefore = staff.messages().size();
        final int viewerBefore = viewer.messages().size();
        this.loginGate.check(new LoginAttempt(BOB, "Bob", this.home, null)).join();
        eventually(() -> viewer.messages().size() > viewerBefore);
        Assertions.assertThat(staff.messages()).as("Bob is hidden").hasSize(staffBefore);
    }
}
