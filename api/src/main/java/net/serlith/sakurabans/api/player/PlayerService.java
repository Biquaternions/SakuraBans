package net.serlith.sakurabans.api.player;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.NameRecord;
import net.serlith.sakurabans.api.model.PlayerProfileView;

/**
 * What SakuraBans has recorded about accounts: names, addresses and alts. Accounts are recorded
 * when they join. Implemented by SakuraBans; don't implement it.
 *
 * <p>Every method returns a future that completes on a SakuraBans worker thread.
 */
public interface PlayerService {

    /**
     * Looks up an account.
     *
     * @param uuid the account's UUID
     * @return the account, or empty if it never joined
     */
    CompletableFuture<Optional<PlayerProfileView>> profile(UUID uuid);

    /**
     * Looks up the account that most recently used a name.
     *
     * @param name the name, in any case
     * @return the account, or empty if no recorded account used the name
     */
    CompletableFuture<Optional<PlayerProfileView>> profile(String name);

    /**
     * The names an account has used.
     *
     * @param uuid the account's UUID
     * @return the names, most recently seen first
     */
    CompletableFuture<List<NameRecord>> names(UUID uuid);

    /**
     * The addresses an account has joined from. Servers configured without IP registration
     * don't record any.
     *
     * @param uuid the account's UUID
     * @return the addresses, most recently seen first
     */
    CompletableFuture<List<AddressRecord>> addresses(UUID uuid);

    /**
     * The accounts that have joined from an address.
     *
     * @param address the address
     * @return the accounts, most recently seen first
     */
    CompletableFuture<List<PlayerProfileView>> accountsAt(InetAddress address);

    /**
     * The accounts linked to one through shared addresses. Allow-listed accounts and addresses
     * don't link accounts.
     *
     * @param uuid the account's UUID
     * @param strictness how far to follow shared addresses; {@link AddressStrictness#LENIENT}
     *         finds none
     * @return the linked accounts, most recently seen first
     */
    CompletableFuture<List<AltEntry>> alts(UUID uuid, AddressStrictness strictness);

    /**
     * Removes an address from an account's history, which unlinks it from accounts that only
     * shared that address ({@code /sb unlink}).
     *
     * @param uuid the account's UUID
     * @param address the address to forget
     * @return {@code true} if the address was recorded and is now removed
     */
    CompletableFuture<Boolean> forgetAddress(UUID uuid, InetAddress address);
}
