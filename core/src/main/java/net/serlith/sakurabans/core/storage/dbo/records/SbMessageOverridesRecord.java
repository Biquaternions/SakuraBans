package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;

public final class SbMessageOverridesRecord extends UpdatableRecordImpl<SbMessageOverridesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbMessageOverridesRecord() {
        super(SakuraTables.MESSAGE_OVERRIDES);
    }

    public int scopeId() {
        return this.get(SakuraTables.MESSAGE_OVERRIDES.SCOPE_ID);
    }

    public String locale() {
        return this.get(SakuraTables.MESSAGE_OVERRIDES.LOCALE);
    }

    public SbMessageOverridesRecord locale(final String value) {
        this.set(SakuraTables.MESSAGE_OVERRIDES.LOCALE, value);
        return this;
    }

    public String name() {
        return this.get(SakuraTables.MESSAGE_OVERRIDES.K);
    }

    public SbMessageOverridesRecord name(final String value) {
        this.set(SakuraTables.MESSAGE_OVERRIDES.K, value);
        return this;
    }

    public String text() {
        return this.get(SakuraTables.MESSAGE_OVERRIDES.TEXT);
    }

    public SbMessageOverridesRecord text(final String value) {
        this.set(SakuraTables.MESSAGE_OVERRIDES.TEXT, value);
        return this;
    }

    public Instant updatedAt() {
        return this.get(SakuraTables.MESSAGE_OVERRIDES.UPDATED_AT);
    }

    public SbMessageOverridesRecord updatedAt(final Instant value) {
        this.set(SakuraTables.MESSAGE_OVERRIDES.UPDATED_AT, value);
        return this;
    }
}
