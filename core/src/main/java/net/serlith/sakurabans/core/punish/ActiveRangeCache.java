package net.serlith.sakurabans.core.punish;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.storage.repository.AltPolicy;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.jspecify.annotations.Nullable;

public final class ActiveRangeCache {

    private final PunishmentRepository punishments;
    private final PlayerRepository players;
    private final AltPolicy policy;
    private final InstantSource clock;
    private final AtomicReference<List<Punishment>> ranges = new AtomicReference<>(List.of());

    public ActiveRangeCache(final PunishmentRepository punishments,
            final PlayerRepository players, final AltPolicy policy, final InstantSource clock) {
        this.punishments = punishments;
        this.players = players;
        this.policy = policy;
        this.clock = clock;
    }

    public CompletableFuture<Void> reload() {
        return this.punishments.activeRanges(this.clock.instant()).thenAccept(this.ranges::set);
    }

    public List<Punishment> all() {
        return this.ranges.get();
    }

    public CompletableFuture<List<Punishment>> matching(final PunishmentType type,
            final UUID uuid, final @Nullable InetAddress address,
            final AddressStrictness strictness, final Collection<? extends Scope> scopes,
            final Instant now) {
        final List<Punishment> candidates = this.ranges.get().stream()
                .filter(punishment -> punishment.type() == type)
                .filter(punishment -> scopes.contains(punishment.scope()))
                .filter(punishment -> punishment.isActiveAt(now))
                .toList();
        if (candidates.isEmpty() || this.policy.allowedAccounts().contains(uuid)) {
            return CompletableFuture.completedFuture(List.of());
        }
        return this.addresses(uuid, address, strictness, now).thenApply(addresses ->
                candidates.stream().filter(punishment -> addresses.stream()
                        .anyMatch(((AddressRangeVictim) punishment.victim())::contains))
                        .toList());
    }

    public CompletableFuture<Boolean> reaches(final Punishment punishment, final UUID uuid,
            final InetAddress address, final AddressStrictness strictness, final Instant now) {
        if (!(punishment.victim() instanceof AddressRangeVictim range)
                || this.policy.allowedAccounts().contains(uuid)) {
            return CompletableFuture.completedFuture(false);
        }
        return this.addresses(uuid, address, strictness, now)
                .thenApply(addresses -> addresses.stream().anyMatch(range::contains));
    }

    private CompletableFuture<List<InetAddress>> addresses(final UUID uuid,
            final @Nullable InetAddress address, final AddressStrictness strictness,
            final Instant now) {
        final Set<InetAddress> allowed = this.policy.allowedAddresses();
        final List<InetAddress> found = new ArrayList<>();
        if (address != null && !allowed.contains(InetAddresses.normalize(address))) {
            found.add(address);
        }
        if (strictness == AddressStrictness.LENIENT) {
            return CompletableFuture.completedFuture(found);
        }
        final Optional<Duration> expiration = this.policy.recordExpiration();
        return this.players.addresses(uuid).thenApply(records -> {
            for (final AddressRecord record : records) {
                final boolean recent = expiration
                        .map(limit -> !record.lastSeen().isBefore(now.minus(limit)))
                        .orElse(true);
                if (recent && !allowed.contains(record.address())) {
                    found.add(record.address());
                }
            }
            return found;
        });
    }
}
