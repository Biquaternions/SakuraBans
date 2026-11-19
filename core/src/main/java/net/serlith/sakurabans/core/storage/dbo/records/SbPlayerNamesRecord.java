package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;

public final class SbPlayerNamesRecord extends UpdatableRecordImpl<SbPlayerNamesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbPlayerNamesRecord() {
        super(SakuraTables.PLAYER_NAMES);
    }

    public UUID uuid() {
        return this.get(SakuraTables.PLAYER_NAMES.UUID);
    }

    public SbPlayerNamesRecord uuid(final UUID value) {
        this.set(SakuraTables.PLAYER_NAMES.UUID, value);
        return this;
    }

    public String name() {
        return this.get(SakuraTables.PLAYER_NAMES.NAME);
    }

    public SbPlayerNamesRecord name(final String value) {
        this.set(SakuraTables.PLAYER_NAMES.NAME, value);
        return this;
    }

    public String nameLower() {
        return this.get(SakuraTables.PLAYER_NAMES.NAME_LOWER);
    }

    public SbPlayerNamesRecord nameLower(final String value) {
        this.set(SakuraTables.PLAYER_NAMES.NAME_LOWER, value);
        return this;
    }

    public Instant firstSeen() {
        return this.get(SakuraTables.PLAYER_NAMES.FIRST_SEEN);
    }

    public SbPlayerNamesRecord firstSeen(final Instant value) {
        this.set(SakuraTables.PLAYER_NAMES.FIRST_SEEN, value);
        return this;
    }

    public Instant lastSeen() {
        return this.get(SakuraTables.PLAYER_NAMES.LAST_SEEN);
    }

    public SbPlayerNamesRecord lastSeen(final Instant value) {
        this.set(SakuraTables.PLAYER_NAMES.LAST_SEEN, value);
        return this;
    }
}
