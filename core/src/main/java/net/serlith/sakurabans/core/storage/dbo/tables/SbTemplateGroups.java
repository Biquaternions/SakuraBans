package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.util.List;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplateGroupsRecord;
import org.jooq.Constraint;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbTemplateGroups extends SbTable<SbTemplateGroupsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbTemplateGroupsRecord, Integer> ID = createField(
            DSL.name("id"), SQLDataType.INTEGER.nullable(false).generatedByDefaultAsIdentity(),
            this, "");

    public final TableField<SbTemplateGroupsRecord, String> NAME = createField(
            DSL.name("name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbTemplateGroupsRecord, Long> VERSION = createField(
            DSL.name("version"), SQLDataType.BIGINT.nullable(false), this, "");

    public SbTemplateGroups(final String prefix) {
        this(prefix, DSL.name(prefix + "template_groups"), null);
    }

    private SbTemplateGroups(final String prefix, final Name alias,
            final @Nullable Table<SbTemplateGroupsRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbTemplateGroupsRecord> getRecordType() {
        return SbTemplateGroupsRecord.class;
    }

    @Override
    public UniqueKey<SbTemplateGroupsRecord> getPrimaryKey() {
        return Keys.TEMPLATE_GROUPS_PK;
    }

    @Override
    public List<UniqueKey<SbTemplateGroupsRecord>> getUniqueKeys() {
        return List.of(Keys.TEMPLATE_GROUPS_NAME_UQ);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("version_ck",
                        this.VERSION.ge(1L)));
    }

    @Override
    public SbTemplateGroups as(final String alias) {
        return new SbTemplateGroups(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbTemplateGroups as(final Name alias) {
        return new SbTemplateGroups(this.prefix(), alias, this);
    }

    @Override
    public SbTemplateGroups rename(final String name) {
        return new SbTemplateGroups(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbTemplateGroups rename(final Name name) {
        return new SbTemplateGroups(this.prefix(), name, null);
    }
}
