package net.serlith.sakurabans.api.model;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An address victim, or a composite victim, whose address was cleared by a data erase
 * ({@link net.serlith.sakurabans.api.data.PlayerDataService#erase}). Only the SHA-256 hash of
 * the address is kept, so the punishment still applies to connections from that exact address
 * while the address itself isn't stored.
 *
 * <p>Existing punishments of an erased victim can be revoked, voided, edited and expunged. A
 * lookup by the original address (for example
 * {@code PunishmentQuery.builder().victim(AddressVictim.of(address))}) finds them too. A draft
 * can't name this view, as it has no address to act on; name the address (or the account and
 * address) instead, which also gives the victim its address back. It gets it back as well when
 * a login from that address is stopped by one of its punishments.
 */
public non-sealed interface ErasedAddressVictim extends Victim {

    /** The length of {@link #hash()}: 32 bytes as hexadecimal digits. */
    int HASH_LENGTH = 64;

    /**
     * The SHA-256 hash of the erased address (of its 4 or 16 bytes; an IPv4-mapped IPv6 address
     * hashes as IPv4).
     *
     * @return 64 lower-case hexadecimal digits
     */
    String hash();

    /**
     * The account, when this was a composite victim. The punishment then also applies to the
     * account, as before the erase.
     *
     * @return the account's UUID, or empty for an address-only victim
     */
    Optional<UUID> uuid();

    /**
     * Creates the victim.
     *
     * @param hash the SHA-256 hash of the address, as 64 hexadecimal digits (either case)
     * @param uuid the account of a composite victim, or {@code null} for an address-only victim
     * @return the victim
     */
    static ErasedAddressVictim of(final String hash, final @Nullable UUID uuid) {
        record Impl(String hash, Optional<UUID> uuid) implements ErasedAddressVictim {
            Impl {
                hash = Checks.hash(hash, "hash");
            }
        }

        return new Impl(hash, Optional.ofNullable(uuid));
    }
}
