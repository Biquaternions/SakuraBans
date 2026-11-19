package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbSettingOverridesRecord;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbSettingOverrides extends SbTable<SbSettingOverridesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbSettingOverridesRecord, String> K = createField(
            DSL.name("k"), SQLDataType.VARCHAR(255).nullable(false), this, "");

    public final TableField<SbSettingOverridesRecord, String> V = createField(
            DSL.name("v"), SQLDataType.CLOB.nullable(false), this, "");

    public final TableField<SbSettingOverridesRecord, Instant> UPDATED_AT = createField(
            DSL.name("updated_at"), SbTypes.INSTANT.nullable(false), this, "");

    public SbSettingOverrides(final String prefix) {
        this(prefix, DSL.name(prefix + "setting_overrides"), null);
    }

    private SbSettingOverrides(final String prefix, final Name alias,
            final @Nullable Table<SbSettingOverridesRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbSettingOverridesRecord> getRecordType() {
        return SbSettingOverridesRecord.class;
    }

    @Override
    public UniqueKey<SbSettingOverridesRecord> getPrimaryKey() {
        return Keys.SETTING_OVERRIDES_PK;
    }

    @Override
    public SbSettingOverrides as(final String alias) {
        return new SbSettingOverrides(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbSettingOverrides as(final Name alias) {
        return new SbSettingOverrides(this.prefix(), alias, this);
    }

    @Override
    public SbSettingOverrides rename(final String name) {
        return new SbSettingOverrides(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbSettingOverrides rename(final Name name) {
        return new SbSettingOverrides(this.prefix(), name, null);
    }
}
