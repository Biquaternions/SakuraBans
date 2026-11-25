package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbTemplatesRecord extends UpdatableRecordImpl<SbTemplatesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbTemplatesRecord() {
        super(SakuraTables.TEMPLATES);
    }

    public Integer id() {
        return this.get(SakuraTables.TEMPLATES.ID);
    }

    public SbTemplatesRecord id(final Integer value) {
        this.set(SakuraTables.TEMPLATES.ID, value);
        return this;
    }

    public String name() {
        return this.get(SakuraTables.TEMPLATES.NAME);
    }

    public SbTemplatesRecord name(final String value) {
        this.set(SakuraTables.TEMPLATES.NAME, value);
        return this;
    }

    public String kind() {
        return this.get(SakuraTables.TEMPLATES.KIND);
    }

    public SbTemplatesRecord kind(final String value) {
        this.set(SakuraTables.TEMPLATES.KIND, value);
        return this;
    }

    public PunishmentType type() {
        return this.get(SakuraTables.TEMPLATES.TYPE);
    }

    public SbTemplatesRecord type(final PunishmentType value) {
        this.set(SakuraTables.TEMPLATES.TYPE, value);
        return this;
    }

    public String reason() {
        return this.get(SakuraTables.TEMPLATES.REASON);
    }

    public SbTemplatesRecord reason(final String value) {
        this.set(SakuraTables.TEMPLATES.REASON, value);
        return this;
    }

    public String requiredEvidence() {
        return this.get(SakuraTables.TEMPLATES.REQUIRED_EVIDENCE);
    }

    public SbTemplatesRecord requiredEvidence(final String value) {
        this.set(SakuraTables.TEMPLATES.REQUIRED_EVIDENCE, value);
        return this;
    }

    public String captureEvidence() {
        return this.get(SakuraTables.TEMPLATES.CAPTURE_EVIDENCE);
    }

    public SbTemplatesRecord captureEvidence(final String value) {
        this.set(SakuraTables.TEMPLATES.CAPTURE_EVIDENCE, value);
        return this;
    }

    public @Nullable Duration ladderExpiryMs() {
        return this.get(SakuraTables.TEMPLATES.LADDER_EXPIRY_MS);
    }

    public SbTemplatesRecord ladderExpiryMs(final @Nullable Duration value) {
        this.set(SakuraTables.TEMPLATES.LADDER_EXPIRY_MS, value);
        return this;
    }

    public Boolean addressShared() {
        return this.get(SakuraTables.TEMPLATES.ADDRESS_SHARED);
    }

    public SbTemplatesRecord addressShared(final Boolean value) {
        this.set(SakuraTables.TEMPLATES.ADDRESS_SHARED, value);
        return this;
    }

    public Boolean permissionRequired() {
        return this.get(SakuraTables.TEMPLATES.PERMISSION_REQUIRED);
    }

    public SbTemplatesRecord permissionRequired(final Boolean value) {
        this.set(SakuraTables.TEMPLATES.PERMISSION_REQUIRED, value);
        return this;
    }

    public String actions() {
        return this.get(SakuraTables.TEMPLATES.ACTIONS);
    }

    public SbTemplatesRecord actions(final String value) {
        this.set(SakuraTables.TEMPLATES.ACTIONS, value);
        return this;
    }

    public Long version() {
        return this.get(SakuraTables.TEMPLATES.VERSION);
    }

    public SbTemplatesRecord version(final Long value) {
        this.set(SakuraTables.TEMPLATES.VERSION, value);
        return this;
    }

    public Instant createdAt() {
        return this.get(SakuraTables.TEMPLATES.CREATED_AT);
    }

    public SbTemplatesRecord createdAt(final Instant value) {
        this.set(SakuraTables.TEMPLATES.CREATED_AT, value);
        return this;
    }

    public Instant updatedAt() {
        return this.get(SakuraTables.TEMPLATES.UPDATED_AT);
    }

    public SbTemplatesRecord updatedAt(final Instant value) {
        this.set(SakuraTables.TEMPLATES.UPDATED_AT, value);
        return this;
    }
}
