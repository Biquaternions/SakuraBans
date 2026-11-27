package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.List;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.EvidenceRef;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import org.jspecify.annotations.Nullable;

public record NewPunishment(
        PunishmentType type,
        boolean shadow,
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
        @Nullable String externalId,
        @Nullable Scope templateVariant) {

    public NewPunishment {
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(victim, "victim");
        Preconditions.checkNotNull(scope, "scope");
        Preconditions.checkNotNull(reason, "reason");
        Preconditions.checkNotNull(actor, "actor");
        Preconditions.checkNotNull(createdAt, "createdAt");
        Preconditions.checkNotNull(visibility, "visibility");
        evidence = List.copyOf(evidence);
        if (templateVariant instanceof GlobalScope) {
            templateVariant = null;
        }
    }

    public NewPunishment(final PunishmentType type, final boolean shadow, final Victim victim,
            final Scope scope, final String reason, final Actor actor, final Instant createdAt,
            final @Nullable Instant expiresAt, final @Nullable String originServer,
            final Visibility visibility, final @Nullable String template,
            final @Nullable Integer templateStep, final List<EvidenceRef> evidence,
            final @Nullable String externalId) {
        this(type, shadow, victim, scope, reason, actor, createdAt, expiresAt, originServer,
                visibility, template, templateStep, evidence, externalId, null);
    }

    public NewPunishment withExpiry(final @Nullable Instant expiry) {
        return new NewPunishment(this.type, this.shadow, this.victim,
                this.scope, this.reason, this.actor, this.createdAt, expiry, this.originServer,
                this.visibility, this.template, this.templateStep, this.evidence, this.externalId,
                this.templateVariant);
    }

    boolean outlasts(final @Nullable Instant otherExpiry) {
        if (this.expiresAt == null) {
            return otherExpiry != null;
        }
        return otherExpiry != null && this.expiresAt.isAfter(otherExpiry);
    }

}
