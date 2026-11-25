package net.serlith.sakurabans.core.event;

import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.event.ChangeType;
import net.serlith.sakurabans.api.event.EntityChangedEvent;
import net.serlith.sakurabans.api.model.Actor;

public record SakuraAppealChangedEvent(ChangeType change, String entityId, long version,
        Actor actor, Appeal appeal, boolean remote) implements EntityChangedEvent.AppealChanged {
}
