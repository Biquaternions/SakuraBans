package net.serlith.sakurabans.core.punish;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Punishment;

public interface Enforcer {

    CompletableFuture<Void> created(Punishment punishment);

    CompletableFuture<Void> ended(Punishment punishment);

    CompletableFuture<Void> kickedUnrecorded(UUID player, String reason);

    CompletableFuture<Void> changed(Punishment punishment, boolean rescoped);

}
