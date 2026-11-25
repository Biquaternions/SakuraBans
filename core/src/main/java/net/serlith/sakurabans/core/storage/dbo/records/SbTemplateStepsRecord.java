package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Duration;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbTemplateStepsRecord extends UpdatableRecordImpl<SbTemplateStepsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbTemplateStepsRecord() {
        super(SakuraTables.TEMPLATE_STEPS);
    }

    public Integer templateId() {
        return this.get(SakuraTables.TEMPLATE_STEPS.TEMPLATE_ID);
    }

    public SbTemplateStepsRecord templateId(final Integer value) {
        this.set(SakuraTables.TEMPLATE_STEPS.TEMPLATE_ID, value);
        return this;
    }

    public Short position() {
        return this.get(SakuraTables.TEMPLATE_STEPS.POSITION);
    }

    public SbTemplateStepsRecord position(final Short value) {
        this.set(SakuraTables.TEMPLATE_STEPS.POSITION, value);
        return this;
    }

    public @Nullable Duration durationMs() {
        return this.get(SakuraTables.TEMPLATE_STEPS.DURATION_MS);
    }

    public SbTemplateStepsRecord durationMs(final @Nullable Duration value) {
        this.set(SakuraTables.TEMPLATE_STEPS.DURATION_MS, value);
        return this;
    }

    public String actions() {
        return this.get(SakuraTables.TEMPLATE_STEPS.ACTIONS);
    }

    public SbTemplateStepsRecord actions(final String value) {
        this.set(SakuraTables.TEMPLATE_STEPS.ACTIONS, value);
        return this;
    }
}
