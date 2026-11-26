package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbInstancesRecord;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbInstances extends SbTable<SbInstancesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbInstancesRecord, String> ID = createField(
            DSL.name("id"), SQLDataType.VARCHAR(36).nullable(false), this, "");

    public final TableField<SbInstancesRecord, String> SERVER_NAME = createField(
            DSL.name("server_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    /** The server's own {@code server.categories}, joined by commas. */
    public final TableField<SbInstancesRecord, String> CATEGORIES = createField(
            DSL.name("categories"), SQLDataType.VARCHAR(1024).nullable(false), this, "");

    public final TableField<SbInstancesRecord, String> PLATFORM = createField(
            DSL.name("platform"), SQLDataType.VARCHAR(16).nullable(false), this, "");

    public final TableField<SbInstancesRecord, String> PLUGIN_VERSION = createField(
            DSL.name("plugin_version"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbInstancesRecord, Instant> STARTED_AT = createField(
            DSL.name("started_at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbInstancesRecord, Instant> LAST_SEEN_AT = createField(
            DSL.name("last_seen_at"), SbTypes.INSTANT.nullable(false), this, "");

    /** The file values of every key that isn't file-only, as a JSON object. */
    public final TableField<SbInstancesRecord, String> FILE_VALUES = createField(
            DSL.name("file_values"), SQLDataType.CLOB.nullable(false), this, "");

    public SbInstances(final String prefix) {
        this(prefix, DSL.name(prefix + "instances"), null);
    }

    private SbInstances(final String prefix, final Name alias,
            final @Nullable Table<SbInstancesRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbInstancesRecord> getRecordType() {
        return SbInstancesRecord.class;
    }

    @Override
    public UniqueKey<SbInstancesRecord> getPrimaryKey() {
        return Keys.INSTANCES_PK;
    }

    @Override
    public SbInstances as(final String alias) {
        return new SbInstances(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbInstances as(final Name alias) {
        return new SbInstances(this.prefix(), alias, this);
    }

    @Override
    public SbInstances rename(final String name) {
        return new SbInstances(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbInstances rename(final Name name) {
        return new SbInstances(this.prefix(), name, null);
    }
}
