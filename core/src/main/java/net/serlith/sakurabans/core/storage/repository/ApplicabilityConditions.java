package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.core.storage.AddressHasher;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPlayerAddresses;
import net.serlith.sakurabans.core.storage.dbo.tables.SbVictims;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

final class ApplicabilityConditions {

    private ApplicabilityConditions() {
    }

    static Select<Record1<Long>> victimIds(final Applicability applicability,
            final AltPolicy policy, final AddressHasher hasher, final Instant now) {
        return union(branches(applicability, policy, hasher, now, false));
    }

    /**
     * Whether a punishment applies (its victim among {@link #victimIds}), with S69's
     * {@code enforcement.ip-ban-decay}: a composite ban older than the decay no longer
     * reaches through its address, only through its player (by UUID, and the alts the
     * strictness gives a player ban). Plain address bans, ranges and mutes are untouched.
     * Decided by time in the query, so nothing is rewritten and the setting is reversible.
     */
    static Condition applies(final Field<Long> victimId, final Field<Instant> createdAt,
            final Field<PunishmentType> type, final Applicability applicability,
            final AltPolicy policy, final AddressHasher hasher, final Instant now) {
        final Condition reached = victimId.in(victimIds(applicability, policy, hasher, now));
        final Duration decay = policy.ipBanDecay().orElse(null);
        if (decay == null) {
            return reached;
        }
        final List<Select<Record1<Long>>> decayable =
                branches(applicability, policy, hasher, now, true);
        if (decayable.isEmpty()) {
            return reached;
        }
        return reached.and(DSL.not(type.eq(PunishmentType.BAN)
                .and(createdAt.le(now.minus(decay)))
                .and(victimId.in(union(decayable)))));
    }

    private static Select<Record1<Long>> union(final List<Select<Record1<Long>>> branches) {
        Select<Record1<Long>> union = branches.getFirst();
        for (final Select<Record1<Long>> branch : branches.subList(1, branches.size())) {
            union = union.union(branch);
        }
        return union;
    }

    /**
     * {@code decayable}: only the branches that reach a composite through an address and not
     * through its own player, which the decay switches off.
     */
    private static List<Select<Record1<Long>>> branches(final Applicability applicability,
            final AltPolicy policy, final AddressHasher hasher, final Instant now,
            final boolean decayable) {
        final AddressStrictness strictness = applicability.strictness();
        final SbVictims victims = SakuraTables.VICTIMS;
        final UUID uuid = applicability.uuid().orElse(null);
        final InetAddress address = applicability.address().orElse(null);
        Preconditions.checkArgument(uuid != null || address != null,
                "an applicability needs a uuid or an address");
        final Rules rules = new Rules(policy, now);
        final List<Select<Record1<Long>>> branches = new ArrayList<>();
        final List<VictimKind> byAddress = decayable
                ? List.of(VictimKind.COMPOSITE)
                : List.of(VictimKind.ADDRESS, VictimKind.COMPOSITE);
        final Condition notOwn = decayable && uuid != null
                ? victims.UUID.ne(uuid)
                : DSL.noCondition();
        if (uuid != null) {
            if (!decayable) {
                // The player itself, and composites that name it: always, allow-listed or not.
                branches.add(DSL.select(victims.ID).from(victims)
                        .where(victims.UUID.eq(uuid))
                        .and(victims.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE)));
            }
            if (policy.allowedAccounts().contains(uuid)) {
                return branches;
            }
        }
        final boolean addressCounts = address != null
                && !policy.allowedAddresses().contains(address);
        if (addressCounts) {
            branches.add(DSL.select(victims.ID).from(victims)
                    .where(victims.ADDRESS_HASH.eq(hasher.hash(address)))
                    .and(victims.KIND.in(byAddress))
                    .and(notOwn));
        }
        if (strictness != AddressStrictness.LENIENT && uuid != null) {
            final SbPlayerAddresses known = SakuraTables.PLAYER_ADDRESSES;
            branches.add(onAddresses(DSL.select(known.ADDRESS_HASH).from(known)
                    .where(known.UUID.eq(uuid))
                    .and(rules.counts(known)), byAddress, notOwn));
        }
        if (strictness == AddressStrictness.STERN || strictness == AddressStrictness.STRICT) {
            final Select<Record1<UUID>> linked = rules.linked(uuid, addressCounts ? address : null);
            final SbPlayerAddresses theirs = SakuraTables.PLAYER_ADDRESSES.as("sb_theirs");
            branches.add(onAddresses(DSL.select(theirs.ADDRESS_HASH).from(theirs)
                    .where(theirs.UUID.in(linked))
                    .and(rules.counts(theirs)), byAddress, notOwn));
            if (strictness == AddressStrictness.STRICT && !decayable) {
                // A player ban covers the player's alts.
                branches.add(DSL.select(victims.ID).from(victims)
                        .where(victims.UUID.in(rules.linked(uuid, addressCounts ? address : null)))
                        .and(victims.KIND.in(VictimKind.PLAYER, VictimKind.COMPOSITE)));
            }
        }
        return branches;
    }

    private static Select<Record1<Long>> onAddresses(final Select<Record1<byte[]>> hashes,
            final List<VictimKind> kinds, final Condition extra) {
        final SbVictims victims = SakuraTables.VICTIMS;
        return DSL.select(victims.ID).from(victims)
                .where(victims.ADDRESS_HASH.in(hashes))
                .and(victims.KIND.in(kinds))
                .and(extra);
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
