package net.serlith.sakurabans.core.enforce;

import java.time.Duration;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class LoginGateTest extends ServiceTest {

    private Punishment ban(final Victim victim) {
        return this.ban(DraftPunishment.builder(PunishmentType.BAN, victim, MODERATOR)
                .reason("cheating"));
    }

    private Punishment ban(final DraftPunishment.Builder draft) {
        return ((PunishResult.Success) this.service.punish(draft.build(),
                PunishOptions.defaults()).join()).punishment();
    }

    private LoginDecision login(final UUID uuid, final String name,
            final @Nullable String server) {
        return this.loginGate.check(new LoginAttempt(uuid, name, this.home, server)).join();
    }

    @Test
    void lettersInAndRecordsTheConnection() {
        Assertions.assertThat(this.login(STEVE, "Steve", "lobby"))
                .isInstanceOf(LoginDecision.Allowed.class);
        // The gate records the connection and loads mutes without waiting for either.
        eventually(() -> this.players.profile(STEVE).join().isPresent()
                && this.mutes.loaded(STEVE));

        Assertions.assertThat(this.players.profile(STEVE).join()).hasValueSatisfying(profile ->
                Assertions.assertThat(profile.lastAddress()).contains(this.home));
        Assertions.assertThat(this.names.uuid("steve")).contains(STEVE);
        Assertions.assertThat(this.mutes.loaded(STEVE)).isTrue();
    }

    @Test
    void serversWithoutIpRegistrationRecordNoAddress() {
        this.login(STEVE, "Steve", "limbo");

        Assertions.assertThat(this.players.profile(STEVE).join()).hasValueSatisfying(profile ->
                Assertions.assertThat(profile.lastAddress()).isEmpty());
    }

    @Test
    void bannedPlayersAreDeniedWithTheScreenAndStaffAreTold() {
        final FakeOnlinePlayer staff = this.platform.join(MOD, "Mod",
                InetAddresses.parseLiteral("198.51.100.1").orElseThrow())
                .grant(Permissions.NOTIFY_ATTEMPT_JOIN);
        final Punishment ban = this.ban(PlayerVictim.of(STEVE));
        final int before = staff.messages().size();

        final LoginDecision decision = this.login(STEVE, "Steve", null);

        Assertions.assertThat(decision).isInstanceOfSatisfying(LoginDecision.Denied.class,
                denied -> {
                    Assertions.assertThat(denied.denial())
                            .isEqualTo(PlayerDenyEvent.Denial.LOGIN_BANNED);
                    Assertions.assertThat(denied.punishment()).isEqualTo(ban);
                    Assertions.assertThat(plain(denied.screen())).contains("cheating");
                });
        Assertions.assertThat(this.events(PlayerDenyEvent.class)).singleElement()
                .satisfies(event -> Assertions.assertThat(event.punishment()).contains(ban));
        Assertions.assertThat(this.mutes.loaded(STEVE)).isFalse();
        Assertions.assertThat(staff.messages()).hasSize(before + 1);
        Assertions.assertThat(plain(staff.messages().getLast()))
                .isEqualTo("SakuraBans » Steve tried to join but is banned (permanent, by Mod):"
                        + " cheating");
    }

    @Test
    void anAddressBanCoversAccountsThatUsedTheAddress() {
        this.players.recordConnection(ALEX, "Alex", this.now.get(), this.home, null).join();
        this.ban(AddressVictim.of(this.home));

        Assertions.assertThat(this.login(ALEX, "Alex", null))
                .isInstanceOf(LoginDecision.Denied.class);
    }

    @Test
    void expiredAndOtherScopesBansDontCount() {
        this.ban(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR)
                .duration(Duration.ofMinutes(10)));
        this.ban(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(ALEX), MODERATOR)
                .scope(ServerScope.of("survival")));
        Assertions.assertThat(this.login(STEVE, "Steve", null))
                .isInstanceOf(LoginDecision.Denied.class);

        this.now.set(this.now.get().plus(Duration.ofMinutes(10)));

        Assertions.assertThat(this.login(STEVE, "Steve", null))
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.login(ALEX, "Alex", null))
                .isInstanceOf(LoginDecision.Allowed.class);
    }

    @Test
    void denyHandlersCanLetThePlayerInOrReplaceTheScreen() {
        this.ban(PlayerVictim.of(STEVE));
        this.ban(PlayerVictim.of(ALEX));
        this.bus.subscribe(PlayerDenyEvent.class, event -> {
            if (event.name().equals("Steve")) {
                event.cancelled(true);
            } else {
                event.message(Component.text("custom"));
            }
        });

        Assertions.assertThat(this.login(STEVE, "Steve", null))
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.login(ALEX, "Alex", null))
                .isInstanceOfSatisfying(LoginDecision.Denied.class, denied ->
                        Assertions.assertThat(plain(denied.screen())).isEqualTo("custom"));
    }

    @Test
    void failingChecksTurnThePlayerAway() {
        this.storage.stop();

        Assertions.assertThat(this.login(STEVE, "Steve", null))
                .isInstanceOfSatisfying(LoginDecision.Denied.class, denied ->
                        Assertions.assertThat(denied.denial())
                                .isEqualTo(PlayerDenyEvent.Denial.LOGIN_CHECK_FAILED));
        Assertions.assertThat(this.log.entries("error")).isNotEmpty();
    }
}
