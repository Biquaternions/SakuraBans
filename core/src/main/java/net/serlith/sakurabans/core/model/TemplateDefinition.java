package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

public record TemplateDefinition(
        String name,
        String kind,
        String reason,
        List<TemplateStep> ladder,
        Set<String> requiredEvidence,
        @Nullable Duration ladderExpiry,
        boolean addressShared,
        boolean permissionRequired,
        List<String> actions,
        Map<String, Integer> groups) {

    public static final int MAX_STEPS = 64;
    public static final int MAX_GROUPS = 16;
    public static final int MAX_WEIGHT = 100;

    public TemplateDefinition {
        ladder = List.copyOf(ladder);
        requiredEvidence = Set.copyOf(requiredEvidence);
        actions = List.copyOf(actions);
        groups = Map.copyOf(groups);
    }

    public static Builder builder(final String name, final String kind) {
        return new Builder(name, kind);
    }

    public static Builder builder(final TemplateInfo template) {
        return builder(template.name(), template.kind())
                .reason(template.reason())
                .ladder(template.ladder())
                .requiredEvidence(template.requiredEvidence())
                .ladderExpiry(template.ladderExpiry().orElse(null))
                .addressShared(template.addressShared())
                .permissionRequired(template.permissionRequired())
                .actions(template.actions())
                .groups(template.groups());
    }

    public static final class Builder {

        private final String name;
        private final String kind;
        private String reason = "";
        private List<TemplateStep> ladder = List.of(TemplateStep.of(null));
        private Set<String> requiredEvidence = Set.of();
        private @Nullable Duration ladderExpiry;
        private boolean addressShared;
        private boolean permissionRequired;
        private List<String> actions = List.of();
        private Map<String, Integer> groups = Map.of();

        private Builder(final String name, final String kind) {
            this.name = Preconditions.checkNotNull(name, "name");
            this.kind = Preconditions.checkNotNull(kind, "kind");
        }

        public Builder reason(final String reason) {
            this.reason = Preconditions.checkNotNull(reason, "reason");
            return this;
        }

        public Builder ladder(final List<TemplateStep> ladder) {
            this.ladder = List.copyOf(ladder);
            return this;
        }

        public Builder requiredEvidence(final Set<String> requiredEvidence) {
            this.requiredEvidence = Set.copyOf(requiredEvidence);
            return this;
        }

        public Builder ladderExpiry(final @Nullable Duration ladderExpiry) {
            this.ladderExpiry = ladderExpiry;
            return this;
        }

        public Builder addressShared(final boolean addressShared) {
            this.addressShared = addressShared;
            return this;
        }

        public Builder permissionRequired(final boolean permissionRequired) {
            this.permissionRequired = permissionRequired;
            return this;
        }

        public Builder actions(final List<String> actions) {
            this.actions = List.copyOf(actions);
            return this;
        }

        public Builder groups(final Map<String, Integer> groups) {
            this.groups = Map.copyOf(groups);
            return this;
        }

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
            TemplateStep.validateActions(errors, "actions", this.actions);
            this.groups.forEach((group, weight) -> errors
                    .identifier("groups", group)
                    .check(weight >= 1 && weight <= MAX_WEIGHT, "groups." + group,
                            Code.OUT_OF_RANGE, "weight must be 1-" + MAX_WEIGHT));
            return errors.errors();
        }

        public TemplateDefinition build() {
            ValidationException.throwIfAny(this.validate());
            final Map<String, Integer> normalizedGroups = new LinkedHashMap<>();
            this.groups.forEach((group, weight) -> normalizedGroups.merge(
                    Validator.normalizeIdentifier(group).orElseThrow(), weight, Math::max));
            return new TemplateDefinition(Validator.normalizeIdentifier(this.name).orElseThrow(),
                    Validator.normalizeIdentifier(this.kind).orElseThrow(), this.reason.strip(),
                    this.ladder, this.requiredEvidence, this.ladderExpiry,
                    this.addressShared, this.permissionRequired,
                    TemplateStep.normalizeActions(this.actions), normalizedGroups);
        }
    }
}
