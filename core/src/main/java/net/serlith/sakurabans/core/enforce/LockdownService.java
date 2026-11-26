package net.serlith.sakurabans.core.enforce;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.alts.SakuraAllowListService;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.model.Lockdown;
import net.serlith.sakurabans.core.permission.PermissionService;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.storage.repository.LockdownRepository;
import net.serlith.sakurabans.core.sync.SyncPacket;
import net.serlith.sakurabans.core.sync.SyncService;
import net.serlith.sakurabans.core.util.Futures;
import org.jspecify.annotations.Nullable;

public final class LockdownService {

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);

    private final LockdownRepository repository;
    private final PermissionService permissions;
    private final SakuraAllowListService allowList;
    private final SyncService sync;
    private final ConfigService config;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final AtomicReference<List<Lockdown>> active = new AtomicReference<>(List.of());
    private final Set<Scope> startedHere = ConcurrentHashMap.newKeySet();

    public LockdownService(final LockdownRepository repository,
            final PermissionService permissions, final SakuraAllowListService allowList,
            final SyncService sync, final ConfigService config, final InstantSource clock,
            final ComponentLogger logger) {
        this.repository = repository;
        this.permissions = permissions;
        this.allowList = allowList;
        this.sync = sync;
        this.config = config;
        this.clock = clock;
        this.logger = logger;
    }

    public CompletableFuture<Void> reload() {
        return this.repository.active(this.clock.instant()).thenAccept(this.active::set);
    }

    public List<Lockdown> lockdowns() {
        final Instant now = this.clock.instant();
        return this.active.get().stream().filter(lockdown -> lockdown.activeAt(now)).toList();
    }

    public CompletableFuture<Void> start(final Scope scope, final String reason,
            final Actor actor, final Instant startedAt, final @Nullable Instant endsAt) {
        final Lockdown lockdown = new Lockdown(scope, reason, actor, startedAt, endsAt);
        return this.repository.start(lockdown).thenCompose(ignored -> {
            this.startedHere.add(scope);
            this.sync.publish(new SyncPacket.LockdownChangedPacket());
            return this.reload();
        });
    }

    public CompletableFuture<Boolean> end(final Scope scope) {
        return this.repository.end(scope).thenCompose(ended -> {
            this.startedHere.remove(scope);
            if (!ended) {
                return CompletableFuture.completedFuture(false);
            }
            this.sync.publish(new SyncPacket.LockdownChangedPacket());
            return this.reload().thenApply(ignored -> true);
        });
    }

    public CompletableFuture<Optional<Lockdown>> blocking(final UUID uuid,
            final Collection<? extends Scope> scopes) {
        final Optional<Lockdown> lockdown = this.lockdowns().stream()
                .filter(candidate -> scopes.contains(candidate.scope()))
                .findFirst();
        if (lockdown.isEmpty() || this.allowList.isAllowed(uuid)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        // Not online yet: the permission comes from LuckPerms.
        return this.permissions.has(uuid, Permissions.LOCKDOWN_BYPASS)
                .thenApply(bypass -> bypass.orElse(false) ? Optional.empty() : lockdown);
    }

    public void stop() {
        if (this.config.settings().lockdown().persist() || this.startedHere.isEmpty()) {
            return;
        }
        final CompletableFuture<?>[] ends = this.startedHere.stream()
                .map(scope -> this.end(scope).exceptionally(failure -> false))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(ends).get(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final Exception e) {
            this.logger.warn("Couldn't end the lockdowns started here", Futures.unwrap(e));
        }
    }

}
