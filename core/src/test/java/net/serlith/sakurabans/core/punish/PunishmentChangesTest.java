package net.serlith.sakurabans.core.punish;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.AuditEntry;
import net.serlith.sakurabans.api.audit.AuditQuery;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.event.PunishmentEditEvent;
import net.serlith.sakurabans.api.event.PunishmentEditedEvent;
import net.serlith.sakurabans.api.event.PunishmentExpungedEvent;
import net.serlith.sakurabans.api.event.PunishmentVoidedEvent;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.EditRequest;
import net.serlith.sakurabans.api.punish.EditResult;
import net.serlith.sakurabans.api.punish.ExpungeResult;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.punish.VoidResult;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.audit.SakuraAuditService;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Edits, voids, expunges, evidence and their audit trail (plan S35). */
class PunishmentChangesTest extends ServiceTest {

    private Punishment punished(final PunishmentType type, final Duration duration) {
        final PunishResult result = this.service.punish(DraftPunishment.builder(type,
                PlayerVictim.of(STEVE), MODERATOR).duration(duration).reason("first").build(),
                PunishOptions.defaults()).join();
        Assertions.assertThat(result).isInstanceOf(PunishResult.Success.class);
        return ((PunishResult.Success) result).punishment();
    }

    private EditResult edit(final long id, final PunishmentEdit edit) {
        return this.service.edit(EditRequest.builder(id, edit, MODERATOR).build()).join();
    }

    private static Punishment changed(final EditResult result) {
        Assertions.assertThat(result).isInstanceOf(EditResult.Success.class);
        return ((EditResult.Success) result).punishment();
    }

    private static EditResult.Failure.Reason failure(final EditResult result) {
        Assertions.assertThat(result).isInstanceOf(EditResult.Failure.class);
        return ((EditResult.Failure) result).reason();
    }

    private List<AuditEntry> audit(final long id) {
        return new SakuraAuditService(new AuditRepository(this.storage)).query(AuditQuery
                .builder().subject(SubjectType.PUNISHMENT, Long.toString(id)).build())
                .join().items().reversed();
    }

    @Test
    void reasonsChangeWithVersionsEventsSyncAndAudit() {
        final Punishment ban = this.punished(PunishmentType.BAN, Duration.ofDays(1));
        this.published.clear();

        final Punishment edited = changed(this.edit(ban.id(),
                PunishmentEdit.NewReason.of("griefing\nspawn")));
        Assertions.assertThat(edited.reason()).isEqualTo("griefing spawn");
        Assertions.assertThat(edited.version()).isEqualTo(ban.version() + 1);
        Assertions.assertThat(this.published).containsExactly(
                new SyncPacket.PunishmentEditedPacket(ban.id(),
                        PunishmentEdit.NewReason.of("first"),
                        PunishmentEdit.NewReason.of("griefing spawn")));
        Assertions.assertThat(this.events(PunishmentEditEvent.class)).hasSize(1);
        Assertions.assertThat(this.events(PunishmentEditedEvent.class)).singleElement()
                .satisfies(event -> {
                    Assertions.assertThat(event.previous().reason()).isEqualTo("first");
                    Assertions.assertThat(event.punishment()).isEqualTo(edited);
                    Assertions.assertThat(event.remote()).isFalse();
                });

        // A stale version conflicts; a blank reason means the default one.
        final EditResult stale = this.service.edit(EditRequest.builder(ban.id(),
                PunishmentEdit.NewReason.of("x"), MODERATOR).expectedVersion(ban.version())
                .build()).join();
        Assertions.assertThat(failure(stale)).isEqualTo(EditResult.Failure.Reason.CONFLICT);
        Assertions.assertThat(((EditResult.Failure) stale).currentVersion())
                .hasValue(edited.version());
        Assertions.assertThat(changed(this.edit(ban.id(), PunishmentEdit.NewReason.of(" ")))
                .reason()).isEqualTo("No reason specified");

        Assertions.assertThat(this.audit(ban.id())).extracting(AuditEntry::action)
                .containsExactly(AuditAction.CREATE, AuditAction.EDIT_REASON,
                        AuditAction.EDIT_REASON);
        Assertions.assertThat(this.audit(ban.id()).get(1)).satisfies(entry -> {
            Assertions.assertThat(entry.oldValue()).contains("first");
            Assertions.assertThat(entry.newValue()).contains("griefing spawn");
            Assertions.assertThat(entry.actor()).isEqualTo(MODERATOR);
        });
    }

    @Test
    void expiriesAndScopesChangeOnlyWhileActive() {
        final Punishment mute = this.punished(PunishmentType.MUTE, Duration.ofHours(1));
        final Instant later = mute.createdAt().plus(Duration.ofDays(2));

        Assertions.assertThat(changed(this.edit(mute.id(), PunishmentEdit.NewExpiry.of(later)))
                .expiresAt()).contains(later);
        Assertions.assertThat(failure(this.edit(mute.id(),
                PunishmentEdit.NewExpiry.of(mute.createdAt().minusSeconds(1)))))
                .isEqualTo(EditResult.Failure.Reason.INVALID_EXPIRY);
        Assertions.assertThat(changed(this.edit(mute.id(), PunishmentEdit.NewExpiry.of(null)))
                .permanent()).isTrue();

        // Another mute already holds server:survival.
        this.service.punish(DraftPunishment.builder(PunishmentType.MUTE, PlayerVictim.of(STEVE),
                MODERATOR).scope(ServerScope.of("survival")).build(), PunishOptions.defaults())
                .join();
        Assertions.assertThat(failure(this.edit(mute.id(),
                PunishmentEdit.NewScope.of(ServerScope.of("survival")))))
                .isEqualTo(EditResult.Failure.Reason.ALREADY_PUNISHED);
        Assertions.assertThat(changed(this.edit(mute.id(),
                PunishmentEdit.NewScope.of(ServerScope.of("lobby")))).scope())
                .isEqualTo(ServerScope.of("lobby"));
        Assertions.assertThat(this.punishments.active(PunishmentType.MUTE,
                PlayerVictim.of(STEVE), ServerScope.of("lobby"), this.clock.instant()).join())
                .extracting(Punishment::id).containsExactly(mute.id());

        this.service.revoke(RevocationRequest.builder(RevocationTarget.ById.of(mute.id()),
                MODERATOR).build()).join();
        Assertions.assertThat(failure(this.edit(mute.id(),
                PunishmentEdit.NewScope.of(GlobalScope.INSTANCE))))
                .isEqualTo(EditResult.Failure.Reason.NOT_ACTIVE);
        Assertions.assertThat(this.audit(mute.id())).extracting(AuditEntry::action)
                .containsExactly(AuditAction.CREATE, AuditAction.EDIT_EXPIRY,
                        AuditAction.EDIT_EXPIRY, AuditAction.EDIT_SCOPE, AuditAction.REVOKE);
    }

    @Test
    void editedMutesReachTheCacheWithoutAnotherMessage() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);
        this.mutes.load(STEVE, this.home).join();
        final Punishment mute = this.punished(PunishmentType.MUTE, Duration.ofHours(1));
        final int messages = steve.messages().size();

        changed(this.edit(mute.id(), PunishmentEdit.NewReason.of("spam")));
        Assertions.assertThat(this.mutes.activeMute(STEVE)).hasValueSatisfying(cached ->
                Assertions.assertThat(cached.reason()).isEqualTo("spam"));
        Assertions.assertThat(steve.messages()).hasSize(messages);
    }

    @Test
    void voidedPunishmentsEndAndLeaveQueriesAndCounts() {
        final FakeOnlinePlayer steve = this.platform.join(STEVE, "Steve", this.home);
        this.mutes.load(STEVE, this.home).join();
        final Punishment mute = this.punished(PunishmentType.MUTE, Duration.ofHours(1));
        Assertions.assertThat(this.mutes.activeMute(STEVE)).isPresent();
        this.published.clear();

        final VoidResult result = this.service.voidPunishment(mute.id(), MODERATOR,
                "wrong player").join();
        Assertions.assertThat(result).isInstanceOfSatisfying(VoidResult.Success.class,
                success -> {
                    final Punishment voided = success.punishment();
                    Assertions.assertThat(voided.voiding()).hasValueSatisfying(voiding ->
                            Assertions.assertThat(voiding.reason()).contains("wrong player"));
                    Assertions.assertThat(voided.end().orElseThrow().kind())
                            .isEqualTo(EndKind.VOIDED);
                });
        Assertions.assertThat(this.mutes.activeMute(STEVE)).isEmpty();
        Assertions.assertThat(this.published).containsExactly(
                new SyncPacket.PunishmentVoidedPacket(mute.id()));
        Assertions.assertThat(this.events(PunishmentVoidedEvent.class)).hasSize(1);
        Assertions.assertThat(this.service.count(PunishmentQuery.builder().build()).join())
                .isZero();
        Assertions.assertThat(this.service.count(PunishmentQuery.builder().includeVoided(true)
                .build()).join()).isEqualTo(1);
        Assertions.assertThat(this.service.voidPunishment(mute.id(), MODERATOR, null).join())
                .isInstanceOfSatisfying(VoidResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(VoidResult.Failure.Reason.ALREADY_VOIDED));
        Assertions.assertThat(steve.messages()).isNotEmpty();
    }

    @Test
    void expungedPunishmentsAreGoneButAudited() {
        final Punishment ban = this.punished(PunishmentType.BAN, Duration.ofDays(1));
        this.service.attachEvidence(ban.id(), EvidenceRef.of("https://example.org/1"),
                MODERATOR).join();
        this.published.clear();

        final ExpungeResult result = this.service.expunge(ban.id(), MODERATOR).join();
        Assertions.assertThat(result).isInstanceOf(ExpungeResult.Success.class);
        Assertions.assertThat(this.service.byId(ban.id()).join()).isEmpty();
        Assertions.assertThat(this.published).singleElement().isInstanceOfSatisfying(
                SyncPacket.PunishmentExpungedPacket.class, packet ->
                        Assertions.assertThat(packet.punishment().id()).isEqualTo(ban.id()));
        Assertions.assertThat(this.events(PunishmentExpungedEvent.class)).hasSize(1);
        Assertions.assertThat(this.audit(ban.id()).getLast()).satisfies(entry -> {
            Assertions.assertThat(entry.action()).isEqualTo(AuditAction.EXPUNGE);
            Assertions.assertThat(entry.oldValue()).hasValueSatisfying(old ->
                    Assertions.assertThat(old).contains("BAN", "ban", STEVE.toString()));
        });
        Assertions.assertThat(this.service.expunge(ban.id(), MODERATOR).join())
                .isInstanceOfSatisfying(ExpungeResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(ExpungeResult.Failure.Reason.NOT_FOUND));
    }

    @Test
    void evidenceIsAttachedOnceAndDetached() {
        final Punishment ban = this.punished(PunishmentType.BAN, Duration.ofDays(1));
        final EvidenceRef ref = EvidenceRef.of("hydra:chat-snapshot:12");

        final Punishment attached = changed(this.service.attachEvidence(ban.id(), ref,
                MODERATOR).join());
        Assertions.assertThat(attached.evidence()).singleElement().satisfies(evidence -> {
            Assertions.assertThat(evidence.ref()).isEqualTo(ref);
            Assertions.assertThat(evidence.addedBy()).isEqualTo(MODERATOR);
        });
        Assertions.assertThat(failure(this.service.attachEvidence(ban.id(), ref, MODERATOR)
                .join())).isEqualTo(EditResult.Failure.Reason.EVIDENCE_ALREADY_ATTACHED);
        Assertions.assertThat(changed(this.service.detachEvidence(ban.id(), ref, MODERATOR)
                .join()).evidence()).isEmpty();
        Assertions.assertThat(failure(this.service.detachEvidence(ban.id(), ref, MODERATOR)
                .join())).isEqualTo(EditResult.Failure.Reason.EVIDENCE_NOT_ATTACHED);
        Assertions.assertThat(this.published).filteredOn(
                SyncPacket.EvidenceChangedPacket.class::isInstance).hasSize(2);
    }

    @Test
    void changesAreAuthorizedAndCancellable() {
        final Punishment ban = this.punished(PunishmentType.BAN, Duration.ofDays(1));

        this.actionCheck = (actor, action) -> java.util.concurrent.CompletableFuture
                .completedFuture(Decision.deny(Decision.Denial.NO_PERMISSION, null));
        Assertions.assertThat(failure(this.edit(ban.id(), PunishmentEdit.NewReason.of("x"))))
                .isEqualTo(EditResult.Failure.Reason.FORBIDDEN);
        Assertions.assertThat(this.service.expunge(ban.id(), MODERATOR).join())
                .isInstanceOfSatisfying(ExpungeResult.Failure.class, failure ->
                        Assertions.assertThat(failure.reason())
                                .isEqualTo(ExpungeResult.Failure.Reason.FORBIDDEN));
        this.actionCheck = (actor, action) -> java.util.concurrent.CompletableFuture
                .completedFuture(Decision.deny(Decision.Denial.EXEMPT, null));
        Assertions.assertThat(failure(this.edit(ban.id(), PunishmentEdit.NewReason.of("x"))))
                .isEqualTo(EditResult.Failure.Reason.EXEMPT);
        this.actionCheck = ActionCheck.ALLOW_ALL;

        this.bus.subscribe(PunishmentEditEvent.class, event -> event.cancelled(true));
        Assertions.assertThat(failure(this.edit(ban.id(), PunishmentEdit.NewReason.of("x"))))
                .isEqualTo(EditResult.Failure.Reason.CANCELLED);
        Assertions.assertThat(failure(this.edit(999, PunishmentEdit.NewReason.of("x"))))
                .isEqualTo(EditResult.Failure.Reason.NOT_FOUND);
    }
}
