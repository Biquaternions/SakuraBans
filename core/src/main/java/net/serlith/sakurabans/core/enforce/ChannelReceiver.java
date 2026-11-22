package net.serlith.sakurabans.core.enforce;

import java.time.InstantSource;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.messaging.ProxyChannel;
import net.serlith.sakurabans.core.punish.Enforcer;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.scope.ServerNameDetector;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.util.Futures;

public final class ChannelReceiver {

    private final ProxyChannel channel;
    private final SakuraScopeService scopes;
    private final ServerNameDetector serverNames;
    private final PunishmentRepository punishments;
    private final Enforcer enforcer;
    private final InstantSource clock;
    private final ComponentLogger logger;

    public ChannelReceiver(final ProxyChannel channel, final SakuraScopeService scopes,
            final ServerNameDetector serverNames, final PunishmentRepository punishments,
            final Enforcer enforcer, final InstantSource clock, final ComponentLogger logger) {
        this.channel = channel;
        this.scopes = scopes;
        this.serverNames = serverNames;
        this.punishments = punishments;
        this.enforcer = enforcer;
        this.clock = clock;
        this.logger = logger;
    }

    public CompletableFuture<Void> received(final byte[] data) {
        return this.channel.decode(data).map(message -> switch (message) {
            case ProxyChannel.Message.ServerName name -> {
                if (!this.scopes.proxy()) {
                    this.serverNames.detected(name.name());
                }
                yield CompletableFuture.<Void>completedFuture(null);
            }
            case ProxyChannel.Message.Enforce enforce -> this.scopes.proxy()
                    ? Futures.logged(this.enforce(enforce.punishmentId()), this.logger,
                            "enforce punishment " + enforce.punishmentId() + " for a backend")
                    : CompletableFuture.<Void>completedFuture(null);
        }).orElseGet(() -> CompletableFuture.completedFuture(null));
    }

    private CompletableFuture<Void> enforce(final long id) {
        return this.punishments.byId(id).thenCompose(found -> found
                .filter(punishment -> punishment.type() == PunishmentType.BAN)
                .filter(punishment -> punishment.isActiveAt(this.clock.instant()))
                .map(this.enforcer::created)
                .orElseGet(() -> CompletableFuture.completedFuture(null)));
    }
}
