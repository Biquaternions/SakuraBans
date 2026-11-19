package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbBanWaveEntriesRecord extends UpdatableRecordImpl<SbBanWaveEntriesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbBanWaveEntriesRecord() {
        super(SakuraTables.BAN_WAVE_ENTRIES);
    }

    public Long id() {
        return this.get(SakuraTables.BAN_WAVE_ENTRIES.ID);
    }

    public SbBanWaveEntriesRecord id(final Long value) {
        this.set(SakuraTables.BAN_WAVE_ENTRIES.ID, value);
        return this;
    }

    public Long waveId() {
        return this.get(SakuraTables.BAN_WAVE_ENTRIES.WAVE_ID);
    }

    public SbBanWaveEntriesRecord waveId(final Long value) {
        this.set(SakuraTables.BAN_WAVE_ENTRIES.WAVE_ID, value);
        return this;
    }

    public String draft() {
        return this.get(SakuraTables.BAN_WAVE_ENTRIES.DRAFT);
    }

    public SbBanWaveEntriesRecord draft(final String value) {
        this.set(SakuraTables.BAN_WAVE_ENTRIES.DRAFT, value);
        return this;
    }

    public OperatorKind addedByKind() {
        return this.get(SakuraTables.BAN_WAVE_ENTRIES.ADDED_BY_KIND);
    }

    public SbBanWaveEntriesRecord addedByKind(final OperatorKind value) {
        this.set(SakuraTables.BAN_WAVE_ENTRIES.ADDED_BY_KIND, value);
        return this;
    }

    public @Nullable UUID addedByUuid() {
        return this.get(SakuraTables.BAN_WAVE_ENTRIES.ADDED_BY_UUID);
    }

    public SbBanWaveEntriesRecord addedByUuid(final @Nullable UUID value) {
        this.set(SakuraTables.BAN_WAVE_ENTRIES.ADDED_BY_UUID, value);
        return this;
    }

    public String addedByName() {
        return this.get(SakuraTables.BAN_WAVE_ENTRIES.ADDED_BY_NAME);
    }

    public SbBanWaveEntriesRecord addedByName(final String value) {
        this.set(SakuraTables.BAN_WAVE_ENTRIES.ADDED_BY_NAME, value);
        return this;
    }

    public String addedBySource() {
        return this.get(SakuraTables.BAN_WAVE_ENTRIES.ADDED_BY_SOURCE);
    }

    public SbBanWaveEntriesRecord addedBySource(final String value) {
        this.set(SakuraTables.BAN_WAVE_ENTRIES.ADDED_BY_SOURCE, value);
        return this;
    }

    public Instant addedAt() {
        return this.get(SakuraTables.BAN_WAVE_ENTRIES.ADDED_AT);
    }

    public SbBanWaveEntriesRecord addedAt(final Instant value) {
        this.set(SakuraTables.BAN_WAVE_ENTRIES.ADDED_AT, value);
        return this;
    }

    public @Nullable String result() {
        return this.get(SakuraTables.BAN_WAVE_ENTRIES.RESULT);
    }

    public SbBanWaveEntriesRecord result(final @Nullable String value) {
        this.set(SakuraTables.BAN_WAVE_ENTRIES.RESULT, value);
        return this;
    }

    public @Nullable Long punishmentId() {
        return this.get(SakuraTables.BAN_WAVE_ENTRIES.PUNISHMENT_ID);
    }

    public SbBanWaveEntriesRecord punishmentId(final @Nullable Long value) {
        this.set(SakuraTables.BAN_WAVE_ENTRIES.PUNISHMENT_ID, value);
        return this;
    }
}
