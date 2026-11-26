package net.serlith.sakurabans.api.event;

import java.util.List;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.RollbackRequest;

/**
 * After a staff rollback ended punishments. Fired once for the whole rollback, not per
 * punishment.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PunishmentsRolledBackEvent extends SakuraEvent {

    /**
     * The rollback.
     *
     * @return the request
     */
    RollbackRequest request();

    /**
     * The punishments it ended, as they are now.
     *
     * @return the punishments, never empty
     */
    List<Punishment> punishments();
}
