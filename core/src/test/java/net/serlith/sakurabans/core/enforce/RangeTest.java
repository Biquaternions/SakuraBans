package net.serlith.sakurabans.core.enforce;

import java.net.InetAddress;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.util.Cidrs;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Address ranges at login, in the mute cache and online. */
class RangeTest extends ServiceTest {

    private static final AddressRangeVictim TEN = Cidrs.parse("10.0.0.0/8").orElseThrow();
    private static final AddressRangeVictim DOCS = Cidrs.parse("2001:db8::/32").orElseThrow();

    private Punishment punish(final PunishmentType type, final AddressRangeVictim range,
            final boolean enforce) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(type, range,
                MODERATOR).build(), PunishOptions.builder().enforce(enforce).build())
                .join()).punishment();
    }

    private LoginDecision join(final UUID uuid, final String address) throws Exception {
        return this.loginGate.check(new LoginAttempt(uuid, "Steve",
                InetAddress.getByName(address), null)).join();
    }

    @Test
    void ipv4AndIpv6RangesDenyJoinsInside() throws Exception {
        this.punish(PunishmentType.BAN, TEN, true);
        this.punish(PunishmentType.BAN, DOCS, true);

        Assertions.assertThat(this.join(STEVE, "10.20.30.40"))
                .isInstanceOf(LoginDecision.Denied.class);
        Assertions.assertThat(this.join(ALEX, "2001:db8:1::5"))
                .isInstanceOf(LoginDecision.Denied.class);
        // Fresh accounts: the ones above now have an address inside on record (NORMAL).
        Assertions.assertThat(this.join(UUID.randomUUID(), "2001:db9::1"))
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.join(UUID.randomUUID(), "11.0.0.1"))
                .isInstanceOf(LoginDecision.Allowed.class);
    }

    @Test
    void fromNormalOnAddressesUsedBeforeCount() throws Exception {
        this.players.recordConnection(STEVE, "Steve", this.now.get(),
                InetAddress.getByName("10.9.9.9"), null).join();
        this.punish(PunishmentType.BAN, TEN, true);

        Assertions.assertThat(this.join(STEVE, "203.0.113.7"))
                .isInstanceOf(LoginDecision.Denied.class);
    }

    @Test
    void allowListedAddressesInsideRangesAreNotReached() throws Exception {
        this.punish(PunishmentType.BAN, TEN, true);
        this.allowList.allow(InetAddress.getByName("10.1.1.1"), MODERATOR, null).join();

        Assertions.assertThat(this.join(STEVE, "10.1.1.1"))
                .isInstanceOf(LoginDecision.Allowed.class);
        Assertions.assertThat(this.join(ALEX, "10.1.1.2"))
                .isInstanceOf(LoginDecision.Denied.class);
    }

    @Test
    void rangeMutesReachTheMuteCache() throws Exception {
        this.punish(PunishmentType.MUTE, TEN, true);

        this.mutes.load(STEVE, InetAddress.getByName("10.0.0.1")).join();

        Assertions.assertThat(this.service.cachedMute(STEVE)).isPresent();
    }

    @Test
    void onlinePlayersInsideAreKicked() throws Exception {
        final FakeOnlinePlayer inside = this.platform.join(STEVE, "Steve",
                InetAddress.getByName("10.0.0.7"));
        final FakeOnlinePlayer outside = this.platform.join(ALEX, "Alex", this.home);

        this.punish(PunishmentType.BAN, TEN, true);

        eventually(() -> inside.disconnectReason().isPresent());
        Assertions.assertThat(outside.disconnectReason()).isEmpty();
    }

    @Test
    void theCacheFollowsOtherInstancesAndRevocations() throws Exception {
        // Issued "elsewhere": stored, but this instance hasn't heard of it.
        final Punishment ban = this.punish(PunishmentType.BAN, TEN, false);
        Assertions.assertThat(this.join(STEVE, "10.0.0.1"))
                .isInstanceOf(LoginDecision.Allowed.class);

        this.syncReceiver().handle(SyncPacket.PunishmentEnforcePacket.of(ban.id())).join();
        Assertions.assertThat(this.join(STEVE, "10.0.0.1"))
                .isInstanceOf(LoginDecision.Denied.class);

        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(ban.id()),
                MODERATOR).build()).join();
        eventually(() -> this.ranges.all().isEmpty());
        Assertions.assertThat(this.join(STEVE, "10.0.0.1"))
                .isInstanceOf(LoginDecision.Allowed.class);
    }

    @Test
    void anExpungedRangeLeavesTheCache() throws Exception {
        final Punishment ban = this.punish(PunishmentType.BAN, TEN, false);
        this.syncReceiver().handle(SyncPacket.PunishmentEnforcePacket.of(ban.id())).join();
        Assertions.assertThat(this.ranges.all()).hasSize(1);

        // Expunged elsewhere: the row is gone, the packet says it was a range.
        this.punishments.expunge(ban.id(), MODERATOR, this.now.get()).join();
        this.syncReceiver().handle(new SyncPacket.PunishmentExpungedPacket(ban.id(), true))
                .join();

        Assertions.assertThat(this.ranges.all()).isEmpty();
        Assertions.assertThat(this.join(STEVE, "10.0.0.1"))
                .isInstanceOf(LoginDecision.Allowed.class);
    }

}
