package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;

public final class SbTemplateGroupMembersRecord
        extends UpdatableRecordImpl<SbTemplateGroupMembersRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbTemplateGroupMembersRecord() {
        super(SakuraTables.TEMPLATE_GROUP_MEMBERS);
    }

    public Integer groupId() {
        return this.get(SakuraTables.TEMPLATE_GROUP_MEMBERS.GROUP_ID);
    }

    public SbTemplateGroupMembersRecord groupId(final Integer value) {
        this.set(SakuraTables.TEMPLATE_GROUP_MEMBERS.GROUP_ID, value);
        return this;
    }

    public Integer templateId() {
        return this.get(SakuraTables.TEMPLATE_GROUP_MEMBERS.TEMPLATE_ID);
    }

    public SbTemplateGroupMembersRecord templateId(final Integer value) {
        this.set(SakuraTables.TEMPLATE_GROUP_MEMBERS.TEMPLATE_ID, value);
        return this;
    }

    public Short weight() {
        return this.get(SakuraTables.TEMPLATE_GROUP_MEMBERS.WEIGHT);
    }

    public SbTemplateGroupMembersRecord weight(final Short value) {
        this.set(SakuraTables.TEMPLATE_GROUP_MEMBERS.WEIGHT, value);
        return this;
    }
}
