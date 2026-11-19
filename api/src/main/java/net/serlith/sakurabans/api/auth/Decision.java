package net.serlith.sakurabans.api.auth;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Whether an actor may perform an action. {@link
 * net.serlith.sakurabans.api.integration.ActionGuard}s return one, so they are created through the
 * factories here.
 */
public interface Decision {

    /**
     * Whether the action is allowed.
     *
     * @return {@code true} if allowed
     */
    boolean allowed();

    /**
     * Why it is denied.
     *
     * @return the denial, or empty if allowed
     */
    Optional<Denial> denial();

    /**
     * A message for the actor, for example from a guard ("verify with 2FA first").
     *
     * @return the message, plain text, or empty
     */
    Optional<String> message();

    /**
     * Allows the action.
     *
     * @return the decision
     */
    static Decision allow() {
        return create(true, null, null);
    }

    /**
     * Denies the action.
     *
     * @param denial why
     * @param message a message for the actor, plain text, or {@code null}
     * @return the decision
     */
    static Decision deny(final Denial denial, final @Nullable String message) {
        return create(false, Objects.requireNonNull(denial, "denial"), message);
    }

    private static Decision create(
            final boolean allowed, final @Nullable Denial denial, final @Nullable String message) {
        record Impl(boolean allowed, Optional<Denial> denial, Optional<String> message)
                implements Decision {
        }

        return new Impl(allowed, Optional.ofNullable(denial), Optional.ofNullable(message));
    }

    /**
     * Why an action is denied.
     */
    enum Denial {

        /** The actor lacks the permission. */
        NO_PERMISSION,

        /** The target is protected from the actor (exemption by weight or permission). */
        EXEMPT,

        /** A registered {@link net.serlith.sakurabans.api.integration.ActionGuard} said no. */
        GUARD,

        /** The actor is offline and LuckPerms isn't installed, so permissions are unknown. */
        UNKNOWN_PERMISSIONS
    }
}
