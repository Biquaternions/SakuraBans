package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.List;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import org.jspecify.annotations.Nullable;

public record NewPunishment(
        PunishmentType type,
        String kind,
        boolean shadow,
        @Nullable AddressStrictness strictness,
        Victim victim,
        Scope scope,
        String reason,
        Actor actor,
        Instant createdAt,
        @Nullable Instant expiresAt,
        @Nullable String originServer,
        Visibility visibility,
        @Nullable String template,
        @Nullable Integer templateStep,
        List<EvidenceRef> evidence,
        @Nullable String externalId) {

    public NewPunishment {
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(kind, "kind");
        Preconditions.checkNotNull(victim, "victim");
        Preconditions.checkNotNull(scope, "scope");
        Preconditions.checkNotNull(reason, "reason");
        Preconditions.checkNotNull(actor, "actor");
        Preconditions.checkNotNull(createdAt, "createdAt");
        Preconditions.checkNotNull(visibility, "visibility");
        evidence = List.copyOf(evidence);
    }

    public NewPunishment withExpiry(final @Nullable Instant expiry) {
        return new NewPunishment(this.type, this.kind, this.shadow, this.strictness, this.victim,
                this.scope, this.reason, this.actor, this.createdAt, expiry, this.originServer,
                this.visibility, this.template, this.templateStep, this.evidence, this.externalId);
    }

    public NewPunishment withTemplate(final @Nullable String name, final @Nullable Integer step) {
        return new NewPunishment(this.type, this.kind, this.shadow, this.strictness, this.victim,
                this.scope, this.reason, this.actor, this.createdAt, this.expiresAt,
                this.originServer, this.visibility, name, step, this.evidence, this.externalId);
    }

    boolean outlasts(final @Nullable Instant otherExpiry) {
        if (this.expiresAt == null) {
            return otherExpiry != null;
        }
        return otherExpiry != null && this.expiresAt.isAfter(otherExpiry);
    }

}
