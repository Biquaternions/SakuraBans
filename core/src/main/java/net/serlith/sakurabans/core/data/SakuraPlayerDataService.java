package net.serlith.sakurabans.core.data;

import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.allow.AllowEntry;
import net.serlith.sakurabans.api.allow.AllowedPlayer;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.data.EraseResult;
import net.serlith.sakurabans.api.data.PlayerDataExport;
import net.serlith.sakurabans.api.data.PlayerDataService;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.core.alts.SakuraAllowListService;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraPlayerDataErasedEvent;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SakuraAdminResult;
import net.serlith.sakurabans.core.model.SakuraPlayerData;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.punish.ActionCheck;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.storage.repository.AuditRepository;
import net.serlith.sakurabans.core.storage.repository.PlayerDataRepository;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;

public final class SakuraPlayerDataService implements PlayerDataService {

    private final PlayerDataRepository repository;
    private final PlayerRepository players;
    private final AuditRepository audit;
    private final SakuraAllowListService allowList;
    private final MuteCache mutes;
    private final NameCache names;
    private final ActionCheck actions;
    private final SyncService sync;
    private final SakuraEventBus events;
    private final InstantSource clock;

    public SakuraPlayerDataService(final PlayerDataRepository repository,
            final PlayerRepository players, final AuditRepository audit,
            final SakuraAllowListService allowList, final MuteCache mutes, final NameCache names,
            final ActionCheck actions, final SyncService sync, final SakuraEventBus events,
            final InstantSource clock) {
        this.repository = repository;
        this.players = players;
        this.audit = audit;
        this.allowList = allowList;
        this.mutes = mutes;
        this.names = names;
        this.actions = actions;
        this.sync = sync;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public CompletableFuture<AdminResult<PlayerDataExport>> export(final UUID uuid,
            final Actor actor) {
        return this.allowed(actor, Action.PlayerData.Operation.EXPORT, uuid, checked -> {
            final Instant now = this.clock.instant();
            final List<AllowEntry> allowed = this.allowList.entries().stream()
                    .filter(entry -> entry instanceof AllowedPlayer player
                            && player.uuid().equals(uuid))
                    .toList();
            return this.players.profile(uuid).thenCompose(profile -> this.players.names(uuid)
                    .thenCompose(names -> this.players.addresses(uuid)
                            .thenCompose(addresses -> this.repository.export(uuid)
                                    .thenCompose(data -> this.audit.append(now, checked,
                                                    AuditAction.EXPORT, SubjectType.PLAYER,
                                                    uuid.toString(), null, null)
                                            .thenApply(ignored ->
                                                    new SakuraAdminResult.Ok<>(
                                                            new SakuraPlayerData.Export(uuid,
                                                                    now, profile.orElse(null),
                                                                    names, addresses,
                                                                    data.received(),
                                                                    data.issued(), allowed,
                                                                    data.audit())))))));
        });
    }

    @Override
    public CompletableFuture<AdminResult<EraseResult>> erase(final UUID uuid, final Actor actor) {
        return this.allowed(actor, Action.PlayerData.Operation.ERASE, uuid, checked ->
                this.repository.erase(uuid, checked, this.clock.instant()).thenCompose(erased -> {
                    this.sync.publish(new SyncPacket.PlayerDataErasedPacket(erased, checked));
                    return this.forget(uuid).thenApply(ignored -> {
                        this.events.post(new SakuraPlayerDataErasedEvent(erased, checked,
                                false));
                        return new SakuraAdminResult.Ok<>((EraseResult) erased);
                    });
                }));
    }

    public CompletableFuture<Void> forget(final UUID uuid) {
        this.names.forget(uuid);
        this.mutes.invalidate(uuid);
        return this.mutes.reloadAll().thenCompose(ignored -> this.allowList.reload());
    }

    public CompletableFuture<Void> erased(final SyncPacket.PlayerDataErasedPacket packet) {
        return this.forget(packet.result().uuid()).thenRun(() -> this.events.post(
                new SakuraPlayerDataErasedEvent(packet.result(), packet.actor(), true)));
    }

    private <T> CompletableFuture<AdminResult<T>> allowed(final Actor actor,
            final Action.PlayerData.Operation operation, final UUID uuid,
            final Function<Actor, CompletableFuture<AdminResult<T>>> work) {
        final Actor checked;
        try {
            checked = ApiValues.actor(actor);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.actions.check(checked, Action.PlayerData.of(operation, uuid))
                .thenCompose(decision -> decision.allowed()
                        ? work.apply(checked)
                        : CompletableFuture.completedFuture(
                                new SakuraAdminResult.Forbidden<>(decision)));
    }

}
