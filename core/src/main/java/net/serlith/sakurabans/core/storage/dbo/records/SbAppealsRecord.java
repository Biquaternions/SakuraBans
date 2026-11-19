package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.api.appeal.Appeal;
import net.serlith.sakurabans.api.appeal.AppealOutcome;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbAppealsRecord extends UpdatableRecordImpl<SbAppealsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbAppealsRecord() {
        super(SakuraTables.APPEALS);
    }

    public Long id() {
        return this.get(SakuraTables.APPEALS.ID);
    }

    public SbAppealsRecord id(final Long value) {
        this.set(SakuraTables.APPEALS.ID, value);
        return this;
    }

    public Long punishmentId() {
        return this.get(SakuraTables.APPEALS.PUNISHMENT_ID);
    }

    public SbAppealsRecord punishmentId(final Long value) {
        this.set(SakuraTables.APPEALS.PUNISHMENT_ID, value);
        return this;
    }

    public UUID appellant() {
        return this.get(SakuraTables.APPEALS.APPELLANT);
    }

    public SbAppealsRecord appellant(final UUID value) {
        this.set(SakuraTables.APPEALS.APPELLANT, value);
        return this;
    }

    public String message() {
        return this.get(SakuraTables.APPEALS.MESSAGE);
    }

    public SbAppealsRecord message(final String value) {
        this.set(SakuraTables.APPEALS.MESSAGE, value);
        return this;
    }

    public Instant createdAt() {
        return this.get(SakuraTables.APPEALS.CREATED_AT);
    }

    public SbAppealsRecord createdAt(final Instant value) {
        this.set(SakuraTables.APPEALS.CREATED_AT, value);
        return this;
    }

    public Appeal.Status status() {
        return this.get(SakuraTables.APPEALS.STATUS);
    }

    public SbAppealsRecord status(final Appeal.Status value) {
        this.set(SakuraTables.APPEALS.STATUS, value);
        return this;
    }

    public @Nullable AppealOutcome outcome() {
        return this.get(SakuraTables.APPEALS.OUTCOME);
    }

    public SbAppealsRecord outcome(final @Nullable AppealOutcome value) {
        this.set(SakuraTables.APPEALS.OUTCOME, value);
        return this;
    }

    public @Nullable OperatorKind decidedByKind() {
        return this.get(SakuraTables.APPEALS.DECIDED_BY_KIND);
    }

    public SbAppealsRecord decidedByKind(final @Nullable OperatorKind value) {
        this.set(SakuraTables.APPEALS.DECIDED_BY_KIND, value);
        return this;
    }

    public @Nullable UUID decidedByUuid() {
        return this.get(SakuraTables.APPEALS.DECIDED_BY_UUID);
    }

    public SbAppealsRecord decidedByUuid(final @Nullable UUID value) {
        this.set(SakuraTables.APPEALS.DECIDED_BY_UUID, value);
        return this;
    }

    public @Nullable String decidedByName() {
        return this.get(SakuraTables.APPEALS.DECIDED_BY_NAME);
    }

    public SbAppealsRecord decidedByName(final @Nullable String value) {
        this.set(SakuraTables.APPEALS.DECIDED_BY_NAME, value);
        return this;
    }

    public @Nullable String decidedBySource() {
        return this.get(SakuraTables.APPEALS.DECIDED_BY_SOURCE);
    }

    public SbAppealsRecord decidedBySource(final @Nullable String value) {
        this.set(SakuraTables.APPEALS.DECIDED_BY_SOURCE, value);
        return this;
    }

    public @Nullable Instant decidedAt() {
        return this.get(SakuraTables.APPEALS.DECIDED_AT);
    }

    public SbAppealsRecord decidedAt(final @Nullable Instant value) {
        this.set(SakuraTables.APPEALS.DECIDED_AT, value);
        return this;
    }

    public @Nullable String note() {
        return this.get(SakuraTables.APPEALS.NOTE);
    }

    public SbAppealsRecord note(final @Nullable String value) {
        this.set(SakuraTables.APPEALS.NOTE, value);
        return this;
    }

    public Long version() {
        return this.get(SakuraTables.APPEALS.VERSION);
    }

    public SbAppealsRecord version(final Long value) {
        this.set(SakuraTables.APPEALS.VERSION, value);
        return this;
    }
}
