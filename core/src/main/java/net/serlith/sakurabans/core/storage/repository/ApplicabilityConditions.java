package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPlayerAddresses;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

final class ApplicabilityConditions {

    private ApplicabilityConditions() {
    }

    static Select<Record1<Long>> victimIds(final Applicability applicability) {
        final AddressStrictness strictness = applicability.strictness();
        if (strictness == AddressStrictness.STERN || strictness == AddressStrictness.STRICT) {
            throw new UnsupportedOperationException(strictness + " arrives with S28");
        }
        final SbVictims victims = SakuraTables.VICTIMS;
        final List<Select<Record1<Long>>> branches = new ArrayList<>();
        final UUID uuid = applicability.uuid().orElse(null);
        final InetAddress address = applicability.address().orElse(null);
        if (uuid != null) {
            // The player itself, and composites that name it.
            branches.add(DSL.select(victims.ID).from(victims)
                    .where(victims.UUID.eq(uuid))
                    .and(victims.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE)));
        }
        if (address != null) {
            // The current address, and composites that name it.
            branches.add(DSL.select(victims.ID).from(victims)
                    .where(victims.ADDRESS.eq(InetAddresses.toBytes(address)))
                    .and(victims.KIND.in(VictimKind.ADDRESS, VictimKind.COMPOSITE)));
        }
        if (uuid != null && strictness == AddressStrictness.NORMAL) {
            // Every address the player has used.
            final SbPlayerAddresses known = SakuraTables.PLAYER_ADDRESSES;
            branches.add(DSL.select(victims.ID).from(victims)
                    .join(known).on(victims.ADDRESS.eq(
                            known.ADDRESS.coerce(SQLDataType.VARBINARY(16))))
                    .where(known.UUID.eq(uuid))
                    .and(victims.KIND.in(VictimKind.ADDRESS, VictimKind.COMPOSITE)));
        }
        Preconditions.checkArgument(!branches.isEmpty(),
                "an applicability needs a uuid or an address");
        Select<Record1<Long>> union = branches.getFirst();
        for (final Select<Record1<Long>> branch : branches.subList(1, branches.size())) {
            union = union.union(branch);
        }
        return union;
    }
}
