package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.records.SbMetaRecord;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbMeta extends SbTable<SbMetaRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbMetaRecord, String> K = createField(
            DSL.name("k"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbMetaRecord, String> V = createField(
            DSL.name("v"), SQLDataType.VARCHAR(255).nullable(false), this, "");

    public SbMeta(final String prefix) {
        this(prefix, DSL.name(prefix + "meta"), null);
    }

    private SbMeta(final String prefix, final Name alias,
            final @Nullable Table<SbMetaRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbMetaRecord> getRecordType() {
        return SbMetaRecord.class;
    }

    @Override
    public UniqueKey<SbMetaRecord> getPrimaryKey() {
        return Keys.META_PK;
    }

    @Override
    public SbMeta as(final String alias) {
        return new SbMeta(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbMeta as(final Name alias) {
        return new SbMeta(this.prefix(), alias, this);
    }

    @Override
    public SbMeta rename(final String name) {
        return new SbMeta(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbMeta rename(final Name name) {
        return new SbMeta(this.prefix(), name, null);
    }
}
