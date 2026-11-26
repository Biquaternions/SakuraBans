package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.core.util.Scopes;
import org.jspecify.annotations.Nullable;

public final class AuditValues {

    private AuditValues() {
    }

    public static String victim(final Victim victim) {
        return switch (victim) {
            case PlayerVictim player -> player.uuid().toString();
            case AddressVictim address -> address.address().getHostAddress();
            case CompositeVictim composite -> composite.uuid() + "@"
                    + composite.address().getHostAddress();
            case AddressRangeVictim range -> range.network().getHostAddress() + "/"
                    + range.prefixLength();
            case ErasedAddressVictim erased -> erased.uuid().map(uuid -> uuid + "@").orElse("")
                    + "erased:" + erased.hash();
        };
    }

    public static String expiry(final @Nullable Instant expiry) {
        return expiry == null ? "permanent" : expiry.toString();
    }

    public static String summary(final Punishment punishment) {
        return punishment.type() + (punishment.shadow() ? " shadow" : "")
                + " " + victim(punishment.victim()) + " "
                + Scopes.format(punishment.scope()) + " "
                + expiry(punishment.expiresAt().orElse(null)) + " " + punishment.reason();
    }

}
