package net.serlith.sakurabans.api.imports;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * A punishment from another system, with its full history (unlike a draft, which is issued now).
 * Build one with {@link #builder(PunishmentType, Victim, Operator, Instant)}.
 */
public interface ImportedPunishment {

    /** The longest {@link #externalId()}. */
    int MAX_EXTERNAL_ID = 128;

    /**
     * The type.
     *
     * @return the type
     */
    PunishmentType type();

    /**
     * The kind.
     *
     * @return the kind id, or empty for the kind named after the type
     */
    Optional<String> kind();

    /**
     * The victim.
     *
     * @return the victim
     */
    Victim victim();

    /**
     * Who issued it in the other system.
     *
     * @return the operator
     */
    Operator operator();

    /**
     * When it was issued.
     *
     * @return the instant
     */
    Instant createdAt();

    /**
     * When it expires.
     *
     * @return the expiry, after {@link #createdAt()}, or empty if permanent
     */
    Optional<Instant> expiresAt();

    /**
     * The scope.
     *
     * @return the scope; global by default
     */
    Scope scope();

    /**
     * The reason.
     *
     * @return the reason, plain text, possibly empty
     */
    String reason();

    /**
     * The visibility it had.
     *
     * @return the visibility
     */
    Visibility visibility();

    /**
     * The server it was issued on.
     *
     * @return the server name, or empty
     */
    Optional<String> originServer();

    /**
     * How it ended in the other system.
     *
     * @return the end, or empty if it is still active
     */
    Optional<End> end();

    /**
     * Evidence to attach.
     *
     * @return the references
     */
    List<EvidenceRef> evidence();

    /**
     * Its id in the other system. A re-run of the same import skips punishments whose external
     * id was already imported.
     *
     * @return the external id, or empty
     */
    Optional<String> externalId();

    /**
     * How an imported punishment ended.
     */
    interface End {

        /**
         * Why it ended.
         *
         * @return the kind of end
         */
        EndKind kind();

        /**
         * When.
         *
         * @return the instant
         */
        Instant at();

        /**
         * Who ended it.
         *
         * @return the operator, or empty
         */
        Optional<Operator> operator();

        /**
         * Why.
         *
         * @return the reason, or empty
         */
        Optional<String> reason();
    }

    /**
     * Starts an imported punishment.
     *
     * @param type the type
     * @param victim the victim
     * @param operator who issued it
     * @param createdAt when it was issued
     * @return a new builder
     */
    static Builder builder(final PunishmentType type, final Victim victim,
            final Operator operator, final Instant createdAt) {
        return new Builder(type, victim, operator, createdAt);
    }

    /**
     * Builds an {@link ImportedPunishment}. Not thread-safe.
     */
    final class Builder {

        private final PunishmentType type;
        private final Victim victim;
        private final Operator operator;
        private final Instant createdAt;
        private @Nullable String kind;
        private @Nullable Instant expiresAt;
        private Scope scope = GlobalScope.INSTANCE;
        private String reason = "";
        private Visibility visibility = Visibility.NORMAL;
        private @Nullable String originServer;
        private @Nullable EndKind endKind;
        private @Nullable Instant endedAt;
        private @Nullable Operator endedBy;
        private @Nullable String endReason;
        private List<EvidenceRef> evidence = List.of();
        private @Nullable String externalId;

        private Builder(final PunishmentType type, final Victim victim, final Operator operator,
                final Instant createdAt) {
            this.type = Objects.requireNonNull(type, "type");
            this.victim = Objects.requireNonNull(victim, "victim");
            this.operator = Objects.requireNonNull(operator, "operator");
            this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        }

        /**
         * Sets the kind.
         *
         * @param kind the kind id, or {@code null} for the kind named after the type
         * @return this builder
         */
        public Builder kind(final @Nullable String kind) {
            this.kind = kind;
            return this;
        }

        /**
         * Sets the expiry.
         *
         * @param expiresAt the expiry, or {@code null} for permanent
         * @return this builder
         */
        public Builder expiresAt(final @Nullable Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        /**
         * Sets the scope.
         *
         * @param scope the scope
         * @return this builder
         */
        public Builder scope(final Scope scope) {
            this.scope = Objects.requireNonNull(scope, "scope");
            return this;
        }

        /**
         * Sets the reason.
         *
         * @param reason the reason, plain text
         * @return this builder
         */
        public Builder reason(final String reason) {
            this.reason = Objects.requireNonNull(reason, "reason");
            return this;
        }

        /**
         * Sets the visibility.
         *
         * @param visibility the visibility
         * @return this builder
         */
        public Builder visibility(final Visibility visibility) {
            this.visibility = Objects.requireNonNull(visibility, "visibility");
            return this;
        }

        /**
         * Sets the origin server.
         *
         * @param originServer the server name, or {@code null}
         * @return this builder
         */
        public Builder originServer(final @Nullable String originServer) {
            this.originServer = originServer;
            return this;
        }

        /**
         * Records how it ended.
         *
         * @param kind why it ended
         * @param at when
         * @param operator who ended it, or {@code null}
         * @param reason why, or {@code null}
         * @return this builder
         */
        public Builder ended(final EndKind kind, final Instant at,
                final @Nullable Operator operator, final @Nullable String reason) {
            this.endKind = Objects.requireNonNull(kind, "kind");
            this.endedAt = Objects.requireNonNull(at, "at");
            this.endedBy = operator;
            this.endReason = reason;
            return this;
        }

        /**
         * Sets the evidence.
         *
         * @param evidence the references
         * @return this builder
         */
        public Builder evidence(final List<EvidenceRef> evidence) {
            this.evidence = List.copyOf(evidence);
            return this;
        }

        /**
         * Sets the id in the other system.
         *
         * @param externalId up to {@link #MAX_EXTERNAL_ID} characters, or {@code null}
         * @return this builder
         */
        public Builder externalId(final @Nullable String externalId) {
            this.externalId = externalId;
            return this;
        }

        /**
         * Checks the values without building.
         *
         * @return every problem: {@code kind} not an identifier, {@code reason},
         *         {@code endReason}, {@code originServer} or {@code externalId} too long, an
         *         expiry or end before creation, or an expiry or end on a kick
         */
        public List<FieldError> validate() {
            return Validator.create()
                    .identifier("kind", this.kind)
                    .maxLength("reason", this.reason, Lengths.MAX_REASON)
                    .maxLength("endReason", this.endReason, Lengths.MAX_REASON)
                    .maxLength("originServer", this.originServer, Lengths.MAX_IDENTIFIER)
                    .name("externalId", this.externalId, MAX_EXTERNAL_ID)
                    .check(this.expiresAt == null || this.expiresAt.isAfter(this.createdAt),
                            "expiresAt", Code.WRONG_ORDER, "must be after createdAt")
                    .check(this.endedAt == null || !this.endedAt.isBefore(this.createdAt),
                            "endedAt", Code.WRONG_ORDER, "must not be before createdAt")
                    .check(!this.type.instant() || this.expiresAt == null && this.endKind == null,
                            "type", Code.NOT_ALLOWED, "a kick can't expire or end")
                    .errors();
        }

        /**
         * Builds the imported punishment.
         *
         * @return the imported punishment
         * @throws ValidationException with every problem {@link #validate()} reports
         */
        public ImportedPunishment build() {
            record EndImpl(EndKind kind, Instant at, Optional<Operator> operator,
                    Optional<String> reason) implements End {
            }

            record Impl(PunishmentType type, Optional<String> kind, Victim victim,
                    Operator operator, Instant createdAt, Optional<Instant> expiresAt,
                    Scope scope, String reason, Visibility visibility,
                    Optional<String> originServer, Optional<End> end, List<EvidenceRef> evidence,
                    Optional<String> externalId) implements ImportedPunishment {
            }

            ValidationException.throwIfAny(this.validate());
            final Optional<End> end = this.endKind == null || this.endedAt == null
                    ? Optional.empty()
                    : Optional.of(new EndImpl(this.endKind, this.endedAt,
                            Optional.ofNullable(this.endedBy),
                            Optional.ofNullable(this.endReason)));
            return new Impl(this.type,
                    Optional.ofNullable(this.kind).flatMap(Validator::normalizeIdentifier),
                    this.victim, this.operator, this.createdAt,
                    Optional.ofNullable(this.expiresAt), this.scope, this.reason,
                    this.visibility, Optional.ofNullable(this.originServer), end, this.evidence,
                    Optional.ofNullable(this.externalId));
        }
    }
}
