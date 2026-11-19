package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.Optional;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.AuditEntry;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Lengths;
import org.jspecify.annotations.Nullable;

public record SakuraAuditEntry(long id, Instant at, Actor actor, AuditAction action,
        SubjectType subjectType, String subjectId, @Nullable String oldValueOrNull,
        @Nullable String newValueOrNull) implements AuditEntry {

    public SakuraAuditEntry {
        Preconditions.checkArgument(id >= 1, "id must be positive: %s", id);
        Preconditions.checkNotNull(at, "at");
        Preconditions.checkNotNull(actor, "actor");
        Preconditions.checkNotNull(action, "action");
        Preconditions.checkNotNull(subjectType, "subjectType");
        ModelChecks.name(subjectId, Lengths.MAX_IDENTIFIER, "subjectId");
    }

    @Override
    public Optional<String> oldValue() {
        return Optional.ofNullable(this.oldValueOrNull);
    }

    @Override
    public Optional<String> newValue() {
        return Optional.ofNullable(this.newValueOrNull);
    }
}
