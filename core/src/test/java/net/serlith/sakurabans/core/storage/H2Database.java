package net.serlith.sakurabans.core.storage;

import java.util.Optional;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/** A fresh in-memory H2 database per call, with the tables for the default prefix. */
final class H2Database {

    static final String PREFIX = "sb_";

    private H2Database() {
    }

    static DSLContext create() {
        SakuraTables.initialize(PREFIX, Optional.empty());
        final JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return DSL.using(dataSource, SQLDialect.H2,
                StorageService.jooqSettings(Optional.empty()));
    }
}
