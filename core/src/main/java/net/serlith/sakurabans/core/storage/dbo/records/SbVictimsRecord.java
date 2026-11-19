package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.VictimKind;
import org.jooq.impl.UpdatableRecordImpl;

public final class SbVictimsRecord extends UpdatableRecordImpl<SbVictimsRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbVictimsRecord() {
        super(SakuraTables.VICTIMS);
    }

    public Long id() {
        return this.get(SakuraTables.VICTIMS.ID);
    }

    public SbVictimsRecord id(final Long value) {
        this.set(SakuraTables.VICTIMS.ID, value);
        return this;
    }

    public VictimKind kind() {
        return this.get(SakuraTables.VICTIMS.KIND);
    }

    public SbVictimsRecord kind(final VictimKind value) {
        this.set(SakuraTables.VICTIMS.KIND, value);
        return this;
    }

    public UUID uuid() {
        return this.get(SakuraTables.VICTIMS.UUID);
    }

    public SbVictimsRecord uuid(final UUID value) {
        this.set(SakuraTables.VICTIMS.UUID, value);
        return this;
    }

    public byte[] address() {
        return this.get(SakuraTables.VICTIMS.ADDRESS);
    }

    public SbVictimsRecord address(final byte[] value) {
        this.set(SakuraTables.VICTIMS.ADDRESS, value);
        return this;
    }

    public Short prefixLength() {
        return this.get(SakuraTables.VICTIMS.PREFIX_LENGTH);
    }

    public SbVictimsRecord prefixLength(final Short value) {
        this.set(SakuraTables.VICTIMS.PREFIX_LENGTH, value);
        return this;
    }
}
