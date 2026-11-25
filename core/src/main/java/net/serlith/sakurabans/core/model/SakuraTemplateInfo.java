package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.template.TemplateDefinition;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.api.template.TemplateStep;
import net.serlith.sakurabans.core.util.Durations;
import org.jspecify.annotations.Nullable;

public record SakuraTemplateInfo(
        String name,
        String kind,
        PunishmentType type,
        String reason,
        List<TemplateStep> ladder,
        Set<String> requiredEvidence,
        Set<String> captureEvidence,
        @Nullable Duration ladderExpiryOrNull,
        boolean addressShared,
        boolean permissionRequired,
        List<String> actions,
        Map<String, Integer> groups,
        long version) implements TemplateInfo {

    public SakuraTemplateInfo {
        name = ModelChecks.identifier(name, "name");
        kind = ModelChecks.identifier(kind, "kind");
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(reason, "reason");
        ladder = List.copyOf(ladder);
        Preconditions.checkArgument(!ladder.isEmpty(), "a ladder has at least one step");
        Preconditions.checkArgument(!type.instant()
                        || ladder.stream().allMatch(step -> step.duration().isEmpty()),
                "a kick template has no durations");
        requiredEvidence = Set.copyOf(requiredEvidence);
        captureEvidence = Set.copyOf(captureEvidence);
        actions = List.copyOf(actions);
        groups = Map.copyOf(groups);
        Preconditions.checkArgument(version >= 1, "version must be positive: %s", version);
    }

    public static SakuraTemplateInfo of(final TemplateDefinition definition,
            final PunishmentType type, final long version) {
        return new SakuraTemplateInfo(definition.name(), definition.kind(), type,
                definition.reason(), definition.ladder(), definition.requiredEvidence(),
                definition.captureEvidence(), definition.ladderExpiry().orElse(null),
                definition.addressShared(), definition.permissionRequired(),
                definition.actions(), definition.groups(), version);
    }

    @Override
    public Optional<Duration> ladderExpiry() {
        return Optional.ofNullable(this.ladderExpiryOrNull);
    }

    public int stepFor(final long points) {
        return Math.clamp(points, 0, this.ladder.size() - 1);
    }

    public String summary() {
        return "kind=" + this.kind
                + " reason=" + this.reason
                + " ladder=" + this.ladder.stream()
                        .map(step -> step.duration().map(Durations::formatCompact)
                                .orElse("permanent")
                                + (step.actions().isEmpty() ? "" : step.actions().toString()))
                        .toList()
                + " ladder-expiry=" + this.ladderExpiry().map(Durations::formatCompact)
                        .orElse("none")
                + " address-shared=" + this.addressShared
                + " permission-required=" + this.permissionRequired
                + " actions=" + this.actions
                + " groups=" + new TreeMap<>(this.groups)
                + " required-evidence=" + new TreeSet<>(this.requiredEvidence)
                + " capture-evidence=" + this.captureEvidence.stream().sorted()
                        .toList();
    }
}
