package net.serlith.sakurabans.api.allow;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Operator;
import org.jspecify.annotations.Nullable;

/**
 * The allow list. It is small and kept in memory, so reads answer immediately on any thread;
 * changes are stored and synced to other instances. Implemented by SakuraBans; don't implement
 * it.
 */
public interface AllowListService {

    /**
     * Whether an account is allow-listed.
     *
     * @param uuid the account's UUID
     * @return {@code true} if it is
     */
    boolean isAllowed(UUID uuid);

    /**
     * Whether an address is allow-listed.
     *
     * @param address the address
     * @return {@code true} if it is
     */
    boolean isAllowed(InetAddress address);

    /**
     * Every entry.
     *
     * @return the entries, newest first
     */
    List<AllowEntry> entries();

    /**
     * Allow-lists an account.
     *
     * @param uuid the account's UUID
     * @param operator who adds it
     * @param note why, at most 256 characters, or {@code null}
     * @return {@code true} if it was added, {@code false} if it already was allow-listed
     */
    CompletableFuture<Boolean> allow(UUID uuid, Operator operator, @Nullable String note);

    /**
     * Allow-lists an address.
     *
     * @param address the address
     * @param operator who adds it
     * @param note why, at most 256 characters, or {@code null}
     * @return {@code true} if it was added, {@code false} if it already was allow-listed
     */
    CompletableFuture<Boolean> allow(InetAddress address, Operator operator, @Nullable String note);

    /**
     * Removes an account from the allow list.
     *
     * @param uuid the account's UUID
     * @param operator who removes it
     * @return {@code true} if it was allow-listed and is now removed
     */
    CompletableFuture<Boolean> disallow(UUID uuid, Operator operator);

    /**
     * Removes an address from the allow list.
     *
     * @param address the address
     * @param operator who removes it
     * @return {@code true} if it was allow-listed and is now removed
     */
    CompletableFuture<Boolean> disallow(InetAddress address, Operator operator);
}
