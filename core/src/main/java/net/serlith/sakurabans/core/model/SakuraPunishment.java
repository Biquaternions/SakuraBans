package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Evidence;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentEnd;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.PunishmentVoid;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.TemplateRef;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import org.jspecify.annotations.Nullable;

public record SakuraPunishment(
        long id,
        String displayId,
        PunishmentType type,
        boolean shadow,
        Victim victim,
        Operator operator,
        String source,
        Scope scope,
        String reason,
        Instant createdAt,
        @Nullable Instant expiresAtOrNull,
        @Nullable String originServerOrNull,
        Visibility visibility,
        @Nullable TemplateRef templateOrNull,
        @Nullable PunishmentEnd endOrNull,
        @Nullable PunishmentVoid voidingOrNull,
        List<Evidence> evidence) implements Punishment {

    public SakuraPunishment {
        if (id < 1) {
            throw new IllegalArgumentException("id must be positive: " + id);
        }
        ModelChecks.name(displayId, Lengths.MAX_IDENTIFIER, "displayId");
        Preconditions.checkNotNull(type, "type");
        if (shadow && type != PunishmentType.MUTE) {
            throw new IllegalArgumentException("only a mute can be a shadow mute");
        }
        Preconditions.checkNotNull(victim, "victim");
        Preconditions.checkNotNull(operator, "operator");
        source = ModelChecks.source(source, "source");
        Preconditions.checkNotNull(scope, "scope");
        ModelChecks.text(reason, Lengths.MAX_REASON, "reason");
        Preconditions.checkNotNull(createdAt, "createdAt");
        ModelChecks.optionalText(originServerOrNull, Lengths.MAX_IDENTIFIER, "originServer");
        Preconditions.checkNotNull(visibility, "visibility");
        evidence = List.copyOf(evidence);
        if (expiresAtOrNull != null && !expiresAtOrNull.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (endOrNull != null && endOrNull.at().isBefore(createdAt)) {
            throw new IllegalArgumentException("end must not be before createdAt");
        }
        if (voidingOrNull != null && voidingOrNull.at().isBefore(createdAt)) {
            throw new IllegalArgumentException("voiding must not be before createdAt");
        }
        if (endOrNull != null && endOrNull.kind() == EndKind.VOIDED && voidingOrNull == null) {
            throw new IllegalArgumentException("an end by voiding needs the voiding");
        }
        if (type.instant() && (expiresAtOrNull != null || endOrNull != null)) {
            throw new IllegalArgumentException(type + " can't expire or end");
        }
    }

    @Override
    public Optional<Instant> expiresAt() {
        return Optional.ofNullable(this.expiresAtOrNull);
    }

    @Override
    public Optional<String> originServer() {
        return Optional.ofNullable(this.originServerOrNull);
    }

    @Override
    public Optional<TemplateRef> template() {
        return Optional.ofNullable(this.templateOrNull);
    }

    @Override
    public Optional<PunishmentEnd> end() {
        return Optional.ofNullable(this.endOrNull);
    }

    @Override
    public Optional<PunishmentVoid> voiding() {
        return Optional.ofNullable(this.voidingOrNull);
    }
}
