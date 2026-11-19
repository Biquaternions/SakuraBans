package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import net.serlith.sakurabans.api.kind.AddressMode;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbKindsRecord extends UpdatableRecordImpl<SbKindsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbKindsRecord() {
        super(SakuraTables.KINDS);
    }

    public String id() {
        return this.get(SakuraTables.KINDS.ID);
    }

    public SbKindsRecord id(final String value) {
        this.set(SakuraTables.KINDS.ID, value);
        return this;
    }

    public String displayName() {
        return this.get(SakuraTables.KINDS.DISPLAY_NAME);
    }

    public SbKindsRecord displayName(final String value) {
        this.set(SakuraTables.KINDS.DISPLAY_NAME, value);
        return this;
    }

    public PunishmentType baseType() {
        return this.get(SakuraTables.KINDS.BASE_TYPE);
    }

    public SbKindsRecord baseType(final PunishmentType value) {
        this.set(SakuraTables.KINDS.BASE_TYPE, value);
        return this;
    }

    public Boolean shadow() {
        return this.get(SakuraTables.KINDS.SHADOW);
    }

    public SbKindsRecord shadow(final Boolean value) {
        this.set(SakuraTables.KINDS.SHADOW, value);
        return this;
    }

    public @Nullable AddressStrictness strictness() {
        return this.get(SakuraTables.KINDS.STRICTNESS);
    }

    public SbKindsRecord strictness(final @Nullable AddressStrictness value) {
        this.set(SakuraTables.KINDS.STRICTNESS, value);
        return this;
    }

    public @Nullable Integer defaultScopeId() {
        return this.get(SakuraTables.KINDS.DEFAULT_SCOPE_ID);
    }

    public SbKindsRecord defaultScopeId(final @Nullable Integer value) {
        this.set(SakuraTables.KINDS.DEFAULT_SCOPE_ID, value);
        return this;
    }

    public AddressMode addressMode() {
        return this.get(SakuraTables.KINDS.ADDRESS_MODE);
    }

    public SbKindsRecord addressMode(final AddressMode value) {
        this.set(SakuraTables.KINDS.ADDRESS_MODE, value);
        return this;
    }

    public Boolean permanentOnly() {
        return this.get(SakuraTables.KINDS.PERMANENT_ONLY);
    }

    public SbKindsRecord permanentOnly(final Boolean value) {
        this.set(SakuraTables.KINDS.PERMANENT_ONLY, value);
        return this;
    }

    public String requiredEvidence() {
        return this.get(SakuraTables.KINDS.REQUIRED_EVIDENCE);
    }

    public SbKindsRecord requiredEvidence(final String value) {
        this.set(SakuraTables.KINDS.REQUIRED_EVIDENCE, value);
        return this;
    }

    public Boolean builtIn() {
        return this.get(SakuraTables.KINDS.BUILT_IN);
    }

    public SbKindsRecord builtIn(final Boolean value) {
        this.set(SakuraTables.KINDS.BUILT_IN, value);
        return this;
    }

    public Long version() {
        return this.get(SakuraTables.KINDS.VERSION);
    }

    public SbKindsRecord version(final Long value) {
        this.set(SakuraTables.KINDS.VERSION, value);
        return this;
    }

    public Instant createdAt() {
        return this.get(SakuraTables.KINDS.CREATED_AT);
    }

    public SbKindsRecord createdAt(final Instant value) {
        this.set(SakuraTables.KINDS.CREATED_AT, value);
        return this;
    }

    public Instant updatedAt() {
        return this.get(SakuraTables.KINDS.UPDATED_AT);
    }

    public SbKindsRecord updatedAt(final Instant value) {
        this.set(SakuraTables.KINDS.UPDATED_AT, value);
        return this;
    }
}
