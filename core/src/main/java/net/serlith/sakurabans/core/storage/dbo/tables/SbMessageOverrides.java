package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbMessageOverridesRecord;
import org.jooq.ForeignKey;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbMessageOverrides extends SbTable<SbMessageOverridesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbMessageOverridesRecord, Integer> SCOPE_ID = createField(
            DSL.name("scope_id"), SQLDataType.INTEGER.nullable(false), this, "");

    public final TableField<SbMessageOverridesRecord, String> LOCALE = createField(
            DSL.name("locale"), SQLDataType.VARCHAR(16).nullable(false), this, "");

    public final TableField<SbMessageOverridesRecord, String> K = createField(
            DSL.name("k"), SQLDataType.VARCHAR(128).nullable(false), this, "");

    public final TableField<SbMessageOverridesRecord, String> TEXT = createField(
            DSL.name("text"), SQLDataType.CLOB.nullable(false), this, "");

    public final TableField<SbMessageOverridesRecord, Instant> UPDATED_AT = createField(
            DSL.name("updated_at"), SbTypes.INSTANT.nullable(false), this, "");

    public SbMessageOverrides(final String prefix) {
        this(prefix, DSL.name(prefix + "message_overrides"), null);
    }

    private SbMessageOverrides(final String prefix, final Name alias,
            final @Nullable Table<SbMessageOverridesRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbMessageOverridesRecord> getRecordType() {
        return SbMessageOverridesRecord.class;
    }

    @Override
    public UniqueKey<SbMessageOverridesRecord> getPrimaryKey() {
        return Keys.MESSAGE_OVERRIDES_PK;
    }

    @Override
    public List<ForeignKey<SbMessageOverridesRecord, ?>> getReferences() {
        return List.of(Keys.MESSAGE_OVERRIDES_SCOPE_FK);
    }

    @Override
    public SbMessageOverrides as(final String alias) {
        return new SbMessageOverrides(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbMessageOverrides as(final Name alias) {
        return new SbMessageOverrides(this.prefix(), alias, this);
    }

    @Override
    public SbMessageOverrides rename(final String name) {
        return new SbMessageOverrides(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbMessageOverrides rename(final Name name) {
        return new SbMessageOverrides(this.prefix(), name, null);
    }
}
