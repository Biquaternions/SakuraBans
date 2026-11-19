package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.api.wave.BanWave;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbBanWavesRecord extends UpdatableRecordImpl<SbBanWavesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbBanWavesRecord() {
        super(SakuraTables.BAN_WAVES);
    }

    public Long id() {
        return this.get(SakuraTables.BAN_WAVES.ID);
    }

    public SbBanWavesRecord id(final Long value) {
        this.set(SakuraTables.BAN_WAVES.ID, value);
        return this;
    }

    public String name() {
        return this.get(SakuraTables.BAN_WAVES.NAME);
    }

    public SbBanWavesRecord name(final String value) {
        this.set(SakuraTables.BAN_WAVES.NAME, value);
        return this;
    }

    public BanWave.Status status() {
        return this.get(SakuraTables.BAN_WAVES.STATUS);
    }

    public SbBanWavesRecord status(final BanWave.Status value) {
        this.set(SakuraTables.BAN_WAVES.STATUS, value);
        return this;
    }

    public @Nullable Instant scheduledAt() {
        return this.get(SakuraTables.BAN_WAVES.SCHEDULED_AT);
    }

    public SbBanWavesRecord scheduledAt(final @Nullable Instant value) {
        this.set(SakuraTables.BAN_WAVES.SCHEDULED_AT, value);
        return this;
    }

    public OperatorKind createdByKind() {
        return this.get(SakuraTables.BAN_WAVES.CREATED_BY_KIND);
    }

    public SbBanWavesRecord createdByKind(final OperatorKind value) {
        this.set(SakuraTables.BAN_WAVES.CREATED_BY_KIND, value);
        return this;
    }

    public @Nullable UUID createdByUuid() {
        return this.get(SakuraTables.BAN_WAVES.CREATED_BY_UUID);
    }

    public SbBanWavesRecord createdByUuid(final @Nullable UUID value) {
        this.set(SakuraTables.BAN_WAVES.CREATED_BY_UUID, value);
        return this;
    }

    public String createdByName() {
        return this.get(SakuraTables.BAN_WAVES.CREATED_BY_NAME);
    }

    public SbBanWavesRecord createdByName(final String value) {
        this.set(SakuraTables.BAN_WAVES.CREATED_BY_NAME, value);
        return this;
    }

    public String createdBySource() {
        return this.get(SakuraTables.BAN_WAVES.CREATED_BY_SOURCE);
    }

    public SbBanWavesRecord createdBySource(final String value) {
        this.set(SakuraTables.BAN_WAVES.CREATED_BY_SOURCE, value);
        return this;
    }

    public Instant createdAt() {
        return this.get(SakuraTables.BAN_WAVES.CREATED_AT);
    }

    public SbBanWavesRecord createdAt(final Instant value) {
        this.set(SakuraTables.BAN_WAVES.CREATED_AT, value);
        return this;
    }

    public @Nullable Instant releasedAt() {
        return this.get(SakuraTables.BAN_WAVES.RELEASED_AT);
    }

    public SbBanWavesRecord releasedAt(final @Nullable Instant value) {
        this.set(SakuraTables.BAN_WAVES.RELEASED_AT, value);
        return this;
    }

    public Long version() {
        return this.get(SakuraTables.BAN_WAVES.VERSION);
    }

    public SbBanWavesRecord version(final Long value) {
        this.set(SakuraTables.BAN_WAVES.VERSION, value);
        return this;
    }
}
