package net.serlith.sakurabans.api.auth;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * Something an actor wants to do, for {@link AuthorizationService#check} and
 * {@link net.serlith.sakurabans.api.integration.ActionGuard}s. SakuraBans checks the same actions
 * before it performs them, so a UI that asks first never shows something that would be refused.
 */
public sealed interface Action permits Action.Punish, Action.ChangePunishment, Action.Rollback,
        Action.PlayerData, Action.DecideAppeal, Action.Manage, Action.View {

    /**
     * Creating a punishment of a kind, in a scope.
     */
    non-sealed interface Punish extends Action {

        /**
         * The kind.
         *
         * @return the kind id, lower case
         */
        String kind();

        /**
         * The scope.
         *
         * @return the scope, or empty for the default scope
         */
        Optional<Scope> scope();

        /**
         * Creates the action.
         *
         * @param kind the kind id
         * @param scope the scope, or {@code null} for the default
         * @return the action
         * @throws net.serlith.sakurabans.api.validation.ValidationException if the kind isn't an
         *         identifier
         */
        static Punish of(final String kind, final @Nullable Scope scope) {
            record Impl(String kind, Optional<Scope> scope) implements Punish {
            }

            Objects.requireNonNull(kind, "kind");
            Validator.create().identifier("kind", kind).throwIfAny();
            return new Impl(Validator.normalizeIdentifier(kind).orElseThrow(),
                    Optional.ofNullable(scope));
        }
    }

    /**
     * Changing an existing punishment.
     */
    non-sealed interface ChangePunishment extends Action {

        /**
         * How.
         *
         * @return the operation
         */
        Operation operation();

        /**
         * Which punishment.
         *
         * @return its database id
         */
        long id();

        /**
         * Creates the action.
         *
         * @param operation how
         * @param id the punishment's database id, at least 1
         * @return the action
         */
        static ChangePunishment of(final Operation operation, final long id) {
            record Impl(Operation operation, long id) implements ChangePunishment {
            }

            Objects.requireNonNull(operation, "operation");
            Validator.create().check(id >= 1, "id", Code.OUT_OF_RANGE, "must be at least 1")
                    .throwIfAny();
            return new Impl(operation, id);
        }

        /**
         * Ways to change a punishment.
         */
        enum Operation {

            /** Revoke it. */
            REVOKE,

            /** Edit its reason, expiry or scope. */
            EDIT,

            /** Void it. */
            VOID,

            /** Delete it from the history. */
            EXPUNGE,

            /** Attach or detach evidence. */
            EVIDENCE
        }
    }

    /**
     * Rolling back an operator's recent punishments.
     */
    non-sealed interface Rollback extends Action {

        /**
         * Whose punishments.
         *
         * @return the operator
         */
        Operator target();

        /**
         * Creates the action.
         *
         * @param target whose punishments
         * @return the action
         */
        static Rollback of(final Operator target) {
            record Impl(Operator target) implements Rollback {
            }

            return new Impl(Objects.requireNonNull(target, "target"));
        }
    }

    /**
     * Seeing or changing a player's personal data.
     */
    non-sealed interface PlayerData extends Action {

        /**
         * How.
         *
         * @return the operation
         */
        Operation operation();

        /**
         * Whose data.
         *
         * @return the account's UUID
         */
        UUID uuid();

        /**
         * Creates the action.
         *
         * @param operation how
         * @param uuid whose data
         * @return the action
         */
        static PlayerData of(final Operation operation, final UUID uuid) {
            record Impl(Operation operation, UUID uuid) implements PlayerData {
            }

            return new Impl(Objects.requireNonNull(operation, "operation"),
                    Objects.requireNonNull(uuid, "uuid"));
        }

        /**
         * Ways to use a player's data.
         */
        enum Operation {

            /** See their IP addresses uncensored. */
            VIEW_ADDRESSES,

            /** Remove an address from their history. */
            FORGET_ADDRESS,

            /** Export everything about them. */
            EXPORT,

            /** Erase their data. */
            ERASE
        }
    }

    /**
     * Deciding an appeal.
     */
    non-sealed interface DecideAppeal extends Action {

        /**
         * Which appeal.
         *
         * @return its id
         */
        long id();

        /**
         * Creates the action.
         *
         * @param id the appeal's id, at least 1
         * @return the action
         */
        static DecideAppeal of(final long id) {
            record Impl(long id) implements DecideAppeal {
            }

            Validator.create().check(id >= 1, "id", Code.OUT_OF_RANGE, "must be at least 1")
                    .throwIfAny();
            return new Impl(id);
        }
    }

    /**
     * Creating, changing or deleting configuration-like data.
     */
    non-sealed interface Manage extends Action {

        /**
         * What.
         *
         * @return the area
         */
        Area area();

        /**
         * Creates the action.
         *
         * @param area what
         * @return the action
         */
        static Manage of(final Area area) {
            record Impl(Area area) implements Manage {
            }

            return new Impl(Objects.requireNonNull(area, "area"));
        }

        /**
         * What can be managed.
         */
        enum Area {

            /** Punishment kinds. */
            KINDS,

            /** Templates. */
            TEMPLATES,

            /** Network-wide settings. */
            SETTINGS,

            /** Message templates. */
            MESSAGES,

            /** Ban waves (creating, filling, scheduling, releasing). */
            BAN_WAVES,

            /** The allow list. */
            ALLOW_LIST,

            /** Bulk imports. */
            IMPORT
        }
    }

    /**
     * Seeing data that isn't public.
     */
    non-sealed interface View extends Action {

        /**
         * What.
         *
         * @return the area
         */
        Area area();

        /**
         * Creates the action.
         *
         * @param area what
         * @return the action
         */
        static View of(final Area area) {
            record Impl(Area area) implements View {
            }

            return new Impl(Objects.requireNonNull(area, "area"));
        }

        /**
         * What can be viewed.
         */
        enum Area {

            /** The audit log. */
            AUDIT,

            /** Statistics. */
            STATS,

            /** Hidden punishments. */
            HIDDEN_PUNISHMENTS,

            /** Appeals. */
            APPEALS
        }
    }
}
