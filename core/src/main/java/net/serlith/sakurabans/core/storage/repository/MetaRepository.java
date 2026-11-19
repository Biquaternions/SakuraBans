package net.serlith.sakurabans.core.storage.repository;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;

public final class MetaRepository {

    private final StorageService storage;

    public MetaRepository(final StorageService storage) {
        this.storage = storage;
    }

    public CompletableFuture<Optional<String>> get(final String key) {
        return this.storage.read(dsl -> dsl.select(SakuraTables.META.V)
                .from(SakuraTables.META)
                .where(SakuraTables.META.K.eq(key))
                .fetchOptional(SakuraTables.META.V));
    }

    public CompletableFuture<Void> put(final String key, final String value) {
        return this.storage.write(configuration -> {
            configuration.dsl().insertInto(SakuraTables.META)
                    .set(SakuraTables.META.K, key)
                    .set(SakuraTables.META.V, value)
                    .onDuplicateKeyUpdate()
                    .set(SakuraTables.META.V, value)
                    .execute();
            return null;
        });
    }

    /** Only inserts; {@code false} if the key already had a value (which is kept). */
    public CompletableFuture<Boolean> putIfAbsent(final String key, final String value) {
        return this.storage.write(configuration -> configuration.dsl()
                .insertInto(SakuraTables.META)
                .set(SakuraTables.META.K, key)
                .set(SakuraTables.META.V, value)
                .onConflictDoNothing()
                .execute() == 1);
    }

    public CompletableFuture<Void> remove(final String key) {
        return this.storage.write(configuration -> {
            configuration.dsl().deleteFrom(SakuraTables.META)
                    .where(SakuraTables.META.K.eq(key))
                    .execute();
            return null;
        });
    }
}
