package net.serlith.sakurabans.core.enforce;

import java.util.List;
import java.util.Set;
import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.Enforcement;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.model.LoginAttempt;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** A proxy: server bans apply to the players on that backend; mutes are left to it (D4). */
class ProxyEnforcementTest extends ServiceTest {

    @Override
    protected boolean proxy() {
        return true;
    }

    ProxyEnforcementTest() {
        super(new FakePlatform(Set.of("lobby", "hub", "survival", "creative"),
                List.of("lobby", "hub")));
    }

    @Override
    protected String configYaml() {
        return "scopes:\n  categories:\n    hubs: [lobby, hub]\n";
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
    void deniedFirstConnectionsNameServerThePlayerIsntBannedOn() {
        this.punish(PunishmentType.BAN, "survival");
        Assertions.assertThat(this.loginGate.checkServer(this.steve(), "survival").join())
                .isInstanceOfSatisfying(LoginDecision.Denied.class, denied ->
                        Assertions.assertThat(denied.fallback()).contains("lobby"));

        this.punish(PunishmentType.BAN, "lobby");
        Assertions.assertThat(this.loginGate.checkServer(this.steve(), "survival").join())
                .isInstanceOfSatisfying(LoginDecision.Denied.class, denied ->
                        Assertions.assertThat(denied.fallback()).contains("hub"));
    }

    @Test
    void noFallbackWhenEveryTryServerIsCovered() {
        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).scope(CategoryScope.of("hubs")).build(),
                PunishOptions.defaults()).join();

        Assertions.assertThat(this.loginGate.checkServer(this.steve(), "lobby").join())
                .isInstanceOfSatisfying(LoginDecision.Denied.class, denied ->
                        Assertions.assertThat(denied.fallback()).isEmpty());
        Assertions.assertThat(this.loginGate.checkServer(this.steve(), "survival").join())
                .isInstanceOf(LoginDecision.Allowed.class);
    }

    @Test
    void serverBansMoveThePlayerToFallback() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home, null,
                "survival");

        this.punish(PunishmentType.BAN, "survival");

        Assertions.assertThat(steve.disconnectReason()).isEmpty();
        Assertions.assertThat(steve.currentServer()).contains("lobby");
        Assertions.assertThat(steve.messages()).hasSize(1);
    }

    @Test
    void bansCoveringEveryFallbackDisconnect() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home, null,
                "lobby");

        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).scope(CategoryScope.of("hubs")).build(),
                PunishOptions.defaults()).join();

        Assertions.assertThat(steve.disconnectReason()).isPresent();
    }

    @Test
    void globalBansDisconnectFromTheProxy() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home, null,
                "survival");

        this.service.punish(DraftPunishment.builder(PunishmentType.BAN, PlayerVictim.of(STEVE),
                MODERATOR).build(), PunishOptions.defaults()).join();

        Assertions.assertThat(steve.disconnectReason()).isPresent();
    }

    @Test
    void punishmentsIssuedOnTheProxyComeFromIt() {
        this.punish(PunishmentType.BAN, "survival");

        Assertions.assertThat(this.service.activeBan(STEVE, null, ServerScope.of("survival"))
                .join().orElseThrow().originServer()).contains("proxy");
    }

    @Test
    void enforceRequestsFromBackendsApplyActiveBans() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home, null,
                "creative");
        // Issued elsewhere (no enforcement here), as a backend would.
        final long id = ((PunishResult.Success) this.service.punish(DraftPunishment.builder(
                PunishmentType.BAN, PlayerVictim.of(STEVE), MODERATOR).build(),
                PunishOptions.builder().enforcement(Enforcement.NONE).build()).join())
                .punishment().id();
        Assertions.assertThat(steve.disconnectReason()).isEmpty();

        this.channelReceiver.received(this.channel.enforce(id + 1000)).join();
        Assertions.assertThat(steve.disconnectReason()).isEmpty();
        this.channelReceiver.received(this.channel.enforce(id)).join();
        Assertions.assertThat(steve.disconnectReason()).isPresent();
    }

    @Test
    void proxiesIgnoreServerNames() {
        this.channelReceiver.received(this.channel.serverName("survival")).join();

        Assertions.assertThat(this.scopes.thisServer()).isEmpty();
    }

    @Test
    void proxiesRecordTheAddressOnlyOnServersThatAllowIt() {
        this.loginGate.check(this.steve()).join();
        eventually(() -> this.players.profile(STEVE).join().isPresent());
        Assertions.assertThat(this.players.addresses(STEVE).join()).as("not at login").isEmpty();

        this.loginGate.connected(new LoginAttempt(STEVE, "Steve", this.home, "limbo"));
        this.loginGate.connected(new LoginAttempt(STEVE, "Steve", this.home, "lobby"));

        eventually(() -> !this.players.addresses(STEVE).join().isEmpty());
        Assertions.assertThat(this.players.addresses(STEVE).join()).singleElement()
                .satisfies(record -> Assertions.assertThat(record.lastServer())
                        .contains("lobby"));
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
