package net.serlith.sakurabans.core.alts;

import java.net.InetAddress;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.player.AltEntry;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.model.SakuraAltEntry;
import net.serlith.sakurabans.core.player.AltLookup;
import net.serlith.sakurabans.core.storage.repository.AltPolicy;
import net.serlith.sakurabans.core.storage.repository.PlayerRepository;
import net.serlith.sakurabans.core.storage.repository.PunishmentRepository;
import net.serlith.sakurabans.core.util.Futures;

public final class AltService implements AltLookup {

    private final PlayerRepository players;
    private final PunishmentRepository punishments;
    private final AltPolicy policy;
    private final InstantSource clock;

    public AltService(final PlayerRepository players, final PunishmentRepository punishments,
            final AltPolicy policy, final InstantSource clock) {
        this.players = players;
        this.punishments = punishments;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public CompletableFuture<List<AltEntry>> alts(final UUID uuid,
            final AddressStrictness strictness) {
        if (strictness == AddressStrictness.LENIENT) {
            return CompletableFuture.completedFuture(List.of());
        }
        final Instant now = this.clock.instant();
        return this.own(uuid, strictness, now)
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
        return new SakuraAltEntry(link.account(), link.shared(), Set.of());
    }

    public static List<AltEntry> visible(final List<AltEntry> alts,
            final Settings.HiddenPlayers hidden) {
        if (hidden.empty()) {
            return alts;
        }
        final List<AltEntry> visible = new ArrayList<>();
        for (final AltEntry alt : alts) {
            if (hidden.hidesPlayer(alt.account().uuid(), alt.account().name())) {
                continue;
            }
            final List<InetAddress> shared = alt.sharedAddresses().stream()
                    .filter(address -> !hidden.hidesAddress(address))
                    .toList();
            if (shared.isEmpty() && !alt.sharedAddresses().isEmpty()) {
                continue;
            }
            visible.add(shared.size() == alt.sharedAddresses().size() ? alt
                    : new SakuraAltEntry(alt.account(), shared, alt.activePunishments()));
        }
        return List.copyOf(visible);
    }

    public static boolean anyPunished(final List<AltEntry> alts) {
        return alts.stream().anyMatch(alt -> alt.activePunishments().contains(PunishmentType.BAN)
                || alt.activePunishments().contains(PunishmentType.MUTE));
    }

}
