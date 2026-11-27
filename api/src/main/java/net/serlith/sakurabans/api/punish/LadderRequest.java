package net.serlith.sakurabans.api.punish;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * A ladder to apply to a victim. Build one with {@link #builder(String, Victim, Actor)} and pass
 * it to {@link PunishmentService#applyLadder(LadderRequest, PunishOptions)}. The victim's
 * progress on the ladder picks the step, and the step decides the type and the duration.
 */
public interface LadderRequest {

    /**
     * The ladder to apply.
     *
     * @return the template name, lower case
     */
    String ladder();

    /**
     * Who it applies to. A ladder with {@code ip} punishes the player's current address, but
     * the use counts for this victim.
     *
     * @return the victim
     */
    Victim victim();

    /**
     * Who applies it, and through which surface.
     *
     * @return the actor
     */
    Actor actor();

    /**
     * Where the punishment applies. A scope given here also picks the ladder's variant (see
     * {@link net.serlith.sakurabans.api.template.TemplateService}); without one, the variant
     * comes from where it is applied.
     *
     * @return the scope, or empty for the ladder's or the configured default scope
     */
    Optional<Scope> scope();

    /**
     * The reason, as plain text; it wins over the step's and the ladder's.
     *
     * @return the reason, or empty for the step's or the ladder's
     */
    Optional<String> reason();

    /**
     * Evidence to attach to the punishment, if the step creates one.
     *
     * @return the evidence references, without duplicates
     */
    List<EvidenceRef> evidence();

    /**
     * Starts a request.
     *
     * @param ladder the ladder's name
     * @param victim who it applies to
     * @param actor who applies it
     * @return a new builder
     */
    static Builder builder(final String ladder, final Victim victim, final Actor actor) {
        return new Builder(ladder, victim, actor);
    }

    /**
     * Builds a {@link LadderRequest}. Not thread-safe.
     */
    final class Builder {

        private final String ladder;
        private final Victim victim;
        private final Actor actor;
        private @Nullable Scope scope;
        private @Nullable String reason;
        private List<EvidenceRef> evidence = List.of();

        private Builder(final String ladder, final Victim victim, final Actor actor) {
            this.ladder = Objects.requireNonNull(ladder, "ladder");
            this.victim = Objects.requireNonNull(victim, "victim");
            this.actor = Objects.requireNonNull(actor, "actor");
        }

        /**
         * Sets the scope.
         *
         * @param scope where it applies, or {@code null} for the default
         * @return this builder
         */
        public Builder scope(final @Nullable Scope scope) {
            this.scope = scope;
            return this;
        }

        /**
         * Sets the reason.
         *
         * @param reason the reason, plain text, or {@code null} for the step's or the ladder's
         * @return this builder
         */
        public Builder reason(final @Nullable String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Sets the evidence to attach. Duplicates (same URI) are dropped.
         *
         * @param evidence the evidence references
         * @return this builder
         */
        public Builder evidence(final List<EvidenceRef> evidence) {
            this.evidence = List.copyOf(evidence);
            return this;
        }

        /**
         * Checks the values without building.
         *
         * @return every problem: {@code ladder} not an identifier, {@code victim} an
         *         {@link ErasedAddressVictim}, {@code reason} too long
         */
        public List<FieldError> validate() {
            return Validator.create()
                    .identifier("ladder", this.ladder)
                    .check(!(this.victim instanceof ErasedAddressVictim), "victim",
                            Code.NOT_ALLOWED,
                            "an erased victim has no address; name the address or player instead")
                    .maxLength("reason", this.reason, Lengths.MAX_REASON)
                    .errors();
        }

        /**
         * Builds the request.
         *
         * @return the request
         * @throws ValidationException with every problem {@link #validate()} reports
         */
        public LadderRequest build() {
            record Impl(String ladder, Victim victim, Actor actor, Optional<Scope> scope,
                    Optional<String> reason, List<EvidenceRef> evidence)
                    implements LadderRequest {
            }

            ValidationException.throwIfAny(this.validate());
            final Map<String, EvidenceRef> unique = new LinkedHashMap<>();
            this.evidence.forEach(ref -> unique.putIfAbsent(ref.uri(), ref));
            return new Impl(Validator.normalizeIdentifier(this.ladder).orElseThrow(),
                    this.victim, this.actor, Optional.ofNullable(this.scope),
                    Optional.ofNullable(this.reason),
                    List.copyOf(new ArrayList<>(unique.values())));
        }
    }
}
