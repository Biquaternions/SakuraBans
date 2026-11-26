package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.model.Punishment;

/**
 * After a punishment's expiry passed and SakuraBans recorded it as {@code EXPIRED}.
 *
 * <p>Best effort and at least once: a punishment stops applying the moment it expires, but this
 * event fires when the expiry sweeper (or a lookup) notices it, which can be later; after a
 * crash it may fire twice. Runs on a SakuraBans worker thread.
 */
public interface PunishmentExpiredEvent extends SakuraEvent {

    /**
     * The punishment, now ended.
     *
     * @return the punishment, with its {@code end()} set to {@code EXPIRED}
     */
    Punishment punishment();
}
