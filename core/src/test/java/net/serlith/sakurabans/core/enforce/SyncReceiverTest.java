package net.serlith.sakurabans.core.enforce;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.Lockdown;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.storage.repository.AllowListRepository;
import net.serlith.sakurabans.core.storage.repository.LockdownRepository;
import net.serlith.sakurabans.core.storage.repository.OverrideRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Packets from another instance: this one does its own work and posts no event (D33). */
class SyncReceiverTest extends ServiceTest {

    private SyncReceiver receiver;
    private FakeOnlinePlayer steve;
    private FakeOnlinePlayer banStaff;
    private FakeOnlinePlayer revokeStaff;
    private FakeOnlinePlayer silentRevokeStaff;

    @BeforeEach
    void receiver() {
        this.receiver = this.syncReceiver();
        this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home, null)).join();
        eventually(() -> this.mutes.loaded(STEVE));
        this.steve = this.platform.join(STEVE, "Steve", this.home);
        this.banStaff = this.staff("BanStaff", Permissions.notify(PunishmentType.BAN));
        this.revokeStaff = this.staff("RevokeStaff",
                Permissions.notifyRevoke(PunishmentType.MUTE));
        this.silentRevokeStaff = this.staff("SilentStaff",
                Permissions.notifyRevoke(PunishmentType.MUTE),
                Permissions.notifySilent(PunishmentType.MUTE));
        this.names.put(STEVE, "Steve");
    }

    private FakeOnlinePlayer staff(final String name, final String... permissions) {
        return this.platform.join(UUID.randomUUID(), name, this.home).grant(permissions);
    }

    /** As if another instance issued it: stored, not enforced here. */
    private Punishment elsewhere(final PunishmentType type, final Scope scope) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(type,
                PlayerVictim.of(STEVE), MODERATOR).scope(scope).build(), PunishOptions.builder()
                .enforce(false).build()).join()).punishment();
    }

    private Punishment elsewhere(final PunishmentType type) {
        return this.elsewhere(type, GlobalScope.INSTANCE);
    }

    /** Handles the packet and checks that no subscriber heard of it. */
    private void handle(final SyncPacket packet) {
        this.events.clear();
        this.receiver.handle(packet).join();
        Assertions.assertThat(this.events).as("events posted for %s", packet).isEmpty();
    }

    private static List<String> texts(final FakeOnlinePlayer player) {
        return player.messages().stream().map(ServiceTest::plain).toList();
    }

    @Test
    void remoteBansKickAndAreAnnouncedWithoutAnEvent() {
        final Punishment ban = this.elsewhere(PunishmentType.BAN);
        final int before = this.banStaff.messages().size();
        Assertions.assertThat(this.steve.disconnectReason()).isEmpty();

        this.handle(SyncPacket.PunishmentEnforcePacket.of(ban.id()));

        Assertions.assertThat(this.steve.disconnectReason()).isPresent();
        Assertions.assertThat(texts(this.banStaff)).hasSize(before + 1).last()
                .satisfies(text -> Assertions.assertThat(text).contains("Mod", "banned",
                        "Steve"));
    }

    @Test
    void remoteMutesAndSilentUnmutesReachTheCacheAndTheSilentStaff() {
        final Punishment mute = this.elsewhere(PunishmentType.MUTE);
        Assertions.assertThat(this.service.cachedMute(STEVE)).isEmpty();

        this.handle(SyncPacket.PunishmentEnforcePacket.of(mute.id()));
        Assertions.assertThat(this.service.cachedMute(STEVE)).isPresent();

        // Lifted elsewhere: the row is ended, this instance only hears about it.
        this.punishments.end(mute.id(), EndKind.REVOKED, this.now.get(), MODERATOR, null)
                .join();
        final int revoke = this.revokeStaff.messages().size();
        final int silent = this.silentRevokeStaff.messages().size();
        this.handle(new SyncPacket.PunishmentRevokePacket(mute.id(), Visibility.SILENT));

        Assertions.assertThat(this.service.cachedMute(STEVE)).isEmpty();
        Assertions.assertThat(this.revokeStaff.messages()).hasSize(revoke);
        Assertions.assertThat(texts(this.silentRevokeStaff)).hasSize(silent + 1).last()
                .satisfies(text -> Assertions.assertThat(text).contains("unmuted", "Steve",
                        "[Silent]"));
    }

    @Test
    void anUnknownRevocationDoesNothing() {
        final Punishment ban = this.elsewhere(PunishmentType.BAN);
        final int console = this.platform.consoleMessages().size();

        // Still in force: the revocation hasn't reached the database this instance reads.
        this.handle(new SyncPacket.PunishmentRevokePacket(ban.id(), Visibility.NORMAL));

        Assertions.assertThat(this.platform.consoleMessages()).hasSize(console);
    }

    @Test
    void liftedOrOldPunishmentsAreNotReplayed() {
        final Punishment ban = this.elsewhere(PunishmentType.BAN);
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(ban.id()),
                MODERATOR).build()).join();
        this.handle(SyncPacket.PunishmentEnforcePacket.of(ban.id()));
        Assertions.assertThat(this.steve.disconnectReason()).isEmpty();

        final Punishment kick = this.elsewhere(PunishmentType.KICK);
        this.now.set(this.now.get().plus(Duration.ofMinutes(5)));
        this.handle(SyncPacket.PunishmentEnforcePacket.of(kick.id()));
        Assertions.assertThat(this.steve.disconnectReason()).isEmpty();
    }

    @Test
    void rescopedBansReachTheirNewPlayersAndOtherEditsDoNot() {
        final Punishment ban = this.elsewhere(PunishmentType.BAN, ServerScope.of("elsewhere"));
        this.handle(SyncPacket.PunishmentEnforcePacket.of(ban.id()));
        Assertions.assertThat(this.steve.disconnectReason()).isEmpty();
        final int notices = this.banStaff.messages().size();

        this.punishments.edit(ban.id(), PunishmentEdit.NewReason.of("new"), MODERATOR,
                this.now.get()).join();
        this.handle(new SyncPacket.PunishmentEditedPacket(ban.id(), false));
        Assertions.assertThat(this.steve.disconnectReason()).isEmpty();

        this.punishments.edit(ban.id(), PunishmentEdit.NewScope.of(GlobalScope.INSTANCE),
                MODERATOR,
                this.now.get()).join();
        this.handle(new SyncPacket.PunishmentEditedPacket(ban.id(), true));
        Assertions.assertThat(this.steve.disconnectReason()).isPresent();
        // An edit is announced by the instance that made it only, as before.
        Assertions.assertThat(this.banStaff.messages()).hasSize(notices);
    }

    @Test
    void voidedAndRolledBackPunishmentsLeaveTheCache() {
        final Punishment first = this.elsewhere(PunishmentType.MUTE);
        this.handle(SyncPacket.PunishmentEnforcePacket.of(first.id()));
        Assertions.assertThat(this.service.cachedMute(STEVE)).isPresent();
        final int console = this.platform.consoleMessages().size();

        this.punishments.end(first.id(), EndKind.ROLLED_BACK, this.now.get(), MODERATOR, null)
                .join();
        this.handle(new SyncPacket.PunishmentsEndedPacket(List.of(first.id(), 404L)));

        Assertions.assertThat(this.service.cachedMute(STEVE)).isEmpty();
        Assertions.assertThat(this.platform.consoleMessages()).hasSize(console);
    }

    @Test
    void anExpungedMuteLeavesTheCache() {
        final Punishment mute = this.elsewhere(PunishmentType.MUTE);
        this.handle(SyncPacket.PunishmentEnforcePacket.of(mute.id()));
        final int console = this.platform.consoleMessages().size();

        this.punishments.expunge(mute.id(), MODERATOR, this.now.get()).join();
        this.handle(new SyncPacket.PunishmentExpungedPacket(mute.id(), false));

        Assertions.assertThat(this.service.cachedMute(STEVE)).isEmpty();
        Assertions.assertThat(this.platform.consoleMessages()).hasSize(console);
    }

    @Test
    void erasedPlayersAreForgotten() {
        Assertions.assertThat(this.names.name(STEVE)).isPresent();

        this.handle(new SyncPacket.PlayerDataErasedPacket(STEVE));

        Assertions.assertThat(this.names.name(STEVE)).isEmpty();
    }

    @Test
    void theAllowListLockdownsAndSettingsAreReadAgain() {
        new AllowListRepository(this.storage).add(ALEX, MODERATOR, this.now.get(), null).join();
        new LockdownRepository(this.storage).start(new Lockdown(GlobalScope.INSTANCE, "raid",
                MODERATOR, this.now.get(), null)).join();
        new OverrideRepository(this.storage).changeSettings(GlobalScope.INSTANCE,
                Map.of("display.id-style", this.config.encode("BOTH")), 1, MODERATOR,
                this.now.get()).join();
        Assertions.assertThat(this.allowList.isAllowed(ALEX)).isFalse();
        Assertions.assertThat(this.lockdowns.lockdowns()).isEmpty();

        this.handle(new SyncPacket.AllowListChangedPacket());
        this.handle(new SyncPacket.LockdownChangedPacket());
        this.handle(new SyncPacket.EntityChangedPacket(SubjectType.SETTINGS));

        Assertions.assertThat(this.allowList.isAllowed(ALEX)).isTrue();
        Assertions.assertThat(this.lockdowns.lockdowns()).singleElement()
                .satisfies(lockdown -> Assertions.assertThat(lockdown.reason())
                        .isEqualTo("raid"));
        Assertions.assertThat(this.config.settings().display().idStyle().name())
                .isEqualTo("BOTH");
    }

    @Test
    void anImportReloadsTheCachesAndAppliesBans() {
        new AllowListRepository(this.storage).add(ALEX, MODERATOR, this.now.get(), null).join();
        this.elsewhere(PunishmentType.BAN);

        this.handle(new SyncPacket.ImportedPacket());

        Assertions.assertThat(this.allowList.isAllowed(ALEX)).isTrue();
        Assertions.assertThat(this.steve.disconnectReason()).isPresent();
    }

    @Test
    void catchingUpReloadsMutesAndAppliesBans() {
        this.elsewhere(PunishmentType.MUTE);
        this.elsewhere(PunishmentType.BAN);

        this.receiver.missed().join();

        Assertions.assertThat(this.steve.disconnectReason()).isPresent();
        Assertions.assertThat(this.service.cachedMute(STEVE)).isPresent();
    }

    @Test
    void cachedMutesAreReadAgainAfterOneMinute() {
        this.elsewhere(PunishmentType.MUTE);
        this.sweeper.sweep().join();
        Assertions.assertThat(this.service.cachedMute(STEVE)).isEmpty();

        this.now.set(this.now.get().plus(Duration.ofMinutes(2)));
        this.sweeper.sweep().join();

        eventually(() -> this.service.cachedMute(STEVE).isPresent());
    }

}
