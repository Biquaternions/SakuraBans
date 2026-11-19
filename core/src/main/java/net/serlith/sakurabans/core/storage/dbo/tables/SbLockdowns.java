package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbLockdownsRecord;
import org.jooq.ForeignKey;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbLockdowns extends SbTable<SbLockdownsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbLockdownsRecord, Integer> SCOPE_ID = createField(
            DSL.name("scope_id"), SQLDataType.INTEGER.nullable(false), this, "");

    public final TableField<SbLockdownsRecord, String> REASON = createField(
            DSL.name("reason"), SQLDataType.VARCHAR(1024).nullable(false), this, "");

    public final TableField<SbLockdownsRecord, OperatorKind> STARTED_BY_KIND = createField(
            DSL.name("started_by_kind"), SbTypes.OPERATOR_KIND.nullable(false), this, "");

    public final TableField<SbLockdownsRecord, UUID> STARTED_BY_UUID = createField(
            DSL.name("started_by_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbLockdownsRecord, String> STARTED_BY_NAME = createField(
            DSL.name("started_by_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbLockdownsRecord, String> STARTED_BY_SOURCE = createField(
            DSL.name("started_by_source"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbLockdownsRecord, Instant> STARTED_AT = createField(
            DSL.name("started_at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbLockdownsRecord, Instant> ENDS_AT = createField(
            DSL.name("ends_at"), SbTypes.INSTANT.nullable(true), this, "");

    public SbLockdowns(final String prefix) {
        this(prefix, DSL.name(prefix + "lockdowns"), null);
    }

    private SbLockdowns(final String prefix, final Name alias,
            final @Nullable Table<SbLockdownsRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbLockdownsRecord> getRecordType() {
        return SbLockdownsRecord.class;
    }

    @Override
    public UniqueKey<SbLockdownsRecord> getPrimaryKey() {
        return Keys.LOCKDOWNS_PK;
    }

    @Override
    public List<ForeignKey<SbLockdownsRecord, ?>> getReferences() {
        return List.of(
                Keys.LOCKDOWNS_SCOPE_FK);
    }

    @Override
    public SbLockdowns as(final String alias) {
        return new SbLockdowns(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbLockdowns as(final Name alias) {
        return new SbLockdowns(this.prefix(), alias, this);
    }

    @Override
    public SbLockdowns rename(final String name) {
        return new SbLockdowns(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbLockdowns rename(final Name name) {
        return new SbLockdowns(this.prefix(), name, null);
    }
}
