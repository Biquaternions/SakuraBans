package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbAllowedPlayersRecord;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbAllowedPlayers extends SbTable<SbAllowedPlayersRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbAllowedPlayersRecord, UUID> UUID = createField(
            DSL.name("uuid"), SbTypes.UUID.nullable(false), this, "");

    public final TableField<SbAllowedPlayersRecord, OperatorKind> ADDED_BY_KIND = createField(
            DSL.name("added_by_kind"), SbTypes.OPERATOR_KIND.nullable(false), this, "");

    public final TableField<SbAllowedPlayersRecord, UUID> ADDED_BY_UUID = createField(
            DSL.name("added_by_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbAllowedPlayersRecord, String> ADDED_BY_NAME = createField(
            DSL.name("added_by_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbAllowedPlayersRecord, String> ADDED_BY_SOURCE = createField(
            DSL.name("added_by_source"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbAllowedPlayersRecord, Instant> ADDED_AT = createField(
            DSL.name("added_at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbAllowedPlayersRecord, String> NOTE = createField(
            DSL.name("note"), SQLDataType.VARCHAR(256).nullable(true), this, "");

    public SbAllowedPlayers(final String prefix) {
        this(prefix, DSL.name(prefix + "allowed_players"), null);
    }

    private SbAllowedPlayers(final String prefix, final Name alias,
            final @Nullable Table<SbAllowedPlayersRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbAllowedPlayersRecord> getRecordType() {
        return SbAllowedPlayersRecord.class;
    }

    @Override
    public UniqueKey<SbAllowedPlayersRecord> getPrimaryKey() {
        return Keys.ALLOWED_PLAYERS_PK;
    }

    @Override
    public SbAllowedPlayers as(final String alias) {
        return new SbAllowedPlayers(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbAllowedPlayers as(final Name alias) {
        return new SbAllowedPlayers(this.prefix(), alias, this);
    }

    @Override
    public SbAllowedPlayers rename(final String name) {
        return new SbAllowedPlayers(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbAllowedPlayers rename(final Name name) {
        return new SbAllowedPlayers(this.prefix(), name, null);
    }
}
