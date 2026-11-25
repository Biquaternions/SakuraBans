package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.player.AltEntry;

public record SakuraAltEntry(PlayerProfileView account, List<InetAddress> sharedAddresses,
        Set<PunishmentType> activePunishments) implements AltEntry {

    public SakuraAltEntry {
        Preconditions.checkNotNull(account, "account");
        sharedAddresses = List.copyOf(sharedAddresses);
        activePunishments = Set.copyOf(activePunishments);
    }

    public SakuraAltEntry punishments(final Set<PunishmentType> types) {
        return new SakuraAltEntry(this.account, this.sharedAddresses, types);
    }
}
