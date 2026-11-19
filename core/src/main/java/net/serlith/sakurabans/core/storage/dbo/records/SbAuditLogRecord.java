package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.api.audit.AuditAction;
import net.serlith.sakurabans.api.audit.SubjectType;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbAuditLogRecord extends UpdatableRecordImpl<SbAuditLogRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbAuditLogRecord() {
        super(SakuraTables.AUDIT_LOG);
    }

    public Long id() {
        return this.get(SakuraTables.AUDIT_LOG.ID);
    }

    public SbAuditLogRecord id(final Long value) {
        this.set(SakuraTables.AUDIT_LOG.ID, value);
        return this;
    }

    public Instant at() {
        return this.get(SakuraTables.AUDIT_LOG.AT);
    }

    public SbAuditLogRecord at(final Instant value) {
        this.set(SakuraTables.AUDIT_LOG.AT, value);
        return this;
    }

    public OperatorKind actorKind() {
        return this.get(SakuraTables.AUDIT_LOG.ACTOR_KIND);
    }

    public SbAuditLogRecord actorKind(final OperatorKind value) {
        this.set(SakuraTables.AUDIT_LOG.ACTOR_KIND, value);
        return this;
    }

    public @Nullable UUID actorUuid() {
        return this.get(SakuraTables.AUDIT_LOG.ACTOR_UUID);
    }

    public SbAuditLogRecord actorUuid(final @Nullable UUID value) {
        this.set(SakuraTables.AUDIT_LOG.ACTOR_UUID, value);
        return this;
    }

    public String actorName() {
        return this.get(SakuraTables.AUDIT_LOG.ACTOR_NAME);
    }

    public SbAuditLogRecord actorName(final String value) {
        this.set(SakuraTables.AUDIT_LOG.ACTOR_NAME, value);
        return this;
    }

    public String source() {
        return this.get(SakuraTables.AUDIT_LOG.SOURCE);
    }

    public SbAuditLogRecord source(final String value) {
        this.set(SakuraTables.AUDIT_LOG.SOURCE, value);
        return this;
    }

    public AuditAction action() {
        return this.get(SakuraTables.AUDIT_LOG.ACTION);
    }

    public SbAuditLogRecord action(final AuditAction value) {
        this.set(SakuraTables.AUDIT_LOG.ACTION, value);
        return this;
    }

    public SubjectType subjectType() {
        return this.get(SakuraTables.AUDIT_LOG.SUBJECT_TYPE);
    }

    public SbAuditLogRecord subjectType(final SubjectType value) {
        this.set(SakuraTables.AUDIT_LOG.SUBJECT_TYPE, value);
        return this;
    }

    public String subjectId() {
        return this.get(SakuraTables.AUDIT_LOG.SUBJECT_ID);
    }

    public SbAuditLogRecord subjectId(final String value) {
        this.set(SakuraTables.AUDIT_LOG.SUBJECT_ID, value);
        return this;
    }

    public @Nullable String oldValue() {
        return this.get(SakuraTables.AUDIT_LOG.OLD_VALUE);
    }

    public SbAuditLogRecord oldValue(final @Nullable String value) {
        this.set(SakuraTables.AUDIT_LOG.OLD_VALUE, value);
        return this;
    }

    public @Nullable String newValue() {
        return this.get(SakuraTables.AUDIT_LOG.NEW_VALUE);
    }

    public SbAuditLogRecord newValue(final @Nullable String value) {
        this.set(SakuraTables.AUDIT_LOG.NEW_VALUE, value);
        return this;
    }
}
