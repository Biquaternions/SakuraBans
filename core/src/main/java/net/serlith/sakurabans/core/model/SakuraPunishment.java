package net.serlith.sakurabans.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Lengths;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentEnd;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.TemplateRef;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;

public record SakuraPunishment(
        long id,
        String displayId,
        PunishmentType type,
        Victim victim,
        Operator operator,
        Scope scope,
        String reason,
        Instant createdAt,
        Optional<Instant> expiresAt,
        Optional<String> originServer,
        Visibility visibility,
        Optional<TemplateRef> template,
        Optional<PunishmentEnd> end) implements Punishment {

    public SakuraPunishment {
        if (id < 1) {
            throw new IllegalArgumentException("id must be positive: " + id);
        }
        ModelChecks.name(displayId, Lengths.MAX_IDENTIFIER, "displayId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(scope, "scope");
        ModelChecks.text(reason, Lengths.MAX_REASON, "reason");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        ModelChecks.text(originServer, Lengths.MAX_IDENTIFIER, "originServer");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(end, "end");
        if (expiresAt.isPresent() && !expiresAt.get().isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (end.isPresent() && end.get().at().isBefore(createdAt)) {
            throw new IllegalArgumentException("end must not be before createdAt");
        }
        if (type.instant() && (expiresAt.isPresent() || end.isPresent())) {
            throw new IllegalArgumentException(type + " can't expire or end");
        }
    }
}
