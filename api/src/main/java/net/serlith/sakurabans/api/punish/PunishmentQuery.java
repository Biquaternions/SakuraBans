package net.serlith.sakurabans.api.punish;

import java.net.InetAddress;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.player.AddressStrictness;
import org.jspecify.annotations.Nullable;

/**
 * A selection of punishments for {@link PunishmentService#query(PunishmentQuery)}. Build one with
 * {@link #builder()}; every filter is optional, and results are newest first.
 */
public interface PunishmentQuery {

    /** The largest allowed {@link #pageSize()}. */
    int MAX_PAGE_SIZE = 500;

    /**
     * The types to include.
     *
     * @return the types, never empty; all types by default
     */
    Set<PunishmentType> types();

    /**
     * Only punishments of exactly this victim.
     *
     * @return the victim, or empty
     */
    Optional<Victim> victim();

    /**
     * Only punishments that apply to a player's connection, the way enforcement decides it.
     *
     * @return the applicability, or empty
     */
    Optional<Applicability> applicableTo();

    /**
     * Only punishments issued by this operator.
     *
     * @return the operator, or empty
     */
    Optional<Operator> operator();

    /**
     * Only punishments in exactly this scope.
     *
     * @return the scope, or empty
     */
    Optional<Scope> scope();

    /**
     * Whether only active punishments are included.
     *
     * @return {@code false} by default
     */
    boolean activeOnly();

    /**
     * Whether {@link net.serlith.sakurabans.api.model.Visibility#HIDDEN hidden} punishments are
     * included.
     *
     * @return {@code true} by default
     */
    boolean includeHidden();

    /**
     * Only punishments created at or after this instant.
     *
     * @return the lower bound, or empty
     */
    Optional<Instant> createdAfter();

    /**
     * Only punishments created before this instant.
     *
     * @return the upper bound, or empty
     */
    Optional<Instant> createdBefore();

    /**
     * The page to return.
     *
     * @return the page, counted from 1
     */
    int page();

    /**
     * The number of punishments per page.
     *
     * @return 1 to {@link #MAX_PAGE_SIZE}; 10 by default
     */
    int pageSize();

    /**
     * A builder that starts with this query's values.
     *
     * @return a new builder
     */
    default Builder toBuilder() {
        final Builder builder = builder()
                .types(this.types())
                .victim(this.victim().orElse(null))
                .applicableTo(this.applicableTo().orElse(null))
                .operator(this.operator().orElse(null))
                .scope(this.scope().orElse(null))
                .activeOnly(this.activeOnly())
                .includeHidden(this.includeHidden())
                .createdAfter(this.createdAfter().orElse(null))
                .createdBefore(this.createdBefore().orElse(null));
        return builder.page(this.page()).pageSize(this.pageSize());
    }

    /**
     * Starts a query that selects everything.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * A player's connection: the account, the address it uses, and how far address punishments
     * reach (see {@link AddressStrictness}).
     */
    interface Applicability {

        /**
         * The account.
         *
         * @return the account's UUID
         */
        UUID uuid();

        /**
         * The address of the connection.
         *
         * @return the address, or empty to match the account and its recorded addresses only
         */
        Optional<InetAddress> address();

        /**
         * How far address punishments reach.
         *
         * @return the strictness
         */
        AddressStrictness strictness();

        /**
         * Creates the applicability.
         *
         * @param uuid the account's UUID
         * @param address the connection's address, or {@code null}
         * @param strictness how far address punishments reach
         * @return the applicability
         */
        static Applicability of(final UUID uuid, final @Nullable InetAddress address,
                final AddressStrictness strictness) {
            record Impl(UUID uuid, Optional<InetAddress> address, AddressStrictness strictness)
                    implements Applicability {
                Impl {
                    Objects.requireNonNull(uuid, "uuid");
                    Objects.requireNonNull(strictness, "strictness");
                }
            }

            return new Impl(uuid, Optional.ofNullable(address), strictness);
        }
    }

    /**
     * Builds a {@link PunishmentQuery}. Not thread-safe.
     */
    final class Builder {

        private Set<PunishmentType> types = EnumSet.allOf(PunishmentType.class);
        private @Nullable Victim victim;
        private @Nullable Applicability applicableTo;
        private @Nullable Operator operator;
        private @Nullable Scope scope;
        private boolean activeOnly;
        private boolean includeHidden = true;
        private @Nullable Instant createdAfter;
        private @Nullable Instant createdBefore;
        private int page = 1;
        private int pageSize = 10;

        private Builder() {
        }

        /**
         * Sets the types to include.
         *
         * @param types the types, not empty
         * @return this builder
         */
        public Builder types(final Set<PunishmentType> types) {
            this.types = Set.copyOf(types);
            return this;
        }

        /**
         * Sets the types to include.
         *
         * @param first a type
         * @param more more types
         * @return this builder
         */
        public Builder types(final PunishmentType first, final PunishmentType... more) {
            return this.types(EnumSet.of(first, more));
        }

        /**
         * Restricts to one victim. Can't be combined with {@link #applicableTo(Applicability)}.
         *
         * @param victim the victim, or {@code null} for any
         * @return this builder
         */
        public Builder victim(final @Nullable Victim victim) {
            this.victim = victim;
            return this;
        }

        /**
         * Restricts to punishments that apply to a connection. Can't be combined with
         * {@link #victim(Victim)}.
         *
         * @param applicableTo the connection, or {@code null} for any
         * @return this builder
         */
        public Builder applicableTo(final @Nullable Applicability applicableTo) {
            this.applicableTo = applicableTo;
            return this;
        }

        /**
         * Restricts to one operator.
         *
         * @param operator the operator, or {@code null} for any
         * @return this builder
         */
        public Builder operator(final @Nullable Operator operator) {
            this.operator = operator;
            return this;
        }

        /**
         * Restricts to one scope.
         *
         * @param scope the scope, or {@code null} for any
         * @return this builder
         */
        public Builder scope(final @Nullable Scope scope) {
            this.scope = scope;
            return this;
        }

        /**
         * Sets whether only active punishments are included.
         *
         * @param activeOnly {@code true} for active ones only
         * @return this builder
         */
        public Builder activeOnly(final boolean activeOnly) {
            this.activeOnly = activeOnly;
            return this;
        }

        /**
         * Sets whether hidden punishments are included.
         *
         * @param includeHidden {@code false} to leave them out
         * @return this builder
         */
        public Builder includeHidden(final boolean includeHidden) {
            this.includeHidden = includeHidden;
            return this;
        }

        /**
         * Sets the lower creation bound (inclusive).
         *
         * @param createdAfter the bound, or {@code null} for none
         * @return this builder
         */
        public Builder createdAfter(final @Nullable Instant createdAfter) {
            this.createdAfter = createdAfter;
            return this;
        }

        /**
         * Sets the upper creation bound (exclusive).
         *
         * @param createdBefore the bound, or {@code null} for none
         * @return this builder
         */
        public Builder createdBefore(final @Nullable Instant createdBefore) {
            this.createdBefore = createdBefore;
            return this;
        }

        /**
         * Sets the page.
         *
         * @param page the page, from 1
         * @return this builder
         */
        public Builder page(final int page) {
            this.page = page;
            return this;
        }

        /**
         * Sets the page size.
         *
         * @param pageSize 1 to {@link #MAX_PAGE_SIZE}
         * @return this builder
         */
        public Builder pageSize(final int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        /**
         * Builds the query.
         *
         * @return the query
         * @throws IllegalArgumentException if no type is selected, both a victim and an
         *         applicability are set, the creation bounds are reversed, or the page or page
         *         size is out of range
         */
        public PunishmentQuery build() {
            record Impl(Set<PunishmentType> types, Optional<Victim> victim,
                    Optional<Applicability> applicableTo, Optional<Operator> operator,
                    Optional<Scope> scope, boolean activeOnly, boolean includeHidden,
                    Optional<Instant> createdAfter, Optional<Instant> createdBefore, int page,
                    int pageSize) implements PunishmentQuery {
                Impl {
                    types = Set.copyOf(types);
                    if (types.isEmpty()) {
                        throw new IllegalArgumentException("select at least one type");
                    }
                    if (victim.isPresent() && applicableTo.isPresent()) {
                        throw new IllegalArgumentException(
                                "choose either a victim or an applicability, not both");
                    }
                    if (createdAfter.isPresent() && createdBefore.isPresent()
                            && !createdAfter.get().isBefore(createdBefore.get())) {
                        throw new IllegalArgumentException(
                                "createdAfter must be before createdBefore");
                    }
                    if (page < 1) {
                        throw new IllegalArgumentException("page must be at least 1: " + page);
                    }
                    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
                        throw new IllegalArgumentException(
                                "pageSize must be 1-" + MAX_PAGE_SIZE + ": " + pageSize);
                    }
                }
            }

            return new Impl(this.types, Optional.ofNullable(this.victim),
                    Optional.ofNullable(this.applicableTo), Optional.ofNullable(this.operator),
                    Optional.ofNullable(this.scope), this.activeOnly, this.includeHidden,
                    Optional.ofNullable(this.createdAfter), Optional.ofNullable(this.createdBefore),
                    this.page, this.pageSize);
        }
    }
}
