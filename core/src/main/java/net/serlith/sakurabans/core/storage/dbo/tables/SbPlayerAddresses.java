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
import net.serlith.sakurabans.core.storage.dbo.records.SbPlayerAddressesRecord;
import org.jooq.Constraint;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbPlayerAddresses extends SbTable<SbPlayerAddressesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbPlayerAddressesRecord, UUID> UUID = createField(
            DSL.name("uuid"), SbTypes.UUID.nullable(false), this, "");

    public final TableField<SbPlayerAddressesRecord, InetAddress> ADDRESS = createField(
            DSL.name("address"), SbTypes.INET.nullable(false), this, "");

    public final TableField<SbPlayerAddressesRecord, Instant> FIRST_SEEN = createField(
            DSL.name("first_seen"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbPlayerAddressesRecord, Instant> LAST_SEEN = createField(
            DSL.name("last_seen"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbPlayerAddressesRecord, String> LAST_SERVER = createField(
            DSL.name("last_server"), SQLDataType.VARCHAR(64).nullable(true), this, "");

    public final TableField<SbPlayerAddressesRecord, byte[]> ADDRESS_HASH = createField(
            DSL.name("address_hash"), SbTypes.binary(32).nullable(false), this, "");

    public SbPlayerAddresses(final String prefix) {
        this(prefix, DSL.name(prefix + "player_addresses"), null);
    }

    private SbPlayerAddresses(final String prefix, final Name alias,
            final @Nullable Table<SbPlayerAddressesRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbPlayerAddressesRecord> getRecordType() {
        return SbPlayerAddressesRecord.class;
    }

    @Override
    public UniqueKey<SbPlayerAddressesRecord> getPrimaryKey() {
        return Keys.PLAYER_ADDRESSES_PK;
    }

    @Override
    public List<ForeignKey<SbPlayerAddressesRecord, ?>> getReferences() {
        return List.of(
                Keys.PLAYER_ADDRESSES_PLAYER_FK);
    }

    @Override
    public List<Index> getIndexes() {
        return List.of(
                Indexes.PLAYER_ADDRESSES_ADDRESS_IX);
    }

    @Override
    public List<Constraint> checks() {
        return List.of(
                this.check("address_ck",
                        octets(this.ADDRESS).in(4, 16).and(octets(this.ADDRESS_HASH).eq(32))));
    }

    @Override
    public SbPlayerAddresses as(final String alias) {
        return new SbPlayerAddresses(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbPlayerAddresses as(final Name alias) {
        return new SbPlayerAddresses(this.prefix(), alias, this);
    }

    @Override
    public SbPlayerAddresses rename(final String name) {
        return new SbPlayerAddresses(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbPlayerAddresses rename(final Name name) {
        return new SbPlayerAddresses(this.prefix(), name, null);
    }
}
