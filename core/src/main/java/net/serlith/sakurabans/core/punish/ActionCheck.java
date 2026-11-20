package net.serlith.sakurabans.core.punish;

import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.auth.Decision;
import net.serlith.sakurabans.api.model.Actor;

@FunctionalInterface
public interface ActionCheck {

    ActionCheck ALLOW_ALL = (actor, action) -> CompletableFuture.completedFuture(Decision.allow());

    CompletableFuture<Decision> check(Actor actor, Action action);
}
