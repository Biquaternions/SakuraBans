package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Duration;
import java.util.List;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbTemplateStepsRecord;
import org.jooq.Constraint;
import org.jooq.ForeignKey;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbTemplateSteps extends SbTable<SbTemplateStepsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbTemplateStepsRecord, Integer> TEMPLATE_ID = createField(
            DSL.name("template_id"), SQLDataType.INTEGER.nullable(false), this, "");

    public final TableField<SbTemplateStepsRecord, Short> POSITION = createField(
            DSL.name("position"), SQLDataType.SMALLINT.nullable(false), this, "");

    public final TableField<SbTemplateStepsRecord, Duration> DURATION_MS = createField(
            DSL.name("duration_ms"), SbTypes.DURATION.nullable(true), this, "");

    public final TableField<SbTemplateStepsRecord, String> ACTIONS = createField(
            DSL.name("actions"), SQLDataType.VARCHAR(4096).nullable(false), this, "");

    public SbTemplateSteps(final String prefix) {
        this(prefix, DSL.name(prefix + "template_steps"), null);
    }

    private SbTemplateSteps(final String prefix, final Name alias,
            final @Nullable Table<SbTemplateStepsRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbTemplateStepsRecord> getRecordType() {
        return SbTemplateStepsRecord.class;
    }

    @Override
    public UniqueKey<SbTemplateStepsRecord> getPrimaryKey() {
        return Keys.TEMPLATE_STEPS_PK;
    }

    @Override
    public List<ForeignKey<SbTemplateStepsRecord, ?>> getReferences() {
        return List.of(
                Keys.TEMPLATE_STEPS_TEMPLATE_FK);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("step_ck",
                        this.POSITION.ge((short) 0)
                                .and(this.DURATION_MS.isNull()
                                .or(this.DURATION_MS.gt(Duration.ZERO)))));
    }

    @Override
    public SbTemplateSteps as(final String alias) {
        return new SbTemplateSteps(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbTemplateSteps as(final Name alias) {
        return new SbTemplateSteps(this.prefix(), alias, this);
    }

    @Override
    public SbTemplateSteps rename(final String name) {
        return new SbTemplateSteps(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbTemplateSteps rename(final Name name) {
        return new SbTemplateSteps(this.prefix(), name, null);
    }
}
