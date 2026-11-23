package net.serlith.sakurabans.core.enforce;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.alts.SakuraAllowListService;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraPunishmentCreatedEvent;
import net.serlith.sakurabans.core.event.SakuraPunishmentRevokedEvent;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.punish.Applicabilities;
import net.serlith.sakurabans.core.punish.Enforcer;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncPacketHandler;
import net.serlith.sakurabans.core.util.Futures;

public final class SyncReceiver implements SyncPacketHandler {

    private static final Duration KICK_WINDOW = Duration.ofMinutes(1);

    private final PunishmentRepository punishments;
    private final Enforcer enforcer;
    private final MuteCache mutes;
    private final SakuraScopeService scopes;
    private final Platform platform;
    private final SakuraEventBus events;
    private final ConfigService config;
    private final InstantSource clock;
    private final SakuraAllowListService allowList;
    private final ComponentLogger logger;

    public SyncReceiver(final PunishmentRepository punishments, final Enforcer enforcer,
            final MuteCache mutes, final SakuraScopeService scopes, final Platform platform,
            final SakuraEventBus events, final ConfigService config, final InstantSource clock,
            final SakuraAllowListService allowList, final ComponentLogger logger) {
        this.punishments = punishments;
        this.enforcer = enforcer;
        this.mutes = mutes;
        this.scopes = scopes;
        this.platform = platform;
        this.events = events;
        this.config = config;
        this.clock = clock;
        this.allowList = allowList;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Void> handle(final SyncPacket packet) {
        return switch (packet) {
            case SyncPacket.PunishmentEnforcePacket enforce -> this.enforce(enforce);
            case SyncPacket.PunishmentRevokePacket revoke -> this.revoke(revoke);
            case SyncPacket.AllowListChangedPacket allowList -> this.allowList.reload();
        };
    }

    private CompletableFuture<Void> enforce(final SyncPacket.PunishmentEnforcePacket packet) {
        final CompletableFuture<Optional<Punishment>> superseded = packet.supersededId()
                .map(this.punishments::byId)
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
        return superseded.thenCompose(old -> this.punishments.byId(packet.punishmentId())
                .thenCompose(found -> {
                    final Instant now = this.clock.instant();
                    final Punishment punishment = found.filter(p -> this.current(p, now))
                            .orElse(null);
                    if (punishment == null) {
                        // Lifted or expired since, or an old kick: nothing to do any more.
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    final CompletableFuture<Void> before = old
                            .map(this.enforcer::ended)
                            .orElseGet(() -> CompletableFuture.completedFuture(null));
                    return before.thenCompose(ignored -> this.enforcer.created(punishment))
                            .thenRun(() -> this.events.post(new SakuraPunishmentCreatedEvent(
                                    punishment, old.orElse(null), true)));
                }));
    }

    private boolean current(final Punishment punishment, final Instant now) {
        return punishment.type().instant()
                ? punishment.createdAt().isAfter(now.minus(KICK_WINDOW))
                : punishment.isActiveAt(now);
    }

    private CompletableFuture<Void> revoke(final SyncPacket.PunishmentRevokePacket packet) {
        return this.punishments.byId(packet.punishmentId()).thenCompose(found -> {
            if (found.isEmpty() || found.get().end().isEmpty()) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            final Punishment punishment = found.get();
            return this.enforcer.ended(punishment).thenRun(() -> this.events.post(
                    new SakuraPunishmentRevokedEvent(punishment, packet.visibility(), true)));
        });
    }

    @Override
    public CompletableFuture<Void> missed() {
        final Instant now = this.clock.instant();
        final List<CompletableFuture<Void>> work = new ArrayList<>();
        for (final OnlinePlayer player : this.platform.onlinePlayers()) {
            if (!this.scopes.proxy()) {
                work.add(this.mutes.load(player.uuid(), player.address()));
            }
            work.add(this.punishments.activeApplicable(PunishmentType.BAN,
                            Applicabilities.of(player.uuid(), player.address(),
                                    this.config.settings().enforcement().addressStrictness()),
                            this.scopes.scopesOn(player.currentServer().orElse(null)), now)
                    .thenCompose(bans -> bans.isEmpty()
                            ? CompletableFuture.<Void>completedFuture(null)
                            : this.enforcer.created(bans.getFirst())));
        }
        return Futures.logged(CompletableFuture.allOf(work.toArray(CompletableFuture[]::new)),
                this.logger, "catch up after missed sync messages");
    }

}
