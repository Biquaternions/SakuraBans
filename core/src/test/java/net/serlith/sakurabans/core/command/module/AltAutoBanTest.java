package net.serlith.sakurabans.core.command.module;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.enforce.AltAutoBans;
import net.serlith.sakurabans.core.enforce.LoginDecision;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * S69: {@code alts.auto-ban}.
 *
 * <pre>
 * STEVE  home              ALEX   home (Steve's alt)      BOB  home (already banned there)
 * CARL   home (exempt)     DAVE   home (hidden)           FRAN shares only "other" with Alex
 * </pre>
 */
class AltAutoBanTest extends CommandTest {

    private static final UUID BOB = UUID.fromString("2b1c9e5a-7f3d-4c8e-9a6b-1d2e3f4a5b6c");
    private static final UUID CARL = UUID.fromString("3c2d0f6b-8a4e-4d9f-ab7c-2e3f4a5b6c7d");
    private static final UUID DAVE = UUID.fromString("4d3e1a7c-9b5f-4e0a-bc8d-3f4a5b6c7d8e");
    private static final UUID FRAN = UUID.fromString("5e4f2b8d-ac6a-4f1b-8d9e-4a5b6c7d8e9f");
    private static final UUID GINA = UUID.fromString("6f5a3c9e-bd7b-4a2c-9eaf-5b6c7d8e9fa0");

    private InetAddress other;

    @Override
    protected String configYaml() {
        return "alts:\n  auto-ban: ON_JOIN\n  auto-ban-confirm-above: 10\n"
                + "  hidden-players: [dave]\n"
                + "exemption:\n  players: [" + CARL + "]\n";
    }

    @BeforeEach
    void fixture() throws Exception {
        this.other = InetAddress.getByName("198.51.100.9");
        for (final var entry : List.of(List.of(STEVE, "Steve"), List.of(ALEX, "Alex"),
                List.of(BOB, "Bob"), List.of(CARL, "Carl"), List.of(DAVE, "Dave"))) {
            this.known((UUID) entry.get(0), (String) entry.get(1));
        }
        this.players.recordConnection(ALEX, "Alex", this.now.get(), this.other, null).join();
        this.players.recordConnection(FRAN, "Fran", this.now.get(), this.other, null).join();
        this.ban(BOB, "earlier", ServerScope.of("lobby"));
    }

    private PunishResult ban(final UUID victim, final String reason,
            final ServerScope scope) {
        return this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                        PlayerVictim.of(victim), MODERATOR).reason(reason).scope(scope)
                .duration(Duration.ofDays(7)).build(), PunishOptions.defaults()).join();
    }

    private List<Punishment> bansOf(final UUID uuid) {
        return this.service.query(PunishmentQuery.builder().types(PunishmentType.BAN)
                .victim(PlayerVictim.of(uuid)).activeOnly(true).build()).join().items();
    }

    @Test
    void nothingHappensUntilTheConsoleAccepts() {
        Assertions.assertThat(this.autoBans.pending().join()).isTrue();
        this.run(this.console, "sb status");
        Assertions.assertThat(String.join("\n", this.console.texts()))
                .contains("does nothing until the console runs /sb accept alts");

        this.ban(STEVE, "griefing", ServerScope.of("lobby"));
        Assertions.assertThat(this.bansOf(ALEX)).isEmpty();

        final FakeCommandActor mod = FakeCommandActor.player(MOD, "Mod", staffPermissions());
        this.run(mod, "sb accept alts");
        Assertions.assertThat(this.autoBans.pending().join()).as("console only").isTrue();
        this.run(this.console, "sb accept alts");
        Assertions.assertThat(this.console.last()).contains("Automatic alt bans are accepted");
        this.run(this.console, "sb accept alts");
        Assertions.assertThat(this.console.last()).contains("already accepted");
        Assertions.assertThat(this.autoBans.pending().join()).isFalse();
    }

    @Test
    void bansAlsoBanTheLinkedAccountsOnceWithOneNotice() {
        this.autoBans.accept().join();
        final FakeOnlinePlayer staff = this.platform.join(UUID.randomUUID(), "Staff",
                this.other).grant(Permissions.notify(PunishmentType.BAN));
        this.published.clear();

        final Punishment ban = ((PunishResult.Success) this.ban(STEVE, "griefing",
                ServerScope.of("lobby"))).punishment();

        eventually(() -> this.bansOf(ALEX).size() == 1);
        final Punishment alex = this.bansOf(ALEX).getFirst();
        Assertions.assertThat(alex.reason()).isEqualTo("griefing");
        Assertions.assertThat(alex.scope()).isEqualTo(ServerScope.of("lobby"));
        Assertions.assertThat(alex.source()).isEqualTo(AltAutoBans.SOURCE);
        Assertions.assertThat(alex.operator()).isEqualTo(ban.operator());
        Assertions.assertThat(alex.expiresAt()).isEqualTo(ban.expiresAt());
        Assertions.assertThat(this.bansOf(BOB)).as("already banned there").hasSize(1)
                .allSatisfy(own -> Assertions.assertThat(own.reason()).isEqualTo("earlier"));
        Assertions.assertThat(this.bansOf(CARL)).as("exempt").isEmpty();
        Assertions.assertThat(this.bansOf(DAVE)).as("hidden").isEmpty();
        Assertions.assertThat(this.bansOf(FRAN)).as("never recursive").isEmpty();

        eventually(() -> staff.messages().stream().anyMatch(message ->
                plain(message).contains("also banned 1 linked accounts")));
        Assertions.assertThat(staff.messages()).as("Steve's ban and one summary").hasSize(2);
        Assertions.assertThat(this.published).anySatisfy(packet ->
                Assertions.assertThat(packet).isEqualTo(
                        new SyncPacket.AltsAutoBannedPacket(ban.id(), List.of("Alex"))));

        // Lifting the first ban leaves the others.
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(ban.id()),
                MODERATOR).build()).join();
        Assertions.assertThat(this.bansOf(ALEX)).hasSize(1);
    }

    @Test
    void manyLinkedAccountsNeedConfirm() throws Exception {
        this.autoBans.accept().join();
        for (int i = 0; i < 11; i++) {
            this.players.recordConnection(UUID.randomUUID(), "Alt" + i, this.now.get(),
                    this.home, null).join();
        }

        this.run(this.console, "ban Steve -r raid");
        Assertions.assertThat(this.console.last()).contains("would also ban", "--confirm");
        Assertions.assertThat(this.bansOf(STEVE)).isEmpty();

        this.run(this.console, "ban Steve --confirm -r raid");
        Assertions.assertThat(this.bansOf(STEVE)).hasSize(1);
    }

    @Test
    void onJoinBansAnAccountAnAddressBanTurnsAway() throws Exception {
        this.autoBans.accept().join();
        final InetAddress banned = InetAddress.getByName("192.0.2.50");
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN,
                AddressVictim.of(banned), MODERATOR).reason("vpn abuse").build(),
                PunishOptions.defaults()).join();

        final LoginDecision decision = this.loginGate.check(
                new LoginAttempt(GINA, "Gina", banned, null)).join();

        Assertions.assertThat(decision).isInstanceOf(LoginDecision.Denied.class);
        eventually(() -> this.bansOf(GINA).size() == 1);
        Assertions.assertThat(this.bansOf(GINA).getFirst().reason()).isEqualTo("vpn abuse");
        Assertions.assertThat(this.bansOf(GINA).getFirst().source())
                .isEqualTo(AltAutoBans.SOURCE);
    }
}
