package net.serlith.sakurabans.api.template;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;

/**
 * The values of a template to create or update with {@link TemplateService}. Build one with
 * {@link #builder(String, String)}, or from an existing template with
 * {@link #builder(TemplateInfo)}.
 */
public interface TemplateDefinition {

    /** The longest ladder. */
    int MAX_STEPS = 64;

    /**
     * The name.
     *
     * @return the name, lower case
     */
    String name();

    /**
     * The kind.
     *
     * @return the kind id, lower case
     */
    String kind();

    /**
     * The default reason.
     *
     * @return the reason
     */
    String reason();

    /**
     * The ladder.
     *
     * @return the steps
     */
    List<TemplateStep> ladder();

    /**
     * Required evidence types.
     *
     * @return the types
     */
    Set<String> requiredEvidence();

    /**
     * Evidence types to capture.
     *
     * @return the types
     */
    Set<String> captureEvidence();

    /**
     * Starts a definition with a one-step permanent ladder.
     *
     * @param name the name: an identifier, in any case
     * @param kind the kind id
     * @return a new builder
     */
    static Builder builder(final String name, final String kind) {
        return new Builder(name, kind);
    }

    /**
     * Starts a definition with an existing template's values, to change some of them.
     *
     * @param template the template
     * @return a new builder
     */
    static Builder builder(final TemplateInfo template) {
        return builder(template.name(), template.kind())
                .reason(template.reason())
                .ladder(template.ladder())
                .requiredEvidence(template.requiredEvidence())
                .captureEvidence(template.captureEvidence());
    }

    /**
     * Builds a {@link TemplateDefinition}. Not thread-safe.
     */
    final class Builder {

        private final String name;
        private final String kind;
        private String reason = "";
        private List<TemplateStep> ladder = List.of(TemplateStep.of(null));
        private Set<String> requiredEvidence = Set.of();
        private Set<String> captureEvidence = Set.of();

        private Builder(final String name, final String kind) {
            this.name = Objects.requireNonNull(name, "name");
            this.kind = Objects.requireNonNull(kind, "kind");
        }

        /**
         * Sets the default reason.
         *
         * @param reason the reason, plain text, not blank
         * @return this builder
         */
        public Builder reason(final String reason) {
            this.reason = Objects.requireNonNull(reason, "reason");
            return this;
        }

        /**
         * Sets the ladder.
         *
         * @param ladder 1 to {@link #MAX_STEPS} steps
         * @return this builder
         */
        public Builder ladder(final List<TemplateStep> ladder) {
            this.ladder = List.copyOf(ladder);
            return this;
        }

        /**
         * Sets the required evidence types.
         *
         * @param requiredEvidence {@code <namespace>:<type>} entries
         * @return this builder
         */
        public Builder requiredEvidence(final Set<String> requiredEvidence) {
            this.requiredEvidence = Set.copyOf(requiredEvidence);
            return this;
        }

        /**
         * Sets the evidence types to capture.
         *
         * @param captureEvidence {@code <namespace>:<type>} entries
         * @return this builder
         */
        public Builder captureEvidence(final Set<String> captureEvidence) {
            this.captureEvidence = Set.copyOf(captureEvidence);
            return this;
        }

        /**
         * Checks the values without building.
         *
         * @return every problem: {@code name} or {@code kind} not an identifier, {@code reason}
         *         blank or too long, {@code ladder} empty or too long, or an evidence entry that
         *         isn't an evidence type
         */
        public List<FieldError> validate() {
            final Validator errors = Validator.create()
                    .identifier("name", this.name)
                    .identifier("kind", this.kind)
                    .name("reason", this.reason, Lengths.MAX_REASON)
                    .check(!this.ladder.isEmpty() && this.ladder.size() <= MAX_STEPS, "ladder",
                            Code.OUT_OF_RANGE, "must have 1-" + MAX_STEPS + " steps");
            this.requiredEvidence.forEach(type -> errors.evidenceType("requiredEvidence", type));
            this.captureEvidence.forEach(type -> errors.evidenceType("captureEvidence", type));
            return errors.errors();
        }

        /**
         * Builds the definition.
         *
         * @return the definition
         * @throws ValidationException with every problem {@link #validate()} reports
         */
        public TemplateDefinition build() {
            record Impl(String name, String kind, String reason, List<TemplateStep> ladder,
                    Set<String> requiredEvidence, Set<String> captureEvidence)
                    implements TemplateDefinition {
            }

            ValidationException.throwIfAny(this.validate());
            return new Impl(Validator.normalizeIdentifier(this.name).orElseThrow(),
                    Validator.normalizeIdentifier(this.kind).orElseThrow(), this.reason,
                    this.ladder, this.requiredEvidence, this.captureEvidence);
        }
    }
}
