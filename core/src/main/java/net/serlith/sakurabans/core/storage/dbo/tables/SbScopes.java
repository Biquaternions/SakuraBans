package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.util.List;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.ScopeKind;
import net.serlith.sakurabans.core.storage.dbo.records.SbScopesRecord;
import org.jooq.Constraint;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbScopes extends SbTable<SbScopesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbScopesRecord, Integer> ID = createField(
            DSL.name("id"), SQLDataType.INTEGER.nullable(false).generatedByDefaultAsIdentity(),
            this, "");

    public final TableField<SbScopesRecord, ScopeKind> KIND = createField(
            DSL.name("kind"), SbTypes.SCOPE_KIND.nullable(false), this, "");

    public final TableField<SbScopesRecord, String> VALUE = createField(
            DSL.name("value"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public SbScopes(final String prefix) {
        this(prefix, DSL.name(prefix + "scopes"), null);
    }

    private SbScopes(final String prefix, final Name alias,
            final @Nullable Table<SbScopesRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbScopesRecord> getRecordType() {
        return SbScopesRecord.class;
    }

    @Override
    public UniqueKey<SbScopesRecord> getPrimaryKey() {
        return Keys.SCOPES_PK;
    }

    @Override
    public List<UniqueKey<SbScopesRecord>> getUniqueKeys() {
        return List.of(Keys.SCOPES_IDENTITY_UQ);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("global_ck",
                        this.KIND.eq(ScopeKind.GLOBAL).and(this.VALUE.eq("*"))
                                .or(this.KIND.ne(ScopeKind.GLOBAL).and(this.VALUE.ne("*")))));
    }

    @Override
    public SbScopes as(final String alias) {
        return new SbScopes(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbScopes as(final Name alias) {
        return new SbScopes(this.prefix(), alias, this);
    }

    @Override
    public SbScopes rename(final String name) {
        return new SbScopes(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbScopes rename(final Name name) {
        return new SbScopes(this.prefix(), name, null);
    }
}
