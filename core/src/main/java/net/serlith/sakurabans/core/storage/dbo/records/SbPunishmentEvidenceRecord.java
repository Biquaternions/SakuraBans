package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbPunishmentEvidenceRecord
        extends UpdatableRecordImpl<SbPunishmentEvidenceRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbPunishmentEvidenceRecord() {
        super(SakuraTables.PUNISHMENT_EVIDENCE);
    }

    public Long punishmentId() {
        return this.get(SakuraTables.PUNISHMENT_EVIDENCE.PUNISHMENT_ID);
    }

    public SbPunishmentEvidenceRecord punishmentId(final Long value) {
        this.set(SakuraTables.PUNISHMENT_EVIDENCE.PUNISHMENT_ID, value);
        return this;
    }

    public String uri() {
        return this.get(SakuraTables.PUNISHMENT_EVIDENCE.URI);
    }

    public SbPunishmentEvidenceRecord uri(final String value) {
        this.set(SakuraTables.PUNISHMENT_EVIDENCE.URI, value);
        return this;
    }

    public OperatorKind addedByKind() {
        return this.get(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_BY_KIND);
    }

    public SbPunishmentEvidenceRecord addedByKind(final OperatorKind value) {
        this.set(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_BY_KIND, value);
        return this;
    }

    public @Nullable UUID addedByUuid() {
        return this.get(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_BY_UUID);
    }

    public SbPunishmentEvidenceRecord addedByUuid(final @Nullable UUID value) {
        this.set(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_BY_UUID, value);
        return this;
    }

    public String addedByName() {
        return this.get(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_BY_NAME);
    }

    public SbPunishmentEvidenceRecord addedByName(final String value) {
        this.set(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_BY_NAME, value);
        return this;
    }

    public String addedBySource() {
        return this.get(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_BY_SOURCE);
    }

    public SbPunishmentEvidenceRecord addedBySource(final String value) {
        this.set(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_BY_SOURCE, value);
        return this;
    }

    public Instant addedAt() {
        return this.get(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_AT);
    }

    public SbPunishmentEvidenceRecord addedAt(final Instant value) {
        this.set(SakuraTables.PUNISHMENT_EVIDENCE.ADDED_AT, value);
        return this;
    }
}
