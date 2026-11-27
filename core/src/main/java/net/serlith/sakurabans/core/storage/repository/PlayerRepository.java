package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressRecord;
import net.serlith.sakurabans.api.model.NameRecord;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.SakuraAddressRecord;
import net.serlith.sakurabans.core.model.SakuraNameRecord;
import net.serlith.sakurabans.core.model.SakuraPlayerProfileView;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.AddressHasher;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbPlayersRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbPlayerAddresses;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

public final class PlayerRepository {

    private final StorageService storage;
    private final AddressHasher hasher;

    public PlayerRepository(final StorageService storage, final AddressHasher hasher) {
        this.storage = storage;
        this.hasher = hasher;
    }

    public CompletableFuture<Void> recordConnection(final UUID uuid, final String name,
            final Instant at, final @Nullable InetAddress address, final @Nullable String server) {
        final String lower = name.toLowerCase(Locale.ROOT);
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final Map<Field<?>, Object> update = new HashMap<>();
            update.put(SakuraTables.PLAYERS.LAST_NAME, name);
            update.put(SakuraTables.PLAYERS.LAST_NAME_LOWER, lower);
            update.put(SakuraTables.PLAYERS.LAST_SEEN, at);
            if (address != null) {
                update.put(SakuraTables.PLAYERS.LAST_ADDRESS, address);
            }
            dsl.insertInto(SakuraTables.PLAYERS)
                    .set(SakuraTables.PLAYERS.UUID, uuid)
                    .set(SakuraTables.PLAYERS.LAST_NAME, name)
                    .set(SakuraTables.PLAYERS.LAST_NAME_LOWER, lower)
                    .set(SakuraTables.PLAYERS.FIRST_SEEN, at)
                    .set(SakuraTables.PLAYERS.LAST_SEEN, at)
                    .set(SakuraTables.PLAYERS.LAST_ADDRESS, address)
                    .onDuplicateKeyUpdate()
                    .set(update)
                    .execute();
            dsl.insertInto(SakuraTables.PLAYER_NAMES)
                    .set(SakuraTables.PLAYER_NAMES.UUID, uuid)
                    .set(SakuraTables.PLAYER_NAMES.NAME, name)
                    .set(SakuraTables.PLAYER_NAMES.NAME_LOWER, lower)
                    .set(SakuraTables.PLAYER_NAMES.FIRST_SEEN, at)
                    .set(SakuraTables.PLAYER_NAMES.LAST_SEEN, at)
                    .onDuplicateKeyUpdate()
                    .set(SakuraTables.PLAYER_NAMES.NAME, name)
                    .set(SakuraTables.PLAYER_NAMES.LAST_SEEN, at)
                    .execute();
            if (address != null) {
                dsl.insertInto(SakuraTables.PLAYER_ADDRESSES)
                        .set(SakuraTables.PLAYER_ADDRESSES.UUID, uuid)
                        .set(SakuraTables.PLAYER_ADDRESSES.ADDRESS, address)
                        .set(SakuraTables.PLAYER_ADDRESSES.ADDRESS_HASH, this.hasher.hash(address))
                        .set(SakuraTables.PLAYER_ADDRESSES.FIRST_SEEN, at)
                        .set(SakuraTables.PLAYER_ADDRESSES.LAST_SEEN, at)
                        .set(SakuraTables.PLAYER_ADDRESSES.LAST_SERVER, server)
                        .onDuplicateKeyUpdate()
                        .set(SakuraTables.PLAYER_ADDRESSES.LAST_SEEN, at)
                        .set(SakuraTables.PLAYER_ADDRESSES.LAST_SERVER, server)
                        .execute();
            }
            return null;
        });
    }

    public CompletableFuture<Optional<PlayerProfileView>> profile(final UUID uuid) {
        return this.storage.read(dsl -> dsl.selectFrom(SakuraTables.PLAYERS)
                .where(SakuraTables.PLAYERS.UUID.eq(uuid))
                .fetchOptional()
                .map(PlayerRepository::toProfile));
    }

    public CompletableFuture<Optional<PlayerProfileView>> profile(final String name) {
        return this.storage.read(dsl -> dsl.selectFrom(SakuraTables.PLAYERS)
                .where(SakuraTables.PLAYERS.LAST_NAME_LOWER.eq(name.toLowerCase(Locale.ROOT)))
                .orderBy(SakuraTables.PLAYERS.LAST_SEEN.desc())
                .limit(1)
                .fetchOptional()
                .map(PlayerRepository::toProfile));
    }

    public CompletableFuture<Optional<UUID>> lastUuid(final String name) {
        return this.storage.read(dsl -> dsl.select(SakuraTables.PLAYER_NAMES.UUID)
                .from(SakuraTables.PLAYER_NAMES)
                .where(SakuraTables.PLAYER_NAMES.NAME_LOWER.eq(name.toLowerCase(Locale.ROOT)))
                .orderBy(SakuraTables.PLAYER_NAMES.LAST_SEEN.desc())
                .limit(1)
                .fetchOptional(SakuraTables.PLAYER_NAMES.UUID));
    }

    public CompletableFuture<List<NameRecord>> names(final UUID uuid) {
        return this.storage.read(dsl -> dsl.selectFrom(SakuraTables.PLAYER_NAMES)
                .where(SakuraTables.PLAYER_NAMES.UUID.eq(uuid))
                .orderBy(SakuraTables.PLAYER_NAMES.LAST_SEEN.desc())
                .fetch(row -> (NameRecord) new SakuraNameRecord(row.name(), row.firstSeen(),
                        row.lastSeen())));
    }

    public CompletableFuture<List<AddressRecord>> addresses(final UUID uuid) {
        return this.storage.read(dsl -> dsl.selectFrom(SakuraTables.PLAYER_ADDRESSES)
                .where(SakuraTables.PLAYER_ADDRESSES.UUID.eq(uuid))
                .orderBy(SakuraTables.PLAYER_ADDRESSES.LAST_SEEN.desc())
                .fetch(row -> (AddressRecord) new SakuraAddressRecord(row.address(),
                        row.firstSeen(), row.lastSeen(), row.lastServer())));
    }

    public CompletableFuture<List<PlayerProfileView>> accountsAt(final InetAddress address) {
        return this.storage.read(dsl -> dsl.select(SakuraTables.PLAYERS.fields())
                .from(SakuraTables.PLAYERS)
                .join(SakuraTables.PLAYER_ADDRESSES)
                .on(SakuraTables.PLAYER_ADDRESSES.UUID.eq(SakuraTables.PLAYERS.UUID))
                .where(SakuraTables.PLAYER_ADDRESSES.ADDRESS.eq(address))
                .orderBy(SakuraTables.PLAYER_ADDRESSES.LAST_SEEN.desc())
                .fetch(row -> toProfile(row.into(SakuraTables.PLAYERS))));
    }

    public CompletableFuture<Integer> forget(final UUID uuid, final @Nullable InetAddress address,
            final Actor actor, final Instant at) {
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final SbPlayerAddresses known = SakuraTables.PLAYER_ADDRESSES;
            final Condition which = address == null
                    ? known.UUID.eq(uuid)
                    : known.UUID.eq(uuid).and(known.ADDRESS.eq(address));
            final List<InetAddress> removed = dsl.select(known.ADDRESS).from(known).where(which)
                    .fetch(known.ADDRESS);
            if (removed.isEmpty()) {
                return 0;
            }
            dsl.deleteFrom(known).where(which).execute();
            final Condition last = address == null
                    ? SakuraTables.PLAYERS.LAST_ADDRESS.isNotNull()
                    : SakuraTables.PLAYERS.LAST_ADDRESS.eq(address);
            dsl.update(SakuraTables.PLAYERS)
                    .setNull(SakuraTables.PLAYERS.LAST_ADDRESS)
                    .where(SakuraTables.PLAYERS.UUID.eq(uuid))
                    .and(last)
                    .execute();
            for (final InetAddress forgotten : removed) {
                AuditRepository.append(dsl, at, actor, AuditAction.FORGET_ADDRESS,
                        SubjectType.PLAYER, uuid.toString(), forgotten.getHostAddress(), null);
            }
            return removed.size();
        });
    }

    public record Link(PlayerProfileView account, List<InetAddress> shared) {
    }

    public CompletableFuture<List<Link>> links(final UUID uuid, final AltPolicy policy,
            final Instant now) {
        if (policy.allowedAccounts().contains(uuid)) {
            return CompletableFuture.completedFuture(List.of());
        }
        final SbPlayerAddresses mine = SakuraTables.PLAYER_ADDRESSES.as("sb_mine");
        final SbPlayerAddresses others = SakuraTables.PLAYER_ADDRESSES.as("sb_others");
        final List<Condition> conditions = new ArrayList<>(List.of(mine.UUID.eq(uuid),
                others.UUID.ne(uuid), ApplicabilityConditions.counts(mine, policy, now),
                ApplicabilityConditions.counts(others, policy, now)));
        if (!policy.allowedAccounts().isEmpty()) {
            conditions.add(others.UUID.notIn(policy.allowedAccounts()));
        }
        return this.storage.read(dsl -> {
            final Map<UUID, PlayerProfileView> accounts = new LinkedHashMap<>();
            final Map<UUID, List<InetAddress>> shared = new HashMap<>();
            dsl.select(SakuraTables.PLAYERS.fields()).select(others.ADDRESS)
                    .from(mine)
                    .join(others).on(others.ADDRESS.eq(mine.ADDRESS))
                    .join(SakuraTables.PLAYERS).on(SakuraTables.PLAYERS.UUID.eq(others.UUID))
                    .where(DSL.and(conditions))
                    .orderBy(SakuraTables.PLAYERS.LAST_SEEN.desc(), others.ADDRESS)
                    .fetch()
                    .forEach(row -> {
                        final PlayerProfileView account =
                                toProfile(row.into(SakuraTables.PLAYERS));
                        accounts.putIfAbsent(account.uuid(), account);
                        shared.computeIfAbsent(account.uuid(), ignored -> new ArrayList<>())
                                .add(row.get(others.ADDRESS));
                    });
            final List<Link> links = new ArrayList<>();
            accounts.forEach((id, account) ->
                    links.add(new Link(account, List.copyOf(shared.get(id)))));
            return List.copyOf(links);
        });
    }

    private static PlayerProfileView toProfile(final SbPlayersRecord row) {
        return new SakuraPlayerProfileView(row.uuid(), row.lastName(), row.firstSeen(),
                row.lastSeen(), row.lastAddress());
    }
}
