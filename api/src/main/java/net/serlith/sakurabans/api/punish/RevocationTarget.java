package net.serlith.sakurabans.api.punish;

import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * Which punishments a {@link RevocationRequest} ends: one by id, or a victim's active ones.
 */
public sealed interface RevocationTarget permits RevocationTarget.ById, RevocationTarget.ByVictim {

    /**
     * One punishment by its id.
     */
    non-sealed interface ById extends RevocationTarget {

        /**
         * The punishment.
         *
         * @return the database id
         */
        long id();

        /**
         * Creates the target.
         *
         * @param id the database id, at least 1
         * @return the target
         * @throws net.serlith.sakurabans.api.validation.ValidationException if {@code id} is
         *         less than 1
         */
        static ById of(final long id) {
            record Impl(long id) implements ById {
                Impl {
                    final Validator errors = Validator.create();
                    errors.check(id >= 1, "id", Code.OUT_OF_RANGE, "must be at least 1: " + id);
                    errors.throwIfAny();
                }
            }

            return new Impl(id);
        }
    }

    /**
     * The victim's active punishments of one type: in one scope, or in every scope.
     */
    non-sealed interface ByVictim extends RevocationTarget {

        /**
         * The kind of punishment.
         *
         * @return the type, never {@link PunishmentType#KICK}
         */
        PunishmentType type();

        /**
         * The victim.
         *
         * @return the victim
         */
        Victim victim();

        /**
         * The scope.
         *
         * @return the scope, or empty for every scope
         */
        Optional<Scope> scope();

        /**
         * Creates the target.
         *
         * @param type the kind of punishment; a kick can't be revoked
         * @param victim the victim
         * @param scope the scope, or {@code null} for every scope
         * @return the target
         */
        static ByVictim of(final PunishmentType type, final Victim victim,
                final @Nullable Scope scope) {
            record Impl(PunishmentType type, Victim victim, Optional<Scope> scope)
                    implements ByVictim {
                Impl {
                    Objects.requireNonNull(type, "type");
                    Objects.requireNonNull(victim, "victim");
                    final Validator errors = Validator.create();
                    errors.check(!type.instant(), "type", Code.NOT_ALLOWED,
                            type + " can't be revoked");
                    errors.throwIfAny();
                }
            }

            return new Impl(type, victim, Optional.ofNullable(scope));
        }
    }
}
