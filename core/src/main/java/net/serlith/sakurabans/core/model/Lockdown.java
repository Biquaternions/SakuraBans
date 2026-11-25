package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Instant;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Scope;
import org.jspecify.annotations.Nullable;

public record Lockdown(Scope scope, String reason, Actor startedBy, Instant startedAt,
        @Nullable Instant endsAt) {

    public Lockdown {
        Preconditions.checkNotNull(scope, "scope");
        Preconditions.checkNotNull(reason, "reason");
        Preconditions.checkNotNull(startedBy, "startedBy");
        Preconditions.checkNotNull(startedAt, "startedAt");
    }

    public boolean activeAt(final Instant now) {
        return this.endsAt == null || this.endsAt.isAfter(now);
    }

}
