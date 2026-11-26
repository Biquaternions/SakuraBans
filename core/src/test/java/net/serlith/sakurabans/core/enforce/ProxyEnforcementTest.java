package net.serlith.sakurabans.core.enforce;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import net.serlith.sakurabans.api.event.PlayerDenyEvent;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.fake.FakePlatform;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.punish.MuteCache;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** A proxy: server bans and mutes apply to the players on that backend (D29). */
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

    private FakeOnlinePlayer online(final @Nullable String server) {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home, null,
                server);
        this.loginGate.check(this.steve()).join();
        eventually(() -> this.mutes.loaded(STEVE));
        return steve;
    }

    private void mute(final Scope scope, final boolean shadow) {
        final DraftPunishment draft = DraftPunishment.builder(PunishmentType.MUTE,
                PlayerVictim.of(STEVE), MODERATOR).scope(scope).reason("spam").shadow(shadow)
                .build();
        this.service.punish(draft, PunishOptions.defaults()).join();
    }

    private ChatDecision chat(final FakeOnlinePlayer player) {
        return this.chatGate.checkChat(player, "hi");
    }

    @Test
    void serverMutesFollowThePlayerAcrossServers() {
        final FakeOnlinePlayer steve = this.online("survival");

        this.mute(ServerScope.of("survival"), false);

        Assertions.assertThat(steve.messages()).hasSize(1);
        Assertions.assertThat(plain(steve.messages().getFirst())).contains("spam");
        Assertions.assertThat(this.chat(steve)).isInstanceOf(ChatDecision.Denied.class);
        steve.connect("lobby").join();
        Assertions.assertThat(this.chat(steve)).isInstanceOf(ChatDecision.Allowed.class);
        Assertions.assertThat(this.service.cachedMute(STEVE)).isEmpty();
        steve.connect("survival").join();
        Assertions.assertThat(this.chat(steve)).isInstanceOf(ChatDecision.Denied.class);
        Assertions.assertThat(this.service.cachedMute(STEVE)).isPresent();
    }

    @Test
    void mutesForAnotherServerAreCachedButNotAnnounced() {
        final FakeOnlinePlayer steve = this.online("lobby");

        this.mute(ServerScope.of("survival"), false);

        Assertions.assertThat(steve.messages()).isEmpty();
        Assertions.assertThat(this.chat(steve)).isInstanceOf(ChatDecision.Allowed.class);
        // punish waits for the local enforcement, so the switch alone must make it apply.
        steve.connect("survival").join();
        Assertions.assertThat(this.chat(steve)).isInstanceOf(ChatDecision.Denied.class);
    }

    @Test
    void categoryMutesApplyOnTheCategorysServers() {
        final FakeOnlinePlayer steve = this.online("survival");

        this.mute(CategoryScope.of("hubs"), false);

        Assertions.assertThat(this.chat(steve)).isInstanceOf(ChatDecision.Allowed.class);
        steve.connect("hub").join();
        Assertions.assertThat(this.chat(steve)).isInstanceOf(ChatDecision.Denied.class);
        steve.connect("lobby").join();
        Assertions.assertThat(this.chat(steve)).isInstanceOf(ChatDecision.Denied.class);
        Assertions.assertThat(steve.messages()).as("muted elsewhere: not told").isEmpty();
    }

    @Test
    void globalMutesApplyEverywhereEvenBeforeTheFirstServer() {
        final FakeOnlinePlayer steve = this.online(null);

        this.mute(GlobalScope.INSTANCE, false);

        Assertions.assertThat(this.chat(steve)).isInstanceOf(ChatDecision.Denied.class);
        steve.connect("creative").join();
        Assertions.assertThat(this.chat(steve)).isInstanceOf(ChatDecision.Denied.class);
    }

    @Test
    void serverMutesDontApplyBeforeTheFirstServer() {
        final FakeOnlinePlayer steve = this.online(null);

        this.mute(ServerScope.of("survival"), false);

        Assertions.assertThat(this.chat(steve)).isInstanceOf(ChatDecision.Allowed.class);
        Assertions.assertThat(this.mutes.lookup(STEVE))
                .isInstanceOf(MuteCache.Lookup.NotMuted.class);
        steve.connect("survival").join();
        Assertions.assertThat(this.mutes.lookup(STEVE))
                .isInstanceOf(MuteCache.Lookup.Muted.class);
    }

    @Test
    void shadowMutedChatIsEchoedToTheSender() {
        final FakeOnlinePlayer steve = this.online("survival");

        this.mute(ServerScope.of("survival"), true);

        Assertions.assertThat(this.chat(steve)).isInstanceOf(ChatDecision.ShadowMuted.class);
        Assertions.assertThat(steve.messages()).isEmpty();
        Assertions.assertThat(plain(this.chatGate.shadowEcho(steve, "hi <b>there</b>")))
                .isEqualTo("<Steve> hi <b>there</b>");
        Assertions.assertThat(this.chatGate.checkCommand(steve, "msg Alex hi"))
                .isInstanceOf(ChatDecision.ShadowMuted.class);
    }

    @Test
    void withoutSignedVelocityMutedChatPassesButSafeCommandsAreBlocked() {
        final FakeOnlinePlayer staff = this.platform.join(MOD, "Mod", this.home, null, "survival")
                .grant(Permissions.NOTIFY_ATTEMPT_CHAT)
                .grant(staffPermissions());
        final FakeOnlinePlayer steve = this.online("survival");
        this.mute(ServerScope.of("survival"), false);
        final int deniesBefore = this.events(PlayerDenyEvent.class).size();
        final int staffBefore = staff.messages().size();
        final SignedChatPolicy policy = new SignedChatPolicy(false);

        Assertions.assertThat(this.chatGate.checkChat(steve, "hi", policy.chatCancellable()))
                .isInstanceOf(ChatDecision.Allowed.class);
        Assertions.assertThat(this.chatGate.checkChat(steve, "again", policy.chatCancellable()))
                .isInstanceOf(ChatDecision.Allowed.class);
        Assertions.assertThat(this.chatGate.checkCommand(steve, "msg Alex hi",
                policy.commandCancellable(true))).isInstanceOf(ChatDecision.Allowed.class);
        Assertions.assertThat(this.events(PlayerDenyEvent.class)).hasSize(deniesBefore);
        Assertions.assertThat(staff.messages()).hasSize(staffBefore);
        Assertions.assertThat(this.log.entries("warn"))
                .filteredOn(entry -> entry.format().contains("went through")).singleElement()
                .satisfies(entry -> Assertions.assertThat(entry.format()).contains("Steve"));

        Assertions.assertThat(this.chatGate.checkCommand(steve, "msg Alex hi",
                policy.commandCancellable(false))).isInstanceOf(ChatDecision.Denied.class);

        this.now.set(this.now.get().plus(Duration.ofMinutes(1)));
        this.chatGate.checkChat(steve, "later", policy.chatCancellable());
        Assertions.assertThat(this.log.entries("warn"))
                .filteredOn(entry -> entry.format().contains("went through")).hasSize(2);
    }

    @Test
    void signedChatPolicy() {
        final SignedChatPolicy with = new SignedChatPolicy(true);
        final SignedChatPolicy without = new SignedChatPolicy(false);

        Assertions.assertThat(with.chatCancellable()).isTrue();
        Assertions.assertThat(with.commandCancellable(true)).isTrue();
        Assertions.assertThat(without.chatCancellable()).isFalse();
        Assertions.assertThat(without.commandCancellable(true)).isFalse();
        Assertions.assertThat(without.commandCancellable(false)).isTrue();

        with.warnIfMissing(this.log.logger());
        Assertions.assertThat(this.log.entries("warn"))
                .noneMatch(entry -> entry.format().contains("SignedVelocity"));
        without.warnIfMissing(this.log.logger());
        Assertions.assertThat(this.log.entries("warn"))
                .filteredOn(entry -> entry.format().contains("SignedVelocity")).hasSize(1);
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
