package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import net.serlith.sakurabans.api.kind.AddressMode;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbKindsRecord;
import org.jooq.Constraint;
import org.jooq.ForeignKey;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbKinds extends SbTable<SbKindsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbKindsRecord, String> ID = createField(
            DSL.name("id"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbKindsRecord, String> DISPLAY_NAME = createField(
            DSL.name("display_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbKindsRecord, PunishmentType> BASE_TYPE = createField(
            DSL.name("base_type"), SbTypes.PUNISHMENT_TYPE.nullable(false), this, "");

    public final TableField<SbKindsRecord, Boolean> SHADOW = createField(
            DSL.name("shadow"), SbTypes.FLAG.nullable(false), this, "");

    public final TableField<SbKindsRecord, AddressStrictness> STRICTNESS = createField(
            DSL.name("strictness"), SbTypes.STRICTNESS.nullable(true), this, "");

    public final TableField<SbKindsRecord, Integer> DEFAULT_SCOPE_ID = createField(
            DSL.name("default_scope_id"), SQLDataType.INTEGER.nullable(true), this, "");

    public final TableField<SbKindsRecord, AddressMode> ADDRESS_MODE = createField(
            DSL.name("address_mode"), SbTypes.ADDRESS_MODE.nullable(false), this, "");

    public final TableField<SbKindsRecord, Boolean> PERMANENT_ONLY = createField(
            DSL.name("permanent_only"), SbTypes.FLAG.nullable(false), this, "");

    public final TableField<SbKindsRecord, String> REQUIRED_EVIDENCE = createField(
            DSL.name("required_evidence"), SQLDataType.VARCHAR(1024).nullable(false), this, "");

    public final TableField<SbKindsRecord, Boolean> BUILT_IN = createField(
            DSL.name("built_in"), SbTypes.FLAG.nullable(false), this, "");

    public final TableField<SbKindsRecord, Long> VERSION = createField(
            DSL.name("version"), SQLDataType.BIGINT.nullable(false), this, "");

    public final TableField<SbKindsRecord, Instant> CREATED_AT = createField(
            DSL.name("created_at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbKindsRecord, Instant> UPDATED_AT = createField(
            DSL.name("updated_at"), SbTypes.INSTANT.nullable(false), this, "");

    public SbKinds(final String prefix) {
        this(prefix, DSL.name(prefix + "kinds"), null);
    }

    private SbKinds(final String prefix, final Name alias,
            final @Nullable Table<SbKindsRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbKindsRecord> getRecordType() {
        return SbKindsRecord.class;
    }

    @Override
    public UniqueKey<SbKindsRecord> getPrimaryKey() {
        return Keys.KINDS_PK;
    }

    @Override
    public List<ForeignKey<SbKindsRecord, ?>> getReferences() {
        return List.of(
                Keys.KINDS_SCOPE_FK);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("version_ck",
                        this.VERSION.ge(1L)));
    }

    @Override
    public SbKinds as(final String alias) {
        return new SbKinds(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbKinds as(final Name alias) {
        return new SbKinds(this.prefix(), alias, this);
    }

    @Override
    public SbKinds rename(final String name) {
        return new SbKinds(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbKinds rename(final Name name) {
        return new SbKinds(this.prefix(), name, null);
    }
}
