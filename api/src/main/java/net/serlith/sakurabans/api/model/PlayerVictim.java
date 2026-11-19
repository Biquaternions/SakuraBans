package net.serlith.sakurabans.api.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A player account as a victim.
 */
public non-sealed interface PlayerVictim extends Victim {

    /**
     * The account.
     *
     * @return the account's UUID
     */
    UUID uuid();

    /**
     * Creates the victim.
     *
     * @param uuid the account's UUID
     * @return the victim
     */
    static PlayerVictim of(final UUID uuid) {
        record Impl(UUID uuid) implements PlayerVictim {
            Impl {
                Objects.requireNonNull(uuid, "uuid");
            }
        }

        return new Impl(uuid);
    }
}
