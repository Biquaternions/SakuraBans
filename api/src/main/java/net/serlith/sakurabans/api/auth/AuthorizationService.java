package net.serlith.sakurabans.api.auth;

import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;

/**
 * Decides whether an actor may perform an action: permissions (from LuckPerms for offline
 * actors), exemption, and every registered
 * {@link net.serlith.sakurabans.api.integration.ActionGuard}. SakuraBans makes the same decision
 * before it performs an action, so UIs ask here instead of guessing from permission names.
 * Implemented by SakuraBans; don't implement it.
 */
public interface AuthorizationService {

    /**
     * Decides an action.
     *
     * @param actor who wants to act
     * @param action what they want to do
     * @return the decision, completed on a worker thread
     */
    CompletableFuture<Decision> check(Actor actor, Action action);
}
