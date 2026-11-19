package net.serlith.sakurabans.core.storage.dbo;

import java.io.Serial;
import java.util.List;
import org.jooq.Schema;
import org.jooq.impl.CatalogImpl;

public final class SakuraCatalog extends CatalogImpl {

    @Serial
    private static final long serialVersionUID = 1L;

    public SakuraCatalog() {
        super("");
    }

    @Override
    public List<Schema> getSchemas() {
        return List.of(SakuraTables.database());
    }
}
