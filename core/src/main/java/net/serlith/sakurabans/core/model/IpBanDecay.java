package net.serlith.sakurabans.core.model;

import java.time.Duration;
import java.time.Instant;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import org.jspecify.annotations.Nullable;

public final class IpBanDecay {

    private IpBanDecay() {
    }

    public static boolean decayed(final Punishment punishment, final @Nullable Duration decay,
            final Instant now) {
        return decay != null && punishment.type() == PunishmentType.BAN
                && punishment.victim() instanceof CompositeVictim
                && !punishment.createdAt().plus(decay).isAfter(now);
    }

}
