package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.Indexes;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbPlayersRecord;
import org.jooq.Constraint;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbPlayers extends SbTable<SbPlayersRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbPlayersRecord, UUID> UUID = createField(
            DSL.name("uuid"), SbTypes.UUID.nullable(false), this, "");

    public final TableField<SbPlayersRecord, String> LAST_NAME = createField(
            DSL.name("last_name"), SQLDataType.VARCHAR(32).nullable(false), this, "");

    public final TableField<SbPlayersRecord, String> LAST_NAME_LOWER = createField(
            DSL.name("last_name_lower"), SQLDataType.VARCHAR(32).nullable(false), this, "");

    public final TableField<SbPlayersRecord, Instant> FIRST_SEEN = createField(
            DSL.name("first_seen"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbPlayersRecord, Instant> LAST_SEEN = createField(
            DSL.name("last_seen"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbPlayersRecord, InetAddress> LAST_ADDRESS = createField(
            DSL.name("last_address"), SbTypes.INET.nullable(true), this, "");

    public SbPlayers(final String prefix) {
        this(prefix, DSL.name(prefix + "players"), null);
    }

    private SbPlayers(final String prefix, final Name alias,
            final @Nullable Table<SbPlayersRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbPlayersRecord> getRecordType() {
        return SbPlayersRecord.class;
    }

    @Override
    public UniqueKey<SbPlayersRecord> getPrimaryKey() {
        return Keys.PLAYERS_PK;
    }

    @Override
    public List<Index> getIndexes() {
        return List.of(
                Indexes.PLAYERS_LAST_NAME_IX);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("last_address_ck",
                        this.LAST_ADDRESS.isNull().or(octets(this.LAST_ADDRESS).in(4, 16))));
    }

    @Override
    public SbPlayers as(final String alias) {
        return new SbPlayers(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbPlayers as(final Name alias) {
        return new SbPlayers(this.prefix(), alias, this);
    }

    @Override
    public SbPlayers rename(final String name) {
        return new SbPlayers(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbPlayers rename(final Name name) {
        return new SbPlayers(this.prefix(), name, null);
    }
}
