package net.serlith.sakurabans.api.integration;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Another plugin's knowledge of accounts, asked when SakuraBans doesn't know a name or UUID yet
 * (before the Mojang lookup). If a call fails or times out, SakuraBans continues without it.
 */
public interface ProfileSource {

    /**
     * A name for logs.
     *
     * @return the name, for example your plugin's name
     */
    String name();

    /**
     * The account that uses a name now.
     *
     * @param name the name, in any case
     * @return the account's UUID, or empty if unknown
     */
    CompletableFuture<Optional<UUID>> uuid(String name);

    /**
     * An account's current name.
     *
     * @param uuid the account
     * @return the name, or empty if unknown
     */
    CompletableFuture<Optional<String>> currentName(UUID uuid);

    /**
     * The address an account last joined from.
     *
     * @param uuid the account
     * @return the address, or empty if unknown
     */
    CompletableFuture<Optional<InetAddress>> lastAddress(UUID uuid);
}
