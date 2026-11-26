package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

public record TemplateDefinition(
        String name,
        PunishmentType type,
        boolean ip,
        boolean shadow,
        @Nullable Scope scope,
        String reason,
        List<TemplateStep> ladder,
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
        actions = List.copyOf(actions);
        groups = Map.copyOf(groups);
    }

    public static Builder builder(final String name, final PunishmentType type) {
        return new Builder(name, type);
    }

    public static Builder builder(final TemplateInfo template) {
        return builder(template.name(), template.type())
                .ip(template.ip())
                .shadow(template.shadow())
                .scope(template.scope().orElse(null))
                .reason(template.reason())
                .ladder(template.ladder())
                .ladderExpiry(template.ladderExpiry().orElse(null))
                .addressShared(template.addressShared())
                .permissionRequired(template.permissionRequired())
                .actions(template.actions())
                .groups(template.groups());
    }

    public Builder toBuilder() {
        return builder(this.name, this.type)
                .ip(this.ip)
                .shadow(this.shadow)
                .scope(this.scope)
                .reason(this.reason)
                .ladder(this.ladder)
                .ladderExpiry(this.ladderExpiry)
                .addressShared(this.addressShared)
                .permissionRequired(this.permissionRequired)
                .actions(this.actions)
                .groups(this.groups);
    }

    public static final class Builder {

        private final String name;
        private final PunishmentType type;
        private boolean ip;
        private boolean shadow;
        private @Nullable Scope scope;
        private String reason = "";
        private List<TemplateStep> ladder = List.of(TemplateStep.of(null));
        private @Nullable Duration ladderExpiry;
        private boolean addressShared;
        private boolean permissionRequired;
        private List<String> actions = List.of();
        private Map<String, Integer> groups = Map.of();

        private Builder(final String name, final PunishmentType type) {
            this.name = Preconditions.checkNotNull(name, "name");
            this.type = Preconditions.checkNotNull(type, "type");
        }

        public Builder ip(final boolean ip) {
            this.ip = ip;
            return this;
        }

        public Builder shadow(final boolean shadow) {
            this.shadow = shadow;
            return this;
        }

        public Builder scope(final @Nullable Scope scope) {
            this.scope = scope;
            return this;
        }

        public Builder reason(final String reason) {
            this.reason = Preconditions.checkNotNull(reason, "reason");
            return this;
        }

        public Builder ladder(final List<TemplateStep> ladder) {
            this.ladder = List.copyOf(ladder);
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
                    .check(!this.shadow || this.type == PunishmentType.MUTE, "shadow",
                            Code.NOT_ALLOWED, "only mutes can be shadow mutes")
                    .name("reason", this.reason, Lengths.MAX_REASON)
                    .check(!this.ladder.isEmpty() && this.ladder.size() <= MAX_STEPS, "ladder",
                            Code.OUT_OF_RANGE, "must have 1-" + MAX_STEPS + " steps")
                    .check(!this.type.instant() || this.ladder.stream()
                                    .allMatch(step -> step.duration().isEmpty()), "ladder",
                            Code.NOT_ALLOWED, "a kick template has only permanent steps")
                    .check(this.ladderExpiry == null || this.ladderExpiry.isPositive(),
                            "ladderExpiry", Code.NOT_POSITIVE, "must be positive")
                    .check(this.groups.size() <= MAX_GROUPS, "groups", Code.OUT_OF_RANGE,
                            "has more than " + MAX_GROUPS + " groups");
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
                    this.type, this.ip, this.shadow,
                    this.scope == null ? null : ApiValues.scope(this.scope), this.reason.strip(),
                    this.ladder, this.ladderExpiry,
                    this.addressShared, this.permissionRequired,
                    TemplateStep.normalizeActions(this.actions), normalizedGroups);
        }
    }
}
