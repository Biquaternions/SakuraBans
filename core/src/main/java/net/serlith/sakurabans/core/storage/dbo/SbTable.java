package net.serlith.sakurabans.core.storage.dbo;

import java.io.Serial;
import java.util.List;
import org.jooq.Condition;
import org.jooq.Constraint;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableOptions;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;
import org.jspecify.annotations.Nullable;

public abstract class SbTable<R extends UpdatableRecord<R>> extends TableImpl<R> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String prefix;

    protected SbTable(final String prefix, final Name name, final @Nullable Table<R> aliased) {
        super(name, null, aliased, null, DSL.comment(""), TableOptions.table());
        this.prefix = prefix;
    }

    public final String prefix() {
        return this.prefix;
    }

    @Override
    public final @Nullable Schema getSchema() {
        return this.aliased() ? null : SakuraTables.database();
    }

    public List<Constraint> checks() {
        return List.of();
    }

    protected final Constraint check(final String suffix, final Condition condition) {
        return DSL.constraint(DSL.name(this.getName() + "_" + suffix)).check(condition);
    }

    /** The length in bytes of a binary column. */
    protected static Field<Integer> octets(final Field<?> field) {
        return DSL.octetLength(field.coerce(SQLDataType.VARCHAR));
    }
}
