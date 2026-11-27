package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.Indexes;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplateUsesRecord;
import org.jooq.Constraint;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

/** Every application of a template, with or without a punishment: a ladder's progress. */
public final class SbTemplateUses extends SbTable<SbTemplateUsesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbTemplateUsesRecord, Long> ID = createField(
            DSL.name("id"), SQLDataType.BIGINT.nullable(false).generatedByDefaultAsIdentity(),
            this, "");

    /** The template's name, as on punishments: a deleted template keeps its uses. */
    public final TableField<SbTemplateUsesRecord, String> TEMPLATE = createField(
            DSL.name("template"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    /** The variant that was used (S62); null: the global definition. */
    public final TableField<SbTemplateUsesRecord, Integer> TEMPLATE_VARIANT_SCOPE_ID =
            createField(DSL.name("template_variant_scope_id"),
                    SQLDataType.INTEGER.nullable(true), this, "");

    /** The ladder step, from 0; null for a preset. */
    public final TableField<SbTemplateUsesRecord, Short> STEP = createField(
            DSL.name("step"), SQLDataType.SMALLINT.nullable(true), this, "");

    public final TableField<SbTemplateUsesRecord, Long> VICTIM_ID = createField(
            DSL.name("victim_id"), SQLDataType.BIGINT.nullable(false), this, "");

    /** Null: a ladder step without a punishment. */
    public final TableField<SbTemplateUsesRecord, Long> PUNISHMENT_ID = createField(
            DSL.name("punishment_id"), SQLDataType.BIGINT.nullable(true), this, "");

    public final TableField<SbTemplateUsesRecord, OperatorKind> OPERATOR_KIND = createField(
            DSL.name("operator_kind"), SbTypes.OPERATOR_KIND.nullable(false), this, "");

    public final TableField<SbTemplateUsesRecord, UUID> OPERATOR_UUID = createField(
            DSL.name("operator_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbTemplateUsesRecord, String> OPERATOR_NAME = createField(
            DSL.name("operator_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbTemplateUsesRecord, String> SOURCE = createField(
            DSL.name("source"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbTemplateUsesRecord, Instant> CREATED_AT = createField(
            DSL.name("created_at"), SbTypes.INSTANT.nullable(false), this, "");

    public SbTemplateUses(final String prefix) {
        this(prefix, DSL.name(prefix + "template_uses"), null);
    }

    private SbTemplateUses(final String prefix, final Name alias,
            final @Nullable Table<SbTemplateUsesRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbTemplateUsesRecord> getRecordType() {
        return SbTemplateUsesRecord.class;
    }

    @Override
    public UniqueKey<SbTemplateUsesRecord> getPrimaryKey() {
        return Keys.TEMPLATE_USES_PK;
    }

    @Override
    public List<UniqueKey<SbTemplateUsesRecord>> getUniqueKeys() {
        return List.of(Keys.TEMPLATE_USES_PUNISHMENT_UQ);
    }

    @Override
    public List<ForeignKey<SbTemplateUsesRecord, ?>> getReferences() {
        return List.of(
                Keys.TEMPLATE_USES_VICTIM_FK,
                Keys.TEMPLATE_USES_PUNISHMENT_FK,
                Keys.TEMPLATE_USES_VARIANT_FK);
    }

    @Override
    public List<Index> getIndexes() {
        return List.of(Indexes.TEMPLATE_USES_TEMPLATE_IX);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("step_ck",
                        this.STEP.isNull().or(this.STEP.ge((short) 0))),
                this.check("operator_ck",
                        this.OPERATOR_KIND.ne(OperatorKind.PLAYER)
                                .or(this.OPERATOR_UUID.isNotNull())));
    }

    @Override
    public SbTemplateUses as(final String alias) {
        return new SbTemplateUses(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbTemplateUses as(final Name alias) {
        return new SbTemplateUses(this.prefix(), alias, this);
    }

    @Override
    public SbTemplateUses rename(final String name) {
        return new SbTemplateUses(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbTemplateUses rename(final Name name) {
        return new SbTemplateUses(this.prefix(), name, null);
    }
}
