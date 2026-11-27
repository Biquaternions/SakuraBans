package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbTemplateUsesRecord extends UpdatableRecordImpl<SbTemplateUsesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbTemplateUsesRecord() {
        super(SakuraTables.TEMPLATE_USES);
    }

    public Long id() {
        return this.get(SakuraTables.TEMPLATE_USES.ID);
    }

    public String template() {
        return this.get(SakuraTables.TEMPLATE_USES.TEMPLATE);
    }

    public @Nullable Integer templateVariantScopeId() {
        return this.get(SakuraTables.TEMPLATE_USES.TEMPLATE_VARIANT_SCOPE_ID);
    }

    public @Nullable Short step() {
        return this.get(SakuraTables.TEMPLATE_USES.STEP);
    }

    public Long victimId() {
        return this.get(SakuraTables.TEMPLATE_USES.VICTIM_ID);
    }

    public @Nullable Long punishmentId() {
        return this.get(SakuraTables.TEMPLATE_USES.PUNISHMENT_ID);
    }

    public OperatorKind operatorKind() {
        return this.get(SakuraTables.TEMPLATE_USES.OPERATOR_KIND);
    }

    public @Nullable UUID operatorUuid() {
        return this.get(SakuraTables.TEMPLATE_USES.OPERATOR_UUID);
    }

    public String operatorName() {
        return this.get(SakuraTables.TEMPLATE_USES.OPERATOR_NAME);
    }

    public String source() {
        return this.get(SakuraTables.TEMPLATE_USES.SOURCE);
    }

    public Instant createdAt() {
        return this.get(SakuraTables.TEMPLATE_USES.CREATED_AT);
    }
}
