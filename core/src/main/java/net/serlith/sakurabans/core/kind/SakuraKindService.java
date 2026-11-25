package net.serlith.sakurabans.core.kind;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.kind.Kind;
import net.serlith.sakurabans.api.kind.KindDefinition;
import net.serlith.sakurabans.api.kind.KindService;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraKindChangedEvent;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SakuraAdminResult;
import net.serlith.sakurabans.core.model.SakuraKind;
import net.serlith.sakurabans.core.punish.ActionCheck;
import net.serlith.sakurabans.core.punish.BuiltInKinds;
import net.serlith.sakurabans.core.punish.KindLookup;
import net.serlith.sakurabans.core.storage.repository.EntityWrite;
import net.serlith.sakurabans.core.storage.repository.KindRepository;
import net.serlith.sakurabans.core.storage.repository.StrictnessLevels;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;

public final class SakuraKindService implements KindService, KindLookup, StrictnessLevels {

    private static final Comparator<Kind> ORDER = Comparator
            .comparing((Kind kind) -> !kind.builtIn())
            .thenComparing(Kind::id);

    private final KindRepository repository;
    private final ActionCheck actions;
    private final SyncService sync;
    private final SakuraEventBus events;
    private final InstantSource clock;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    public SakuraKindService(final KindRepository repository, final ActionCheck actions,
            final SyncService sync, final SakuraEventBus events, final InstantSource clock) {
        this.repository = repository;
        this.actions = actions;
        this.sync = sync;
        this.events = events;
        this.clock = clock;
    }

    private record Snapshot(Map<String, SakuraKind> byId, Set<AddressStrictness> levels) {

        static final Snapshot EMPTY = new Snapshot(Map.of(), Set.of());

        static Snapshot of(final List<SakuraKind> kinds) {
            final Map<String, SakuraKind> byId = new TreeMap<>();
            final Set<AddressStrictness> levels = EnumSet.noneOf(AddressStrictness.class);
            for (final SakuraKind kind : kinds) {
                byId.put(kind.id(), kind);
                kind.strictness().ifPresent(levels::add);
            }
            return new Snapshot(Map.copyOf(byId), Set.copyOf(levels));
        }
    }

    public CompletableFuture<Void> start() {
        return this.repository.seed(BuiltInKinds.all(), this.clock.instant())
                .thenCompose(ignored -> this.reload());
    }

    public CompletableFuture<Void> reload() {
        return this.repository.all().thenAccept(all -> this.snapshot.set(Snapshot.of(all)));
    }

    @Override
    public List<Kind> kinds() {
        final List<Kind> kinds = new ArrayList<>(this.snapshot.get().byId().values());
        kinds.sort(ORDER);
        return List.copyOf(kinds);
    }

    @Override
    public Optional<Kind> kind(final String id) {
        return this.find(id).map(Kind.class::cast);
    }

    @Override
    public Optional<SakuraKind> find(final String id) {
        return Validator.normalizeIdentifier(id).map(this.snapshot.get().byId()::get);
    }

    @Override
    public Set<AddressStrictness> levels() {
        return this.snapshot.get().levels();
    }

    @Override
    public CompletableFuture<AdminResult<Kind>> create(final KindDefinition definition,
            final Actor actor) {
        return this.authorized(actor, checked -> this.valid(definition, valid ->
                this.repository.create(valid, checked, this.clock.instant())
                        .thenCompose(write -> this.written(write, ChangeType.CREATED, checked))));
    }

    @Override
    public CompletableFuture<AdminResult<Kind>> update(final KindDefinition definition,
            final long expectedVersion, final Actor actor) {
        return this.authorized(actor, checked -> this.valid(definition, valid ->
                this.repository.update(valid, expectedVersion, checked, this.clock.instant())
                        .thenCompose(write -> this.written(write, ChangeType.UPDATED, checked))));
    }

    @Override
    public CompletableFuture<AdminResult<Kind>> delete(final String id,
            final long expectedVersion, final Actor actor) {
        return this.authorized(actor, checked -> {
            final String normalized = Validator.normalizeIdentifier(id).orElse(null);
            if (normalized == null) {
                return CompletableFuture.completedFuture(new SakuraAdminResult.NotFound<>());
            }
            return this.repository.delete(normalized, expectedVersion, checked,
                            this.clock.instant())
                    .thenCompose(write -> this.written(write, ChangeType.DELETED, checked));
        });
    }

    public CompletableFuture<Void> changed(final SyncPacket.EntityChangedPacket packet) {
        return this.reload().thenRun(() -> this.events.post(new SakuraKindChangedEvent(
                packet.change(), packet.id(), packet.version(), packet.actor(),
                packet.change() == ChangeType.DELETED ? null
                        : this.snapshot.get().byId().get(packet.id()), true)));
    }

    private CompletableFuture<AdminResult<Kind>> valid(final KindDefinition given,
            final Function<KindDefinition, CompletableFuture<AdminResult<Kind>>> onValid) {
        final KindDefinition.Builder builder;
        try {
            builder = KindDefinition.builder(given.id(), given.baseType())
                    .displayName(given.displayName())
                    .shadow(given.shadow())
                    .strictness(given.strictness().orElse(null))
                    .defaultScope(given.defaultScope().map(ApiValues::scope).orElse(null))
                    .addressMode(given.addressMode())
                    .permanentOnly(given.permanentOnly())
                    .requiredEvidence(given.requiredEvidence());
        } catch (final ValidationException e) {
            return CompletableFuture.completedFuture(
                    new SakuraAdminResult.ValidationFailed<>(e.errors()));
        }
        final List<FieldError> errors = builder.validate();
        return errors.isEmpty() ? onValid.apply(builder.build())
                : CompletableFuture.completedFuture(
                        new SakuraAdminResult.ValidationFailed<>(errors));
    }

    private CompletableFuture<AdminResult<Kind>> written(final EntityWrite<SakuraKind> write,
            final ChangeType change, final Actor actor) {
        return switch (write) {
            case EntityWrite.Done<SakuraKind> done -> this.reload().thenApply(ignored -> {
                final SakuraKind kind = done.value();
                this.sync.publish(new SyncPacket.EntityChangedPacket(SubjectType.KIND,
                        kind.id(), change, kind.version(), actor));
                this.events.post(new SakuraKindChangedEvent(change, kind.id(), kind.version(),
                        actor, change == ChangeType.DELETED ? null : kind, false));
                return new SakuraAdminResult.Ok<>(kind);
            });
            case EntityWrite.Unchanged<SakuraKind> unchanged ->
                    CompletableFuture.completedFuture(new SakuraAdminResult.Ok<>(
                            unchanged.value()));
            case EntityWrite.Taken<SakuraKind> taken ->
                    CompletableFuture.completedFuture(SakuraAdminResult.invalid("id",
                            Code.NOT_ALLOWED, "a kind is called '" + taken.existing().id()
                                    + "' already"));
            case EntityWrite.NotFound<SakuraKind> notFound ->
                    CompletableFuture.completedFuture(new SakuraAdminResult.NotFound<>());
            case EntityWrite.Conflict<SakuraKind> conflict ->
                    CompletableFuture.completedFuture(new SakuraAdminResult.Conflict<>(
                            conflict.currentVersion()));
            case EntityWrite.Refused<SakuraKind> refused ->
                    CompletableFuture.completedFuture(SakuraAdminResult.invalid(refused.path(),
                            Code.NOT_ALLOWED, refused.message()));
        };
    }

    private CompletableFuture<AdminResult<Kind>> authorized(final Actor actor,
            final Function<Actor, CompletableFuture<AdminResult<Kind>>> work) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.actions.check(checked, Action.Manage.of(Action.Manage.Area.KINDS))
                .thenCompose(decision -> decision.allowed()
                        ? work.apply(checked)
                        : CompletableFuture.completedFuture(
                                new SakuraAdminResult.Forbidden<>(decision)));
    }

}
