package net.serlith.sakurabans.core.event;

import net.serlith.sakurabans.api.event.PunishmentRevokeEvent;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.RevocationRequest;

public final class SakuraPunishmentRevokeEvent implements PunishmentRevokeEvent {

    private final Punishment punishment;
    private final RevocationRequest request;
    private boolean cancelled;

    public SakuraPunishmentRevokeEvent(final Punishment punishment,
            final RevocationRequest request) {
        this.punishment = punishment;
        this.request = request;
    }

    @Override
    public Punishment punishment() {
        return this.punishment;
    }

    @Override
    public RevocationRequest request() {
        return this.request;
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
