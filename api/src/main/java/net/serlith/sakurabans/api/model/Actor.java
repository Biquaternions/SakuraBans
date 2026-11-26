package net.serlith.sakurabans.api.model;

import java.util.Objects;

/**
 * Who performs an action, and through which surface. Every action that changes data takes an
 * actor, and the source is stored with the change and in the audit log.
 */
public interface Actor {

    /**
     * Who performs the action.
     *
     * @return the operator
     */
    Operator operator();

    /**
     * Through which surface, as a short tag: {@code in-game}, {@code console}, {@code web},
     * {@code api:<plugin>}, ...
     *
     * @return the source, lower case
     */
    String source();

    /**
     * Creates an actor.
     *
     * @param operator who performs the action
     * @param source the surface: 1-64 characters of {@code a-z}, {@code 0-9}, {@code _},
     *         {@code .}, {@code :} or {@code -}, in any case
     * @return the actor
     */
    static Actor of(final Operator operator, final String source) {
        record Impl(Operator operator, String source) implements Actor {
            Impl {
                Objects.requireNonNull(operator, "operator");
                source = Checks.source(source, "source");
            }
        }

        return new Impl(operator, source);
    }
}
