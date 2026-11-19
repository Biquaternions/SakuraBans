package net.serlith.sakurabans.core.storage.dbo;

import java.io.Serial;
import java.util.List;
import org.jooq.Catalog;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;

public final class SakuraDatabase extends SchemaImpl {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Table<?>> tables;

    public SakuraDatabase(final String name, final Catalog catalog,
            final List<? extends Table<?>> tables) {
        super(name, catalog);
        this.tables = List.copyOf(tables);
    }

    @Override
    public List<Table<?>> getTables() {
        return this.tables;
    }
}
