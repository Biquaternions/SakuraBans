package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.player.AltEntry;
import org.jspecify.annotations.Nullable;

public record SakuraAltEntry(PlayerProfileView account, List<InetAddress> sharedAddresses,
        String source, @Nullable Double scoreOrNull, Set<PunishmentType> activePunishments)
        implements AltEntry {

    public static final String OWN_SOURCE = "sakurabans";

    public SakuraAltEntry {
        Preconditions.checkNotNull(account, "account");
        Preconditions.checkNotNull(source, "source");
        sharedAddresses = List.copyOf(sharedAddresses);
        activePunishments = Set.copyOf(activePunishments);
    }

    @Override
    public OptionalDouble score() {
        return this.scoreOrNull == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(this.scoreOrNull);
    }

    public SakuraAltEntry punishments(final Set<PunishmentType> types) {
        return new SakuraAltEntry(this.account, this.sharedAddresses, this.source,
                this.scoreOrNull, types);
    }
}
