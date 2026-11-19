package net.serlith.sakurabans.api.punish;

import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
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
         */
        static ById of(final long id) {
            record Impl(long id) implements ById {
                Impl {
                    if (id < 1) {
                        throw new IllegalArgumentException("id must be positive: " + id);
                    }
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
                    if (type.instant()) {
                        throw new IllegalArgumentException(type + " can't be revoked");
                    }
                }
            }

            return new Impl(type, victim, Optional.ofNullable(scope));
        }
    }
}
