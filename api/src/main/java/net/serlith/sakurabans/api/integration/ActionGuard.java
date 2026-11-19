package net.serlith.sakurabans.api.integration;

import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.model.Actor;

/**
 * Decides, together with permissions, whether an actor may perform an action, for example to
 * block staff who haven't passed two-factor authentication. Consulted before every action and
 * by {@link net.serlith.sakurabans.api.auth.AuthorizationService}. A denial makes the action fail
 * with {@code FORBIDDEN}.
 *
 * <p>Fails closed: if the future fails or doesn't complete within the configured timeout, the
 * action is denied.
 */
public interface ActionGuard {

    /**
     * A name for logs.
     *
     * @return the name, for example your plugin's name
     */
    String name();

    /**
     * Decides an action. Return {@link Decision#allow()} for actions you don't care about.
     *
     * @param actor who wants to act
     * @param action what they want to do
     * @return the decision
     */
    CompletableFuture<Decision> check(Actor actor, Action action);
}
