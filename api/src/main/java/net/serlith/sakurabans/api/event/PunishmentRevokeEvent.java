package net.serlith.sakurabans.api.event;

import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.RevocationRequest;

/**
 * Before one punishment is revoked. Fired once per punishment a request would end; cancelling
 * keeps that punishment active (when all are kept, the request fails with {@code CANCELLED}).
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public interface PunishmentRevokeEvent extends Cancellable {

    /**
     * The punishment that is about to end.
     *
     * @return the punishment, still active
     */
    Punishment punishment();

    /**
     * The request that ends it.
     *
     * @return the request
     */
    RevocationRequest request();
}
