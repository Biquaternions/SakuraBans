package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.wave.BanWave;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbBanWavesRecord;
import org.jooq.Constraint;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbBanWaves extends SbTable<SbBanWavesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbBanWavesRecord, Long> ID = createField(
            DSL.name("id"), SQLDataType.BIGINT.nullable(false).generatedByDefaultAsIdentity(),
            this, "");

    public final TableField<SbBanWavesRecord, String> NAME = createField(
            DSL.name("name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbBanWavesRecord, BanWave.Status> STATUS = createField(
            DSL.name("status"), SbTypes.WAVE_STATUS.nullable(false), this, "");

    public final TableField<SbBanWavesRecord, Instant> SCHEDULED_AT = createField(
            DSL.name("scheduled_at"), SbTypes.INSTANT.nullable(true), this, "");

    public final TableField<SbBanWavesRecord, OperatorKind> CREATED_BY_KIND = createField(
            DSL.name("created_by_kind"), SbTypes.OPERATOR_KIND.nullable(false), this, "");

    public final TableField<SbBanWavesRecord, UUID> CREATED_BY_UUID = createField(
            DSL.name("created_by_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbBanWavesRecord, String> CREATED_BY_NAME = createField(
            DSL.name("created_by_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbBanWavesRecord, String> CREATED_BY_SOURCE = createField(
            DSL.name("created_by_source"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbBanWavesRecord, Instant> CREATED_AT = createField(
            DSL.name("created_at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbBanWavesRecord, Instant> RELEASED_AT = createField(
            DSL.name("released_at"), SbTypes.INSTANT.nullable(true), this, "");

    public final TableField<SbBanWavesRecord, Long> VERSION = createField(
            DSL.name("version"), SQLDataType.BIGINT.nullable(false), this, "");

    public SbBanWaves(final String prefix) {
        this(prefix, DSL.name(prefix + "ban_waves"), null);
    }

    private SbBanWaves(final String prefix, final Name alias,
            final @Nullable Table<SbBanWavesRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbBanWavesRecord> getRecordType() {
        return SbBanWavesRecord.class;
    }

    @Override
    public UniqueKey<SbBanWavesRecord> getPrimaryKey() {
        return Keys.BAN_WAVES_PK;
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("version_ck",
                        this.VERSION.ge(1L)));
    }

    @Override
    public SbBanWaves as(final String alias) {
        return new SbBanWaves(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbBanWaves as(final Name alias) {
        return new SbBanWaves(this.prefix(), alias, this);
    }

    @Override
    public SbBanWaves rename(final String name) {
        return new SbBanWaves(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbBanWaves rename(final Name name) {
        return new SbBanWaves(this.prefix(), name, null);
    }
}
