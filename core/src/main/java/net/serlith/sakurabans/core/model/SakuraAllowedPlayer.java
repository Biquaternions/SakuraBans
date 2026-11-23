package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.api.allow.AllowedPlayer;
import net.serlith.sakurabans.api.model.Actor;
import org.jspecify.annotations.Nullable;

public record SakuraAllowedPlayer(UUID uuid, Actor addedBy, Instant addedAt,
        @Nullable String noteOrNull) implements AllowedPlayer {

    public SakuraAllowedPlayer {
        Preconditions.checkNotNull(uuid, "uuid");
        Preconditions.checkNotNull(addedBy, "addedBy");
        Preconditions.checkNotNull(addedAt, "addedAt");
    }

    @Override
    public Optional<String> note() {
        return Optional.ofNullable(this.noteOrNull);
    }
}
