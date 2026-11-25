package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.util.HexFormat;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.api.model.ErasedAddressVictim;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
import net.serlith.sakurabans.core.util.AddressHashes;
import net.serlith.sakurabans.core.util.InetAddresses;
import net.serlith.sakurabans.core.util.Uuids;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

record StoredVictim(VictimKind kind, UUID uuid, byte @Nullable [] address, short prefixLength,
        byte[] addressHash) {

    private static final byte[] NO_ADDRESS = new byte[0];
    private static final byte[] NO_HASH = new byte[AddressHashes.LENGTH];

    static StoredVictim of(final Victim victim) {
        return switch (victim) {
            case PlayerVictim player -> new StoredVictim(VictimKind.PLAYER, player.uuid(),
                    NO_ADDRESS, (short) 0, NO_HASH);
            case AddressVictim address -> exact(VictimKind.ADDRESS, Uuids.ZERO, address.address());
            case CompositeVictim composite -> exact(VictimKind.COMPOSITE, composite.uuid(),
                    composite.address());
            case AddressRangeVictim range -> new StoredVictim(VictimKind.ADDRESS_RANGE,
                    Uuids.ZERO, InetAddresses.toBytes(range.network()),
                    (short) range.prefixLength(), AddressHashes.of(range.network()));
            case ErasedAddressVictim erased -> new StoredVictim(
                    erased.uuid().isPresent() ? VictimKind.COMPOSITE : VictimKind.ADDRESS,
                    erased.uuid().orElse(Uuids.ZERO), null, (short) 0,
                    HexFormat.of().parseHex(erased.hash()));
        };
    }

    Victim victim() {
        if (this.address == null) {
            return ErasedAddressVictim.of(HexFormat.of().formatHex(this.addressHash),
                    this.kind == VictimKind.COMPOSITE ? this.uuid : null);
        }
        return switch (this.kind) {
            case PLAYER -> PlayerVictim.of(this.uuid);
            case ADDRESS -> AddressVictim.of(InetAddresses.fromBytes(this.address));
            case COMPOSITE -> CompositeVictim.of(this.uuid, InetAddresses.fromBytes(this.address));
            case ADDRESS_RANGE -> AddressRangeVictim.of(InetAddresses.fromBytes(this.address),
                    this.prefixLength);
        };
    }

    Condition matches(final SbVictims victims) {
        return victims.KIND.eq(this.kind)
                .and(victims.UUID.eq(this.uuid))
                .and(victims.ADDRESS_HASH.eq(this.addressHash))
                .and(this.kind == VictimKind.ADDRESS_RANGE
                        ? victims.PREFIX_LENGTH.eq(this.prefixLength)
                        : DSL.noCondition());
    }

    private static StoredVictim exact(final VictimKind kind, final UUID uuid,
            final InetAddress address) {
        final byte[] bytes = InetAddresses.toBytes(address);
        return new StoredVictim(kind, uuid, bytes, (short) (bytes.length * 8),
                AddressHashes.of(address));
    }
}
