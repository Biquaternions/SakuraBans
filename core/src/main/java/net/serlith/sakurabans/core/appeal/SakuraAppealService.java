package net.serlith.sakurabans.core.appeal;

import java.time.InstantSource;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.appeal.AppealOutcome;
import net.serlith.sakurabans.api.appeal.AppealQuery;
import net.serlith.sakurabans.api.appeal.AppealService;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.api.model.PlayerOperator;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.PunishmentService;
import net.serlith.sakurabans.api.punish.RevocationRequest;
import net.serlith.sakurabans.api.punish.RevocationTarget;
import net.serlith.sakurabans.api.punish.RevokeResult;
import net.serlith.sakurabans.api.punish.VoidResult;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.core.event.SakuraAppealChangedEvent;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SakuraAdminResult;
import net.serlith.sakurabans.core.notify.NotificationService;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.punish.ActionCheck;
import net.serlith.sakurabans.core.storage.repository.AppealRepository;
import net.serlith.sakurabans.core.storage.repository.AppealRepository.Change;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import org.jspecify.annotations.Nullable;

public final class SakuraAppealService implements AppealService {

    static final String SOURCE = "appeal";

    private final AppealRepository repository;
    private final PunishmentService punishments;
    private final ActionCheck actions;
    private final NotificationService notifications;
    private final NameCache names;
    private final SyncService sync;
    private final SakuraEventBus events;
    private final InstantSource clock;

    public SakuraAppealService(final AppealRepository repository,
            final PunishmentService punishments, final ActionCheck actions,
            final NotificationService notifications, final NameCache names,
            final SyncService sync, final SakuraEventBus events, final InstantSource clock) {
        this.repository = repository;
        this.punishments = punishments;
        this.actions = actions;
        this.notifications = notifications;
        this.names = names;
        this.sync = sync;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public CompletableFuture<AdminResult<Appeal>> submit(final long punishmentId,
            final UUID appellant, final String message) {
        final String text = message == null ? "" : message.strip();
        if (text.isEmpty() || text.length() > MAX_MESSAGE) {
            return CompletableFuture.completedFuture(SakuraAdminResult.invalid("message",
                    FieldError.Code.INVALID, "must be 1-" + MAX_MESSAGE + " characters"));
        }
        return this.punishments.byId(punishmentId).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(new SakuraAdminResult.NotFound<>());
            }
            final Punishment punishment = found.get();
            if (!appellant.equals(account(punishment))) {
                return CompletableFuture.completedFuture(SakuraAdminResult.invalid(
                        "appellant", FieldError.Code.NOT_ALLOWED,
                        "only the punished player may appeal"));
            }
            if (punishment.voiding().isPresent()) {
                return CompletableFuture.completedFuture(SakuraAdminResult.invalid(
                        "punishmentId", FieldError.Code.NOT_ALLOWED, "it is voided already"));
            }
            // A UUID without dashes fits a player name (32 characters).
            final String name = this.names.name(appellant)
                    .orElseGet(() -> appellant.toString().replace("-", ""));
            final Actor actor = Actor.of(PlayerOperator.of(appellant,
                    name.length() > Lengths.MAX_PLAYER_NAME
                            ? name.substring(0, Lengths.MAX_PLAYER_NAME) : name), SOURCE);
            return this.repository.submit(punishmentId, appellant, text, actor,
                    this.clock.instant()).thenApply(change -> switch (change) {
                        case Change.Done done -> {
                            this.changed(ChangeType.CREATED, done.appeal(), actor);
                            this.notifications.appealed(done.appeal(), punishment, name);
                            yield new SakuraAdminResult.Ok<>(done.appeal());
                        }
                        case Change.AlreadyOpen open -> SakuraAdminResult.<Appeal>invalid(
                                "punishmentId", FieldError.Code.NOT_ALLOWED,
                                "it has an open appeal already");
                        default -> new SakuraAdminResult.NotFound<>();
                    });
        });
    }

    private static @Nullable UUID account(final Punishment punishment) {
        return switch (punishment.victim()) {
            case PlayerVictim player -> player.uuid();
            case CompositeVictim composite -> composite.uuid();
            default -> null;
        };
    }

    @Override
    public CompletableFuture<Page<Appeal>> appeals(final AppealQuery query) {
        final AppealQuery checked;
        try {
            checked = AppealQuery.builder().statuses(query.statuses())
                    .punishmentId(query.punishmentId().orElse(null))
                    .appellant(query.appellant().orElse(null))
                    .after(query.after().orElse(null))
                    .pageSize(query.pageSize())
                    .build();
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.repository.query(checked);
    }

    @Override
    public CompletableFuture<Optional<Appeal>> appeal(final long id) {
        return this.repository.appeal(id);
    }

    @Override
    public CompletableFuture<AdminResult<Appeal>> decide(final long id,
            final AppealOutcome outcome, final @Nullable String note,
            final long expectedVersion, final Actor actor) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        final String text = note == null || note.isBlank() ? null : note.strip();
        if (text != null && text.length() > Lengths.MAX_REASON) {
            return CompletableFuture.completedFuture(SakuraAdminResult.invalid("note",
                    FieldError.Code.TOO_LONG, "is longer than " + Lengths.MAX_REASON));
        }
        return this.repository.appeal(id).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(new SakuraAdminResult.NotFound<>());
            }
            final Appeal appeal = found.get();
            if (appeal.version() != expectedVersion) {
                return CompletableFuture.completedFuture(
                        new SakuraAdminResult.Conflict<>(appeal.version()));
            }
            if (appeal.status() != Appeal.Status.OPEN) {
                return CompletableFuture.completedFuture(SakuraAdminResult.<Appeal>invalid(
                        "status", FieldError.Code.NOT_ALLOWED, "the appeal is decided"));
            }
            return this.actions.check(checked, Action.DecideAppeal.of(id))
                    .thenCompose(decision -> decision.allowed()
                            ? this.apply(appeal, outcome, text, checked)
                            : CompletableFuture.completedFuture(denied(decision)))
                    .thenCompose(refused -> refused != null
                            ? CompletableFuture.completedFuture(refused)
                            : this.store(appeal, outcome, text, expectedVersion, checked));
        });
    }

    private CompletableFuture<@Nullable AdminResult<Appeal>> apply(final Appeal appeal,
            final AppealOutcome outcome, final @Nullable String note, final Actor actor) {
        return switch (outcome) {
            case REJECT -> CompletableFuture.completedFuture(null);
            case REVOKE -> this.punishments.revoke(RevocationRequest.builder(
                            RevocationTarget.ById.of(appeal.punishmentId()), actor)
                            .reason(note).build())
                    .thenApply(result -> result instanceof RevokeResult.Failure failure
                            && (failure.reason() == RevokeResult.Failure.Reason.FORBIDDEN
                            || failure.reason() == RevokeResult.Failure.Reason.EXEMPT
                            || failure.reason() == RevokeResult.Failure.Reason.CANCELLED
                            || failure.reason() == RevokeResult.Failure.Reason.REASON_REQUIRED)
                            ? denied(Decision.deny(Decision.Denial.NO_PERMISSION, null))
                            : null);
            case VOID -> this.punishments.voidPunishment(appeal.punishmentId(), actor, note)
                    .thenApply(result -> result instanceof VoidResult.Failure failure
                            && failure.reason() != VoidResult.Failure.Reason.ALREADY_VOIDED
                            && failure.reason() != VoidResult.Failure.Reason.NOT_FOUND
                            ? denied(Decision.deny(failure.reason()
                                    == VoidResult.Failure.Reason.EXEMPT
                                    ? Decision.Denial.EXEMPT : Decision.Denial.NO_PERMISSION,
                                    null))
                            : null);
        };
    }

    private CompletableFuture<AdminResult<Appeal>> store(final Appeal appeal,
            final AppealOutcome outcome, final @Nullable String note,
            final long expectedVersion, final Actor actor) {
        return this.repository.decide(appeal.id(), outcome, note, expectedVersion, actor,
                this.clock.instant()).thenCompose(change -> switch (change) {
                    case Change.Done done -> {
                        this.changed(ChangeType.UPDATED, done.appeal(), actor);
                        yield this.punishments.byId(done.appeal().punishmentId())
                                .thenApply(punishment -> {
                                    this.notifications.appealDecided(done.appeal(), punishment
                                            .map(Punishment::displayId)
                                            .orElse(Long.toString(done.appeal().punishmentId())));
                                    return (AdminResult<Appeal>)
                                            new SakuraAdminResult.Ok<>(done.appeal());
                                });
                    }
                    case Change.Conflict conflict -> CompletableFuture.completedFuture(
                            new SakuraAdminResult.Conflict<>(conflict.currentVersion()));
                    case Change.NotOpen notOpen -> CompletableFuture.completedFuture(
                            SakuraAdminResult.<Appeal>invalid("status",
                                    FieldError.Code.NOT_ALLOWED, "the appeal is decided"));
                    default -> CompletableFuture.completedFuture(
                            new SakuraAdminResult.NotFound<>());
                });
    }

    /** Another instance changed an appeal. */
    public CompletableFuture<Void> changed(final SyncPacket.EntityChangedPacket packet) {
        return this.repository.appeal(Long.parseLong(packet.id())).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            final Appeal appeal = found.get();
            this.events.post(new SakuraAppealChangedEvent(packet.change(), packet.id(),
                    packet.version(), packet.actor(), appeal, true));
            return this.punishments.byId(appeal.punishmentId()).thenAccept(punishment -> {
                if (appeal.status() != Appeal.Status.OPEN) {
                    this.notifications.appealDecided(appeal, punishment
                            .map(Punishment::displayId)
                            .orElse(Long.toString(appeal.punishmentId())));
                } else if (packet.change() == ChangeType.CREATED && punishment.isPresent()) {
                    this.notifications.appealed(appeal, punishment.get(), this.names
                            .name(appeal.appellant()).orElse(appeal.appellant().toString()));
                }
            });
        });
    }

    private void changed(final ChangeType change, final Appeal appeal, final Actor actor) {
        this.sync.publish(new SyncPacket.EntityChangedPacket(SubjectType.APPEAL,
                Long.toString(appeal.id()), change, appeal.version(), actor));
        this.events.post(new SakuraAppealChangedEvent(change, Long.toString(appeal.id()),
                appeal.version(), actor, appeal, false));
    }

    private static AdminResult<Appeal> denied(final Decision decision) {
        return new SakuraAdminResult.Forbidden<>(decision);
    }
}
