package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.serlith.sakurabans.api.template.TemplateDisplay;
import net.serlith.sakurabans.core.model.TemplateKind;
import net.serlith.sakurabans.core.storage.dbo.DurationMode;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplatesRecord;
import org.jooq.Constraint;
import org.jooq.ForeignKey;
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

    public final TableField<SbTemplatesRecord, TemplateKind> KIND = createField(
            DSL.name("kind"), SbTypes.TEMPLATE_KIND.nullable(false), this, "");

    /** A preset's duration; always open for a ladder. */
    public final TableField<SbTemplatesRecord, DurationMode> DURATION_MODE = createField(
            DSL.name("duration_mode"), SbTypes.DURATION_MODE.nullable(false), this, "");

    public final TableField<SbTemplatesRecord, Duration> DURATION_MS = createField(
            DSL.name("duration_ms"), SbTypes.DURATION.nullable(true), this, "");

    public final TableField<SbTemplatesRecord, Boolean> IP = createField(
            DSL.name("ip"), SbTypes.FLAG.nullable(false), this, "");

    public final TableField<SbTemplatesRecord, Boolean> SHADOW = createField(
            DSL.name("shadow"), SbTypes.FLAG.nullable(false), this, "");

    /**
     * Where the definition is in force (S62): the global scope row for the global definition,
     * a server or category for a variant. Not {@link #SCOPE_ID}, where punishments apply.
     */
    public final TableField<SbTemplatesRecord, Integer> VARIANT_SCOPE_ID = createField(
            DSL.name("variant_scope_id"), SQLDataType.INTEGER.nullable(false), this, "");

    /** Where its punishments apply when the command names no scope; null: the default. */
    public final TableField<SbTemplatesRecord, Integer> SCOPE_ID = createField(
            DSL.name("scope_id"), SQLDataType.INTEGER.nullable(true), this, "");

    public final TableField<SbTemplatesRecord, String> REASON = createField(
            DSL.name("reason"), SQLDataType.VARCHAR(1024).nullable(false), this, "");

    public final TableField<SbTemplatesRecord, Duration> LADDER_EXPIRY_MS = createField(
            DSL.name("ladder_expiry_ms"), SbTypes.DURATION.nullable(true), this, "");

    public final TableField<SbTemplatesRecord, Boolean> ADDRESS_SHARED = createField(
            DSL.name("address_shared"), SbTypes.FLAG.nullable(false), this, "");

    public final TableField<SbTemplatesRecord, Boolean> PERMISSION_REQUIRED = createField(
            DSL.name("permission_required"), SbTypes.FLAG.nullable(false), this, "");

    public final TableField<SbTemplatesRecord, String> ACTIONS = createField(
            DSL.name("actions"), SQLDataType.VARCHAR(4096).nullable(false), this, "");

    public final TableField<SbTemplatesRecord, String> MESSAGE = createField(
            DSL.name("message"), SQLDataType.VARCHAR(4096).nullable(true), this, "");

    public final TableField<SbTemplatesRecord, TemplateDisplay> DISPLAY = createField(
            DSL.name("display"), SbTypes.TEMPLATE_DISPLAY.nullable(false), this, "");

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
        return List.of(Keys.TEMPLATES_VARIANT_NAME_UQ);
    }

    @Override
    public List<ForeignKey<SbTemplatesRecord, ?>> getReferences() {
        return List.of(
                Keys.TEMPLATES_VARIANT_SCOPE_FK,
                Keys.TEMPLATES_SCOPE_FK);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("version_ck",
                        this.VERSION.ge(1L)),
                this.check("duration_ck",
                        this.DURATION_MS.isNull().or(this.DURATION_MS.gt(Duration.ZERO))));
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
