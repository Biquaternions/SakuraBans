package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplatesRecord;
import org.jooq.Constraint;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbTemplates extends SbTable<SbTemplatesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbTemplatesRecord, Integer> ID = createField(
            DSL.name("id"), SQLDataType.INTEGER.nullable(false).generatedByDefaultAsIdentity(),
            this, "");

    public final TableField<SbTemplatesRecord, String> NAME = createField(
            DSL.name("name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbTemplatesRecord, String> KIND = createField(
            DSL.name("kind"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbTemplatesRecord, PunishmentType> TYPE = createField(
            DSL.name("type"), SbTypes.PUNISHMENT_TYPE.nullable(false), this, "");

    public final TableField<SbTemplatesRecord, String> REASON = createField(
            DSL.name("reason"), SQLDataType.VARCHAR(1024).nullable(false), this, "");

    public final TableField<SbTemplatesRecord, String> REQUIRED_EVIDENCE = createField(
            DSL.name("required_evidence"), SQLDataType.VARCHAR(1024).nullable(false), this, "");

    public final TableField<SbTemplatesRecord, String> CAPTURE_EVIDENCE = createField(
            DSL.name("capture_evidence"), SQLDataType.VARCHAR(1024).nullable(false), this, "");

    public final TableField<SbTemplatesRecord, Duration> LADDER_EXPIRY_MS = createField(
            DSL.name("ladder_expiry_ms"), SbTypes.DURATION.nullable(true), this, "");

    public final TableField<SbTemplatesRecord, Boolean> ADDRESS_SHARED = createField(
            DSL.name("address_shared"), SbTypes.FLAG.nullable(false), this, "");

    public final TableField<SbTemplatesRecord, Boolean> PERMISSION_REQUIRED = createField(
            DSL.name("permission_required"), SbTypes.FLAG.nullable(false), this, "");

    public final TableField<SbTemplatesRecord, String> ACTIONS = createField(
            DSL.name("actions"), SQLDataType.VARCHAR(4096).nullable(false), this, "");

    public final TableField<SbTemplatesRecord, Long> VERSION = createField(
            DSL.name("version"), SQLDataType.BIGINT.nullable(false), this, "");

    public final TableField<SbTemplatesRecord, Instant> CREATED_AT = createField(
            DSL.name("created_at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbTemplatesRecord, Instant> UPDATED_AT = createField(
            DSL.name("updated_at"), SbTypes.INSTANT.nullable(false), this, "");

    public SbTemplates(final String prefix) {
        this(prefix, DSL.name(prefix + "templates"), null);
    }

    private SbTemplates(final String prefix, final Name alias,
            final @Nullable Table<SbTemplatesRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbTemplatesRecord> getRecordType() {
        return SbTemplatesRecord.class;
    }

    @Override
    public UniqueKey<SbTemplatesRecord> getPrimaryKey() {
        return Keys.TEMPLATES_PK;
    }

    @Override
    public List<UniqueKey<SbTemplatesRecord>> getUniqueKeys() {
        return List.of(Keys.TEMPLATES_NAME_UQ);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("version_ck",
                        this.VERSION.ge(1L)));
    }

    @Override
    public SbTemplates as(final String alias) {
        return new SbTemplates(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbTemplates as(final Name alias) {
        return new SbTemplates(this.prefix(), alias, this);
    }

    @Override
    public SbTemplates rename(final String name) {
        return new SbTemplates(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbTemplates rename(final Name name) {
        return new SbTemplates(this.prefix(), name, null);
    }
}
