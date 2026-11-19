package net.serlith.sakurabans.core.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Uuids {

    public static final UUID ZERO = new UUID(0, 0);

    private static final Pattern DASHED = Pattern.compile(
            "\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}");
    private static final Pattern PLAIN = Pattern.compile("\\p{XDigit}{32}");

    private Uuids() {
    }

    public static Optional<UUID> parse(final String text) {
        if (DASHED.matcher(text).matches()) {
            return Optional.of(UUID.fromString(text));
        }
        if (PLAIN.matcher(text).matches()) {
            return Optional.of(new UUID(Long.parseUnsignedLong(text.substring(0, 16), 16),
                    Long.parseUnsignedLong(text.substring(16), 16)));
        }
        return Optional.empty();
    }

    public static byte[] toBytes(final UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    public static UUID fromBytes(final byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("a UUID has 16 bytes, not " + bytes.length);
        }
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    public static UUID offline(final String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isFloodgate(final UUID uuid) {
        return uuid.getMostSignificantBits() == 0 && uuid.getLeastSignificantBits() != 0;
    }

}
