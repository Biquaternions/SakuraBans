package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;

public final class SbSettingOverridesRecord extends UpdatableRecordImpl<SbSettingOverridesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbSettingOverridesRecord() {
        super(SakuraTables.SETTING_OVERRIDES);
    }

    public String name() {
        return this.get(SakuraTables.SETTING_OVERRIDES.K);
    }

    public SbSettingOverridesRecord name(final String value) {
        this.set(SakuraTables.SETTING_OVERRIDES.K, value);
        return this;
    }

    public String value() {
        return this.get(SakuraTables.SETTING_OVERRIDES.V);
    }

    public SbSettingOverridesRecord value(final String value) {
        this.set(SakuraTables.SETTING_OVERRIDES.V, value);
        return this;
    }

    public Instant updatedAt() {
        return this.get(SakuraTables.SETTING_OVERRIDES.UPDATED_AT);
    }

    public SbSettingOverridesRecord updatedAt(final Instant value) {
        this.set(SakuraTables.SETTING_OVERRIDES.UPDATED_AT, value);
        return this;
    }
}
