package net.serlith.sakurabans.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.serlith.sakurabans.api.model.AddressStrictness;
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

public record SakuraPunishment(
        long id,
        String displayId,
        PunishmentType type,
        String kind,
        boolean shadow,
        Optional<AddressStrictness> strictness,
        Victim victim,
        Operator operator,
        String source,
        Scope scope,
        String reason,
        Instant createdAt,
        Optional<Instant> expiresAt,
        Optional<String> originServer,
        Visibility visibility,
        Optional<TemplateRef> template,
        Optional<PunishmentEnd> end,
        Optional<PunishmentVoid> voiding,
        List<Evidence> evidence,
        long version) implements Punishment {

    public SakuraPunishment {
        if (id < 1) {
            throw new IllegalArgumentException("id must be positive: " + id);
        }
        ModelChecks.name(displayId, Lengths.MAX_IDENTIFIER, "displayId");
        Objects.requireNonNull(type, "type");
        kind = ModelChecks.identifier(kind, "kind");
        if (shadow && type != PunishmentType.MUTE) {
            throw new IllegalArgumentException("only a mute can be a shadow mute");
        }
        Objects.requireNonNull(strictness, "strictness");
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(operator, "operator");
        source = ModelChecks.source(source, "source");
        Objects.requireNonNull(scope, "scope");
        ModelChecks.text(reason, Lengths.MAX_REASON, "reason");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        ModelChecks.text(originServer, Lengths.MAX_IDENTIFIER, "originServer");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(voiding, "voiding");
        evidence = List.copyOf(evidence);
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive: " + version);
        }
        if (expiresAt.isPresent() && !expiresAt.get().isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (end.isPresent() && end.get().at().isBefore(createdAt)) {
            throw new IllegalArgumentException("end must not be before createdAt");
        }
        if (voiding.isPresent() && voiding.get().at().isBefore(createdAt)) {
            throw new IllegalArgumentException("voiding must not be before createdAt");
        }
        if (end.isPresent() && end.get().kind() == EndKind.VOIDED && voiding.isEmpty()) {
            throw new IllegalArgumentException("an end by voiding needs the voiding");
        }
        if (type.instant() && (expiresAt.isPresent() || end.isPresent())) {
            throw new IllegalArgumentException(type + " can't expire or end");
        }
    }
}
