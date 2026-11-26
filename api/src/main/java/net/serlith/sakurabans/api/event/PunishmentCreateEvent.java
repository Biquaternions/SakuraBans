package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;

/**
 * Before a punishment is stored. Handlers can cancel it ({@code CANCELLED}); they can't change
 * it. Fired after defaults, templates, limits and exemption were applied, so the draft holds the
 * values that will be stored.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PunishmentCreateEvent extends Cancellable {

    /**
     * The punishment that is about to be created.
     *
     * @return the draft
     */
    DraftPunishment draft();

    /**
     * The options it is created with.
     *
     * @return the options
     */
    PunishOptions options();
}
