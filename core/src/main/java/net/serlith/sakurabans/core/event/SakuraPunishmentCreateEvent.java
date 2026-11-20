package net.serlith.sakurabans.core.event;

import com.google.common.base.Preconditions;
import net.serlith.sakurabans.api.event.PunishmentCreateEvent;
import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;
import net.serlith.sakurabans.core.model.ApiValues;

public final class SakuraPunishmentCreateEvent implements PunishmentCreateEvent {

    private DraftPunishment draft;
    private PunishOptions options;
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
    public void draft(final DraftPunishment draft) {
        final DraftPunishment checked = ApiValues.draft(Preconditions.checkNotNull(draft, "draft"));
        Preconditions.checkArgument(checked.type() == this.draft.type(),
                "the type can't change (%s to %s)", this.draft.type(), checked.type());
        Preconditions.checkArgument(checked.victim().equals(this.draft.victim()),
                "the victim can't change");
        this.draft = checked;
    }

    @Override
    public PunishOptions options() {
        return this.options;
    }

    @Override
    public void options(final PunishOptions options) {
        this.options = ApiValues.options(Preconditions.checkNotNull(options, "options"));
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
