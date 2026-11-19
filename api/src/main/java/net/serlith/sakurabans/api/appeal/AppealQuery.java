package net.serlith.sakurabans.api.appeal;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * A selection of appeals, oldest open first. Build one with {@link #builder()}.
 */
public interface AppealQuery {

    /** The largest allowed {@link #pageSize()}. */
    int MAX_PAGE_SIZE = 500;

    /**
     * Only appeals with these statuses.
     *
     * @return the statuses, empty for every status
     */
    Set<Appeal.Status> statuses();

    /**
     * Only appeals against this punishment.
     *
     * @return the punishment's database id, or empty
     */
    Optional<Long> punishmentId();

    /**
     * Only appeals by this account.
     *
     * @return the account's UUID, or empty
     */
    Optional<UUID> appellant();

    /**
     * Where to continue: the {@code nextCursor()} of the previous page.
     *
     * @return the cursor, or empty to start from the beginning
     */
    Optional<String> after();

    /**
     * The number of appeals per page.
     *
     * @return 1 to {@link #MAX_PAGE_SIZE}; 20 by default
     */
    int pageSize();

    /**
     * Starts a query that selects every appeal.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builds an {@link AppealQuery}. Not thread-safe.
     */
    final class Builder {

        private Set<Appeal.Status> statuses = Set.of();
        private @Nullable Long punishmentId;
        private @Nullable UUID appellant;
        private @Nullable String after;
        private int pageSize = 20;

        private Builder() {
        }

        /**
         * Restricts to some statuses.
         *
         * @param statuses the statuses, or an empty set for every status
         * @return this builder
         */
        public Builder statuses(final Set<Appeal.Status> statuses) {
            this.statuses = Set.copyOf(statuses);
            return this;
        }

        /**
         * Restricts to one punishment.
         *
         * @param punishmentId the punishment's database id, or {@code null} for any
         * @return this builder
         */
        public Builder punishmentId(final @Nullable Long punishmentId) {
            this.punishmentId = punishmentId;
            return this;
        }

        /**
         * Restricts to one appellant.
         *
         * @param appellant the account's UUID, or {@code null} for any
         * @return this builder
         */
        public Builder appellant(final @Nullable UUID appellant) {
            this.appellant = appellant;
            return this;
        }

        /**
         * Continues after a cursor.
         *
         * @param after a {@code nextCursor()}, or {@code null} to start from the beginning
         * @return this builder
         */
        public Builder after(final @Nullable String after) {
            this.after = after;
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
         * @return every problem: a blank cursor, or {@code pageSize} out of range
         */
        public List<FieldError> validate() {
            return Validator.create()
                    .check(this.after == null || !this.after.isBlank(), "after", Code.BLANK,
                            "must not be blank")
                    .check(this.pageSize >= 1 && this.pageSize <= MAX_PAGE_SIZE, "pageSize",
                            Code.OUT_OF_RANGE, "must be 1-" + MAX_PAGE_SIZE + ": " + this.pageSize)
                    .errors();
        }

        /**
         * Builds the query.
         *
         * @return the query
         * @throws ValidationException with every problem {@link #validate()} reports
         */
        public AppealQuery build() {
            record Impl(Set<Appeal.Status> statuses, Optional<Long> punishmentId,
                    Optional<UUID> appellant, Optional<String> after, int pageSize)
                    implements AppealQuery {
            }

            ValidationException.throwIfAny(this.validate());
            return new Impl(this.statuses, Optional.ofNullable(this.punishmentId),
                    Optional.ofNullable(this.appellant), Optional.ofNullable(this.after),
                    this.pageSize);
        }
    }
}
