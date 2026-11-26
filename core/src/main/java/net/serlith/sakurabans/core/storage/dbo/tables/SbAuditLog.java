package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.core.model.AuditAction;
import net.serlith.sakurabans.core.model.SubjectType;
import net.serlith.sakurabans.core.storage.dbo.Indexes;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbAuditLogRecord;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbAuditLog extends SbTable<SbAuditLogRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbAuditLogRecord, Long> ID = createField(
            DSL.name("id"), SQLDataType.BIGINT.nullable(false).generatedByDefaultAsIdentity(),
            this, "");

    public final TableField<SbAuditLogRecord, Instant> AT = createField(
            DSL.name("at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbAuditLogRecord, OperatorKind> ACTOR_KIND = createField(
            DSL.name("actor_kind"), SbTypes.OPERATOR_KIND.nullable(false), this, "");

    public final TableField<SbAuditLogRecord, UUID> ACTOR_UUID = createField(
            DSL.name("actor_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbAuditLogRecord, String> ACTOR_NAME = createField(
            DSL.name("actor_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbAuditLogRecord, String> SOURCE = createField(
            DSL.name("source"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbAuditLogRecord, AuditAction> ACTION = createField(
            DSL.name("action"), SbTypes.AUDIT_ACTION.nullable(false), this, "");

    public final TableField<SbAuditLogRecord, SubjectType> SUBJECT_TYPE = createField(
            DSL.name("subject_type"), SbTypes.SUBJECT_TYPE.nullable(false), this, "");

    public final TableField<SbAuditLogRecord, String> SUBJECT_ID = createField(
            DSL.name("subject_id"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbAuditLogRecord, String> OLD_VALUE = createField(
            DSL.name("old_value"), SQLDataType.CLOB.nullable(true), this, "");

    public final TableField<SbAuditLogRecord, String> NEW_VALUE = createField(
            DSL.name("new_value"), SQLDataType.CLOB.nullable(true), this, "");

    public SbAuditLog(final String prefix) {
        this(prefix, DSL.name(prefix + "audit_log"), null);
    }

    private SbAuditLog(final String prefix, final Name alias,
            final @Nullable Table<SbAuditLogRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbAuditLogRecord> getRecordType() {
        return SbAuditLogRecord.class;
    }

    @Override
    public UniqueKey<SbAuditLogRecord> getPrimaryKey() {
        return Keys.AUDIT_LOG_PK;
    }

    @Override
    public List<Index> getIndexes() {
        return List.of(
                Indexes.AUDIT_LOG_SUBJECT_IX,
                Indexes.AUDIT_LOG_AT_IX,
                Indexes.AUDIT_LOG_ACTOR_IX);
    }

    @Override
    public SbAuditLog as(final String alias) {
        return new SbAuditLog(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbAuditLog as(final Name alias) {
        return new SbAuditLog(this.prefix(), alias, this);
    }

    @Override
    public SbAuditLog rename(final String name) {
        return new SbAuditLog(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbAuditLog rename(final Name name) {
        return new SbAuditLog(this.prefix(), name, null);
    }
}
