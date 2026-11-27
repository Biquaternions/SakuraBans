package net.serlith.sakurabans.core.event;

import java.util.Optional;
import net.serlith.sakurabans.api.event.TemplateAppliedEvent;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.TemplateRef;
import net.serlith.sakurabans.api.model.Victim;
import org.jspecify.annotations.Nullable;

public record SakuraTemplateAppliedEvent(@Nullable Punishment punishmentOrNull, Victim victim,
        TemplateRef template, Actor actor, String reason) implements TemplateAppliedEvent {

    @Override
    public Optional<Punishment> punishment() {
        return Optional.ofNullable(this.punishmentOrNull);
    }
}
