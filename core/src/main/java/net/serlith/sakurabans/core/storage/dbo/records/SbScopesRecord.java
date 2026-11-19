package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.ScopeKind;
import org.jooq.impl.UpdatableRecordImpl;

public final class SbScopesRecord extends UpdatableRecordImpl<SbScopesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbScopesRecord() {
        super(SakuraTables.SCOPES);
    }

    public Integer id() {
        return this.get(SakuraTables.SCOPES.ID);
    }

    public SbScopesRecord id(final Integer value) {
        this.set(SakuraTables.SCOPES.ID, value);
        return this;
    }

    public ScopeKind kind() {
        return this.get(SakuraTables.SCOPES.KIND);
    }

    public SbScopesRecord kind(final ScopeKind value) {
        this.set(SakuraTables.SCOPES.KIND, value);
        return this;
    }

    public String value() {
        return this.get(SakuraTables.SCOPES.VALUE);
    }

    public SbScopesRecord value(final String value) {
        this.set(SakuraTables.SCOPES.VALUE, value);
        return this;
    }
}
