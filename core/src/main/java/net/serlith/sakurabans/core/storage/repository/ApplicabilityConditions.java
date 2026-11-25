package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPlayerAddresses;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
import net.serlith.sakurabans.core.util.AddressHashes;
import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

final class ApplicabilityConditions {

    private ApplicabilityConditions() {
    }

    static Select<Record1<Long>> victimIds(final Applicability applicability,
            final AltPolicy policy, final Instant now) {
        final AddressStrictness strictness = applicability.strictness();
        final SbVictims victims = SakuraTables.VICTIMS;
        final UUID uuid = applicability.uuid().orElse(null);
        final InetAddress address = applicability.address().orElse(null);
        Preconditions.checkArgument(uuid != null || address != null,
                "an applicability needs a uuid or an address");
        final Rules rules = new Rules(policy, now);
        final List<Select<Record1<Long>>> branches = new ArrayList<>();
        if (uuid != null) {
            // The player itself, and composites that name it: always, allow-listed or not.
            branches.add(DSL.select(victims.ID).from(victims)
                    .where(victims.UUID.eq(uuid))
                    .and(victims.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE)));
            if (policy.allowedAccounts().contains(uuid)) {
                return branches.getFirst();
            }
        }
        final boolean addressCounts = address != null
                && !policy.allowedAddresses().contains(address);
        if (addressCounts) {
            branches.add(DSL.select(victims.ID).from(victims)
                    .where(victims.ADDRESS_HASH.eq(AddressHashes.of(address)))
                    .and(victims.KIND.in(VictimKind.ADDRESS, VictimKind.COMPOSITE)));
        }
        if (strictness != AddressStrictness.LENIENT && uuid != null) {
            final SbPlayerAddresses known = SakuraTables.PLAYER_ADDRESSES;
            branches.add(onAddresses(DSL.select(known.ADDRESS_HASH).from(known)
                    .where(known.UUID.eq(uuid))
                    .and(rules.counts(known))));
        }
        if (strictness == AddressStrictness.STERN || strictness == AddressStrictness.STRICT) {
            final Select<Record1<UUID>> linked = rules.linked(uuid, addressCounts ? address : null);
            final SbPlayerAddresses theirs = SakuraTables.PLAYER_ADDRESSES.as("sb_theirs");
            branches.add(onAddresses(DSL.select(theirs.ADDRESS_HASH).from(theirs)
                    .where(theirs.UUID.in(linked))
                    .and(rules.counts(theirs))));
            if (strictness == AddressStrictness.STRICT) {
                // A player ban covers the player's alts.
                branches.add(DSL.select(victims.ID).from(victims)
                        .where(victims.UUID.in(rules.linked(uuid, addressCounts ? address : null)))
                        .and(victims.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE)));
            }
        }
        Select<Record1<Long>> union = branches.getFirst();
        for (final Select<Record1<Long>> branch : branches.subList(1, branches.size())) {
            union = union.union(branch);
        }
        return union;
    }

    private static Select<Record1<Long>> onAddresses(final Select<Record1<byte[]>> hashes) {
        final SbVictims victims = SakuraTables.VICTIMS;
        return DSL.select(victims.ID).from(victims)
                .where(victims.ADDRESS_HASH.in(hashes))
                .and(victims.KIND.in(VictimKind.ADDRESS, VictimKind.COMPOSITE));
    }

    static Condition counts(final SbPlayerAddresses table, final AltPolicy policy,
            final Instant now) {
        final List<Condition> conditions = new ArrayList<>();
        policy.recordExpiration().ifPresent(expiration ->
                conditions.add(table.LAST_SEEN.ge(now.minus(expiration))));
        final Set<InetAddress> allowed = policy.allowedAddresses();
        if (!allowed.isEmpty()) {
            conditions.add(table.ADDRESS.notIn(allowed));
        }
        return DSL.and(conditions);
    }

    private record Rules(AltPolicy policy, Instant now) {

        Condition counts(final SbPlayerAddresses table) {
            return ApplicabilityConditions.counts(table, this.policy, this.now);
        }

        Select<Record1<UUID>> linked(final @Nullable UUID uuid,
                final @Nullable InetAddress address) {
            final SbPlayerAddresses mine = SakuraTables.PLAYER_ADDRESSES.as("sb_mine");
            final SbPlayerAddresses others = SakuraTables.PLAYER_ADDRESSES.as("sb_others");
            final List<Condition> sources = new ArrayList<>();
            if (uuid != null) {
                sources.add(mine.UUID.eq(uuid));
            }
            if (address != null) {
                sources.add(mine.ADDRESS.eq(address));
            }
            final List<Condition> conditions = new ArrayList<>();
            conditions.add(DSL.or(sources));
            conditions.add(this.counts(mine));
            conditions.add(this.counts(others));
            final Set<UUID> allowed = this.policy.allowedAccounts();
            if (!allowed.isEmpty()) {
                // Allow-listed accounts are never someone's alt.
                conditions.add(others.UUID.notIn(allowed));
            }
            return DSL.selectDistinct(others.UUID).from(mine)
                    .join(others).on(others.ADDRESS.eq(mine.ADDRESS))
                    .where(DSL.and(conditions));
        }
    }
}
