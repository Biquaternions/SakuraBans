package net.serlith.sakurabans.core.punish;

import java.net.InetAddress;
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
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.Page;
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
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraPunishmentCreateEvent;
import net.serlith.sakurabans.core.event.SakuraPunishmentCreatedEvent;
import net.serlith.sakurabans.core.event.SakuraPunishmentRevokeEvent;
import net.serlith.sakurabans.core.event.SakuraPunishmentRevokedEvent;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SakuraPunishResult;
import net.serlith.sakurabans.core.model.SakuraRevokeResult;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
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
    private final SakuraEventBus events;
    private final SyncService sync;

    public SakuraPunishmentService(final Lifecycle lifecycle, final Executor worker,
            final InstantSource clock, final ConfigService config,
            final SakuraScopeService scopes, final PunishmentRepository punishments,
            final PunishmentFactory factory, final MuteCache mutes, final Enforcer enforcer,
            final ActionCheck actions, final SakuraEventBus events, final SyncService sync) {
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
        this.events = events;
        this.sync = sync;
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
        return this.factory.prepare(draft, options).thenComposeAsync(prepared -> {
            if (prepared instanceof PunishmentFactory.Prepared.Refused refused) {
                return CompletableFuture.completedFuture(
                        new SakuraPunishResult.Failure(refused.reason()));
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
            return this.factory.prepare(event.draft(), event.options()).thenCompose(again ->
                    again instanceof PunishmentFactory.Prepared.Ready readyAgain
                            ? this.store(readyAgain, event.options())
                            : CompletableFuture.completedFuture(new SakuraPunishResult.Failure(
                                    ((PunishmentFactory.Prepared.Refused) again).reason())));
        }, this.worker);
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
            if (superseded != null) {
                this.sync.publish(new SyncPacket.PunishmentRevokePacket(superseded.id()));
            }
            this.sync.publish(new SyncPacket.PunishmentEnforcePacket(punishment.id()));
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
        final List<CompletableFuture<Boolean>> checks = candidates.stream()
                .map(punishment -> this.authorize(request.actor(), Action.ChangePunishment.of(
                        Action.ChangePunishment.Operation.REVOKE, punishment.id())))
                .toList();
        return Futures.allOf(checks).thenComposeAsync(allowed -> {
            if (allowed.contains(Boolean.FALSE)) {
                return failure(RevokeResult.Failure.Reason.FORBIDDEN);
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
                this.sync.publish(new SyncPacket.PunishmentRevokePacket(punishment.id()));
            }
            return CompletableFuture.allOf(local.toArray(CompletableFuture[]::new))
                    .thenApplyAsync(ignored -> {
                        for (final Punishment punishment : revoked) {
                            this.events.post(new SakuraPunishmentRevokedEvent(punishment,
                                    request.visibility(), false));
                        }
                        return (RevokeResult) new SakuraRevokeResult.Success(revoked);
                    }, this.worker);
        }, this.worker);
    }

    @Override
    public CompletableFuture<EditResult> edit(final EditRequest request) {
        return unsupported("editing", "S35");
    }

    @Override
    public CompletableFuture<VoidResult> voidPunishment(final long id, final Actor actor,
            final @Nullable String reason) {
        return unsupported("voiding", "S35");
    }

    @Override
    public CompletableFuture<VoidResult> voidPunishment(final long id, final Actor actor,
            final @Nullable String reason, final long expectedVersion) {
        return unsupported("voiding", "S35");
    }

    @Override
    public CompletableFuture<ExpungeResult> expunge(final long id, final Actor actor) {
        return unsupported("expunging", "S35");
    }

    @Override
    public CompletableFuture<ExpungeResult> expunge(final long id, final Actor actor,
            final long expectedVersion) {
        return unsupported("expunging", "S35");
    }

    @Override
    public CompletableFuture<EditResult> attachEvidence(final long id, final EvidenceRef evidence,
            final Actor actor) {
        return unsupported("attaching evidence", "S35");
    }

    @Override
    public CompletableFuture<EditResult> detachEvidence(final long id, final EvidenceRef evidence,
            final Actor actor) {
        return unsupported("detaching evidence", "S35");
    }

    @Override
    public CompletableFuture<RollbackResult> rollback(final RollbackRequest request) {
        return unsupported("rollback", "S36");
    }

    @Override
    public CompletableFuture<Optional<Punishment>> byId(final long id) {
        return this.punishments.byId(id);
    }

    @Override
    public CompletableFuture<Optional<Punishment>> byDisplayId(final String displayId) {
        final OptionalLong id = parseId(displayId);
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

    private static OptionalLong parseId(final String text) {
        try {
            final long id = Long.parseLong(text.strip());
            return id >= 1 ? OptionalLong.of(id) : OptionalLong.empty();
        } catch (final NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static CompletableFuture<RevokeResult> failure(
            final RevokeResult.Failure.Reason reason) {
        return CompletableFuture.completedFuture(new SakuraRevokeResult.Failure(reason));
    }

    private static <T> CompletableFuture<T> unsupported(final String what, final String step) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                what + " isn't supported yet (" + step + ")"));
    }

}
