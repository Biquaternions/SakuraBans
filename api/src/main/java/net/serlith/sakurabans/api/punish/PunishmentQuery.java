package net.serlith.sakurabans.api.punish;

import java.net.InetAddress;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.Page;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * A selection of punishments for {@link PunishmentService#query(PunishmentQuery)} and
 * {@link PunishmentService#count(PunishmentQuery)}. Build one with {@link #builder()}; every
 * filter is optional.
 *
 * <p>Operators are matched by identity: a player or custom operator by UUID (name snapshots are
 * ignored), a custom operator without UUID by name, and the console as the console.
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
     * Only punishments of these kinds.
     *
     * @return the kind ids, lower case; empty for every kind
     */
    Set<String> kinds();

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
     * Only punishments that this operator revoked.
     *
     * @return the operator, or empty
     */
    Optional<Operator> revokedBy();

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
     * Whether voided punishments are included.
     *
     * @return {@code false} by default
     */
    boolean includeVoided();

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
     * The order of the results.
     *
     * @return the order; {@link Order#NEWEST_FIRST} by default
     */
    Order order();

    /**
     * The page to return, when paging by number.
     *
     * @return the page, counted from 1
     */
    int page();

    /**
     * Where to continue, when paging by cursor: the {@link Page#nextCursor()} of the previous
     * page. Can't be combined with a page number other than 1.
     *
     * @return the cursor, or empty to start from the beginning
     */
    Optional<String> after();

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
                .kinds(this.kinds())
                .operator(this.operator().orElse(null))
                .revokedBy(this.revokedBy().orElse(null))
                .scope(this.scope().orElse(null))
                .activeOnly(this.activeOnly())
                .includeHidden(this.includeHidden())
                .includeVoided(this.includeVoided())
                .order(this.order())
                .after(this.after().orElse(null))
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
     * The order of query results.
     */
    enum Order {

        /** The most recently created first. */
        NEWEST_FIRST,

        /** The oldest first. */
        OLDEST_FIRST,

        /** The soonest to expire first; permanent ones last. */
        EXPIRING_FIRST
    }

    /**
     * A connection, matched the way enforcement matches it: an account, an address, or both, and
     * how far address punishments reach (see {@link AddressStrictness}).
     */
    interface Applicability {

        /**
         * The account.
         *
         * @return the account's UUID, or empty to match by address only
         */
        Optional<UUID> uuid();

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
         * Creates the applicability. With an address only, it matches the punishments that would
         * stop a connection from that address: address and range punishments, and (from
         * {@link AddressStrictness#NORMAL}) those of accounts that joined from it.
         *
         * @param uuid the account's UUID, or {@code null}
         * @param address the connection's address, or {@code null}
         * @param strictness how far address punishments reach
         * @return the applicability
         * @throws net.serlith.sakurabans.api.validation.ValidationException if both
         *         {@code uuid} and {@code address} are {@code null}
         */
        static Applicability of(final @Nullable UUID uuid, final @Nullable InetAddress address,
                final AddressStrictness strictness) {
            record Impl(Optional<UUID> uuid, Optional<InetAddress> address,
                    AddressStrictness strictness) implements Applicability {
                Impl {
                    Objects.requireNonNull(strictness, "strictness");
                    final Validator errors = Validator.create();
                    errors.check(uuid.isPresent() || address.isPresent(), "uuid",
                            Code.INVALID, "set a uuid, an address or both");
                    errors.throwIfAny();
                }
            }

            return new Impl(Optional.ofNullable(uuid), Optional.ofNullable(address), strictness);
        }
    }

    /**
     * Builds a {@link PunishmentQuery}. Not thread-safe.
     */
    final class Builder {

        private Set<PunishmentType> types = EnumSet.allOf(PunishmentType.class);
        private Set<String> kinds = Set.of();
        private @Nullable Victim victim;
        private @Nullable Applicability applicableTo;
        private @Nullable Operator operator;
        private @Nullable Operator revokedBy;
        private @Nullable Scope scope;
        private boolean activeOnly;
        private boolean includeHidden = true;
        private boolean includeVoided;
        private @Nullable Instant createdAfter;
        private @Nullable Instant createdBefore;
        private Order order = Order.NEWEST_FIRST;
        private int page = 1;
        private @Nullable String after;
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
         * Restricts to some kinds.
         *
         * @param kinds the kind ids, or an empty set for every kind
         * @return this builder
         */
        public Builder kinds(final Set<String> kinds) {
            this.kinds = Set.copyOf(kinds);
            return this;
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
         * Restricts to punishments one operator revoked.
         *
         * @param revokedBy the operator, or {@code null} for any
         * @return this builder
         */
        public Builder revokedBy(final @Nullable Operator revokedBy) {
            this.revokedBy = revokedBy;
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
         * Sets whether voided punishments are included.
         *
         * @param includeVoided {@code true} to include them
         * @return this builder
         */
        public Builder includeVoided(final boolean includeVoided) {
            this.includeVoided = includeVoided;
            return this;
        }

        /**
         * Sets the order.
         *
         * @param order the order
         * @return this builder
         */
        public Builder order(final Order order) {
            this.order = Objects.requireNonNull(order, "order");
            return this;
        }

        /**
         * Continues after a cursor.
         *
         * @param after a {@link Page#nextCursor()}, or {@code null} to start from the beginning
         * @return this builder
         */
        public Builder after(final @Nullable String after) {
            this.after = after;
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
         * Checks the values without building.
         *
         * @return every problem: no {@code types}, a {@code kinds} entry that isn't an
         *         identifier, both {@code victim} and {@code applicableTo}, creation bounds in the
         *         wrong order, {@code page} or {@code pageSize} out of range, or a page number
         *         with a cursor
         */
        public List<FieldError> validate() {
            final Validator errors = Validator.create();
            errors.check(!this.types.isEmpty(), "types", Code.OUT_OF_RANGE,
                    "select at least one type");
            this.kinds.forEach(kind -> errors.identifier("kinds", kind));
            errors.check(this.victim == null || this.applicableTo == null, "applicableTo",
                    Code.EXCLUSIVE, "choose either a victim or an applicability, not both");
            errors.check(this.createdAfter == null || this.createdBefore == null
                    || this.createdAfter.isBefore(this.createdBefore), "createdAfter",
                    Code.WRONG_ORDER, "must be before createdBefore");
            errors.check(this.page >= 1, "page", Code.OUT_OF_RANGE,
                    "must be at least 1: " + this.page);
            errors.check(this.pageSize >= 1 && this.pageSize <= MAX_PAGE_SIZE, "pageSize",
                    Code.OUT_OF_RANGE, "must be 1-" + MAX_PAGE_SIZE + ": " + this.pageSize);
            errors.check(this.after == null || this.page == 1, "after", Code.EXCLUSIVE,
                    "page by number or by cursor, not both");
            errors.check(this.after == null || !this.after.isBlank(), "after", Code.BLANK,
                    "must not be blank");
            return errors.errors();
        }

        /**
         * Builds the query.
         *
         * @return the query
         * @throws ValidationException with every problem {@link #validate()} reports
         */
        public PunishmentQuery build() {
            record Impl(Set<PunishmentType> types, Set<String> kinds, Optional<Victim> victim,
                    Optional<Applicability> applicableTo, Optional<Operator> operator,
                    Optional<Operator> revokedBy, Optional<Scope> scope, boolean activeOnly,
                    boolean includeHidden, boolean includeVoided, Optional<Instant> createdAfter,
                    Optional<Instant> createdBefore, Order order, int page, Optional<String> after,
                    int pageSize) implements PunishmentQuery {
            }

            ValidationException.throwIfAny(this.validate());
            final Set<String> kinds = this.kinds.stream()
                    .map(name -> Validator.normalizeIdentifier(name).orElseThrow())
                    .collect(Collectors.toUnmodifiableSet());
            return new Impl(Set.copyOf(this.types), kinds,
                    Optional.ofNullable(this.victim), Optional.ofNullable(this.applicableTo),
                    Optional.ofNullable(this.operator), Optional.ofNullable(this.revokedBy),
                    Optional.ofNullable(this.scope), this.activeOnly, this.includeHidden,
                    this.includeVoided, Optional.ofNullable(this.createdAfter),
                    Optional.ofNullable(this.createdBefore), this.order, this.page,
                    Optional.ofNullable(this.after), this.pageSize);
        }
    }
}
