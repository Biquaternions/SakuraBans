package net.serlith.sakurabans.core.importer;

import com.google.common.base.Preconditions;
import java.time.Instant;
import net.serlith.sakurabans.api.model.EndKind;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import org.jspecify.annotations.Nullable;

public record ImportedPunishment(
        PunishmentType type,
        Victim victim,
        Operator operator,
        Instant createdAt,
        @Nullable Instant expiresAt,
        Scope scope,
        String reason,
        Visibility visibility,
        boolean shadow,
        @Nullable String originServer,
        @Nullable End end,
        String externalId) {

    public static final int MAX_EXTERNAL_ID = 128;

    public ImportedPunishment {
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(victim, "victim");
        Preconditions.checkNotNull(operator, "operator");
        Preconditions.checkNotNull(createdAt, "createdAt");
        Preconditions.checkNotNull(scope, "scope");
        Preconditions.checkNotNull(reason, "reason");
        Preconditions.checkNotNull(visibility, "visibility");
        Preconditions.checkNotNull(externalId, "externalId");
    }

    /** How it ended; a revocation without an operator is attributed to an unknown revoker. */
    public record End(EndKind kind, Instant at, @Nullable Operator operator,
            @Nullable String reason) {

        public End {
            Preconditions.checkNotNull(kind, "kind");
            Preconditions.checkNotNull(at, "at");
        }
    }
}
