package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbEditorSessionsRecord extends UpdatableRecordImpl<SbEditorSessionsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbEditorSessionsRecord() {
        super(SakuraTables.EDITOR_SESSIONS);
    }

    public String id() {
        return this.get(SakuraTables.EDITOR_SESSIONS.ID);
    }

    public byte[] secret() {
        return this.get(SakuraTables.EDITOR_SESSIONS.SECRET);
    }

    public String token() {
        return this.get(SakuraTables.EDITOR_SESSIONS.TOKEN);
    }

    public String sections() {
        return this.get(SakuraTables.EDITOR_SESSIONS.SECTIONS);
    }

    public OperatorKind creatorKind() {
        return this.get(SakuraTables.EDITOR_SESSIONS.CREATOR_KIND);
    }

    public @Nullable UUID creatorUuid() {
        return this.get(SakuraTables.EDITOR_SESSIONS.CREATOR_UUID);
    }

    public String creatorName() {
        return this.get(SakuraTables.EDITOR_SESSIONS.CREATOR_NAME);
    }

    public Instant createdAt() {
        return this.get(SakuraTables.EDITOR_SESSIONS.CREATED_AT);
    }

    public Long lastSequence() {
        return this.get(SakuraTables.EDITOR_SESSIONS.LAST_SEQUENCE);
    }
}
