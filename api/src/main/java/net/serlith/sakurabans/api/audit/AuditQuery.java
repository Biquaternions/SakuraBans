package net.serlith.sakurabans.api.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * A selection of audit entries, newest first. Build one with {@link #builder()}; every filter is
 * optional. Paging is by cursor only.
 */
public interface AuditQuery {

    /** The largest allowed {@link #pageSize()}. */
    int MAX_PAGE_SIZE = 500;

    /**
     * Only entries about this type of subject.
     *
     * @return the subject type, or empty
     */
    Optional<SubjectType> subjectType();

    /**
     * Only entries about this entity; requires {@link #subjectType()}.
     *
     * @return the subject id, or empty
     */
    Optional<String> subjectId();

    /**
     * Only entries by this operator, matched by identity.
     *
     * @return the operator, or empty
     */
    Optional<Operator> actor();

    /**
     * Only entries through this surface.
     *
     * @return the source, or empty
     */
    Optional<String> source();

    /**
     * Only these actions.
     *
     * @return the actions, empty for every action
     */
    Set<AuditAction> actions();

    /**
     * Only entries at or after this instant.
     *
     * @return the lower bound, or empty
     */
    Optional<Instant> from();

    /**
     * Only entries before this instant.
     *
     * @return the upper bound, or empty
     */
    Optional<Instant> to();

    /**
     * Where to continue: the {@code nextCursor()} of the previous page.
     *
     * @return the cursor, or empty to start with the newest entry
     */
    Optional<String> after();

    /**
     * The number of entries per page.
     *
     * @return 1 to {@link #MAX_PAGE_SIZE}; 50 by default
     */
    int pageSize();

    /**
     * Starts a query that selects everything.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builds an {@link AuditQuery}. Not thread-safe.
     */
    final class Builder {

        private @Nullable SubjectType subjectType;
        private @Nullable String subjectId;
        private @Nullable Operator actor;
        private @Nullable String source;
        private Set<AuditAction> actions = Set.of();
        private @Nullable Instant from;
        private @Nullable Instant to;
        private @Nullable String after;
        private int pageSize = 50;

        private Builder() {
        }

        /**
         * Restricts to one subject.
         *
         * @param subjectType the type, or {@code null} for any
         * @param subjectId the id, or {@code null} for any of that type
         * @return this builder
         */
        public Builder subject(
                final @Nullable SubjectType subjectType, final @Nullable String subjectId) {
            this.subjectType = subjectType;
            this.subjectId = subjectId;
            return this;
        }

        /**
         * Restricts to one operator.
         *
         * @param actor the operator, or {@code null} for any
         * @return this builder
         */
        public Builder actor(final @Nullable Operator actor) {
            this.actor = actor;
            return this;
        }

        /**
         * Restricts to one surface.
         *
         * @param source the source tag, or {@code null} for any
         * @return this builder
         */
        public Builder source(final @Nullable String source) {
            this.source = source;
            return this;
        }

        /**
         * Restricts to some actions.
         *
         * @param actions the actions, or an empty set for every action
         * @return this builder
         */
        public Builder actions(final Set<AuditAction> actions) {
            this.actions = Set.copyOf(actions);
            return this;
        }

        /**
         * Restricts to a time range.
         *
         * @param from the lower bound (inclusive), or {@code null}
         * @param to the upper bound (exclusive), or {@code null}
         * @return this builder
         */
        public Builder between(final @Nullable Instant from, final @Nullable Instant to) {
            this.from = from;
            this.to = to;
            return this;
        }

        /**
         * Continues after a cursor.
         *
         * @param after a {@code nextCursor()}, or {@code null} to start from the newest
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
         * @return every problem: a subject id without a type, bounds in the wrong order, a
         *         blank cursor, or {@code pageSize} out of range
         */
        public List<FieldError> validate() {
            return Validator.create()
                    .check(this.subjectId == null || this.subjectType != null, "subjectId",
                            Code.INVALID, "needs a subject type")
                    .check(this.from == null || this.to == null || this.from.isBefore(this.to),
                            "from", Code.WRONG_ORDER, "must be before to")
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
        public AuditQuery build() {
            record Impl(Optional<SubjectType> subjectType, Optional<String> subjectId,
                    Optional<Operator> actor, Optional<String> source, Set<AuditAction> actions,
                    Optional<Instant> from, Optional<Instant> to, Optional<String> after,
                    int pageSize) implements AuditQuery {
            }

            ValidationException.throwIfAny(this.validate());
            return new Impl(Optional.ofNullable(this.subjectType),
                    Optional.ofNullable(this.subjectId), Optional.ofNullable(this.actor),
                    Optional.ofNullable(this.source), this.actions, Optional.ofNullable(this.from),
                    Optional.ofNullable(this.to), Optional.ofNullable(this.after), this.pageSize);
        }
    }
}
