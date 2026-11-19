package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbPlayersRecord extends UpdatableRecordImpl<SbPlayersRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbPlayersRecord() {
        super(SakuraTables.PLAYERS);
    }

    public UUID uuid() {
        return this.get(SakuraTables.PLAYERS.UUID);
    }

    public SbPlayersRecord uuid(final UUID value) {
        this.set(SakuraTables.PLAYERS.UUID, value);
        return this;
    }

    public String lastName() {
        return this.get(SakuraTables.PLAYERS.LAST_NAME);
    }

    public SbPlayersRecord lastName(final String value) {
        this.set(SakuraTables.PLAYERS.LAST_NAME, value);
        return this;
    }

    public String lastNameLower() {
        return this.get(SakuraTables.PLAYERS.LAST_NAME_LOWER);
    }

    public SbPlayersRecord lastNameLower(final String value) {
        this.set(SakuraTables.PLAYERS.LAST_NAME_LOWER, value);
        return this;
    }

    public Instant firstSeen() {
        return this.get(SakuraTables.PLAYERS.FIRST_SEEN);
    }

    public SbPlayersRecord firstSeen(final Instant value) {
        this.set(SakuraTables.PLAYERS.FIRST_SEEN, value);
        return this;
    }

    public Instant lastSeen() {
        return this.get(SakuraTables.PLAYERS.LAST_SEEN);
    }

    public SbPlayersRecord lastSeen(final Instant value) {
        this.set(SakuraTables.PLAYERS.LAST_SEEN, value);
        return this;
    }

    public @Nullable InetAddress lastAddress() {
        return this.get(SakuraTables.PLAYERS.LAST_ADDRESS);
    }

    public SbPlayersRecord lastAddress(final @Nullable InetAddress value) {
        this.set(SakuraTables.PLAYERS.LAST_ADDRESS, value);
        return this;
    }
}
