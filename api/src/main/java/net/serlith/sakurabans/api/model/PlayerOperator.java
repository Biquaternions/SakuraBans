package net.serlith.sakurabans.api.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A player as an operator.
 */
public non-sealed interface PlayerOperator extends Operator {

    /**
     * The player.
     *
     * @return the player's UUID
     */
    UUID uuid();

    /**
     * The player's name at the time of the action. It is kept even if the player renames.
     *
     * @return the name
     */
    String name();

    /**
     * Creates the operator.
     *
     * @param uuid the player's UUID
     * @param name the player's name, not blank, at most {@link Lengths#MAX_PLAYER_NAME}
     *         characters
     * @return the operator
     */
    static PlayerOperator of(final UUID uuid, final String name) {
        record Impl(UUID uuid, String name) implements PlayerOperator {
            Impl {
                Objects.requireNonNull(uuid, "uuid");
                Checks.name(name, Lengths.MAX_PLAYER_NAME, "name");
            }
        }

        return new Impl(uuid, name);
    }
}
