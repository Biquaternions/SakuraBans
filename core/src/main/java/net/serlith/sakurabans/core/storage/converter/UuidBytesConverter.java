package net.serlith.sakurabans.core.storage.converter;

import java.util.UUID;
import net.serlith.sakurabans.core.util.Uuids;
import org.jooq.Converter;
import org.jspecify.annotations.Nullable;

public final class UuidBytesConverter implements Converter<byte[], UUID> {

    private static final long serialVersionUID = 1L;

    @Override
    public @Nullable UUID from(final byte @Nullable [] bytes) {
        return bytes == null ? null : Uuids.fromBytes(bytes);
    }

    @Override
    public byte @Nullable [] to(final @Nullable UUID uuid) {
        return uuid == null ? null : Uuids.toBytes(uuid);
    }

    @Override
    public Class<byte[]> fromType() {
        return byte[].class;
    }

    @Override
    public Class<UUID> toType() {
        return UUID.class;
    }
}
