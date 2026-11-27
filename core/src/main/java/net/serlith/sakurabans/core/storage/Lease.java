package net.serlith.sakurabans.core.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.DSLContext;

public final class Lease {

    private static final String PREFIX = "lease.";

    private final StorageService storage;
    private final UUID instanceId;

    public Lease(final StorageService storage, final UUID instanceId) {
        this.storage = storage;
        this.instanceId = instanceId;
    }

    public CompletableFuture<Boolean> tryAcquire(final String name, final Duration ttl) {
        return this.storage.write(configuration -> {
            final DSLContext dsl = configuration.dsl();
            final Instant now = DatabaseClock.now(dsl);
            final String key = PREFIX + name;
            final String mine = this.instanceId + ":" + now.plus(ttl).toEpochMilli();
            final String current = dsl.select(SakuraTables.META.V).from(SakuraTables.META)
                    .where(SakuraTables.META.K.eq(key))
                    .fetchOne(SakuraTables.META.V);
            if (current == null) {
                return dsl.insertInto(SakuraTables.META)
                        .set(SakuraTables.META.K, key)
                        .set(SakuraTables.META.V, mine)
                        .onConflictDoNothing()
                        .execute() == 1;
            }
            if (!ownedBy(current, this.instanceId) && !expired(current, now)) {
                return false;
            }
            return dsl.update(SakuraTables.META)
                    .set(SakuraTables.META.V, mine)
                    .where(SakuraTables.META.K.eq(key))
                    .and(SakuraTables.META.V.eq(current))
                    .execute() == 1;
        });
    }

    public CompletableFuture<Void> release(final String name) {
        return this.storage.write(configuration -> {
            configuration.dsl().deleteFrom(SakuraTables.META)
                    .where(SakuraTables.META.K.eq(PREFIX + name))
                    .and(SakuraTables.META.V.like(this.instanceId + ":%"))
                    .execute();
            return null; // void
        });
    }

    private static boolean ownedBy(final String value, final UUID instance) {
        return value.startsWith(instance + ":");
    }

    private static boolean expired(final String value, final Instant now) {
        final int separator = value.lastIndexOf(':');
        try {
            return Instant.ofEpochMilli(Long.parseLong(value.substring(separator + 1)))
                    .isBefore(now);
        } catch (final NumberFormatException e) {
            // A malformed row is treated as free.
            return true;
        }
    }
}
