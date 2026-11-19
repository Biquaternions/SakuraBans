package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.punish.DraftPunishment;
import net.serlith.sakurabans.api.punish.PunishOptions;

/**
 * Before a punishment is stored. Handlers can change the draft and the options, or cancel it
 * ({@code CANCELLED}). Fired after defaults, templates, limits and exemption were applied, so the
 * draft holds the values that would be stored, and changes are validated again afterwards.
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
     * Replaces the draft, for example {@code event.draft(event.draft().toBuilder().reason("…")
     * .build())}. The type and the victim can't change.
     *
     * @param draft the new draft
     * @throws IllegalArgumentException if the type or the victim differs
     */
    void draft(DraftPunishment draft);

    /**
     * The options it is created with.
     *
     * @return the options
     */
    PunishOptions options();

    /**
     * Replaces the options, for example to make it silent.
     *
     * @param options the new options
     */
    void options(PunishOptions options);
}
