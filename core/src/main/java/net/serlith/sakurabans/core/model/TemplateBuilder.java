package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.PresetTemplate;
import net.serlith.sakurabans.api.template.TemplateDisplay;
import net.serlith.sakurabans.api.template.TemplateDuration;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/**
 * The only way to make a {@link SakuraPresetTemplate} or {@link SakuraLadderTemplate}: a file,
 * the editor, a command or a stored row fills it in, {@link #validate()} lists every problem
 * and {@link #build()} makes the record. Error paths are the keys of {@code templates.yml}.
 * {@code variant}: the server or category the definition is in force for, null for the global
 * definition (S62); a variant has no groups of its own.
 */
public final class TemplateBuilder {

    public static final int MAX_STEPS = LadderTemplate.MAX_STEPS;
    public static final int MAX_GROUPS = 16;
    public static final int MAX_WEIGHT = 100;

    private final String name;
    private final TemplateKind kind;
    private boolean ip;
    private boolean shadow;
    private @Nullable Scope scope;
    private String reason = "";
    private TemplateDuration duration = TemplateDuration.open();
    private List<TemplateStep> steps = List.of();
    private @Nullable Duration expiry;
    private boolean addressShared;
    private boolean permissionRequired;
    private List<String> actions = List.of();
    private Map<String, Integer> groups = Map.of();
    private @Nullable String message;
    private TemplateDisplay display = TemplateDisplay.CHAT;
    private boolean silent;
    private @Nullable Scope variant;

    private TemplateBuilder(final String name, final TemplateKind kind) {
        this.name = Preconditions.checkNotNull(name, "name");
        this.kind = Preconditions.checkNotNull(kind, "kind");
    }

    public static TemplateBuilder preset(final String name, final PunishmentType type) {
        return new TemplateBuilder(name, TemplateKind.of(type));
    }

    public static TemplateBuilder ladder(final String name) {
        return new TemplateBuilder(name, TemplateKind.LADDER);
    }

    public static TemplateBuilder of(final String name, final TemplateKind kind) {
        return new TemplateBuilder(name, kind);
    }

    /** A copy of every field, to change some and build again. */
    public static TemplateBuilder from(final TemplateInfo template) {
        final TemplateBuilder builder = of(template.name(), TemplateKind.of(template))
                .ip(template.ip())
                .shadow(template.shadow())
                .scope(template.scope().orElse(null))
                .reason(template.reason())
                .permissionRequired(template.permissionRequired())
                .actions(template.actions())
                .groups(template.groups())
                .message(template.message().orElse(null))
                .display(template.display())
                .silent(template.silent())
                .variant(template.variant().orElse(null));
        return switch (template) {
            case PresetTemplate preset -> builder.duration(preset.duration());
            case LadderTemplate ladder -> builder.steps(ladder.steps())
                    .expiry(ladder.expiry().orElse(null))
                    .addressShared(ladder.addressShared());
        };
    }

    public TemplateBuilder ip(final boolean ip) {
        this.ip = ip;
        return this;
    }

    public TemplateBuilder shadow(final boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public TemplateBuilder scope(final @Nullable Scope scope) {
        this.scope = scope;
        return this;
    }

    public TemplateBuilder reason(final String reason) {
        this.reason = Preconditions.checkNotNull(reason, "reason");
        return this;
    }

    public TemplateBuilder duration(final TemplateDuration duration) {
        this.duration = Preconditions.checkNotNull(duration, "duration");
        return this;
    }

    public TemplateBuilder steps(final List<TemplateStep> steps) {
        this.steps = List.copyOf(steps);
        return this;
    }

    public TemplateBuilder expiry(final @Nullable Duration expiry) {
        this.expiry = expiry;
        return this;
    }

    public TemplateBuilder addressShared(final boolean addressShared) {
        this.addressShared = addressShared;
        return this;
    }

    public TemplateBuilder permissionRequired(final boolean permissionRequired) {
        this.permissionRequired = permissionRequired;
        return this;
    }

    public TemplateBuilder actions(final List<String> actions) {
        this.actions = List.copyOf(actions);
        return this;
    }

    public TemplateBuilder groups(final Map<String, Integer> groups) {
        this.groups = Map.copyOf(groups);
        return this;
    }

    public TemplateBuilder message(final @Nullable String message) {
        this.message = message;
        return this;
    }

    public TemplateBuilder display(final TemplateDisplay display) {
        this.display = Preconditions.checkNotNull(display, "display");
        return this;
    }

    public TemplateBuilder silent(final boolean silent) {
        this.silent = silent;
        return this;
    }

    /** Where this definition is in force; null or {@code global} for the global one. */
    public TemplateBuilder variant(final @Nullable Scope variant) {
        this.variant = variant instanceof GlobalScope ? null : variant;
        return this;
    }

    public List<FieldError> validate() {
        final Validator errors = Validator.create()
                .identifier("name", this.name)
                .name("reason", this.reason, Lengths.MAX_REASON)
                .check(this.groups.size() <= MAX_GROUPS, "groups", Code.OUT_OF_RANGE,
                        "has more than " + MAX_GROUPS + " groups")
                .check(this.variant == null || this.groups.isEmpty(), "groups",
                        Code.NOT_ALLOWED, "a variant counts in its global template's groups;"
                                + " set them there");
        if (this.kind.ladder()) {
            errors.check(this.duration instanceof TemplateDuration.Open, "duration",
                            Code.NOT_ALLOWED, "a ladder's steps have the durations")
                    .check(!this.steps.isEmpty() && this.steps.size() <= MAX_STEPS,
                            "steps", Code.OUT_OF_RANGE, "must have 1-" + MAX_STEPS
                                    + " steps")
                    .check(!this.shadow || this.steps.stream().anyMatch(step ->
                                    step.type().orElse(null) == PunishmentType.MUTE),
                            "shadow", Code.NOT_ALLOWED, "needs a mute step")
                    .check(this.display == TemplateDisplay.CHAT
                                    || this.steps.stream().anyMatch(step -> step.type()
                                            .filter(TemplateBuilder::notifies)
                                            .isPresent()),
                            "display", Code.NOT_ALLOWED, "needs a mute or warn step");
            if (this.expiry != null) {
                TemplateDuration.validateLength(errors, "expiry", this.expiry);
            }
        } else {
            final PunishmentType type = this.kind.type().orElseThrow();
            errors.check(this.steps.isEmpty(), "steps", Code.NOT_ALLOWED,
                            "only a ladder has steps (type: ladder)")
                    .check(this.expiry == null, "expiry", Code.NOT_ALLOWED,
                            "only a ladder has an expiry")
                    .check(!this.addressShared, "address-shared", Code.NOT_ALLOWED,
                            "only a ladder shares progress")
                    .check(!type.instant()
                                    || this.duration instanceof TemplateDuration.Open,
                            "duration", Code.NOT_ALLOWED, "a kick has no duration")
                    .check(!this.shadow || type == PunishmentType.MUTE, "shadow",
                            Code.NOT_ALLOWED, "only a mute is a shadow mute")
                    .check(this.display == TemplateDisplay.CHAT || notifies(type),
                            "display", Code.NOT_ALLOWED,
                            "only a mute or a warning is shown as a dialog")
                    .check(this.display == TemplateDisplay.CHAT || !notifies(type)
                                    || !this.shadow,
                            "display", Code.NOT_ALLOWED,
                            "a shadow mute shows the player nothing");
        }
        TemplateStep.validateActions(errors, "actions", this.actions);
        TemplateStep.validateMessage(errors, "message", this.message);
        this.groups.forEach((group, weight) -> errors
                .identifier("groups", group)
                .check(weight >= 1 && weight <= MAX_WEIGHT, "groups." + group,
                        Code.OUT_OF_RANGE, "weight must be 1-" + MAX_WEIGHT));
        return errors.errors();
    }

    /** Validated and normalized; a {@link ValidationException} lists every problem. */
    public TemplateInfo build() {
        ValidationException.throwIfAny(this.validate());
        final String normalizedName = Validator.normalizeIdentifier(this.name).orElseThrow();
        final Map<String, Integer> normalizedGroups = new LinkedHashMap<>();
        this.groups.forEach((group, weight) -> normalizedGroups.merge(
                Validator.normalizeIdentifier(group).orElseThrow(), weight, Math::max));
        final Scope checkedScope = this.scope == null ? null : ApiValues.scope(this.scope);
        final List<String> normalizedActions = TemplateStep.normalizeActions(this.actions);
        final Scope checkedVariant = this.variant == null ? null : ApiValues.scope(this.variant);
        if (this.kind.ladder()) {
            // Re-created, so a step implemented elsewhere is held to the API's rules.
            final List<TemplateStep> checkedSteps = this.steps.stream()
                    .map(step -> TemplateStep.of(step.type().orElse(null), step.duration(),
                            step.reason().orElse(null), step.message().orElse(null),
                            step.actions()))
                    .toList();
            return new SakuraLadderTemplate(normalizedName, checkedSteps, this.expiry,
                    this.addressShared, this.ip, this.shadow, checkedScope, this.reason.strip(),
                    this.permissionRequired, normalizedActions, normalizedGroups, this.message,
                    this.display, this.silent, checkedVariant);
        }
        return new SakuraPresetTemplate(normalizedName, this.kind.type().orElseThrow(),
                this.duration, this.ip, this.shadow, checkedScope, this.reason.strip(),
                this.permissionRequired, normalizedActions, normalizedGroups, this.message,
                this.display, this.silent, checkedVariant);
    }

    /** Whether a punishment of this type tells the online player with a notice. */
    public static boolean notifies(final PunishmentType type) {
        return type == PunishmentType.MUTE || type == PunishmentType.WARN;
    }
}
