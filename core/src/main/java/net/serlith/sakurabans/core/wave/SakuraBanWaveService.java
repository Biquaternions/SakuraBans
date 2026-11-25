package net.serlith.sakurabans.core.wave;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.api.punish.PunishResult;
import net.serlith.sakurabans.api.punish.PunishmentService;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.wave.BanWave;
import net.serlith.sakurabans.api.wave.BanWaveEntry;
import net.serlith.sakurabans.api.wave.BanWaveService;
import net.serlith.sakurabans.core.event.SakuraBanWaveEvents;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SakuraAdminResult;
import net.serlith.sakurabans.core.model.SakuraBanWaveEntry;
import net.serlith.sakurabans.core.model.SakuraPunishResult;
import net.serlith.sakurabans.core.model.ValueCodec;
import net.serlith.sakurabans.core.punish.ActionCheck;
import net.serlith.sakurabans.core.storage.repository.AuditValues;
import net.serlith.sakurabans.core.storage.repository.BanWaveRepository;
import net.serlith.sakurabans.core.storage.repository.BanWaveRepository.Change;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.util.Futures;
import org.jspecify.annotations.Nullable;

public final class SakuraBanWaveService implements BanWaveService {

    private static final Set<BanWave.Status> OPEN =
            Set.of(BanWave.Status.DRAFT, BanWave.Status.SCHEDULED);

    private final BanWaveRepository repository;
    private final PunishmentService punishments;
    private final ActionCheck actions;
    private final SyncService sync;
    private final SakuraEventBus events;
    private final InstantSource clock;
    private final ComponentLogger logger;

    public SakuraBanWaveService(final BanWaveRepository repository,
            final PunishmentService punishments, final ActionCheck actions,
            final SyncService sync, final SakuraEventBus events, final InstantSource clock,
            final ComponentLogger logger) {
        this.repository = repository;
        this.punishments = punishments;
        this.actions = actions;
        this.sync = sync;
        this.events = events;
        this.clock = clock;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<List<BanWave>> waves(final Set<BanWave.Status> statuses) {
        return this.repository.waves(Set.copyOf(statuses));
    }

    @Override
    public CompletableFuture<Optional<BanWave>> wave(final long id) {
        return this.repository.wave(id);
    }

    @Override
    public CompletableFuture<List<BanWaveEntry>> entries(final long waveId) {
        return this.repository.entries(waveId).thenCompose(stored -> {
            final List<CompletableFuture<BanWaveEntry>> entries = new ArrayList<>();
            for (final BanWaveRepository.StoredEntry entry : stored) {
                entries.add(this.entry(entry));
            }
            return Futures.allOf(entries);
        });
    }

    private CompletableFuture<BanWaveEntry> entry(final BanWaveRepository.StoredEntry stored) {
        final ValueCodec.StoredDraft draft = ValueCodec.decodeDraft(stored.draft());
        return this.result(stored.result()).thenApply(result -> new SakuraBanWaveEntry(
                stored.id(), stored.waveId(), draft.draft(), draft.options(), stored.addedBy(),
                stored.addedAt(), result));
    }

    private CompletableFuture<@Nullable PunishResult> result(final @Nullable String text) {
        if (text == null) {
            return CompletableFuture.completedFuture(null);
        }
        final int colon = text.indexOf(':');
        final String head = colon < 0 ? text : text.substring(0, colon);
        final Long id = colon < 0 ? null : Long.parseLong(text.substring(colon + 1));
        final CompletableFuture<Optional<Punishment>> found = id == null
                ? CompletableFuture.completedFuture(Optional.empty())
                : this.punishments.byId(id);
        return found.thenApply(punishment -> head.equals("ok")
                ? punishment.<PunishResult>map(value -> new SakuraPunishResult.Success(value,
                        null)).orElse(null)
                : new SakuraPunishResult.Failure(PunishResult.Failure.Reason.valueOf(head),
                        punishment.orElse(null)));
    }

    @Override
    public CompletableFuture<AdminResult<BanWave>> create(final String name, final Actor actor) {
        final String checkedName = name == null ? "" : name.strip();
        if (checkedName.isEmpty() || checkedName.length() > Lengths.MAX_IDENTIFIER) {
            return CompletableFuture.completedFuture(SakuraAdminResult.invalid("name",
                    FieldError.Code.INVALID, "must be 1-" + Lengths.MAX_IDENTIFIER
                            + " characters"));
        }
        return this.allowed(actor, checked -> this.repository.create(checkedName, checked,
                        this.clock.instant())
                .thenApply(wave -> {
                    this.changed(ChangeType.CREATED, wave, checked);
                    return new SakuraAdminResult.Ok<>(wave);
                }));
    }

    @Override
    public CompletableFuture<AdminResult<BanWaveEntry>> addEntry(final long waveId,
            final DraftPunishment draft, final PunishOptions options, final Actor actor) {
        final DraftPunishment checkedDraft;
        final PunishOptions checkedOptions;
        try {
            checkedDraft = ApiValues.draft(draft);
            checkedOptions = ApiValues.options(options);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        final String encoded = ValueCodec.encodeDraft(checkedDraft, checkedOptions);
        return this.allowed(actor, checked -> this.repository.addEntry(waveId, encoded,
                        checkedDraft.type() + " " + AuditValues.victim(checkedDraft.victim()),
                        checked, this.clock.instant())
                .thenCompose(change -> this.entryChange(change, checked)));
    }

    @Override
    public CompletableFuture<AdminResult<BanWaveEntry>> removeEntry(final long waveId,
            final long entryId, final Actor actor) {
        return this.allowed(actor, checked -> this.repository.entries(waveId)
                .thenCompose(entries -> {
                    final BanWaveRepository.StoredEntry stored = entries.stream()
                            .filter(entry -> entry.id() == entryId).findFirst().orElse(null);
                    if (stored == null) {
                        return CompletableFuture.completedFuture(
                                new SakuraAdminResult.NotFound<>());
                    }
                    return this.repository.removeEntry(waveId, entryId, checked,
                                    this.clock.instant())
                            .thenCompose(change -> change instanceof Change.Done done
                                    ? this.entry(stored).thenApply(entry -> {
                                        this.changed(ChangeType.UPDATED, done.wave(), checked);
                                        return new SakuraAdminResult.Ok<>(entry);
                                    })
                                    : CompletableFuture.completedFuture(
                                            failure(change)));
                }));
    }

    private CompletableFuture<AdminResult<BanWaveEntry>> entryChange(final Change change,
            final Actor actor) {
        if (!(change instanceof Change.Done(BanWave wave, long entryId))) {
            return CompletableFuture.completedFuture(failure(change));
        }
        this.changed(ChangeType.UPDATED, wave, actor);
        return this.repository.entries(wave.id()).thenCompose(entries -> this.entry(
                entries.stream().filter(entry -> entry.id() == entryId).findFirst()
                        .orElseThrow()))
                .thenApply(SakuraAdminResult.Ok::new);
    }

    @Override
    public CompletableFuture<AdminResult<BanWave>> schedule(final long waveId,
            final @Nullable Instant at, final long expectedVersion, final Actor actor) {
        return this.allowed(actor, checked -> this.repository.transition(waveId, OPEN,
                        at == null ? BanWave.Status.DRAFT : BanWave.Status.SCHEDULED, at,
                        expectedVersion, checked, AuditAction.SCHEDULE, this.clock.instant())
                .thenApply(change -> this.waveChange(change, checked)));
    }

    @Override
    public CompletableFuture<AdminResult<BanWave>> cancel(final long waveId,
            final long expectedVersion, final Actor actor) {
        return this.allowed(actor, checked -> this.repository.transition(waveId, OPEN,
                        BanWave.Status.CANCELLED, null, expectedVersion, checked,
                        AuditAction.CANCEL, this.clock.instant())
                .thenApply(change -> this.waveChange(change, checked)));
    }

    @Override
    public CompletableFuture<AdminResult<BanWave>> release(final long waveId,
            final long expectedVersion, final Actor actor) {
        return this.allowed(actor, checked -> this.claim(waveId, OPEN, expectedVersion,
                checked));
    }

    /** Claims a wave and punishes its entries; the scheduler also resumes stuck ones. */
    CompletableFuture<AdminResult<BanWave>> claim(final long waveId,
            final Set<BanWave.Status> from, final @Nullable Long expectedVersion,
            final Actor actor) {
        return this.repository.transition(waveId, from, BanWave.Status.RELEASING, null,
                        expectedVersion, actor, AuditAction.RELEASE, this.clock.instant())
                .thenCompose(change -> {
                    if (!(change instanceof Change.Done)) {
                        return CompletableFuture.completedFuture(failure(change));
                    }
                    return this.punishAll(waveId)
                            .thenCompose(ignored -> this.repository.transition(waveId,
                                    Set.of(BanWave.Status.RELEASING), BanWave.Status.RELEASED,
                                    null, null, actor, AuditAction.RELEASE,
                                    this.clock.instant()))
                            .thenCompose(done -> this.released(done, actor));
                });
    }

    private CompletableFuture<Void> punishAll(final long waveId) {
        return this.repository.entries(waveId).thenCompose(entries -> {
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (final BanWaveRepository.StoredEntry entry : entries) {
                if (entry.result() != null) {
                    continue;
                }
                // One after another: a wave can be large, and each punish is a transaction.
                chain = chain.thenCompose(ignored -> this.punishOne(entry));
            }
            return chain;
        });
    }

    private CompletableFuture<Void> punishOne(final BanWaveRepository.StoredEntry entry) {
        final ValueCodec.StoredDraft stored;
        try {
            stored = ValueCodec.decodeDraft(entry.draft());
        } catch (final RuntimeException e) {
            this.logger.warn("Ban wave entry {} can't be read and was skipped", entry.id(), e);
            return this.repository.result(entry.id(), "CANCELLED", null);
        }
        return this.punishments.punish(stored.draft(), stored.options())
                .handle((result, failure) -> {
                    if (failure != null) {
                        this.logger.warn("Ban wave entry {} failed", entry.id(),
                                Futures.unwrap(failure));
                        return this.repository.result(entry.id(), "CANCELLED", null);
                    }
                    return switch (result) {
                        case PunishResult.Success success -> this.repository.result(entry.id(),
                                "ok:" + success.punishment().id(), success.punishment().id());
                        case PunishResult.Failure failed -> this.repository.result(entry.id(),
                                failed.reason().name() + failed.conflicting()
                                        .map(conflicting -> ":" + conflicting.id()).orElse(""),
                                null);
                    };
                })
                .thenCompose(write -> write);
    }

    private CompletableFuture<AdminResult<BanWave>> released(final Change change,
            final Actor actor) {
        if (!(change instanceof Change.Done done)) {
            return CompletableFuture.completedFuture(failure(change));
        }
        this.changed(ChangeType.UPDATED, done.wave(), actor);
        return this.entries(done.wave().id()).thenApply(entries -> {
            this.events.post(new SakuraBanWaveEvents.Released(done.wave(), entries, false));
            return new SakuraAdminResult.Ok<>(done.wave());
        });
    }

    /** Another instance changed a wave. */
    public CompletableFuture<Void> changed(final SyncPacket.EntityChangedPacket packet) {
        final long id = Long.parseLong(packet.id());
        return this.repository.wave(id).thenCompose(wave -> {
            this.events.post(new SakuraBanWaveEvents.Changed(packet.change(), packet.id(),
                    packet.version(), packet.actor(), wave.orElse(null), true));
            if (wave.isEmpty() || wave.get().status() != BanWave.Status.RELEASED) {
                return CompletableFuture.completedFuture(null);
            }
            return this.entries(id).thenAccept(entries -> this.events.post(
                    new SakuraBanWaveEvents.Released(wave.get(), entries, true)));
        });
    }

    private void changed(final ChangeType change, final BanWave wave, final Actor actor) {
        this.sync.publish(new SyncPacket.EntityChangedPacket(SubjectType.BAN_WAVE,
                Long.toString(wave.id()), change, wave.version(), actor));
        this.events.post(new SakuraBanWaveEvents.Changed(change, Long.toString(wave.id()),
                wave.version(), actor, wave, false));
    }

    private AdminResult<BanWave> waveChange(final Change change, final Actor actor) {
        if (change instanceof Change.Done done) {
            this.changed(ChangeType.UPDATED, done.wave(), actor);
            return new SakuraAdminResult.Ok<>(done.wave());
        }
        return failure(change);
    }

    private static <T> AdminResult<T> failure(final Change change) {
        return switch (change) {
            case Change.NotFound notFound -> new SakuraAdminResult.NotFound<>();
            case Change.Conflict conflict -> new SakuraAdminResult.Conflict<>(
                    conflict.currentVersion());
            case Change.WrongStatus wrong -> SakuraAdminResult.invalid("status",
                    FieldError.Code.NOT_ALLOWED, "the wave is " + wrong.status());
            case Change.Done done -> throw new IllegalStateException("done");
        };
    }

    private <T> CompletableFuture<AdminResult<T>> allowed(final Actor actor,
            final Function<Actor, CompletableFuture<AdminResult<T>>> work) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.actions.check(checked, Action.Manage.of(Action.Manage.Area.BAN_WAVES))
                .thenCompose(decision -> decision.allowed()
                        ? work.apply(checked)
                        : CompletableFuture.completedFuture(
                                new SakuraAdminResult.Forbidden<>(decision)));
    }
}
