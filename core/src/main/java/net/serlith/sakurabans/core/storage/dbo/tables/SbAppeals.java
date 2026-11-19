package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.appeal.AppealOutcome;
import net.serlith.sakurabans.core.storage.dbo.Indexes;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbAppealsRecord;
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

public final class SbAppeals extends SbTable<SbAppealsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbAppealsRecord, Long> ID = createField(
            DSL.name("id"), SQLDataType.BIGINT.nullable(false).generatedByDefaultAsIdentity(),
            this, "");

    public final TableField<SbAppealsRecord, Long> PUNISHMENT_ID = createField(
            DSL.name("punishment_id"), SQLDataType.BIGINT.nullable(false), this, "");

    public final TableField<SbAppealsRecord, UUID> APPELLANT = createField(
            DSL.name("appellant"), SbTypes.UUID.nullable(false), this, "");

    public final TableField<SbAppealsRecord, String> MESSAGE = createField(
            DSL.name("message"), SQLDataType.VARCHAR(1024).nullable(false), this, "");

    public final TableField<SbAppealsRecord, Instant> CREATED_AT = createField(
            DSL.name("created_at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbAppealsRecord, Appeal.Status> STATUS = createField(
            DSL.name("status"), SbTypes.APPEAL_STATUS.nullable(false), this, "");

    public final TableField<SbAppealsRecord, AppealOutcome> OUTCOME = createField(
            DSL.name("outcome"), SbTypes.APPEAL_OUTCOME.nullable(true), this, "");

    public final TableField<SbAppealsRecord, OperatorKind> DECIDED_BY_KIND = createField(
            DSL.name("decided_by_kind"), SbTypes.OPERATOR_KIND.nullable(true), this, "");

    public final TableField<SbAppealsRecord, UUID> DECIDED_BY_UUID = createField(
            DSL.name("decided_by_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbAppealsRecord, String> DECIDED_BY_NAME = createField(
            DSL.name("decided_by_name"), SQLDataType.VARCHAR(64).nullable(true), this, "");

    public final TableField<SbAppealsRecord, String> DECIDED_BY_SOURCE = createField(
            DSL.name("decided_by_source"), SQLDataType.VARCHAR(64).nullable(true), this, "");

    public final TableField<SbAppealsRecord, Instant> DECIDED_AT = createField(
            DSL.name("decided_at"), SbTypes.INSTANT.nullable(true), this, "");

    public final TableField<SbAppealsRecord, String> NOTE = createField(
            DSL.name("note"), SQLDataType.VARCHAR(1024).nullable(true), this, "");

    public final TableField<SbAppealsRecord, Long> VERSION = createField(
            DSL.name("version"), SQLDataType.BIGINT.nullable(false), this, "");

    public SbAppeals(final String prefix) {
        this(prefix, DSL.name(prefix + "appeals"), null);
    }

    private SbAppeals(final String prefix, final Name alias,
            final @Nullable Table<SbAppealsRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbAppealsRecord> getRecordType() {
        return SbAppealsRecord.class;
    }

    @Override
    public UniqueKey<SbAppealsRecord> getPrimaryKey() {
        return Keys.APPEALS_PK;
    }

    @Override
    public List<ForeignKey<SbAppealsRecord, ?>> getReferences() {
        return List.of(
                Keys.APPEALS_PUNISHMENT_FK);
    }

    @Override
    public List<Index> getIndexes() {
        return List.of(
                Indexes.APPEALS_PUNISHMENT_IX,
                Indexes.APPEALS_STATUS_IX,
                Indexes.APPEALS_APPELLANT_IX);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("decision_ck",
                        this.STATUS.eq(Appeal.Status.OPEN)
                                .and(this.DECIDED_AT.isNull())
                                .or(this.STATUS.ne(Appeal.Status.OPEN)
                                .and(this.DECIDED_AT.isNotNull()))),
                this.check("version_ck",
                        this.VERSION.ge(1L)));
    }

    @Override
    public SbAppeals as(final String alias) {
        return new SbAppeals(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbAppeals as(final Name alias) {
        return new SbAppeals(this.prefix(), alias, this);
    }

    @Override
    public SbAppeals rename(final String name) {
        return new SbAppeals(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbAppeals rename(final Name name) {
        return new SbAppeals(this.prefix(), name, null);
    }
}
