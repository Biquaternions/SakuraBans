package net.serlith.sakurabans.core.storage.dbo.tables;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.Keys;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import net.serlith.sakurabans.core.storage.dbo.SbTypes;
import net.serlith.sakurabans.core.storage.dbo.records.SbEditorSessionsRecord;
import org.jooq.Name;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.Nullable;

public final class SbEditorSessions extends SbTable<SbEditorSessionsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public final TableField<SbEditorSessionsRecord, String> ID = createField(
            DSL.name("id"), SQLDataType.VARCHAR(22).nullable(false), this, "");

    public final TableField<SbEditorSessionsRecord, byte[]> SECRET = createField(
            DSL.name("secret"), SQLDataType.BINARY(32).nullable(false), this, "");

    public final TableField<SbEditorSessionsRecord, String> TOKEN = createField(
            DSL.name("token"), SQLDataType.VARCHAR(128).nullable(false), this, "");

    /** The sections the session was opened with: ids joined by commas. */
    public final TableField<SbEditorSessionsRecord, String> SECTIONS = createField(
            DSL.name("sections"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbEditorSessionsRecord, OperatorKind> CREATOR_KIND = createField(
            DSL.name("creator_kind"), SbTypes.OPERATOR_KIND.nullable(false), this, "");

    public final TableField<SbEditorSessionsRecord, UUID> CREATOR_UUID = createField(
            DSL.name("creator_uuid"), SbTypes.UUID.nullable(true), this, "");

    public final TableField<SbEditorSessionsRecord, String> CREATOR_NAME = createField(
            DSL.name("creator_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    public final TableField<SbEditorSessionsRecord, Instant> CREATED_AT = createField(
            DSL.name("created_at"), SbTypes.INSTANT.nullable(false), this, "");

    public final TableField<SbEditorSessionsRecord, Long> LAST_SEQUENCE = createField(
            DSL.name("last_sequence"), SQLDataType.BIGINT.nullable(false).defaultValue(0L), this,
            "");

    public SbEditorSessions(final String prefix) {
        this(prefix, DSL.name(prefix + "editor_sessions"), null);
    }

    private SbEditorSessions(final String prefix, final Name alias,
            final @Nullable Table<SbEditorSessionsRecord> aliased) {
        super(prefix, alias, aliased);
    }

    @Override
    public Class<SbEditorSessionsRecord> getRecordType() {
        return SbEditorSessionsRecord.class;
    }

    @Override
    public UniqueKey<SbEditorSessionsRecord> getPrimaryKey() {
        return Keys.EDITOR_SESSIONS_PK;
    }

    @Override
    public SbEditorSessions as(final String alias) {
        return new SbEditorSessions(this.prefix(), DSL.name(alias), this);
    }

    @Override
    public SbEditorSessions as(final Name alias) {
        return new SbEditorSessions(this.prefix(), alias, this);
    }

    @Override
    public SbEditorSessions rename(final String name) {
        return new SbEditorSessions(this.prefix(), DSL.name(name), null);
    }

    @Override
    public SbEditorSessions rename(final Name name) {
        return new SbEditorSessions(this.prefix(), name, null);
    }
}
