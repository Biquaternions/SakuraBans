package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbLockdownsRecord extends UpdatableRecordImpl<SbLockdownsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbLockdownsRecord() {
        super(SakuraTables.LOCKDOWNS);
    }

    public Integer scopeId() {
        return this.get(SakuraTables.LOCKDOWNS.SCOPE_ID);
    }

    public SbLockdownsRecord scopeId(final Integer value) {
        this.set(SakuraTables.LOCKDOWNS.SCOPE_ID, value);
        return this;
    }

    public String reason() {
        return this.get(SakuraTables.LOCKDOWNS.REASON);
    }

    public SbLockdownsRecord reason(final String value) {
        this.set(SakuraTables.LOCKDOWNS.REASON, value);
        return this;
    }

    public OperatorKind startedByKind() {
        return this.get(SakuraTables.LOCKDOWNS.STARTED_BY_KIND);
    }

    public SbLockdownsRecord startedByKind(final OperatorKind value) {
        this.set(SakuraTables.LOCKDOWNS.STARTED_BY_KIND, value);
        return this;
    }

    public @Nullable UUID startedByUuid() {
        return this.get(SakuraTables.LOCKDOWNS.STARTED_BY_UUID);
    }

    public SbLockdownsRecord startedByUuid(final @Nullable UUID value) {
        this.set(SakuraTables.LOCKDOWNS.STARTED_BY_UUID, value);
        return this;
    }

    public String startedByName() {
        return this.get(SakuraTables.LOCKDOWNS.STARTED_BY_NAME);
    }

    public SbLockdownsRecord startedByName(final String value) {
        this.set(SakuraTables.LOCKDOWNS.STARTED_BY_NAME, value);
        return this;
    }

    public String startedBySource() {
        return this.get(SakuraTables.LOCKDOWNS.STARTED_BY_SOURCE);
    }

    public SbLockdownsRecord startedBySource(final String value) {
        this.set(SakuraTables.LOCKDOWNS.STARTED_BY_SOURCE, value);
        return this;
    }

    public Instant startedAt() {
        return this.get(SakuraTables.LOCKDOWNS.STARTED_AT);
    }

    public SbLockdownsRecord startedAt(final Instant value) {
        this.set(SakuraTables.LOCKDOWNS.STARTED_AT, value);
        return this;
    }

    public @Nullable Instant endsAt() {
        return this.get(SakuraTables.LOCKDOWNS.ENDS_AT);
    }

    public SbLockdownsRecord endsAt(final @Nullable Instant value) {
        this.set(SakuraTables.LOCKDOWNS.ENDS_AT, value);
        return this;
    }
}
