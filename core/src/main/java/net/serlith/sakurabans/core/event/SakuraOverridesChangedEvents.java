package net.serlith.sakurabans.core.event;

import java.util.Locale;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.event.EntityChangedEvent;
import net.serlith.sakurabans.api.model.Actor;

public final class SakuraOverridesChangedEvents {

    private SakuraOverridesChangedEvents() {
    }

    public record Settings(ChangeType change, String entityId, long version, Actor actor,
            boolean remote) implements EntityChangedEvent.SettingsChanged {
    }

    public record Messages(ChangeType change, String entityId, long version, Actor actor,
            Locale locale, boolean remote) implements EntityChangedEvent.MessagesChanged {
    }
}
