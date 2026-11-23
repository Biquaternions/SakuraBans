package net.serlith.sakurabans.core.punish;

import java.net.InetAddress;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import org.jspecify.annotations.Nullable;

public final class Applicabilities {

    private Applicabilities() {
    }

    public static Applicability of(final UUID uuid, final @Nullable InetAddress address,
            final AddressStrictness strictness) {
        return Applicability.of(uuid, address, strictness);
    }
}
