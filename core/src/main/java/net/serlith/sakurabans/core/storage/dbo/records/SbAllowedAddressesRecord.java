package net.serlith.sakurabans.core.storage.dbo.records;

import java.io.Serial;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import net.serlith.sakurabans.core.storage.dbo.OperatorKind;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import org.jooq.impl.UpdatableRecordImpl;
import org.jspecify.annotations.Nullable;

public final class SbAllowedAddressesRecord extends UpdatableRecordImpl<SbAllowedAddressesRecord> {

    @Serial
    private static final long serialVersionUID = 1L;

    public SbAllowedAddressesRecord() {
        super(SakuraTables.ALLOWED_ADDRESSES);
    }

    public InetAddress address() {
        return this.get(SakuraTables.ALLOWED_ADDRESSES.ADDRESS);
    }

    public SbAllowedAddressesRecord address(final InetAddress value) {
        this.set(SakuraTables.ALLOWED_ADDRESSES.ADDRESS, value);
        return this;
    }

    public OperatorKind addedByKind() {
        return this.get(SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_KIND);
    }

    public SbAllowedAddressesRecord addedByKind(final OperatorKind value) {
        this.set(SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_KIND, value);
        return this;
    }

    public @Nullable UUID addedByUuid() {
        return this.get(SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_UUID);
    }

    public SbAllowedAddressesRecord addedByUuid(final @Nullable UUID value) {
        this.set(SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_UUID, value);
        return this;
    }

    public String addedByName() {
        return this.get(SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_NAME);
    }

    public SbAllowedAddressesRecord addedByName(final String value) {
        this.set(SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_NAME, value);
        return this;
    }

    public String addedBySource() {
        return this.get(SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_SOURCE);
    }

    public SbAllowedAddressesRecord addedBySource(final String value) {
        this.set(SakuraTables.ALLOWED_ADDRESSES.ADDED_BY_SOURCE, value);
        return this;
    }

    public Instant addedAt() {
        return this.get(SakuraTables.ALLOWED_ADDRESSES.ADDED_AT);
    }

    public SbAllowedAddressesRecord addedAt(final Instant value) {
        this.set(SakuraTables.ALLOWED_ADDRESSES.ADDED_AT, value);
        return this;
    }

    public @Nullable String note() {
        return this.get(SakuraTables.ALLOWED_ADDRESSES.NOTE);
    }

    public SbAllowedAddressesRecord note(final @Nullable String value) {
        this.set(SakuraTables.ALLOWED_ADDRESSES.NOTE, value);
        return this;
    }
}
