package net.serlith.sakurabans.api.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An operator named by the console or a plugin, for example with {@code --sender}.
 */
public non-sealed interface CustomOperator extends Operator {

    /**
     * The name to show.
     *
     * @return the name
     */
    String name();

    /**
     * The account the action is attributed to, if any.
     *
     * @return the UUID, or empty
     */
    Optional<UUID> uuid();

    /**
     * Creates the operator.
     *
     * @param name the name, not blank, at most {@link Lengths#MAX_OPERATOR_NAME} characters
     * @param uuid the UUID to attribute the action to, or {@code null}
     * @return the operator
     */
    static CustomOperator of(final String name, final @Nullable UUID uuid) {
        record Impl(String name, Optional<UUID> uuid) implements CustomOperator {
            Impl {
                Checks.name(name, Lengths.MAX_OPERATOR_NAME, "name");
                Objects.requireNonNull(uuid, "uuid");
            }
        }

        return new Impl(name, Optional.ofNullable(uuid));
    }
}
