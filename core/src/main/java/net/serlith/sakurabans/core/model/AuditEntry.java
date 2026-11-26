package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Instant;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Lengths;
import org.jspecify.annotations.Nullable;

public record AuditEntry(long id, Instant at, Actor actor, AuditAction action,
        SubjectType subjectType, String subjectId, @Nullable String oldValue,
        @Nullable String newValue) {

    public AuditEntry {
        Preconditions.checkArgument(id >= 1, "id must be positive: %s", id);
        Preconditions.checkNotNull(at, "at");
        Preconditions.checkNotNull(actor, "actor");
        Preconditions.checkNotNull(action, "action");
        Preconditions.checkNotNull(subjectType, "subjectType");
        ModelChecks.name(subjectId, Lengths.MAX_IDENTIFIER, "subjectId");
    }
}
