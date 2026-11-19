package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.model.Punishment;

/**
 * After a punishment was revoked.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PunishmentRevokedEvent extends SyncedEvent {

    /**
     * The punishment, now ended.
     *
     * @return the punishment, with its {@code end()} set to {@code REVOKED}
     */
    Punishment punishment();
}
