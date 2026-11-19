package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.util.List;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplateGroupMembersRecord;
import org.jooq.Constraint;
import org.jooq.ForeignKey;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbTemplateGroupMembers extends SbTable<SbTemplateGroupMembersRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbTemplateGroupMembersRecord, Integer> GROUP_ID = createField(
            DSL.name("group_id"), SQLDataType.INTEGER.nullable(false), this, "");

    public final TableField<SbTemplateGroupMembersRecord, Integer> TEMPLATE_ID = createField(
            DSL.name("template_id"), SQLDataType.INTEGER.nullable(false), this, "");

    public final TableField<SbTemplateGroupMembersRecord, Short> WEIGHT = createField(
            DSL.name("weight"), SQLDataType.SMALLINT.nullable(false), this, "");

    public SbTemplateGroupMembers(final String prefix) {
        this(prefix, DSL.name(prefix + "template_group_members"), null);
    }

    private SbTemplateGroupMembers(final String prefix, final Name alias,
            final @Nullable Table<SbTemplateGroupMembersRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbTemplateGroupMembersRecord> getRecordType() {
        return SbTemplateGroupMembersRecord.class;
    }

    @Override
    public UniqueKey<SbTemplateGroupMembersRecord> getPrimaryKey() {
        return Keys.TEMPLATE_GROUP_MEMBERS_PK;
    }

    @Override
    public List<ForeignKey<SbTemplateGroupMembersRecord, ?>> getReferences() {
        return List.of(
                Keys.TEMPLATE_GROUP_MEMBERS_GROUP_FK,
                Keys.TEMPLATE_GROUP_MEMBERS_TEMPLATE_FK);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("weight_ck",
                        this.WEIGHT.ge((short) 1)));
    }

    @Override
    public SbTemplateGroupMembers as(final String alias) {
        return new SbTemplateGroupMembers(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbTemplateGroupMembers as(final Name alias) {
        return new SbTemplateGroupMembers(this.prefix(), alias, this);
    }

    @Override
    public SbTemplateGroupMembers rename(final String name) {
        return new SbTemplateGroupMembers(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbTemplateGroupMembers rename(final Name name) {
        return new SbTemplateGroupMembers(this.prefix(), name, null);
    }
}
