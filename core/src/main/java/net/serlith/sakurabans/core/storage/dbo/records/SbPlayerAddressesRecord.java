package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbPlayerAddressesRecord extends UpdatableRecordImpl<SbPlayerAddressesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbPlayerAddressesRecord() {
        super(SakuraTables.PLAYER_ADDRESSES);
    }

    public UUID uuid() {
        return this.get(SakuraTables.PLAYER_ADDRESSES.UUID);
    }

    public SbPlayerAddressesRecord uuid(final UUID value) {
        this.set(SakuraTables.PLAYER_ADDRESSES.UUID, value);
        return this;
    }

    public InetAddress address() {
        return this.get(SakuraTables.PLAYER_ADDRESSES.ADDRESS);
    }

    public SbPlayerAddressesRecord address(final InetAddress value) {
        this.set(SakuraTables.PLAYER_ADDRESSES.ADDRESS, value);
        return this;
    }

    public Instant firstSeen() {
        return this.get(SakuraTables.PLAYER_ADDRESSES.FIRST_SEEN);
    }

    public SbPlayerAddressesRecord firstSeen(final Instant value) {
        this.set(SakuraTables.PLAYER_ADDRESSES.FIRST_SEEN, value);
        return this;
    }

    public Instant lastSeen() {
        return this.get(SakuraTables.PLAYER_ADDRESSES.LAST_SEEN);
    }

    public SbPlayerAddressesRecord lastSeen(final Instant value) {
        this.set(SakuraTables.PLAYER_ADDRESSES.LAST_SEEN, value);
        return this;
    }

    public @Nullable String lastServer() {
        return this.get(SakuraTables.PLAYER_ADDRESSES.LAST_SERVER);
    }

    public SbPlayerAddressesRecord lastServer(final @Nullable String value) {
        this.set(SakuraTables.PLAYER_ADDRESSES.LAST_SERVER, value);
        return this;
    }
}
