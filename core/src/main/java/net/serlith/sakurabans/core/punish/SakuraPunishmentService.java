package net.serlith.sakurabans.core.punish;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Evidence;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.EditRequest;
import net.serlith.sakurabans.api.punish.EditResult;
import net.serlith.sakurabans.api.punish.Enforcement;
import net.serlith.sakurabans.api.punish.ExpungeResult;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.api.punish.PunishmentService;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.punish.RevokeResult;
import net.serlith.sakurabans.api.punish.RollbackRequest;
import net.serlith.sakurabans.api.punish.RollbackResult;
import net.serlith.sakurabans.api.punish.VoidResult;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraPunishmentChangeEvents;
import net.serlith.sakurabans.core.event.SakuraPunishmentCreateEvent;
import net.serlith.sakurabans.core.event.SakuraPunishmentCreatedEvent;
import net.serlith.sakurabans.core.event.SakuraPunishmentRevokeEvent;
import net.serlith.sakurabans.core.event.SakuraPunishmentRevokedEvent;
import net.serlith.sakurabans.core.event.SakuraPunishmentsRolledBackEvent;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SakuraChangeResults;
import net.serlith.sakurabans.core.model.SakuraPunishResult;
import net.serlith.sakurabans.core.model.SakuraRevokeResult;
import net.serlith.sakurabans.core.model.SakuraRollbackResult;
import net.serlith.sakurabans.core.permission.ExemptionService;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.DisplayIdCodec;
import net.serlith.sakurabans.core.storage.repository.ChangeOutcome;
import net.serlith.sakurabans.core.storage.repository.InsertOutcome;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.util.Futures;
import org.jspecify.annotations.Nullable;

public final class SakuraPunishmentService implements PunishmentService {

    private final Lifecycle lifecycle;
    private final Executor worker;
    private final InstantSource clock;
    private final ConfigService config;
    private final SakuraScopeService scopes;
    private final PunishmentRepository punishments;
    private final PunishmentFactory factory;
    private final MuteCache mutes;
    private final Enforcer enforcer;
    private final ActionCheck actions;
    private final PunishLimits limits;
    private final ExemptionService exemption;
    private final SakuraEventBus events;
    private final SyncService sync;
    private final DisplayIdCodec displayIds;

    public SakuraPunishmentService(final Lifecycle lifecycle, final Executor worker,
            final InstantSource clock, final ConfigService config,
            final SakuraScopeService scopes, final PunishmentRepository punishments,
            final PunishmentFactory factory, final MuteCache mutes, final Enforcer enforcer,
            final ActionCheck actions, final PunishLimits limits,
            final ExemptionService exemption, final SakuraEventBus events,
            final SyncService sync, final DisplayIdCodec displayIds) {
        this.lifecycle = lifecycle;
        this.worker = worker;
        this.clock = clock;
        this.config = config;
        this.scopes = scopes;
        this.punishments = punishments;
        this.factory = factory;
        this.mutes = mutes;
        this.enforcer = enforcer;
        this.actions = actions;
        this.limits = limits;
        this.exemption = exemption;
        this.events = events;
        this.sync = sync;
        this.displayIds = displayIds;
    }

    @Override
    public CompletableFuture<PunishResult> punish(final DraftPunishment draft,
            final PunishOptions options) {
        final DraftPunishment checkedDraft;
        final PunishOptions checkedOptions;
        try {
            checkedDraft = ApiValues.draft(draft);
            checkedOptions = ApiValues.options(options);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.whenRunning()
                .thenCompose(ignored -> this.authorize(checkedDraft.actor(), Action.Punish.of(
                        checkedDraft.kind().orElseGet(() ->
                                BuiltInKinds.defaultFor(checkedDraft.type())),
                        checkedDraft.scope().orElse(null))))
                .thenCompose(allowed -> allowed
                        ? this.prepareAndStore(checkedDraft, checkedOptions)
                        : CompletableFuture.completedFuture(new SakuraPunishResult.Failure(
                                PunishResult.Failure.Reason.FORBIDDEN)));
    }

    private CompletableFuture<PunishResult> prepareAndStore(final DraftPunishment draft,
            final PunishOptions options) {
        return this.prepareLimited(draft, options, null).thenComposeAsync(prepared -> {
            if (prepared instanceof PunishmentFactory.Prepared.Refused(PunishResult.Failure.Reason reason)) {
                return CompletableFuture.completedFuture(
                        new SakuraPunishResult.Failure(reason));
            }
            final PunishmentFactory.Prepared.Ready ready =
                    (PunishmentFactory.Prepared.Ready) prepared;
            final DraftPunishment resolved = PunishmentFactory.asDraft(ready.punishment());
            final SakuraPunishmentCreateEvent event =
                    this.events.post(new SakuraPunishmentCreateEvent(resolved, options));
            if (event.cancelled()) {
                return CompletableFuture.completedFuture(
                        new SakuraPunishResult.Failure(PunishResult.Failure.Reason.CANCELLED));
            }
            if (event.draft() == resolved && event.options() == options) {
                return this.store(ready, event.options());
            }
            return this.prepareLimited(event.draft(), event.options(), ready)
                    .thenCompose(again -> again instanceof PunishmentFactory.Prepared.Ready
                            readyAgain
                            ? this.store(readyAgain, event.options())
                            : CompletableFuture.completedFuture(new SakuraPunishResult.Failure(
                                    ((PunishmentFactory.Prepared.Refused) again).reason())));
        }, this.worker);
    }

    private CompletableFuture<PunishmentFactory.Prepared> prepareLimited(
            final DraftPunishment draft, final PunishOptions options,
            final PunishmentFactory.Prepared.@Nullable Ready original) {
        return this.factory.prepare(draft, options).thenCompose(prepared -> {
            if (!(prepared instanceof PunishmentFactory.Prepared.Ready ready)) {
                return CompletableFuture.completedFuture(prepared);
            }
            return this.limits.apply(original == null ? ready : keepTemplate(ready, original),
                    options);
        }).thenCompose(this::unlessExempt);
    }

    private CompletableFuture<PunishmentFactory.Prepared> unlessExempt(
            final PunishmentFactory.Prepared prepared) {
        if (!(prepared instanceof PunishmentFactory.Prepared.Ready ready)) {
            return CompletableFuture.completedFuture(prepared);
        }
        final UUID victim = switch (ready.punishment().victim()) {
            case PlayerVictim player -> player.uuid();
            case CompositeVictim composite -> composite.uuid();
            default -> null;
        };
        if (victim == null) {
            return CompletableFuture.completedFuture(prepared);
        }
        return this.exemption.isExempt(ready.punishment().actor(), victim,
                ready.punishment().type()).thenApply(exempt -> exempt
                        ? new PunishmentFactory.Prepared.Refused(
                                PunishResult.Failure.Reason.EXEMPT)
                        : prepared);
    }

    private static PunishmentFactory.Prepared.Ready keepTemplate(
            final PunishmentFactory.Prepared.Ready replaced,
            final PunishmentFactory.Prepared.Ready original) {
        if (replaced.punishment().template() != null
                || original.punishment().template() == null) {
            return replaced;
        }
        return new PunishmentFactory.Prepared.Ready(replaced.punishment().withTemplate(
                original.punishment().template(), original.punishment().templateStep()),
                replaced.kind());
    }

    private CompletableFuture<PunishResult> store(final PunishmentFactory.Prepared.Ready ready,
            final PunishOptions options) {
        return this.punishments.insert(ready.punishment(), options.override())
                .thenCompose(outcome -> switch (outcome) {
                    case InsertOutcome.Rejected rejected -> CompletableFuture.completedFuture(
                            new SakuraPunishResult.Failure(
                                    PunishResult.Failure.Reason.ALREADY_PUNISHED,
                                    rejected.existing()));
                    case InsertOutcome.Inserted inserted -> this.created(inserted, options);
                });
    }

    private CompletableFuture<PunishResult> created(final InsertOutcome.Inserted inserted,
            final PunishOptions options) {
        final Punishment punishment = inserted.punishment();
        final Punishment superseded = inserted.superseded();
        this.limits.issued(punishment);
        final CompletableFuture<Void> local;
        if (options.enforcement() == Enforcement.NONE) {
            local = CompletableFuture.completedFuture(null);
        } else {
            final CompletableFuture<Void> before = superseded == null
                    ? CompletableFuture.completedFuture(null)
                    : this.enforcer.ended(superseded);
            local = before.thenCompose(ignored -> this.enforcer.created(punishment));
        }
        if (options.enforcement() == Enforcement.GLOBAL) {
            this.sync.publish(new SyncPacket.PunishmentEnforcePacket(punishment.id(),
                    superseded == null ? null : superseded.id()));
        }
        return local.thenApplyAsync(ignored -> {
            this.events.post(new SakuraPunishmentCreatedEvent(punishment, superseded, false));
            return new SakuraPunishResult.Success(punishment, superseded);
        }, this.worker);
    }

    @Override
    public CompletableFuture<RevokeResult> revoke(final RevocationRequest request) {
        final RevocationRequest checked;
        try {
            checked = ApiValues.revocation(request);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (checked.reason().map(String::isBlank).orElse(true)
                && this.config.settings().reasons().requiredForRevoke()) {
            return CompletableFuture.completedFuture(
                    new SakuraRevokeResult.Failure(RevokeResult.Failure.Reason.REASON_REQUIRED));
        }
        return this.whenRunning()
                .thenCompose(ignored -> this.targets(checked.target()))
                .thenCompose(found -> this.revokeFound(checked, found));
    }

    private CompletableFuture<List<Punishment>> targets(final RevocationTarget target) {
        return switch (target) {
            case RevocationTarget.ById byId -> this.punishments.byId(byId.id())
                    .thenApply(found -> found
                            .filter(punishment -> punishment.isActiveAt(this.clock.instant()))
                            .map(List::of)
                            .orElse(List.of()));
            case RevocationTarget.ByVictim byVictim -> this.punishments.active(byVictim.type(),
                    byVictim.victim(), byVictim.scope().orElse(null), this.clock.instant());
        };
    }

    private CompletableFuture<RevokeResult> revokeFound(final RevocationRequest request,
            final List<Punishment> found) {
        if (found.isEmpty()) {
            return failure(RevokeResult.Failure.Reason.NOT_FOUND);
        }
        final List<Punishment> candidates = request.ownOnly()
                ? found.stream().filter(punishment -> ApiValues.sameOperator(
                        punishment.operator(), request.actor().operator())).toList()
                : found;
        if (candidates.isEmpty()) {
            return failure(RevokeResult.Failure.Reason.NOT_OWN);
        }
        final List<CompletableFuture<Decision>> checks = candidates.stream()
                .map(punishment -> this.actions.check(request.actor(), Action.ChangePunishment
                        .of(Action.ChangePunishment.Operation.REVOKE, punishment.id())))
                .toList();
        return Futures.allOf(checks).thenComposeAsync(decisions -> {
            final Decision denied = decisions.stream().filter(decision -> !decision.allowed())
                    .findFirst().orElse(null);
            if (denied != null) {
                return failure(denied.denial().orElse(null) == Decision.Denial.EXEMPT
                        ? RevokeResult.Failure.Reason.EXEMPT
                        : RevokeResult.Failure.Reason.FORBIDDEN);
            }
            final List<Punishment> approved = new ArrayList<>();
            for (final Punishment punishment : candidates) {
                if (!this.events.post(new SakuraPunishmentRevokeEvent(punishment, request))
                        .cancelled()) {
                    approved.add(punishment);
                }
            }
            if (approved.isEmpty()) {
                return failure(RevokeResult.Failure.Reason.CANCELLED);
            }
            return this.end(approved, request);
        }, this.worker);
    }

    private CompletableFuture<RevokeResult> end(final List<Punishment> approved,
            final RevocationRequest request) {
        final List<CompletableFuture<Optional<Punishment>>> ends = approved.stream()
                .map(punishment -> this.punishments.end(punishment.id(), EndKind.REVOKED,
                        this.clock.instant(), request.actor(), request.reason().orElse(null)))
                .toList();
        return Futures.allOf(ends).thenComposeAsync(results -> {
            // Empty: another instance or the sweeper ended it first.
            final List<Punishment> revoked = results.stream().flatMap(Optional::stream).toList();
            if (revoked.isEmpty()) {
                return failure(RevokeResult.Failure.Reason.NOT_FOUND);
            }
            final List<CompletableFuture<Void>> local = new ArrayList<>();
            for (final Punishment punishment : revoked) {
                local.add(this.enforcer.ended(punishment));
                this.sync.publish(new SyncPacket.PunishmentRevokePacket(punishment.id(),
                        request.visibility()));
            }
            return CompletableFuture.allOf(local.toArray(CompletableFuture[]::new))
                    .thenApplyAsync(ignored -> {
                        for (final Punishment punishment : revoked) {
                            this.events.post(new SakuraPunishmentRevokedEvent(punishment,
                                    request.visibility(), false));
                        }
                        return new SakuraRevokeResult.Success(revoked);
                    }, this.worker);
        }, this.worker);
    }

    @Override
    public CompletableFuture<EditResult> edit(final EditRequest request) {
        final EditRequest checked;
        try {
            checked = ApiValues.edit(request);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        final PunishmentEdit edit = this.normalized(checked.edit());
        return this.changeable(checked.id(), checked.actor(),
                Action.ChangePunishment.Operation.EDIT).thenComposeAsync(found -> {
                    if (found instanceof Changeable.Refused(String reason)) {
                        return CompletableFuture.completedFuture(editFailure(reason));
                    }
                    final Punishment punishment = ((Changeable.Found) found).punishment();
                    if (this.events.post(new SakuraPunishmentChangeEvents.Edit(punishment,
                            checked)).cancelled()) {
                        return CompletableFuture.completedFuture(
                                editFailure(EditResult.Failure.Reason.CANCELLED));
                    }
                    return this.punishments.edit(checked.id(), edit, checked.actor(),
                                    expected(checked.expectedVersion()), this.clock.instant())
                            .thenCompose(outcome -> this.edited(outcome, edit));
                }, this.worker);
    }

    private CompletableFuture<EditResult> edited(final ChangeOutcome outcome,
            final PunishmentEdit edit) {
        if (!(outcome instanceof ChangeOutcome.Changed(Punishment previous, Punishment current))) {
            return CompletableFuture.completedFuture(editFailure(outcome));
        }
        this.sync.publish(new SyncPacket.PunishmentEditedPacket(current.id(),
                previousValue(previous, edit), edit));
        return this.enforcer.changed(previous, current).thenApplyAsync(ignored -> {
            this.events.post(new SakuraPunishmentChangeEvents.Edited(previous,
                    current, edit, false));
            return new SakuraChangeResults.EditSuccess(current);
        }, this.worker);
    }

    public static PunishmentEdit previousValue(final Punishment previous,
            final PunishmentEdit edit) {
        return switch (edit) {
            case PunishmentEdit.NewReason reason -> PunishmentEdit.NewReason.of(previous.reason());
            case PunishmentEdit.NewExpiry expiry ->
                    PunishmentEdit.NewExpiry.of(previous.expiresAt().orElse(null));
            case PunishmentEdit.NewScope scope -> PunishmentEdit.NewScope.of(previous.scope());
        };
    }

    private PunishmentEdit normalized(final PunishmentEdit edit) {
        if (!(edit instanceof PunishmentEdit.NewReason reason)) {
            return edit;
        }
        final Settings.Reasons settings = this.config.settings().reasons();
        if (reason.reason().isBlank()) {
            return PunishmentEdit.NewReason.of(settings.defaultReason());
        }
        return PunishmentEdit.NewReason.of(settings.allowNewlines()
                ? reason.reason()
                : reason.reason().replaceAll("\\R+", " "));
    }

    @Override
    public CompletableFuture<VoidResult> voidPunishment(final long id, final Actor actor,
            final @Nullable String reason) {
        return this.voidPunishment(id, actor, reason, null);
    }

    @Override
    public CompletableFuture<VoidResult> voidPunishment(final long id, final Actor actor,
            final @Nullable String reason, final long expectedVersion) {
        return this.voidPunishment(id, actor, reason, (Long) expectedVersion);
    }

    private CompletableFuture<VoidResult> voidPunishment(final long id, final Actor actor,
            final @Nullable String reason, final @Nullable Long expectedVersion) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
            Preconditions.checkArgument(reason == null || reason.length() <= Lengths.MAX_REASON,
                    "reason is longer than %s characters", Lengths.MAX_REASON);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        final String text = reason == null || reason.isBlank() ? null : reason.strip();
        return this.changeable(id, checked, Action.ChangePunishment.Operation.VOID)
                .thenComposeAsync(found -> {
                    if (found instanceof Changeable.Refused(String reason1)) {
                        return CompletableFuture.completedFuture(voidFailure(reason1));
                    }
                    final Punishment punishment = ((Changeable.Found) found).punishment();
                    if (this.events.post(new SakuraPunishmentChangeEvents.Voiding(punishment,
                            checked, text)).cancelled()) {
                        return CompletableFuture.completedFuture(new SakuraChangeResults
                                .VoidFailure(VoidResult.Failure.Reason.CANCELLED, null));
                    }
                    return this.punishments.voidPunishment(id, checked, text, expectedVersion,
                            this.clock.instant()).thenCompose(this::voided);
                }, this.worker);
    }

    private CompletableFuture<VoidResult> voided(final ChangeOutcome outcome) {
        if (!(outcome instanceof ChangeOutcome.Changed(Punishment previous, Punishment current))) {
            return CompletableFuture.completedFuture(voidFailure(outcome));
        }
        this.sync.publish(new SyncPacket.PunishmentVoidedPacket(current.id()));
        final CompletableFuture<Void> local = previous.end().isEmpty()
                ? this.enforcer.ended(current)
                : CompletableFuture.completedFuture(null);
        return local.thenApplyAsync(ignored -> {
            this.events.post(new SakuraPunishmentChangeEvents.Voided(current, false));
            return new SakuraChangeResults.VoidSuccess(current);
        }, this.worker);
    }

    @Override
    public CompletableFuture<ExpungeResult> expunge(final long id, final Actor actor) {
        return this.expunge(id, actor, null);
    }

    @Override
    public CompletableFuture<ExpungeResult> expunge(final long id, final Actor actor,
            final long expectedVersion) {
        return this.expunge(id, actor, (Long) expectedVersion);
    }

    private CompletableFuture<ExpungeResult> expunge(final long id, final Actor actor,
            final @Nullable Long expectedVersion) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.changeable(id, checked, Action.ChangePunishment.Operation.EXPUNGE)
                .thenComposeAsync(found -> {
                    if (found instanceof Changeable.Refused(String reason)) {
                        return CompletableFuture.completedFuture(
                                expungeFailure(reason));
                    }
                    final Punishment punishment = ((Changeable.Found) found).punishment();
                    if (this.events.post(new SakuraPunishmentChangeEvents.Expunge(punishment,
                            checked)).cancelled()) {
                        return CompletableFuture.completedFuture(new SakuraChangeResults
                                .ExpungeFailure(ExpungeResult.Failure.Reason.CANCELLED, null));
                    }
                    return this.punishments.expunge(id, checked, expectedVersion,
                            this.clock.instant()).thenCompose(this::expunged);
                }, this.worker);
    }

    private CompletableFuture<ExpungeResult> expunged(final ChangeOutcome outcome) {
        if (!(outcome instanceof ChangeOutcome.Changed changed)) {
            return CompletableFuture.completedFuture(expungeFailure(outcome));
        }
        final Punishment gone = changed.previous();
        this.sync.publish(new SyncPacket.PunishmentExpungedPacket(gone));
        final CompletableFuture<Void> local = gone.end().isEmpty()
                ? this.enforcer.ended(gone)
                : CompletableFuture.completedFuture(null);
        return local.thenApplyAsync(ignored -> {
            this.events.post(new SakuraPunishmentChangeEvents.Expunged(gone, false));
            return new SakuraChangeResults.ExpungeSuccess(gone);
        }, this.worker);
    }

    @Override
    public CompletableFuture<EditResult> attachEvidence(final long id, final EvidenceRef evidence,
            final Actor actor) {
        return this.evidence(id, evidence, actor, true);
    }

    @Override
    public CompletableFuture<EditResult> detachEvidence(final long id, final EvidenceRef evidence,
            final Actor actor) {
        return this.evidence(id, evidence, actor, false);
    }

    private CompletableFuture<EditResult> evidence(final long id, final EvidenceRef evidence,
            final Actor actor, final boolean attach) {
        final Actor checked;
        final EvidenceRef ref;
        try {
            checked = ApiValues.actor(actor);
            ref = EvidenceRef.of(evidence.uri());
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.changeable(id, checked, Action.ChangePunishment.Operation.EVIDENCE)
                .thenComposeAsync(found -> found instanceof Changeable.Refused(String reason)
                        ? CompletableFuture.completedFuture(editFailure(reason))
                        : this.punishments.evidence(id, ref, attach, checked,
                                this.clock.instant()).thenApplyAsync(outcome -> {
                                    if (!(outcome instanceof ChangeOutcome.Changed(
                                            Punishment previous, Punishment current
                                    ))) {
                                        return editFailure(outcome);
                                    }
                                    final Punishment source = attach
                                            ? current : previous;
                                    final Evidence item = source.evidence().stream()
                                            .filter(e -> e.ref().equals(ref))
                                            .findFirst().orElseThrow();
                                    this.sync.publish(new SyncPacket.EvidenceChangedPacket(
                                            id, attach, item));
                                    this.events.post(attach
                                            ? new SakuraPunishmentChangeEvents.EvidenceAttached(
                                            current, item, false)
                                            : new SakuraPunishmentChangeEvents.EvidenceDetached(
                                            current, item, false));
                                    return new SakuraChangeResults.EditSuccess(
                                            current);
                                }, this.worker), this.worker);
    }

    private sealed interface Changeable {

        record Found(Punishment punishment) implements Changeable {
        }

        record Refused(String reason) implements Changeable {
        }
    }

    private CompletableFuture<Changeable> changeable(final long id, final Actor actor,
            final Action.ChangePunishment.Operation operation) {
        if (id < 1) {
            return CompletableFuture.completedFuture(new Changeable.Refused("NOT_FOUND"));
        }
        return this.whenRunning()
                .thenCompose(ignored -> this.punishments.byId(id))
                .thenCompose(found -> {
                    if (found.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new Changeable.Refused("NOT_FOUND"));
                    }
                    return this.actions.check(actor, Action.ChangePunishment.of(operation, id))
                            .thenApply(decision -> decision.allowed()
                                    ? new Changeable.Found(found.get())
                                    : new Changeable.Refused(decision.denial().orElse(null)
                                            == Decision.Denial.EXEMPT ? "EXEMPT" : "FORBIDDEN"));
                });
    }

    private static @Nullable Long expected(final OptionalLong version) {
        return version.isPresent() ? version.getAsLong() : null;
    }

    private static EditResult editFailure(final String reason) {
        return new SakuraChangeResults.EditFailure(EditResult.Failure.Reason.valueOf(reason),
                null);
    }

    private static EditResult editFailure(final EditResult.Failure.Reason reason) {
        return new SakuraChangeResults.EditFailure(reason, null);
    }

    private static EditResult editFailure(final ChangeOutcome outcome) {
        return switch (outcome) {
            case ChangeOutcome.NotFound notFound -> editFailure("NOT_FOUND");
            case ChangeOutcome.Conflict conflict -> new SakuraChangeResults.EditFailure(
                    EditResult.Failure.Reason.CONFLICT, conflict.currentVersion());
            case ChangeOutcome.Refused refused -> new SakuraChangeResults.EditFailure(
                    switch (refused.problem()) {
                        case NOT_ACTIVE -> EditResult.Failure.Reason.NOT_ACTIVE;
                        case INVALID_EXPIRY -> EditResult.Failure.Reason.INVALID_EXPIRY;
                        case ALREADY_PUNISHED -> EditResult.Failure.Reason.ALREADY_PUNISHED;
                        case EVIDENCE_ALREADY_ATTACHED ->
                                EditResult.Failure.Reason.EVIDENCE_ALREADY_ATTACHED;
                        case EVIDENCE_NOT_ATTACHED ->
                                EditResult.Failure.Reason.EVIDENCE_NOT_ATTACHED;
                        case ALREADY_VOIDED -> throw new IllegalStateException("not an edit");
                    }, refused.currentVersion());
            case ChangeOutcome.Changed changed -> throw new IllegalStateException("changed");
        };
    }

    private static VoidResult voidFailure(final String reason) {
        return new SakuraChangeResults.VoidFailure(VoidResult.Failure.Reason.valueOf(reason),
                null);
    }

    private static VoidResult voidFailure(final ChangeOutcome outcome) {
        return switch (outcome) {
            case ChangeOutcome.NotFound notFound -> voidFailure("NOT_FOUND");
            case ChangeOutcome.Conflict conflict -> new SakuraChangeResults.VoidFailure(
                    VoidResult.Failure.Reason.CONFLICT, conflict.currentVersion());
            case ChangeOutcome.Refused refused -> new SakuraChangeResults.VoidFailure(
                    VoidResult.Failure.Reason.ALREADY_VOIDED, refused.currentVersion());
            case ChangeOutcome.Changed changed -> throw new IllegalStateException("changed");
        };
    }

    private static ExpungeResult expungeFailure(final String reason) {
        return new SakuraChangeResults.ExpungeFailure(
                ExpungeResult.Failure.Reason.valueOf(reason), null);
    }

    private static ExpungeResult expungeFailure(final ChangeOutcome outcome) {
        return switch (outcome) {
            case ChangeOutcome.NotFound notFound -> expungeFailure("NOT_FOUND");
            case ChangeOutcome.Conflict conflict -> new SakuraChangeResults.ExpungeFailure(
                    ExpungeResult.Failure.Reason.CONFLICT, conflict.currentVersion());
            case ChangeOutcome.Refused refused -> throw new IllegalStateException("refused");
            case ChangeOutcome.Changed changed -> throw new IllegalStateException("changed");
        };
    }

    @Override
    public CompletableFuture<RollbackResult> rollback(final RollbackRequest request) {
        final RollbackRequest checked;
        try {
            checked = RollbackRequest.builder(ApiValues.operator(request.target()),
                            request.since(), ApiValues.actor(request.actor()))
                    .reason(request.reason().map(String::strip).filter(text -> !text.isEmpty())
                            .orElse(null))
                    .visibility(request.visibility())
                    .build();
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (checked.reason().isEmpty() && this.config.settings().reasons().requiredForRevoke()) {
            return CompletableFuture.completedFuture(new SakuraRollbackResult.Failure(
                    RollbackResult.Failure.Reason.REASON_REQUIRED));
        }
        return this.whenRunning()
                .thenCompose(ignored -> this.authorize(checked.actor(),
                        Action.Rollback.of(checked.target())))
                .thenCompose(allowed -> {
                    if (!allowed) {
                        return CompletableFuture.completedFuture(
                                new SakuraRollbackResult.Failure(
                                        RollbackResult.Failure.Reason.FORBIDDEN));
                    }
                    final Instant now = this.clock.instant();
                    return this.punishments.activeIssuedBy(checked.target(), checked.since(), now)
                            .thenCompose(found -> this.rollBack(checked, found, now));
                });
    }

    private CompletableFuture<RollbackResult> rollBack(final RollbackRequest request,
            final List<Punishment> found, final Instant now) {
        final List<CompletableFuture<Optional<Punishment>>> ends = found.stream()
                .map(punishment -> this.punishments.end(punishment.id(), EndKind.ROLLED_BACK,
                        now, request.actor(), request.reason().orElse(null)))
                .toList();
        return Futures.allOf(ends).thenComposeAsync(results -> {
            final List<Punishment> ended = results.stream().flatMap(Optional::stream).toList();
            if (!ended.isEmpty()) {
                this.sync.publish(new SyncPacket.PunishmentsRolledBackPacket(
                        ended.stream().map(Punishment::id).toList(), request.target(),
                        request.since(), request.actor(), request.reason().orElse(null),
                        request.visibility()));
            }
            final CompletableFuture<?>[] local = ended.stream().map(this.enforcer::ended)
                    .toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(local).thenApplyAsync(ignored -> {
                if (!ended.isEmpty()) {
                    this.events.post(new SakuraPunishmentsRolledBackEvent(request, ended,
                            false));
                }
                return new SakuraRollbackResult.Success(ended);
            }, this.worker);
        }, this.worker);
    }

    @Override
    public CompletableFuture<Optional<Punishment>> byId(final long id) {
        return this.punishments.byId(id);
    }

    @Override
    public CompletableFuture<Optional<Punishment>> byDisplayId(final String displayId) {
        final OptionalLong id = this.displayIds.decode(displayId);
        return id.isPresent()
                ? this.punishments.byId(id.getAsLong())
                : CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Page<Punishment>> query(final PunishmentQuery query) {
        return this.punishments.query(query, this.clock.instant());
    }

    @Override
    public CompletableFuture<Long> count(final PunishmentQuery query) {
        return this.punishments.count(query, this.clock.instant());
    }

    @Override
    public CompletableFuture<Optional<Punishment>> activeBan(final UUID uuid,
            final @Nullable InetAddress address, final Scope scope) {
        return this.applicable(PunishmentType.BAN, uuid, address, scope);
    }

    @Override
    public CompletableFuture<Optional<Punishment>> activeMute(final UUID uuid,
            final @Nullable InetAddress address, final Scope scope) {
        return this.applicable(PunishmentType.MUTE, uuid, address, scope);
    }

    @Override
    public Optional<Punishment> cachedMute(final UUID uuid) {
        return this.mutes.activeMute(uuid);
    }

    private CompletableFuture<Optional<Punishment>> applicable(final PunishmentType type,
            final UUID uuid, final @Nullable InetAddress address, final Scope scope) {
        return this.punishments.activeApplicable(type, Applicabilities.of(uuid, address,
                                this.config.settings().enforcement().addressStrictness()),
                        this.scopes.covering(ApiValues.scope(scope)), this.clock.instant())
                .thenApply(found -> found.stream().findFirst());
    }

    private CompletableFuture<Boolean> authorize(final Actor actor, final Action action) {
        return this.actions.check(actor, action).thenApply(Decision::allowed);
    }

    private CompletableFuture<Void> whenRunning() {
        if (!this.lifecycle.running()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "SakuraBans is " + this.lifecycle.state() + ", not running"));
        }
        // Hop onto a worker: callers may be on a platform thread, and events run on workers.
        return CompletableFuture.runAsync(() -> { }, this.worker);
    }

    private static CompletableFuture<RevokeResult> failure(
            final RevokeResult.Failure.Reason reason) {
        return CompletableFuture.completedFuture(new SakuraRevokeResult.Failure(reason));
    }

}
