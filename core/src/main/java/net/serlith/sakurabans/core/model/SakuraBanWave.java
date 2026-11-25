package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.wave.BanWave;
import org.jspecify.annotations.Nullable;

public record SakuraBanWave(long id, String name, Status status,
        @Nullable Instant scheduledAtOrNull, Actor createdBy, Instant createdAt,
        @Nullable Instant releasedAtOrNull, int entryCount, long version) implements BanWave {

    public SakuraBanWave {
        Preconditions.checkArgument(id >= 1, "id must be positive: %s", id);
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(status, "status");
        Preconditions.checkNotNull(createdBy, "createdBy");
        Preconditions.checkNotNull(createdAt, "createdAt");
        Preconditions.checkArgument(version >= 1, "version must be positive: %s", version);
    }

    @Override
    public Optional<Instant> scheduledAt() {
        return Optional.ofNullable(this.scheduledAtOrNull);
    }

    @Override
    public Optional<Instant> releasedAt() {
        return Optional.ofNullable(this.releasedAtOrNull);
    }
}
