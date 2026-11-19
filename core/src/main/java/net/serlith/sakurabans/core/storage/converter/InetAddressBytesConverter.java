package net.serlith.sakurabans.core.storage.converter;

import java.net.InetAddress;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.jooq.Converter;
import org.jspecify.annotations.Nullable;

public final class InetAddressBytesConverter implements Converter<byte[], InetAddress> {

    private static final long serialVersionUID = 1L;

    @Override
    public @Nullable InetAddress from(final byte @Nullable [] bytes) {
        return bytes == null ? null : InetAddresses.fromBytes(bytes);
    }

    @Override
    public byte @Nullable [] to(final @Nullable InetAddress address) {
        return address == null ? null : InetAddresses.toBytes(address);
    }

    @Override
    public Class<byte[]> fromType() {
        return byte[].class;
    }

    @Override
    public Class<InetAddress> toType() {
        return InetAddress.class;
    }
}
