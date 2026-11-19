package net.serlith.sakurabans.api.integration;

import java.net.InetAddress;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Another plugin's alt detection. Its alts are merged into
 * {@link net.serlith.sakurabans.api.player.PlayerService#alts}, {@code /alts} and join notices,
 * marked with its {@link #name()} and score. SakuraBans' own enforcement keeps using its address
 * strictness; external alts don't ban anyone by themselves.
 *
 * <p>If a call fails or times out, SakuraBans shows its own alts only.
 */
public interface AltSource {

    /**
     * The name shown next to its alts.
     *
     * @return the name, for example your plugin's name
     */
    String name();

    /**
     * The accounts linked to one.
     *
     * @param uuid the account
     * @param minScore the lowest score to return, from 0 to 1
     * @return the alts, highest score first
     */
    CompletableFuture<List<ExternalAlt>> alts(UUID uuid, double minScore);

    /**
     * How sure the source is that two accounts belong to the same person.
     *
     * @param first one account
     * @param second the other account
     * @return the score, from 0 to 1, or empty if the source has no opinion
     */
    CompletableFuture<OptionalDouble> score(UUID first, UUID second);

    /**
     * The accounts the source has seen on an address.
     *
     * @param address the address
     * @return the accounts, possibly empty
     */
    CompletableFuture<List<UUID>> accountsAt(InetAddress address);
}
