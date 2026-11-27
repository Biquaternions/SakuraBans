package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.List;
import net.serlith.sakurabans.core.storage.dbo.Indexes;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbSyncMessagesRecord;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbSyncMessages extends SbTable<SbSyncMessagesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbSyncMessagesRecord, Long> ID = createField(
            DSL.name("id"), SQLDataType.BIGINT.nullable(false).generatedByDefaultAsIdentity(),
            this, "");

    public final TableField<SbSyncMessagesRecord, Instant> CREATED_AT = createField(
            DSL.name("created_at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbSyncMessagesRecord, byte[]> PAYLOAD = createField(
            DSL.name("payload"), SbTypes.varbinary(16384).nullable(false), this, "");

    public SbSyncMessages(final String prefix) {
        this(prefix, DSL.name(prefix + "sync_messages"), null);
    }

    private SbSyncMessages(final String prefix, final Name alias,
            final @Nullable Table<SbSyncMessagesRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbSyncMessagesRecord> getRecordType() {
        return SbSyncMessagesRecord.class;
    }

    @Override
    public UniqueKey<SbSyncMessagesRecord> getPrimaryKey() {
        return Keys.SYNC_MESSAGES_PK;
    }

    @Override
    public List<Index> getIndexes() {
        return List.of(
                Indexes.SYNC_MESSAGES_CREATED_IX);
    }

    @Override
    public SbSyncMessages as(final String alias) {
        return new SbSyncMessages(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbSyncMessages as(final Name alias) {
        return new SbSyncMessages(this.prefix(), alias, this);
    }

    @Override
    public SbSyncMessages rename(final String name) {
        return new SbSyncMessages(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbSyncMessages rename(final Name name) {
        return new SbSyncMessages(this.prefix(), name, null);
    }
}
