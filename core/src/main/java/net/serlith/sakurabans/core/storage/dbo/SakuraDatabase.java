package net.serlith.sakurabans.core.storage.dbo;

import org.jooq.Catalog;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;
import org.jspecify.annotations.NullMarked;

import java.io.Serial;
import java.util.List;

@NullMarked
public class SakuraDatabase extends SchemaImpl {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Table<?>> tables;

    public SakuraDatabase(String name, Catalog catalog, final List<Table<?>> tables) {
        super(name, catalog);
        this.tables = tables;
    }

    @Override
    public List<Table<?>> getTables() {
        return this.tables;
    }
}
