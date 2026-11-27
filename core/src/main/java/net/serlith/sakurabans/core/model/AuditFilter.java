package net.serlith.sakurabans.core.model;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AuditFilter(@Nullable UUID actorUuid, boolean consoleActor,
        @Nullable UUID playerUuid, @Nullable Long punishmentId, @Nullable String template,
        @Nullable String setting, @Nullable AuditAction action, @Nullable Instant since) {

    public static AuditFilter none() {
        return new AuditFilter(null, false, null, null, null, null, null, null);
    }
}
