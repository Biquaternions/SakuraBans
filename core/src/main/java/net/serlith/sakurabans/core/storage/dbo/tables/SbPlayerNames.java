package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.Indexes;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbPlayerNamesRecord;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbPlayerNames extends SbTable<SbPlayerNamesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbPlayerNamesRecord, UUID> UUID = createField(
            DSL.name("uuid"), SbTypes.UUID.nullable(false), this, "");

    public final TableField<SbPlayerNamesRecord, String> NAME = createField(
            DSL.name("name"), SQLDataType.VARCHAR(32).nullable(false), this, "");

    public final TableField<SbPlayerNamesRecord, String> NAME_LOWER = createField(
            DSL.name("name_lower"), SQLDataType.VARCHAR(32).nullable(false), this, "");

    public final TableField<SbPlayerNamesRecord, Instant> FIRST_SEEN = createField(
            DSL.name("first_seen"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbPlayerNamesRecord, Instant> LAST_SEEN = createField(
            DSL.name("last_seen"), SbTypes.INSTANT.nullable(false), this, "");

    public SbPlayerNames(final String prefix) {
        this(prefix, DSL.name(prefix + "player_names"), null);
    }

    private SbPlayerNames(final String prefix, final Name alias,
            final @Nullable Table<SbPlayerNamesRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbPlayerNamesRecord> getRecordType() {
        return SbPlayerNamesRecord.class;
    }

    @Override
    public UniqueKey<SbPlayerNamesRecord> getPrimaryKey() {
        return Keys.PLAYER_NAMES_PK;
    }

    @Override
    public List<ForeignKey<SbPlayerNamesRecord, ?>> getReferences() {
        return List.of(
                Keys.PLAYER_NAMES_PLAYER_FK);
    }

    @Override
    public List<Index> getIndexes() {
        return List.of(
                Indexes.PLAYER_NAMES_NAME_IX);
    }

    @Override
    public SbPlayerNames as(final String alias) {
        return new SbPlayerNames(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbPlayerNames as(final Name alias) {
        return new SbPlayerNames(this.prefix(), alias, this);
    }

    @Override
    public SbPlayerNames rename(final String name) {
        return new SbPlayerNames(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbPlayerNames rename(final Name name) {
        return new SbPlayerNames(this.prefix(), name, null);
    }
}
