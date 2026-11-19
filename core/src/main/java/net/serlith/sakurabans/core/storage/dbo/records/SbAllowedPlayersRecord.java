package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbAllowedPlayersRecord extends UpdatableRecordImpl<SbAllowedPlayersRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbAllowedPlayersRecord() {
        super(SakuraTables.ALLOWED_PLAYERS);
    }

    public UUID uuid() {
        return this.get(SakuraTables.ALLOWED_PLAYERS.UUID);
    }

    public SbAllowedPlayersRecord uuid(final UUID value) {
        this.set(SakuraTables.ALLOWED_PLAYERS.UUID, value);
        return this;
    }

    public OperatorKind addedByKind() {
        return this.get(SakuraTables.ALLOWED_PLAYERS.ADDED_BY_KIND);
    }

    public SbAllowedPlayersRecord addedByKind(final OperatorKind value) {
        this.set(SakuraTables.ALLOWED_PLAYERS.ADDED_BY_KIND, value);
        return this;
    }

    public @Nullable UUID addedByUuid() {
        return this.get(SakuraTables.ALLOWED_PLAYERS.ADDED_BY_UUID);
    }

    public SbAllowedPlayersRecord addedByUuid(final @Nullable UUID value) {
        this.set(SakuraTables.ALLOWED_PLAYERS.ADDED_BY_UUID, value);
        return this;
    }

    public String addedByName() {
        return this.get(SakuraTables.ALLOWED_PLAYERS.ADDED_BY_NAME);
    }

    public SbAllowedPlayersRecord addedByName(final String value) {
        this.set(SakuraTables.ALLOWED_PLAYERS.ADDED_BY_NAME, value);
        return this;
    }

    public String addedBySource() {
        return this.get(SakuraTables.ALLOWED_PLAYERS.ADDED_BY_SOURCE);
    }

    public SbAllowedPlayersRecord addedBySource(final String value) {
        this.set(SakuraTables.ALLOWED_PLAYERS.ADDED_BY_SOURCE, value);
        return this;
    }

    public Instant addedAt() {
        return this.get(SakuraTables.ALLOWED_PLAYERS.ADDED_AT);
    }

    public SbAllowedPlayersRecord addedAt(final Instant value) {
        this.set(SakuraTables.ALLOWED_PLAYERS.ADDED_AT, value);
        return this;
    }

    public @Nullable String note() {
        return this.get(SakuraTables.ALLOWED_PLAYERS.NOTE);
    }

    public SbAllowedPlayersRecord note(final @Nullable String value) {
        this.set(SakuraTables.ALLOWED_PLAYERS.NOTE, value);
        return this;
    }
}
