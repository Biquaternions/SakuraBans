package net.serlith.sakurabans.core.punish;

import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.punish.PunishOptions;

public interface PunishLimits {

    PunishLimits NONE = new PunishLimits() {
        @Override
        public CompletableFuture<PunishmentFactory.Prepared> apply(
                final PunishmentFactory.Prepared.Ready ready, final PunishOptions options) {
            return CompletableFuture.completedFuture(ready);
        }

        @Override
        public void issued(final Punishment punishment) {
        }
    };

    CompletableFuture<PunishmentFactory.Prepared> apply(PunishmentFactory.Prepared.Ready ready,
            PunishOptions options);

    void issued(Punishment punishment);

}
