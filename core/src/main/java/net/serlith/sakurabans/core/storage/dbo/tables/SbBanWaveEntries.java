package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.Indexes;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbBanWaveEntriesRecord;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbBanWaveEntries extends SbTable<SbBanWaveEntriesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbBanWaveEntriesRecord, Long> ID = createField(
            DSL.name("id"), SQLDataType.BIGINT.nullable(false).generatedByDefaultAsIdentity(),
            this, "");

    public final TableField<SbBanWaveEntriesRecord, Long> WAVE_ID = createField(
            DSL.name("wave_id"), SQLDataType.BIGINT.nullable(false), this, "");

    public final TableField<SbBanWaveEntriesRecord, String> DRAFT = createField(
            DSL.name("draft"), SQLDataType.CLOB.nullable(false), this, "");

    public final TableField<SbBanWaveEntriesRecord, OperatorKind> ADDED_BY_KIND = createField(
            DSL.name("added_by_kind"), SbTypes.OPERATOR_KIND.nullable(false), this, "");

    public final TableField<SbBanWaveEntriesRecord, UUID> ADDED_BY_UUID = createField(
            DSL.name("added_by_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbBanWaveEntriesRecord, String> ADDED_BY_NAME = createField(
            DSL.name("added_by_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbBanWaveEntriesRecord, String> ADDED_BY_SOURCE = createField(
            DSL.name("added_by_source"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbBanWaveEntriesRecord, Instant> ADDED_AT = createField(
            DSL.name("added_at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbBanWaveEntriesRecord, String> RESULT = createField(
            DSL.name("result"), SQLDataType.CLOB.nullable(true), this, "");

    public final TableField<SbBanWaveEntriesRecord, Long> PUNISHMENT_ID = createField(
            DSL.name("punishment_id"), SQLDataType.BIGINT.nullable(true), this, "");

    public SbBanWaveEntries(final String prefix) {
        this(prefix, DSL.name(prefix + "ban_wave_entries"), null);
    }

    private SbBanWaveEntries(final String prefix, final Name alias,
            final @Nullable Table<SbBanWaveEntriesRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbBanWaveEntriesRecord> getRecordType() {
        return SbBanWaveEntriesRecord.class;
    }

    @Override
    public UniqueKey<SbBanWaveEntriesRecord> getPrimaryKey() {
        return Keys.BAN_WAVE_ENTRIES_PK;
    }

    @Override
    public List<ForeignKey<SbBanWaveEntriesRecord, ?>> getReferences() {
        return List.of(
                Keys.BAN_WAVE_ENTRIES_WAVE_FK,
                Keys.BAN_WAVE_ENTRIES_PUNISHMENT_FK);
    }

    @Override
    public List<Index> getIndexes() {
        return List.of(
                Indexes.BAN_WAVE_ENTRIES_WAVE_IX);
    }

    @Override
    public SbBanWaveEntries as(final String alias) {
        return new SbBanWaveEntries(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbBanWaveEntries as(final Name alias) {
        return new SbBanWaveEntries(this.prefix(), alias, this);
    }

    @Override
    public SbBanWaveEntries rename(final String name) {
        return new SbBanWaveEntries(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbBanWaveEntries rename(final Name name) {
        return new SbBanWaveEntries(this.prefix(), name, null);
    }
}
