package net.serlith.sakurabans.api.admin;

import java.util.List;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.validation.FieldError;

/**
 * The outcome of an administrative operation (kinds, templates, settings, messages, ban waves,
 * appeals, player data, imports). Implemented by SakuraBans; don't implement it.
 *
 * @param <T> the type of the value on success
 */
public sealed interface AdminResult<T> permits AdminResult.Ok, AdminResult.ValidationFailed,
        AdminResult.Conflict, AdminResult.NotFound, AdminResult.Forbidden {

    /**
     * It worked.
     *
     * @param <T> the type of the value
     */
    non-sealed interface Ok<T> extends AdminResult<T> {

        /**
         * The created, changed or removed entity, as it is now (or was, when removed).
         *
         * @return the value
         */
        T value();
    }

    /**
     * The input is invalid, or breaks a rule (for example deleting a kind that punishments use).
     *
     * @param <T> the type of the value on success
     */
    non-sealed interface ValidationFailed<T> extends AdminResult<T> {

        /**
         * Every problem.
         *
         * @return the errors, never empty
         */
        List<FieldError> errors();
    }

    /**
     * The entity changed since the expected version; nothing was changed.
     *
     * @param <T> the type of the value on success
     */
    non-sealed interface Conflict<T> extends AdminResult<T> {

        /**
         * The entity's current version. Reload it and try again.
         *
         * @return the version
         */
        long currentVersion();
    }

    /**
     * The entity doesn't exist.
     *
     * @param <T> the type of the value on success
     */
    non-sealed interface NotFound<T> extends AdminResult<T> {
    }

    /**
     * The actor isn't allowed to do this.
     *
     * @param <T> the type of the value on success
     */
    non-sealed interface Forbidden<T> extends AdminResult<T> {

        /**
         * Why not.
         *
         * @return the denying decision
         */
        Decision decision();
    }
}
