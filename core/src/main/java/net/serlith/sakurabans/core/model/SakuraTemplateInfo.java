package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.util.Durations;
import net.serlith.sakurabans.core.util.Scopes;
import org.jspecify.annotations.Nullable;

public record SakuraTemplateInfo(
        String name,
        PunishmentType type,
        boolean ip,
        boolean shadow,
        @Nullable Scope scopeOrNull,
        String reason,
        List<TemplateStep> ladder,
        @Nullable Duration ladderExpiryOrNull,
        boolean addressShared,
        boolean permissionRequired,
        List<String> actions,
        Map<String, Integer> groups,
        @Nullable String messageOrNull,
        long version) implements TemplateInfo {

    public SakuraTemplateInfo {
        name = ModelChecks.identifier(name, "name");
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(reason, "reason");
        ladder = List.copyOf(ladder);
        Preconditions.checkArgument(!ladder.isEmpty(), "a ladder has at least one step");
        Preconditions.checkArgument(!shadow || ladder.stream().anyMatch(step ->
                step.type().orElse(type) == PunishmentType.MUTE),
                "shadow needs a mute step");
        Preconditions.checkArgument(ladder.stream().noneMatch(step ->
                step.type().orElse(type).instant() && step.duration().isPresent()),
                "a kick step has no duration");
        actions = List.copyOf(actions);
        groups = Map.copyOf(groups);
        Preconditions.checkArgument(version >= 1, "version must be positive: %s", version);
    }

    public static SakuraTemplateInfo of(final TemplateDefinition definition, final long version) {
        return new SakuraTemplateInfo(definition.name(), definition.type(), definition.ip(),
                definition.shadow(), definition.scope(),
                definition.reason(), definition.ladder(),
                definition.ladderExpiry(), definition.addressShared(),
                definition.permissionRequired(), definition.actions(), definition.groups(),
                definition.message(), version);
    }

    @Override
    public Optional<String> message() {
        return Optional.ofNullable(this.messageOrNull);
    }

    @Override
    public Optional<Scope> scope() {
        return Optional.ofNullable(this.scopeOrNull);
    }

    @Override
    public Optional<Duration> ladderExpiry() {
        return Optional.ofNullable(this.ladderExpiryOrNull);
    }

    public int stepFor(final long points) {
        return Math.clamp(points, 0, this.ladder.size() - 1);
    }

    public String summary() {
        return "type=" + this.type
                + " ip=" + this.ip
                + " shadow=" + this.shadow
                + " scope=" + this.scope().map(Scopes::format).orElse("default")
                + " reason=" + this.reason
                + " ladder=" + this.ladder.stream()
                        .map(step -> TemplateSteps.format(step, this.type)
                                + (step.actions().isEmpty() ? "" : step.actions().toString())
                                + step.message().map(text -> " message=" + text).orElse(""))
                        .toList()
                + " ladder-expiry=" + this.ladderExpiry().map(Durations::formatCompact)
                        .orElse("none")
                + " address-shared=" + this.addressShared
                + " permission-required=" + this.permissionRequired
                + " actions=" + this.actions
                + " groups=" + new TreeMap<>(this.groups)
                + this.message().map(text -> " message=" + text).orElse("");
    }
}
