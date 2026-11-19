package net.serlith.sakurabans.api.event;

import java.util.Optional;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * After a punishment was stored and enforced.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PunishmentCreatedEvent extends SyncedEvent {

    /**
     * The new punishment.
     *
     * @return the punishment
     */
    Punishment punishment();

    /**
     * The punishment it replaced under the override policy.
     *
     * @return the superseded punishment, or empty
     */
    Optional<Punishment> superseded();
}
