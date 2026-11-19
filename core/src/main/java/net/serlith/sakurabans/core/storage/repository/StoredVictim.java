package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
import net.serlith.sakurabans.core.util.InetAddresses;
import net.serlith.sakurabans.core.util.Uuids;
import org.jooq.Condition;

record StoredVictim(VictimKind kind, UUID uuid, byte[] address, short prefixLength) {

    private static final byte[] NO_ADDRESS = new byte[0];

    static StoredVictim of(final Victim victim) {
        return switch (victim) {
            case PlayerVictim player -> new StoredVictim(VictimKind.PLAYER, player.uuid(),
                    NO_ADDRESS, (short) 0);
            case AddressVictim address -> exact(VictimKind.ADDRESS, Uuids.ZERO, address.address());
            case CompositeVictim composite -> exact(VictimKind.COMPOSITE, composite.uuid(),
                    composite.address());
            case AddressRangeVictim range -> new StoredVictim(VictimKind.ADDRESS_RANGE,
                    Uuids.ZERO, InetAddresses.toBytes(range.network()),
                    (short) range.prefixLength());
        };
    }

    Victim victim() {
        return switch (this.kind) {
            case PLAYER -> PlayerVictim.of(this.uuid);
            case ADDRESS -> AddressVictim.of(this.inetAddress());
            case COMPOSITE -> CompositeVictim.of(this.uuid, this.inetAddress());
            case ADDRESS_RANGE -> AddressRangeVictim.of(this.inetAddress(), this.prefixLength);
        };
    }

    Condition matches(final SbVictims victims) {
        return victims.KIND.eq(this.kind)
                .and(victims.UUID.eq(this.uuid))
                .and(victims.ADDRESS.eq(this.address))
                .and(victims.PREFIX_LENGTH.eq(this.prefixLength));
    }

    private InetAddress inetAddress() {
        return InetAddresses.fromBytes(this.address);
    }

    private static StoredVictim exact(final VictimKind kind, final UUID uuid,
            final InetAddress address) {
        final byte[] bytes = InetAddresses.toBytes(address);
        return new StoredVictim(kind, uuid, bytes, (short) (bytes.length * 8));
    }
}
