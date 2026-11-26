package net.serlith.sakurabans.core.event;

import net.serlith.sakurabans.api.data.EraseResult;
import net.serlith.sakurabans.api.event.PlayerDataErasedEvent;
import net.serlith.sakurabans.api.model.Actor;

public record SakuraPlayerDataErasedEvent(EraseResult result, Actor actor)
        implements PlayerDataErasedEvent {
}
