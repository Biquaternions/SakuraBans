package net.serlith.sakurabans.core.storage.dbo;

import java.io.Serial;
import java.util.List;
import org.jooq.Catalog;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;

public class SakuraDatabase extends SchemaImpl {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Table<?>> tables;

    /**
     * Creates the schema.
     *
     * @param name the schema name
     * @param catalog the catalog that owns the schema
     * @param tables the tables of the schema
     */
    public SakuraDatabase(final String name, final Catalog catalog, final List<Table<?>> tables) {
        super(name, catalog);
        this.tables = tables;
    }

    @Override
    public List<Table<?>> getTables() {
        return this.tables;
    }
}
