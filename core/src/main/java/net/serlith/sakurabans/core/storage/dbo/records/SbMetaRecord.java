package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;

public final class SbMetaRecord extends UpdatableRecordImpl<SbMetaRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbMetaRecord() {
        super(SakuraTables.META);
    }

    public String name() {
        return this.get(SakuraTables.META.K);
    }

    public SbMetaRecord name(final String value) {
        this.set(SakuraTables.META.K, value);
        return this;
    }

    public String value() {
        return this.get(SakuraTables.META.V);
    }

    public SbMetaRecord value(final String value) {
        this.set(SakuraTables.META.V, value);
        return this;
    }
}
