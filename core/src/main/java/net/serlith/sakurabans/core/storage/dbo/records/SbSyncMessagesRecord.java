package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;

public final class SbSyncMessagesRecord extends UpdatableRecordImpl<SbSyncMessagesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbSyncMessagesRecord() {
        super(SakuraTables.SYNC_MESSAGES);
    }

    public Long id() {
        return this.get(SakuraTables.SYNC_MESSAGES.ID);
    }

    public SbSyncMessagesRecord id(final Long value) {
        this.set(SakuraTables.SYNC_MESSAGES.ID, value);
        return this;
    }

    public Instant createdAt() {
        return this.get(SakuraTables.SYNC_MESSAGES.CREATED_AT);
    }

    public SbSyncMessagesRecord createdAt(final Instant value) {
        this.set(SakuraTables.SYNC_MESSAGES.CREATED_AT, value);
        return this;
    }

    public UUID origin() {
        return this.get(SakuraTables.SYNC_MESSAGES.ORIGIN);
    }

    public SbSyncMessagesRecord origin(final UUID value) {
        this.set(SakuraTables.SYNC_MESSAGES.ORIGIN, value);
        return this;
    }

    public byte[] payload() {
        return this.get(SakuraTables.SYNC_MESSAGES.PAYLOAD);
    }

    public SbSyncMessagesRecord payload(final byte[] value) {
        this.set(SakuraTables.SYNC_MESSAGES.PAYLOAD, value);
        return this;
    }
}
