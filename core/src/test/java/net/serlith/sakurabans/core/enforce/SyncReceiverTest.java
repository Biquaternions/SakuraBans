package net.serlith.sakurabans.core.enforce;

import java.time.Duration;
import net.serlith.sakurabans.api.event.PunishmentCreatedEvent;
import net.serlith.sakurabans.api.event.PunishmentEditedEvent;
import net.serlith.sakurabans.api.event.PunishmentExpungedEvent;
import net.serlith.sakurabans.api.event.PunishmentRevokedEvent;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.EditRequest;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.LoginAttempt;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Packets from another instance, applied here. */
class SyncReceiverTest extends ServiceTest {

    private SyncReceiver receiver;
    private FakeOnlinePlayer steve;

    @BeforeEach
    void receiver() {
        this.receiver = this.syncReceiver();
        this.loginGate.check(new LoginAttempt(STEVE, "Steve", this.home, null)).join();
        eventually(() -> this.mutes.loaded(STEVE));
        this.steve = this.platform.join(STEVE, "Steve", this.home);
        this.events.clear();
    }

    /** As if another instance issued it: stored, not enforced here. */
    private Punishment elsewhere(final PunishmentType type) {
        return ((PunishResult.Success) this.service.punish(DraftPunishment.builder(type,
                PlayerVictim.of(STEVE), MODERATOR).build(), PunishOptions.builder()
                .enforce(false).build()).join()).punishment();
    }

    @Test
    void remoteBansKickAndAreAnnouncedAsRemote() {
        final Punishment ban = this.elsewhere(PunishmentType.BAN);
        this.events.clear();
        Assertions.assertThat(this.steve.disconnectReason()).isEmpty();

        this.receiver.handle(SyncPacket.PunishmentEnforcePacket.of(ban.id())).join();

        Assertions.assertThat(this.steve.disconnectReason()).isPresent();
        Assertions.assertThat(this.events(PunishmentCreatedEvent.class)).singleElement()
                .satisfies(event -> Assertions.assertThat(event.remote()).isTrue());
    }

    @Test
    void remoteMutesAndUnmutesReachTheCache() {
        final Punishment mute = this.elsewhere(PunishmentType.MUTE);
        Assertions.assertThat(this.service.cachedMute(STEVE)).isEmpty();

        this.receiver.handle(SyncPacket.PunishmentEnforcePacket.of(mute.id())).join();
        Assertions.assertThat(this.service.cachedMute(STEVE)).isPresent();

        // Lifted elsewhere: the row is ended, this instance only hears about it.
        this.punishments.end(mute.id(), EndKind.REVOKED,
                this.now.get(), MODERATOR, null).join();
        this.receiver.handle(new SyncPacket.PunishmentRevokePacket(mute.id(),
                Visibility.SILENT)).join();

        Assertions.assertThat(this.service.cachedMute(STEVE)).isEmpty();
        Assertions.assertThat(this.events(PunishmentRevokedEvent.class)).singleElement()
                .satisfies(event -> Assertions.assertThat(event.remote()).isTrue());
    }

    @Test
    void liftedOrOldPunishmentsAreNotReplayed() {
        final Punishment ban = this.elsewhere(PunishmentType.BAN);
        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(ban.id()),
                MODERATOR).build()).join();
        this.receiver.handle(SyncPacket.PunishmentEnforcePacket.of(ban.id())).join();
        Assertions.assertThat(this.steve.disconnectReason()).isEmpty();

        final Punishment kick = this.elsewhere(PunishmentType.KICK);
        this.now.set(this.now.get().plus(Duration.ofMinutes(5)));
        this.receiver.handle(SyncPacket.PunishmentEnforcePacket.of(kick.id())).join();
        Assertions.assertThat(this.steve.disconnectReason()).isEmpty();
    }

    @Test
    void catchingUpReloadsMutesAndAppliesBans() {
        this.elsewhere(PunishmentType.MUTE);
        this.elsewhere(PunishmentType.BAN);

        this.receiver.missed().join();

        Assertions.assertThat(this.steve.disconnectReason()).isPresent();
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

    @Test
    void remoteEditsVoidsAndExpungesArriveWithTheirValues() {
        final Punishment ban = this.elsewhere(PunishmentType.BAN);
        this.service.edit(EditRequest.builder(ban.id(), PunishmentEdit.NewReason.of("new"),
                MODERATOR).build()).join();
        this.events.clear();

        this.receiver.handle(new SyncPacket.PunishmentEditedPacket(ban.id(),
                PunishmentEdit.NewReason.of(ban.reason()), PunishmentEdit.NewReason.of("new")))
                .join();
        Assertions.assertThat(this.events(PunishmentEditedEvent.class)).singleElement()
                .satisfies(event -> {
                    Assertions.assertThat(event.remote()).isTrue();
                    Assertions.assertThat(event.previous().reason()).isEqualTo(ban.reason());
                    Assertions.assertThat(event.punishment().reason()).isEqualTo("new");
                });

        final Punishment gone = this.service.byId(ban.id()).join().orElseThrow();
        this.receiver.handle(new SyncPacket.PunishmentExpungedPacket(gone)).join();
        Assertions.assertThat(this.events(PunishmentExpungedEvent.class)).singleElement()
                .satisfies(event -> Assertions.assertThat(event.punishment()).isEqualTo(gone));
    }
}
