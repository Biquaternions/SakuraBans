package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.tables.SbInstances;

public final class InstanceRepository {

    private final StorageService storage;

    public InstanceRepository(final StorageService storage) {
        this.storage = storage;
    }

    /**
     * One server of the network. {@code fileValues} is JSON and may hold secrets: the
     * database is trusted, but nothing reads them out except publishing.
     */
    public record Instance(String id, String serverName, List<String> categories,
            String platform, String pluginVersion, Instant startedAt, Instant lastSeenAt,
            String fileValues) {

        public Instance {
            categories = List.copyOf(categories);
        }

        @Override
        public String toString() {
            return "Instance[id=" + this.id + ", serverName=" + this.serverName + "]";
        }
    }

    /** Writes this server's row, and removes the rows nobody has seen since the cutoff. */
    public CompletableFuture<Void> write(final Instance instance, final Instant staleBefore) {
        final SbInstances table = SakuraTables.INSTANCES;
        return this.storage.writeRetryingConflicts(configuration -> {
            final var dsl = configuration.dsl();
            final int updated = dsl.update(table)
                    .set(table.SERVER_NAME, instance.serverName())
                    .set(table.CATEGORIES, String.join(",", instance.categories()))
                    .set(table.PLATFORM, instance.platform())
                    .set(table.PLUGIN_VERSION, instance.pluginVersion())
                    .set(table.STARTED_AT, instance.startedAt())
                    .set(table.LAST_SEEN_AT, instance.lastSeenAt())
                    .set(table.FILE_VALUES, instance.fileValues())
                    .where(table.ID.eq(instance.id()))
                    .execute();
            if (updated == 0) {
                dsl.insertInto(table)
                        .set(table.ID, instance.id())
                        .set(table.SERVER_NAME, instance.serverName())
                        .set(table.CATEGORIES, String.join(",", instance.categories()))
                        .set(table.PLATFORM, instance.platform())
                        .set(table.PLUGIN_VERSION, instance.pluginVersion())
                        .set(table.STARTED_AT, instance.startedAt())
                        .set(table.LAST_SEEN_AT, instance.lastSeenAt())
                        .set(table.FILE_VALUES, instance.fileValues())
                        .execute();
            }
            dsl.deleteFrom(table).where(table.LAST_SEEN_AT.lt(staleBefore)).execute();
            return null;
        });
    }

    /** Every known server, the most recently seen first. */
    public CompletableFuture<List<Instance>> all() {
        final SbInstances table = SakuraTables.INSTANCES;
        return this.storage.read(dsl -> dsl.selectFrom(table)
                .orderBy(table.LAST_SEEN_AT.desc(), table.ID)
                .fetch(row -> new Instance(row.id(), row.serverName(),
                        row.categories().isEmpty() ? List.of()
                                : List.of(row.categories().split(",")),
                        row.platform(), row.pluginVersion(), row.startedAt(),
                        row.lastSeenAt(), row.fileValues())));
    }
}
