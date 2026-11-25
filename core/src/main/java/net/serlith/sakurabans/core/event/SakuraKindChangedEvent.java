package net.serlith.sakurabans.core.event;

import java.util.Optional;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.event.EntityChangedEvent;
import net.serlith.sakurabans.api.kind.Kind;
import net.serlith.sakurabans.api.model.Actor;
import org.jspecify.annotations.Nullable;

public record SakuraKindChangedEvent(ChangeType change, String entityId, long version,
        Actor actor, @Nullable Kind kindOrNull, boolean remote)
        implements EntityChangedEvent.KindChanged {

    @Override
    public Optional<Kind> kind() {
        return Optional.ofNullable(this.kindOrNull);
    }
}
