package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;

public final class SbTemplateGroupsRecord extends UpdatableRecordImpl<SbTemplateGroupsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbTemplateGroupsRecord() {
        super(SakuraTables.TEMPLATE_GROUPS);
    }

    public Integer id() {
        return this.get(SakuraTables.TEMPLATE_GROUPS.ID);
    }

    public SbTemplateGroupsRecord id(final Integer value) {
        this.set(SakuraTables.TEMPLATE_GROUPS.ID, value);
        return this;
    }

    public String name() {
        return this.get(SakuraTables.TEMPLATE_GROUPS.NAME);
    }

    public SbTemplateGroupsRecord name(final String value) {
        this.set(SakuraTables.TEMPLATE_GROUPS.NAME, value);
        return this;
    }

    public Long version() {
        return this.get(SakuraTables.TEMPLATE_GROUPS.VERSION);
    }

    public SbTemplateGroupsRecord version(final Long value) {
        this.set(SakuraTables.TEMPLATE_GROUPS.VERSION, value);
        return this;
    }
}
