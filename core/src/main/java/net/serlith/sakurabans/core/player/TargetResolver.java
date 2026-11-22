package net.serlith.sakurabans.core.player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.model.TargetInput;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;

public final class TargetResolver {

    private final Platform platform;
    private final NameCache names;
    private final PlayerRepository players;
    private final UuidResolver uuids;

    public TargetResolver(final Platform platform, final NameCache names,
            final PlayerRepository players, final UuidResolver uuids) {
        this.platform = platform;
        this.names = names;
        this.players = players;
        this.uuids = uuids;
    }

    public CompletableFuture<ResolvedTarget> resolve(final TargetInput input) {
        return switch (input) {
            case TargetInput.NameInput name -> this.byName(name);
            case TargetInput.UuidInput uuid -> this.byUuid(uuid.uuid())
                    .thenApply(ResolvedTarget.class::cast);
            case TargetInput.AddressInput address ->
                    CompletableFuture.completedFuture(
                            new ResolvedTarget.Address(address.address()));
            case TargetInput.RangeInput range ->
                    CompletableFuture.completedFuture(new ResolvedTarget.Range(range.range()));
            case TargetInput.IdInput id ->
                    CompletableFuture.completedFuture(new ResolvedTarget.PunishmentId(id.id()));
        };
    }

    private CompletableFuture<ResolvedTarget> byName(final TargetInput.NameInput input) {
        final String name = input.name();
        final Optional<? extends OnlinePlayer> online = this.platform.onlinePlayers().stream()
                .filter(player -> player.name().equalsIgnoreCase(name))
                .findFirst();
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(this.online(online.get()));
        }
        final Optional<UUID> cached = this.names.uuid(name);
        return cached
                .map(uuid -> this.byUuid(uuid).thenApply(ResolvedTarget.class::cast))
                .orElseGet(() -> this.players.profile(name).thenCompose(profile -> profile
                        .<CompletionStage<ResolvedTarget>>map(known ->
                                CompletableFuture.completedFuture(this.known(known)))
                        .orElseGet(() -> this.uuids.resolve(name).thenApply(uuid -> uuid
                                .<ResolvedTarget>map(found -> {
                                    this.names.put(found, name);
                                    return new ResolvedTarget.Player(found, name, null);
                                })
                                .orElseGet(() -> new ResolvedTarget.Unknown(input))))));
    }

    private CompletableFuture<ResolvedTarget.Player> byUuid(final UUID uuid) {
        final Optional<OnlinePlayer> online = this.platform.player(uuid);
        return online
                .map(player -> CompletableFuture.completedFuture(this.online(player)))
                .orElseGet(() -> this.players.profile(uuid).thenApply(profile -> profile
                        .map(this::known)
                        .orElseGet(() -> new ResolvedTarget.Player(uuid,
                                this.names.name(uuid).orElse(null), null))));
    }

    private ResolvedTarget.Player online(final OnlinePlayer player) {
        this.names.put(player.uuid(), player.name());
        return new ResolvedTarget.Player(player.uuid(), player.name(), player.address());
    }

    private ResolvedTarget.Player known(final PlayerProfileView profile) {
        this.names.put(profile.uuid(), profile.name());
        return new ResolvedTarget.Player(profile.uuid(), profile.name(),
                profile.lastAddress().orElse(null));
    }
}
