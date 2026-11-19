package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbActivePunishmentsRecord
        extends UpdatableRecordImpl<SbActivePunishmentsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbActivePunishmentsRecord() {
        super(SakuraTables.ACTIVE_PUNISHMENTS);
    }

    public Long punishmentId() {
        return this.get(SakuraTables.ACTIVE_PUNISHMENTS.PUNISHMENT_ID);
    }

    public SbActivePunishmentsRecord punishmentId(final Long value) {
        this.set(SakuraTables.ACTIVE_PUNISHMENTS.PUNISHMENT_ID, value);
        return this;
    }

    public PunishmentType type() {
        return this.get(SakuraTables.ACTIVE_PUNISHMENTS.TYPE);
    }

    public SbActivePunishmentsRecord type(final PunishmentType value) {
        this.set(SakuraTables.ACTIVE_PUNISHMENTS.TYPE, value);
        return this;
    }

    public Long victimId() {
        return this.get(SakuraTables.ACTIVE_PUNISHMENTS.VICTIM_ID);
    }

    public SbActivePunishmentsRecord victimId(final Long value) {
        this.set(SakuraTables.ACTIVE_PUNISHMENTS.VICTIM_ID, value);
        return this;
    }

    public Integer scopeId() {
        return this.get(SakuraTables.ACTIVE_PUNISHMENTS.SCOPE_ID);
    }

    public SbActivePunishmentsRecord scopeId(final Integer value) {
        this.set(SakuraTables.ACTIVE_PUNISHMENTS.SCOPE_ID, value);
        return this;
    }

    public @Nullable Instant expiresAt() {
        return this.get(SakuraTables.ACTIVE_PUNISHMENTS.EXPIRES_AT);
    }

    public SbActivePunishmentsRecord expiresAt(final @Nullable Instant value) {
        this.set(SakuraTables.ACTIVE_PUNISHMENTS.EXPIRES_AT, value);
        return this;
    }

    public Long exclusivitySlot() {
        return this.get(SakuraTables.ACTIVE_PUNISHMENTS.EXCLUSIVITY_SLOT);
    }

    public SbActivePunishmentsRecord exclusivitySlot(final Long value) {
        this.set(SakuraTables.ACTIVE_PUNISHMENTS.EXCLUSIVITY_SLOT, value);
        return this;
    }
}
