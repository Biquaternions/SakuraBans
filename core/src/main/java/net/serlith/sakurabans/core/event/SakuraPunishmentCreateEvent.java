package net.serlith.sakurabans.core.event;

import net.serlith.sakurabans.api.event.PunishmentCreateEvent;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;

public final class SakuraPunishmentCreateEvent implements PunishmentCreateEvent {

    private final DraftPunishment draft;
    private final PunishOptions options;
    private boolean cancelled;

    public SakuraPunishmentCreateEvent(final DraftPunishment draft, final PunishOptions options) {
        this.draft = draft;
        this.options = options;
    }

    @Override
    public DraftPunishment draft() {
        return this.draft;
    }

    @Override
    public PunishOptions options() {
        return this.options;
    }

    @Override
    public boolean cancelled() {
        return this.cancelled;
    }

    @Override
    public void cancelled(final boolean cancelled) {
        this.cancelled = cancelled;
    }

}
