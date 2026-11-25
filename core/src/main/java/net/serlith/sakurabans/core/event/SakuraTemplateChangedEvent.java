package net.serlith.sakurabans.core.event;

import java.util.Optional;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.event.EntityChangedEvent;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.template.TemplateInfo;
import org.jspecify.annotations.Nullable;

public record SakuraTemplateChangedEvent(ChangeType change, String entityId, long version,
        Actor actor, @Nullable TemplateInfo templateOrNull, boolean remote)
        implements EntityChangedEvent.TemplateChanged {

    @Override
    public Optional<TemplateInfo> template() {
        return Optional.ofNullable(this.templateOrNull);
    }
}
