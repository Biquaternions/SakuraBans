package net.serlith.sakurabans.core.punish;

import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Punishment;

public interface Enforcer {

    CompletableFuture<Void> created(Punishment punishment);

    CompletableFuture<Void> ended(Punishment punishment);

    CompletableFuture<Void> changed(Punishment previous, Punishment current);

}
