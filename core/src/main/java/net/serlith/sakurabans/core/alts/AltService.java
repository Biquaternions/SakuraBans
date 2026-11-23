package net.serlith.sakurabans.core.alts;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.integration.AltSource;
import net.serlith.sakurabans.api.integration.ExternalAlt;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.player.AltEntry;
import net.serlith.sakurabans.core.integration.SakuraIntegrationRegistry;
import net.serlith.sakurabans.core.model.SakuraAltEntry;
import net.serlith.sakurabans.core.player.AltLookup;
import net.serlith.sakurabans.core.storage.repository.AltPolicy;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.util.Futures;

public final class AltService implements AltLookup {

    private static final Duration SOURCE_TIMEOUT = Duration.ofSeconds(3);

    private final PlayerRepository players;
    private final PunishmentRepository punishments;
    private final AltPolicy policy;
    private final SakuraIntegrationRegistry integrations;
    private final InstantSource clock;
    private final ComponentLogger logger;

    public AltService(final PlayerRepository players, final PunishmentRepository punishments,
            final AltPolicy policy, final SakuraIntegrationRegistry integrations,
            final InstantSource clock, final ComponentLogger logger) {
        this.players = players;
        this.punishments = punishments;
        this.policy = policy;
        this.integrations = integrations;
        this.clock = clock;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<List<AltEntry>> alts(final UUID uuid,
            final AddressStrictness strictness) {
        if (strictness == AddressStrictness.LENIENT) {
            return CompletableFuture.completedFuture(List.of());
        }
        final Instant now = this.clock.instant();
        return this.own(uuid, strictness, now)
                .thenCompose(own -> this.external(uuid, own))
                .thenCompose(found -> this.punishments.activeTypes(found.keySet(), now)
                        .thenApply(types -> found.values().stream()
                                .map(entry -> entry.punishments(
                                        types.getOrDefault(entry.account().uuid(), Set.of())))
                                .map(AltEntry.class::cast)
                                .toList()));
    }

    private CompletableFuture<Map<UUID, SakuraAltEntry>> own(final UUID uuid,
            final AddressStrictness strictness, final Instant now) {
        return this.players.links(uuid, this.policy, now).thenCompose(first -> {
            final Map<UUID, SakuraAltEntry> found = new LinkedHashMap<>();
            first.forEach(link -> found.put(link.account().uuid(), own(link)));
            if (strictness == AddressStrictness.NORMAL) {
                return CompletableFuture.completedFuture(found);
            }
            // One more hop: the addresses shown are the intermediate account's.
            final List<CompletableFuture<List<PlayerRepository.Link>>> second = first.stream()
                    .map(link -> this.players.links(link.account().uuid(), this.policy, now))
                    .toList();
            return Futures.allOf(second).thenApply(hops -> {
                for (final List<PlayerRepository.Link> links : hops) {
                    for (final PlayerRepository.Link link : links) {
                        if (!link.account().uuid().equals(uuid)) {
                            found.putIfAbsent(link.account().uuid(), own(link));
                        }
                    }
                }
                return found;
            });
        });
    }

    private static SakuraAltEntry own(final PlayerRepository.Link link) {
        return new SakuraAltEntry(link.account(), link.shared(), SakuraAltEntry.OWN_SOURCE, null,
                Set.of());
    }

    private CompletableFuture<Map<UUID, SakuraAltEntry>> external(final UUID uuid,
            final Map<UUID, SakuraAltEntry> found) {
        final List<CompletableFuture<List<SakuraAltEntry>>> lookups = new ArrayList<>();
        for (final AltSource source : this.integrations.altSources()) {
            lookups.add(this.ask(source, uuid));
        }
        return Futures.allOf(lookups).thenApply(results -> {
            results.forEach(entries -> entries.forEach(entry -> {
                if (!entry.account().uuid().equals(uuid)) {
                    found.putIfAbsent(entry.account().uuid(), entry);
                }
            }));
            return found;
        });
    }

    private CompletableFuture<List<SakuraAltEntry>> ask(final AltSource source,
            final UUID uuid) {
        final CompletableFuture<List<ExternalAlt>> alts;
        try {
            alts = source.alts(uuid, 0);
        } catch (final RuntimeException e) {
            return this.leftOut(source, e);
        }
        return alts.orTimeout(SOURCE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .thenCompose(external -> {
                    final List<CompletableFuture<Optional<SakuraAltEntry>>> entries =
                            external.stream().map(alt -> this.players.profile(alt.uuid())
                                    // Only accounts SakuraBans has seen can be shown.
                                    .thenApply(profile -> profile.map(account ->
                                            new SakuraAltEntry(account, alt.sharedAddresses(),
                                                    source.name(), alt.score(), Set.of()))))
                                    .toList();
                    return Futures.allOf(entries).thenApply(all ->
                            all.stream().flatMap(Optional::stream).toList());
                })
                .exceptionallyCompose(failure -> this.leftOut(source, failure));
    }

    private CompletableFuture<List<SakuraAltEntry>> leftOut(final AltSource source,
            final Throwable failure) {
        this.logger.warn("The alt source {} failed; showing SakuraBans' own alts only",
                source.name(), Futures.unwrap(failure));
        return CompletableFuture.completedFuture(List.of());
    }

    public static boolean anyPunished(final List<AltEntry> alts) {
        return alts.stream().anyMatch(alt -> alt.activePunishments().contains(PunishmentType.BAN)
                || alt.activePunishments().contains(PunishmentType.MUTE));
    }

}
