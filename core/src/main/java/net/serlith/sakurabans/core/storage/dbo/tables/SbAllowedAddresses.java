package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbAllowedAddressesRecord;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbAllowedAddresses extends SbTable<SbAllowedAddressesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbAllowedAddressesRecord, InetAddress> ADDRESS = createField(
            DSL.name("address"), SbTypes.INET.nullable(false), this, "");

    public final TableField<SbAllowedAddressesRecord, OperatorKind> ADDED_BY_KIND = createField(
            DSL.name("added_by_kind"), SbTypes.OPERATOR_KIND.nullable(false), this, "");

    public final TableField<SbAllowedAddressesRecord, UUID> ADDED_BY_UUID = createField(
            DSL.name("added_by_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbAllowedAddressesRecord, String> ADDED_BY_NAME = createField(
            DSL.name("added_by_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbAllowedAddressesRecord, String> ADDED_BY_SOURCE = createField(
            DSL.name("added_by_source"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbAllowedAddressesRecord, Instant> ADDED_AT = createField(
            DSL.name("added_at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbAllowedAddressesRecord, String> NOTE = createField(
            DSL.name("note"), SQLDataType.VARCHAR(256).nullable(true), this, "");

    public SbAllowedAddresses(final String prefix) {
        this(prefix, DSL.name(prefix + "allowed_addresses"), null);
    }

    private SbAllowedAddresses(final String prefix, final Name alias,
            final @Nullable Table<SbAllowedAddressesRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbAllowedAddressesRecord> getRecordType() {
        return SbAllowedAddressesRecord.class;
    }

    @Override
    public UniqueKey<SbAllowedAddressesRecord> getPrimaryKey() {
        return Keys.ALLOWED_ADDRESSES_PK;
    }

    @Override
    public SbAllowedAddresses as(final String alias) {
        return new SbAllowedAddresses(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbAllowedAddresses as(final Name alias) {
        return new SbAllowedAddresses(this.prefix(), alias, this);
    }

    @Override
    public SbAllowedAddresses rename(final String name) {
        return new SbAllowedAddresses(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbAllowedAddresses rename(final Name name) {
        return new SbAllowedAddresses(this.prefix(), name, null);
    }
}
