package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.model.Punishment;

/**
 * After a punishment was voided.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PunishmentVoidedEvent extends SyncedEvent {

    /**
     * The punishment, now voided (and ended with {@code VOIDED} if it was active).
     *
     * @return the punishment
     */
    Punishment punishment();
}
