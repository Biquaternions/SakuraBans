package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.allow.AllowEntry;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.SakuraAllowedAddress;
import net.serlith.sakurabans.core.model.SakuraAllowedPlayer;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jspecify.annotations.Nullable;

public final class AllowListRepository {

    private final StorageService storage;

    public AllowListRepository(final StorageService storage) {
        this.storage = storage;
    }

    public CompletableFuture<List<AllowEntry>> entries() {
        return this.storage.read(dsl -> {
            final List<AllowEntry> entries = new ArrayList<>();
            dsl.selectFrom(SakuraTables.ALLOWED_PLAYERS).fetch().forEach(row -> entries.add(
                    new SakuraAllowedPlayer(row.uuid(), StoredOperator.actor(row.addedByKind(),
                            row.addedByUuid(), row.addedByName(), row.addedBySource()),
                            row.addedAt(), row.note())));
            dsl.selectFrom(SakuraTables.ALLOWED_ADDRESSES).fetch().forEach(row -> entries.add(
                    new SakuraAllowedAddress(row.address(), StoredOperator.actor(
                            row.addedByKind(), row.addedByUuid(), row.addedByName(),
                            row.addedBySource()), row.addedAt(), row.note())));
            entries.sort(Comparator.comparing(AllowEntry::addedAt).reversed());
            return List.copyOf(entries);
        });
    }

    public CompletableFuture<Boolean> add(final UUID uuid, final Actor actor, final Instant at,
            final @Nullable String note) {
        final StoredOperator by = StoredOperator.of(actor.operator());
        return this.storage.writeRetryingConflicts(configuration -> {
            final boolean added = configuration.dsl().insertInto(SakuraTables.ALLOWED_PLAYERS)
                    .set(SakuraTables.ALLOWED_PLAYERS.UUID, uuid)
                    .set(SakuraTables.ALLOWED_PLAYERS.ADDED_BY_KIND, by.kind())
                    .set(SakuraTables.ALLOWED_PLAYERS.ADDED_BY_UUID, by.uuid())
                    .set(SakuraTables.ALLOWED_PLAYERS.ADDED_BY_NAME, by.name())
                    .set(SakuraTables.ALLOWED_PLAYERS.ADDED_BY_SOURCE, actor.source())
                    .set(SakuraTables.ALLOWED_PLAYERS.ADDED_AT, at)
                    .set(SakuraTables.ALLOWED_PLAYERS.NOTE, note)
                    .onConflictDoNothing()
                    .execute() == 1;
            if (added) {
                AuditRepository.append(configuration.dsl(), at, actor, AuditAction.ALLOW,
                        SubjectType.ALLOW_LIST, uuid.toString(), null, note);
            }
            return added;
        });
    }

    public CompletableFuture<Boolean> add(final InetAddress address, final Actor actor,
            final Instant at, final @Nullable String note) {
        final StoredOperator by = StoredOperator.of(actor.operator());
        return this.storage.writeRetryingConflicts(configuration -> {
            final boolean added = configuration.dsl().insertInto(SakuraTables.ALLOWED_ADDRESSES)
                    .set(SakuraTables.ALLOWED_ADDRESSES.ADDRESS, address)
                    .set(SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_KIND, by.kind())
                    .set(SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_UUID, by.uuid())
                    .set(SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_NAME, by.name())
                    .set(SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_SOURCE, actor.source())
                    .set(SakuraTables.ALLOWED_ADDRESSES.ADDED_AT, at)
                    .set(SakuraTables.ALLOWED_ADDRESSES.NOTE, note)
                    .onConflictDoNothing()
                    .execute() == 1;
            if (added) {
                AuditRepository.append(configuration.dsl(), at, actor, AuditAction.ALLOW,
                        SubjectType.ALLOW_LIST, address.getHostAddress(), null, note);
            }
            return added;
        });
    }

    public CompletableFuture<Boolean> remove(final UUID uuid, final Actor actor,
            final Instant at) {
        return this.storage.write(configuration -> {
            final boolean removed = configuration.dsl().deleteFrom(SakuraTables.ALLOWED_PLAYERS)
                    .where(SakuraTables.ALLOWED_PLAYERS.UUID.eq(uuid))
                    .execute() > 0;
            if (removed) {
                AuditRepository.append(configuration.dsl(), at, actor, AuditAction.DISALLOW,
                        SubjectType.ALLOW_LIST, uuid.toString(), null, null);
            }
            return removed;
        });
    }

    public CompletableFuture<Boolean> remove(final InetAddress address, final Actor actor,
            final Instant at) {
        return this.storage.write(configuration -> {
            final boolean removed = configuration.dsl()
                    .deleteFrom(SakuraTables.ALLOWED_ADDRESSES)
                    .where(SakuraTables.ALLOWED_ADDRESSES.ADDRESS.eq(address))
                    .execute() > 0;
            if (removed) {
                AuditRepository.append(configuration.dsl(), at, actor, AuditAction.DISALLOW,
                        SubjectType.ALLOW_LIST, address.getHostAddress(), null, null);
            }
            return removed;
        });
    }

}
