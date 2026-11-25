package net.serlith.sakurabans.api.template;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * The values of a template to create or update with {@link TemplateService}. Build one with
 * {@link #builder(String, String)}, or from an existing template with
 * {@link #builder(TemplateInfo)}.
 */
public interface TemplateDefinition {

    /** The longest ladder. */
    int MAX_STEPS = 64;

    /** The most groups a template may be in. */
    int MAX_GROUPS = 16;

    /** The highest weight in a group. */
    int MAX_WEIGHT = 100;

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
     * See {@link TemplateInfo#ladderExpiry()}.
     *
     * @return the window, or empty
     */
    Optional<Duration> ladderExpiry();

    /**
     * See {@link TemplateInfo#addressShared()}.
     *
     * @return {@code true} if shared
     */
    boolean addressShared();

    /**
     * See {@link TemplateInfo#permissionRequired()}.
     *
     * @return {@code true} if a permission is needed
     */
    boolean permissionRequired();

    /**
     * See {@link TemplateInfo#actions()}.
     *
     * @return the commands
     */
    List<String> actions();

    /**
     * See {@link TemplateInfo#groups()}.
     *
     * @return group name to weight
     */
    Map<String, Integer> groups();

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
                .captureEvidence(template.captureEvidence())
                .ladderExpiry(template.ladderExpiry().orElse(null))
                .addressShared(template.addressShared())
                .permissionRequired(template.permissionRequired())
                .actions(template.actions())
                .groups(template.groups());
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
        private @Nullable Duration ladderExpiry;
        private boolean addressShared;
        private boolean permissionRequired;
        private List<String> actions = List.of();
        private Map<String, Integer> groups = Map.of();

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
         * Sets how long a punishment counts towards the ladder.
         *
         * @param ladderExpiry a positive duration, or {@code null} for always
         * @return this builder
         */
        public Builder ladderExpiry(final @Nullable Duration ladderExpiry) {
            this.ladderExpiry = ladderExpiry;
            return this;
        }

        /**
         * Sets whether progress is shared through addresses.
         *
         * @param addressShared {@code true} to share it
         * @return this builder
         */
        public Builder addressShared(final boolean addressShared) {
            this.addressShared = addressShared;
            return this;
        }

        /**
         * Sets whether using the template needs {@code sakurabans.template.<name>}.
         *
         * @param permissionRequired {@code true} if it does
         * @return this builder
         */
        public Builder permissionRequired(final boolean permissionRequired) {
            this.permissionRequired = permissionRequired;
            return this;
        }

        /**
         * Sets the template-wide actions.
         *
         * @param actions console commands, see {@link TemplateStep#actions()}
         * @return this builder
         */
        public Builder actions(final List<String> actions) {
            this.actions = List.copyOf(actions);
            return this;
        }

        /**
         * Sets the groups.
         *
         * @param groups group name (an identifier) to weight (1-{@value #MAX_WEIGHT})
         * @return this builder
         */
        public Builder groups(final Map<String, Integer> groups) {
            this.groups = Map.copyOf(groups);
            return this;
        }

        /**
         * Checks the values without building.
         *
         * @return every problem: {@code name} or {@code kind} not an identifier, {@code reason}
         *         blank or too long, {@code ladder} empty or too long, an evidence entry that
         *         isn't an evidence type, {@code ladderExpiry} not positive, an invalid action, or
         *         a group that isn't an identifier or has a weight out of range
         */
        public List<FieldError> validate() {
            final Validator errors = Validator.create()
                    .identifier("name", this.name)
                    .identifier("kind", this.kind)
                    .name("reason", this.reason, Lengths.MAX_REASON)
                    .check(!this.ladder.isEmpty() && this.ladder.size() <= MAX_STEPS, "ladder",
                            Code.OUT_OF_RANGE, "must have 1-" + MAX_STEPS + " steps")
                    .check(this.ladderExpiry == null || this.ladderExpiry.isPositive(),
                            "ladderExpiry", Code.NOT_POSITIVE, "must be positive")
                    .check(this.groups.size() <= MAX_GROUPS, "groups", Code.OUT_OF_RANGE,
                            "has more than " + MAX_GROUPS + " groups");
            this.requiredEvidence.forEach(type -> errors.evidenceType("requiredEvidence", type));
            this.captureEvidence.forEach(type -> errors.evidenceType("captureEvidence", type));
            TemplateStep.validateActions(errors, "actions", this.actions);
            this.groups.forEach((group, weight) -> errors
                    .identifier("groups", group)
                    .check(weight >= 1 && weight <= MAX_WEIGHT, "groups." + group,
                            Code.OUT_OF_RANGE, "weight must be 1-" + MAX_WEIGHT));
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
                    Set<String> requiredEvidence, Set<String> captureEvidence,
                    Optional<Duration> ladderExpiry, boolean addressShared,
                    boolean permissionRequired, List<String> actions,
                    Map<String, Integer> groups) implements TemplateDefinition {
            }

            ValidationException.throwIfAny(this.validate());
            final Map<String, Integer> normalizedGroups = new LinkedHashMap<>();
            this.groups.forEach((group, weight) -> normalizedGroups.merge(
                    Validator.normalizeIdentifier(group).orElseThrow(), weight, Math::max));
            return new Impl(Validator.normalizeIdentifier(this.name).orElseThrow(),
                    Validator.normalizeIdentifier(this.kind).orElseThrow(), this.reason.strip(),
                    this.ladder, this.requiredEvidence, this.captureEvidence,
                    Optional.ofNullable(this.ladderExpiry), this.addressShared,
                    this.permissionRequired, TemplateStep.normalizeActions(this.actions),
                    Map.copyOf(normalizedGroups));
        }
    }
}
