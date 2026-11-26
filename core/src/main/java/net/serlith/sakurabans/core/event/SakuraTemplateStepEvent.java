package net.serlith.sakurabans.core.event;

import net.serlith.sakurabans.api.event.TemplateStepEvent;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.TemplateRef;

public record SakuraTemplateStepEvent(Punishment punishment, TemplateRef template)
        implements TemplateStepEvent {
}
