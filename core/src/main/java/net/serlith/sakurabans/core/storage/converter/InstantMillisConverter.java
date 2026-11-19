package net.serlith.sakurabans.core.storage.converter;

import java.time.Instant;
import org.jooq.Converter;
import org.jspecify.annotations.Nullable;

public final class InstantMillisConverter implements Converter<Long, Instant> {

    private static final long serialVersionUID = 1L;

    @Override
    public @Nullable Instant from(final @Nullable Long millis) {
        return millis == null ? null : Instant.ofEpochMilli(millis);
    }

    @Override
    public @Nullable Long to(final @Nullable Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    @Override
    public Class<Long> fromType() {
        return Long.class;
    }

    @Override
    public Class<Instant> toType() {
        return Instant.class;
    }
}
