package net.serlith.sakurabans.api.event;

import java.util.Optional;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * Before a punishment is voided. Cancelling keeps it as it is ({@code CANCELLED}).
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PunishmentVoidEvent extends Cancellable {

    /**
     * The punishment.
     *
     * @return the punishment, not yet voided
     */
    Punishment punishment();

    /**
     * Who voids it.
     *
     * @return the actor
     */
    Actor actor();

    /**
     * Why.
     *
     * @return the reason, or empty
     */
    Optional<String> reason();
}
