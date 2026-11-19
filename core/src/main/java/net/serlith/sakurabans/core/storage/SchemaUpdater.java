package net.serlith.sakurabans.core.storage;

import org.jooq.DSLContext;

final class SchemaUpdater {

    void update(final DSLContext dsl, final int from, final int to) {
        for (int version = from; version < to; version++) {
            switch (version) {
                default -> throw new StorageException(
                        "No update from database schema version " + version + " exists");
            }
        }
    }
}
