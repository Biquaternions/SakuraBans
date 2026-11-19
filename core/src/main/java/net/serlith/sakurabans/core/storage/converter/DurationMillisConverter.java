package net.serlith.sakurabans.core.storage.converter;

import java.time.Duration;
import org.jooq.Converter;
import org.jspecify.annotations.Nullable;

public final class DurationMillisConverter implements Converter<Long, Duration> {

    private static final long serialVersionUID = 1L;

    @Override
    public @Nullable Duration from(final @Nullable Long millis) {
        return millis == null ? null : Duration.ofMillis(millis);
    }

    @Override
    public @Nullable Long to(final @Nullable Duration duration) {
        return duration == null ? null : duration.toMillis();
    }

    @Override
    public Class<Long> fromType() {
        return Long.class;
    }

    @Override
    public Class<Duration> toType() {
        return Duration.class;
    }
}
