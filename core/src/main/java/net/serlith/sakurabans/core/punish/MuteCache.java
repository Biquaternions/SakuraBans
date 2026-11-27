package net.serlith.sakurabans.core.punish;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import org.jspecify.annotations.Nullable;

public final class MuteCache {

    private static final Duration ENDED_MEMORY = Duration.ofMinutes(5);

    private final PunishmentRepository punishments;
    private final SakuraScopeService scopes;
    private final ConfigService config;
    private final InstantSource clock;
    private final ActiveRangeCache ranges;
    private final ConcurrentMap<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final Cache<Long, Boolean> ended = Caffeine.newBuilder()
            .expireAfterWrite(ENDED_MEMORY)
            .build();

    public MuteCache(final PunishmentRepository punishments, final SakuraScopeService scopes,
            final ConfigService config, final InstantSource clock,
            final ActiveRangeCache ranges) {
        this.punishments = punishments;
        this.scopes = scopes;
        this.config = config;
        this.clock = clock;
        this.ranges = ranges;
    }

    private record Entry(@Nullable InetAddress address, List<Punishment> mutes,
            Instant loadedAt) {
    }

    public sealed interface Lookup {

        record Unknown() implements Lookup {
        }

        record NotMuted() implements Lookup {
        }

        record Muted(Punishment mute) implements Lookup {
        }
    }

    public CompletableFuture<Void> load(final UUID uuid, final @Nullable InetAddress address) {
        Preconditions.checkNotNull(uuid, "uuid");
        final Instant now = this.clock.instant();
        final AddressStrictness strictness =
                this.config.settings().enforcement().addressStrictness();
        final Applicability applicability = Applicabilities.of(uuid, address, strictness);
        final boolean everyScope = this.scopes.proxy();
        final Set<Scope> scopes = this.scopes.localScopes();
        final CompletableFuture<List<Punishment>> direct = everyScope
                ? this.punishments.activeApplicableInEveryScope(PunishmentType.MUTE,
                        applicability, now)
                : this.punishments.activeApplicable(PunishmentType.MUTE, applicability, scopes,
                        now);
        return direct
                .thenCompose(mutes -> address == null
                        ? CompletableFuture.completedFuture(mutes)
                        : this.punishments.restoreErased(mutes, uuid, address)
                                .thenApply(restored -> restored.orElse(mutes)))
                .thenCompose(mutes -> (everyScope
                                ? this.ranges.matchingInEveryScope(PunishmentType.MUTE, uuid,
                                        address, strictness, now)
                                : this.ranges.matching(PunishmentType.MUTE, uuid, address,
                                        strictness, scopes, now))
                        .thenAccept(ranged -> {
                            final List<Punishment> all = new ArrayList<>(mutes);
                            all.addAll(ranged);
                            this.entries.put(uuid, new Entry(address,
                                    all.stream().filter(this::notEnded).toList(), now));
                        }));
    }

    public CompletableFuture<Void> reload(final UUID uuid) {
        final Entry entry = this.entries.get(uuid);
        return entry == null
                ? CompletableFuture.completedFuture(null)
                : this.load(uuid, entry.address());
    }

    public Lookup lookup(final UUID uuid) {
        final Entry entry = this.entries.get(uuid);
        if (entry == null) {
            return new Lookup.Unknown();
        }
        return this.lookup(entry, this.scopes.scopesOf(uuid));
    }

    public Lookup lookup(final OnlinePlayer player) {
        final Entry entry = this.entries.get(player.uuid());
        if (entry == null) {
            return new Lookup.Unknown();
        }
        return this.lookup(entry, this.scopes.scopesOn(player.currentServer().orElse(null)));
    }

    private Lookup lookup(final Entry entry, final Set<Scope> scopes) {
        return this.find(entry, scopes, true);
    }

    public Lookup lookupVisible(final UUID uuid) {
        final Entry entry = this.entries.get(uuid);
        if (entry == null) {
            return new Lookup.Unknown();
        }
        return this.find(entry, this.scopes.scopesOf(uuid), false);
    }

    private Lookup find(final Entry entry, final Set<Scope> scopes, final boolean shadow) {
        final Instant now = this.clock.instant();
        for (final Punishment mute : entry.mutes()) {
            if ((shadow || !mute.shadow()) && scopes.contains(mute.scope())
                    && mute.isActiveAt(now) && this.notEnded(mute)) {
                return new Lookup.Muted(mute);
            }
        }
        return new Lookup.NotMuted();
    }

    public Optional<Punishment> activeMute(final UUID uuid) {
        return this.lookup(uuid) instanceof Lookup.Muted(Punishment mute)
                ? Optional.of(mute)
                : Optional.empty();
    }

    public boolean loaded(final UUID uuid) {
        return this.entries.containsKey(uuid);
    }

    public void ended(final long punishmentId) {
        this.ended.put(punishmentId, Boolean.TRUE);
        this.entries.replaceAll((uuid, entry) -> entry.mutes().stream()
                .anyMatch(mute -> mute.id() == punishmentId)
                ? new Entry(entry.address(), entry.mutes().stream()
                        .filter(mute -> mute.id() != punishmentId).toList(), entry.loadedAt())
                : entry);
    }

    public CompletableFuture<Void> refreshLoadedBefore(final Instant cutoff) {
        final List<CompletableFuture<Void>> loads = new ArrayList<>();
        this.entries.forEach((uuid, entry) -> {
            if (entry.loadedAt().isBefore(cutoff)) {
                loads.add(this.load(uuid, entry.address()));
            }
        });
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<Void> reloadAll() {
        final List<CompletableFuture<Void>> loads = new ArrayList<>();
        this.entries.forEach((uuid, entry) -> loads.add(this.load(uuid, entry.address())));
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    public void invalidate(final UUID uuid) {
        this.entries.remove(uuid);
    }

    public void retainOnly(final Set<UUID> online, final Instant loadedBefore) {
        this.entries.entrySet().removeIf(entry -> !online.contains(entry.getKey())
                && entry.getValue().loadedAt().isBefore(loadedBefore));
    }

    private boolean notEnded(final Punishment mute) {
        return this.ended.getIfPresent(mute.id()) == null;
    }
}
