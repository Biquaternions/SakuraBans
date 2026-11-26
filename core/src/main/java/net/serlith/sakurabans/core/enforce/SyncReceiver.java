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
import net.serlith.sakurabans.core.admin.SakuraMessageTemplateService;
import net.serlith.sakurabans.core.admin.SakuraSettingsService;
import net.serlith.sakurabans.core.alts.SakuraAllowListService;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.data.SakuraPlayerDataService;
import net.serlith.sakurabans.core.notify.NotificationService;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.punish.ActiveRangeCache;
import net.serlith.sakurabans.core.punish.Applicabilities;
import net.serlith.sakurabans.core.punish.Enforcer;
import net.serlith.sakurabans.core.punish.MuteCache;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.DisplayIdCodec;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncPacketHandler;
import net.serlith.sakurabans.core.template.SakuraTemplateService;
import net.serlith.sakurabans.core.util.Futures;

public final class SyncReceiver implements SyncPacketHandler {

    private static final Duration KICK_WINDOW = Duration.ofMinutes(1);

    private final PunishmentRepository punishments;
    private final Enforcer enforcer;
    private final MuteCache mutes;
    private final SakuraScopeService scopes;
    private final Platform platform;
    private final NotificationService notifications;
    private final ConfigService config;
    private final InstantSource clock;
    private final SakuraAllowListService allowList;
    private final SakuraTemplateService templates;
    private final LockdownService lockdowns;
    private final SakuraSettingsService settings;
    private final SakuraMessageTemplateService messageTemplates;
    private final SakuraPlayerDataService playerData;
    private final ActiveRangeCache ranges;
    private final DisplayIdCodec displayIds;
    private final ComponentLogger logger;

    public SyncReceiver(final PunishmentRepository punishments, final Enforcer enforcer,
            final MuteCache mutes, final SakuraScopeService scopes, final Platform platform,
            final NotificationService notifications, final ConfigService config,
            final InstantSource clock, final SakuraAllowListService allowList,
            final SakuraTemplateService templates,
            final LockdownService lockdowns, final SakuraSettingsService settings,
            final SakuraMessageTemplateService messageTemplates,
            final SakuraPlayerDataService playerData, final ActiveRangeCache ranges,
            final DisplayIdCodec displayIds, final ComponentLogger logger) {
        this.punishments = punishments;
        this.enforcer = enforcer;
        this.mutes = mutes;
        this.scopes = scopes;
        this.platform = platform;
        this.notifications = notifications;
        this.config = config;
        this.clock = clock;
        this.allowList = allowList;
        this.templates = templates;
        this.lockdowns = lockdowns;
        this.settings = settings;
        this.messageTemplates = messageTemplates;
        this.playerData = playerData;
        this.ranges = ranges;
        this.displayIds = displayIds;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Void> handle(final SyncPacket packet) {
        return switch (packet) {
            case SyncPacket.PunishmentEnforcePacket enforce -> this.enforce(enforce);
            case SyncPacket.PunishmentRevokePacket revoke -> this.revoke(revoke);
            case SyncPacket.PunishmentEditedPacket edited -> this.edited(edited);
            case SyncPacket.PunishmentsEndedPacket ended -> this.ended(ended);
            case SyncPacket.PunishmentExpungedPacket expunged -> this.expunged(expunged);
            case SyncPacket.PlayerDataErasedPacket erased -> this.playerData.forget(erased.uuid());
            case SyncPacket.AllowListChangedPacket _ -> this.allowList.reload();
            case SyncPacket.LockdownChangedPacket _ -> this.lockdowns.reload();
            case SyncPacket.ImportedPacket imported -> this.imported();
            case SyncPacket.EntityChangedPacket changed -> switch (changed.entity()) {
                case TEMPLATE -> this.templates.reload();
                case SETTINGS -> this.settings.reload();
                case MESSAGE -> this.messageTemplates.reload();
                default -> CompletableFuture.completedFuture(null);
            };
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
                        // Lifted or expired since, or an old kick: nothing to do anymore.
                        return CompletableFuture.completedFuture(null);
                    }
                    final CompletableFuture<Void> before = old
                            .map(this.enforcer::ended)
                            .orElseGet(() -> CompletableFuture.completedFuture(null));
                    return before.thenCompose(ignored -> this.enforcer.created(punishment))
                            .thenRun(() -> this.notifications.created(punishment));
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
                return CompletableFuture.completedFuture(null);
            }
            final Punishment punishment = found.get();
            return this.enforcer.ended(punishment).thenRun(() ->
                    this.notifications.revoked(punishment, packet.visibility()));
        });
    }

    private CompletableFuture<Void> edited(final SyncPacket.PunishmentEditedPacket packet) {
        return this.punishments.byId(packet.punishmentId()).thenCompose(found -> found
                .map(current -> this.enforcer.changed(current, packet.rescoped()))
                .orElseGet(() -> CompletableFuture.completedFuture(null)));
    }

    private CompletableFuture<Void> ended(final SyncPacket.PunishmentsEndedPacket packet) {
        final List<CompletableFuture<Optional<Punishment>>> reads = packet.punishmentIds()
                .stream().map(this.punishments::byId).toList();
        return Futures.allOf(reads).thenCompose(found -> CompletableFuture.allOf(found.stream()
                .flatMap(Optional::stream)
                .map(this.enforcer::ended)
                .toArray(CompletableFuture[]::new)));
    }

    private CompletableFuture<Void> expunged(final SyncPacket.PunishmentExpungedPacket packet) {
        // The row is gone, so there is nothing to read: let the caches drop it.
        this.mutes.ended(packet.punishmentId());
        return packet.range()
                ? Futures.logged(this.ranges.reload(), this.logger, "reload the range punishments")
                : CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> missed() {
        return Futures.logged(this.reload(new ArrayList<>()), this.logger,
                "catch up after missed sync messages");
    }

    public CompletableFuture<Void> imported() {
        final List<CompletableFuture<Void>> work = new ArrayList<>();
        work.add(this.displayIds.load());
        work.add(this.allowList.reload().thenCompose(ignored -> this.ranges.reload()));
        return Futures.logged(this.reload(work), this.logger,
                "reload the caches after an import");
    }

    private CompletableFuture<Void> reload(final List<CompletableFuture<Void>> work) {
        final Instant now = this.clock.instant();
        work.add(this.templates.reload());
        work.add(this.lockdowns.reload());
        work.add(this.settings.reload());
        work.add(this.messageTemplates.reload());
        for (final OnlinePlayer player : this.platform.onlinePlayers()) {
            work.add(this.mutes.load(player.uuid(), player.address()));
            work.add(this.punishments.activeApplicable(PunishmentType.BAN,
                            Applicabilities.of(player.uuid(), player.address(),
                                    this.config.settings().enforcement().addressStrictness()),
                            this.scopes.scopesOn(player.currentServer().orElse(null)), now)
                    .thenCompose(bans -> bans.isEmpty()
                            ? CompletableFuture.completedFuture(null)
                            : this.enforcer.created(bans.getFirst())));
        }
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }

}
