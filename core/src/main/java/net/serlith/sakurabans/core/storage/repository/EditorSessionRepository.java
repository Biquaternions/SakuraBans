package net.serlith.sakurabans.core.storage.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.model.Operator;
import net.serlith.sakurabans.core.storage.StorageService;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.records.SbEditorSessionsRecord;
import net.serlith.sakurabans.core.storage.dbo.tables.SbEditorSessions;

public final class EditorSessionRepository {

    private final StorageService storage;

    public EditorSessionRepository(final StorageService storage) {
        this.storage = storage;
    }

    /**
     * {@code sections}: the ids the session was opened with. {@code secret} (the key) and
     * {@code token} (toward the site) are never logged.
     */
    public record Session(String id, byte[] secret, String token, Set<String> sections,
            Operator creator, Instant createdAt, long lastSequence) {

        public Session {
            sections = Set.copyOf(sections);
        }

        @Override
        public String toString() {
            return "Session[id=" + this.id + ", creator=" + this.creator + ", createdAt="
                    + this.createdAt + ", lastSequence=" + this.lastSequence + "]";
        }
    }

    /** Stores a session and deletes those created before {@code expiredBefore}. */
    public CompletableFuture<Void> create(final String id, final byte[] secret,
            final String token, final Set<String> sections, final Operator creator,
            final Instant at, final Instant expiredBefore) {
        final SbEditorSessions table = SakuraTables.EDITOR_SESSIONS;
        final StoredOperator by = StoredOperator.of(creator);
        return this.storage.write(configuration -> {
            configuration.dsl().deleteFrom(table).where(table.CREATED_AT.lt(expiredBefore))
                    .execute();
            configuration.dsl().insertInto(table)
                    .set(table.ID, id)
                    .set(table.SECRET, secret)
                    .set(table.TOKEN, token)
                    .set(table.SECTIONS, String.join(",", new TreeSet<>(sections)))
                    .set(table.CREATOR_KIND, by.kind())
                    .set(table.CREATOR_UUID, by.uuid())
                    .set(table.CREATOR_NAME, by.name())
                    .set(table.CREATED_AT, at)
                    .set(table.LAST_SEQUENCE, 0L)
                    .execute();
            return null;
        });
    }

    public CompletableFuture<Optional<Session>> find(final String id) {
        final SbEditorSessions table = SakuraTables.EDITOR_SESSIONS;
        return this.storage.read(dsl -> Optional.ofNullable(
                        dsl.selectFrom(table).where(table.ID.eq(id)).fetchOne())
                .map(EditorSessionRepository::session));
    }

    /**
     * Records that the change set with this sequence is being applied. False when the session
     * already applied this one or a later one: another instance (or a replay) got there first.
     */
    public CompletableFuture<Boolean> claim(final String id, final long sequence) {
        final SbEditorSessions table = SakuraTables.EDITOR_SESSIONS;
        return this.storage.write(configuration -> configuration.dsl().update(table)
                .set(table.LAST_SEQUENCE, sequence)
                .where(table.ID.eq(id))
                .and(table.LAST_SEQUENCE.lt(sequence))
                .execute() == 1);
    }

    public CompletableFuture<Integer> deleteBefore(final Instant cutoff) {
        final SbEditorSessions table = SakuraTables.EDITOR_SESSIONS;
        return this.storage.write(configuration -> configuration.dsl().deleteFrom(table)
                .where(table.CREATED_AT.lt(cutoff)).execute());
    }

    private static Session session(final SbEditorSessionsRecord row) {
        return new Session(row.id(), row.secret(), row.token(), row.sections().isEmpty()
                ? Set.of() : Set.of(row.sections().split(",")), StoredOperator.operator(
                row.creatorKind(), row.creatorUuid(), row.creatorName()), row.createdAt(),
                row.lastSequence());
    }
}
