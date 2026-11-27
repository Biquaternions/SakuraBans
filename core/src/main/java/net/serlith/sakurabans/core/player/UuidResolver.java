package net.serlith.sakurabans.core.player;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.integration.ProfileSource;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.util.Futures;
import net.serlith.sakurabans.core.util.Strings;
import net.serlith.sakurabans.core.util.Uuids;

public final class UuidResolver {

    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(3);

    private final SakuraIntegrationRegistry integrations;
    private final Platform platform;
    private final ConfigService config;
    private final ComponentLogger logger;
    private final Cache<String, UUID> found = Caffeine.newBuilder()
            .maximumSize(5_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
    private final Cache<String, Boolean> missing = Caffeine.newBuilder()
            .maximumSize(5_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public UuidResolver(final SakuraIntegrationRegistry integrations, final Platform platform,
            final ConfigService config, final ComponentLogger logger) {
        this.integrations = integrations;
        this.platform = platform;
        this.config = config;
        this.logger = logger;
    }

    public CompletableFuture<Optional<UUID>> resolve(final String name) {
        // Floodgate names (prefix + Bedrock name) aren't valid Java names.
        if (!Strings.isValidPlayerName(name)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (!this.platform.onlineMode()) {
            return CompletableFuture.completedFuture(Optional.of(Uuids.offline(name)));
        }
        final String key = Strings.lower(name);
        final UUID cached = this.found.getIfPresent(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        if (this.missing.getIfPresent(key) != null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return this.fromSources(name, this.integrations.profileSources(), 0)
                .thenCompose(result -> result.isPresent()
                                || !this.config.settings().players().remoteLookup()
                        ? CompletableFuture.completedFuture(result)
                        : this.step("the server's profile lookup", name,
                                () -> this.platform.profiles().uuid(name)))
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        return;
                    }
                    if (result.isPresent()) {
                        this.found.put(key, result.get());
                    } else {
                        this.missing.put(key, Boolean.TRUE);
                    }
                });
    }

    private CompletableFuture<Optional<UUID>> fromSources(final String name,
            final List<ProfileSource> sources, final int index) {
        if (index >= sources.size()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        final ProfileSource source = sources.get(index);
        return this.step("the profile source " + source.name(), name, () -> source.uuid(name))
                .thenCompose(result -> result.isPresent()
                        ? CompletableFuture.completedFuture(result)
                        : this.fromSources(name, sources, index + 1));
    }

    private CompletableFuture<Optional<UUID>> step(final String what, final String name,
            final Supplier<CompletableFuture<Optional<UUID>>> lookup) {
        CompletableFuture<Optional<UUID>> answer;
        try {
            answer = lookup.get();
        } catch (final RuntimeException e) {
            answer = CompletableFuture.failedFuture(e);
        }
        return answer
                .orTimeout(STEP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(failure -> {
                    this.logger.warn("Looking up {} through {} failed; treating it as unknown",
                            name, what, Futures.unwrap(failure));
                    return Optional.empty();
                });
    }

}
