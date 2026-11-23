package net.serlith.sakurabans.core.alts;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.serlith.sakurabans.api.allow.AllowEntry;
import net.serlith.sakurabans.api.allow.AllowListService;
import net.serlith.sakurabans.api.allow.AllowedAddress;
import net.serlith.sakurabans.api.allow.AllowedPlayer;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.storage.repository.AllowListRepository;
import net.serlith.sakurabans.core.storage.repository.AltPolicy;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.jspecify.annotations.Nullable;

public final class SakuraAllowListService implements AllowListService, AltPolicy {

    static final int MAX_NOTE = 256;

    private final AllowListRepository repository;
    private final SyncService sync;
    private final ConfigService config;
    private final InstantSource clock;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    public SakuraAllowListService(final AllowListRepository repository, final SyncService sync,
            final ConfigService config, final InstantSource clock) {
        this.repository = repository;
        this.sync = sync;
        this.config = config;
        this.clock = clock;
    }

    private record Snapshot(List<AllowEntry> entries, Set<UUID> accounts,
            Set<InetAddress> addresses) {

        static final Snapshot EMPTY = new Snapshot(List.of(), Set.of(), Set.of());

        static Snapshot of(final List<AllowEntry> entries) {
            return new Snapshot(entries,
                    entries.stream().filter(AllowedPlayer.class::isInstance)
                            .map(entry -> ((AllowedPlayer) entry).uuid())
                            .collect(Collectors.toUnmodifiableSet()),
                    entries.stream().filter(AllowedAddress.class::isInstance)
                            .map(entry -> InetAddresses.normalize(
                                    ((AllowedAddress) entry).address()))
                            .collect(Collectors.toUnmodifiableSet()));
        }
    }

    public CompletableFuture<Void> reload() {
        return this.repository.entries()
                .thenAccept(entries -> this.snapshot.set(Snapshot.of(entries)));
    }

    @Override
    public boolean isAllowed(final UUID uuid) {
        return this.snapshot.get().accounts().contains(uuid);
    }

    @Override
    public boolean isAllowed(final InetAddress address) {
        return this.snapshot.get().addresses().contains(InetAddresses.normalize(address));
    }

    @Override
    public List<AllowEntry> entries() {
        return this.snapshot.get().entries();
    }

    @Override
    public CompletableFuture<Boolean> allow(final UUID uuid, final Actor actor,
            final @Nullable String note) {
        Preconditions.checkNotNull(uuid, "uuid");
        return this.changed(this.repository.add(uuid, checked(actor), this.clock.instant(),
                note(note)));
    }

    @Override
    public CompletableFuture<Boolean> allow(final InetAddress address, final Actor actor,
            final @Nullable String note) {
        Preconditions.checkNotNull(address, "address");
        return this.changed(this.repository.add(InetAddresses.normalize(address), checked(actor),
                this.clock.instant(), note(note)));
    }

    @Override
    public CompletableFuture<Boolean> disallow(final UUID uuid, final Actor actor) {
        Preconditions.checkNotNull(uuid, "uuid");
        return this.changed(this.repository.remove(uuid, checked(actor), this.clock.instant()));
    }

    @Override
    public CompletableFuture<Boolean> disallow(final InetAddress address, final Actor actor) {
        Preconditions.checkNotNull(address, "address");
        return this.changed(this.repository.remove(InetAddresses.normalize(address),
                checked(actor), this.clock.instant()));
    }

    private CompletableFuture<Boolean> changed(final CompletableFuture<Boolean> write) {
        return write.thenCompose(changed -> {
            if (!changed) {
                return CompletableFuture.completedFuture(false);
            }
            this.sync.publish(new SyncPacket.AllowListChangedPacket());
            return this.reload().thenApply(ignored -> true);
        });
    }

    private static Actor checked(final Actor actor) {
        return Preconditions.checkNotNull(actor, "actor");
    }

    private static @Nullable String note(final @Nullable String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        final String stripped = note.strip();
        Preconditions.checkArgument(stripped.length() <= MAX_NOTE,
                "a note has at most %s characters", MAX_NOTE);
        return stripped;
    }

    @Override
    public Set<UUID> allowedAccounts() {
        return this.snapshot.get().accounts();
    }

    @Override
    public Set<InetAddress> allowedAddresses() {
        return this.snapshot.get().addresses();
    }

    @Override
    public Optional<Duration> recordExpiration() {
        return Optional.ofNullable(this.config.settings().alts().recordExpiration());
    }

}
