package net.serlith.sakurabans.core.configuration.serializer;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.function.Predicate;
import net.serlith.sakurabans.core.util.Durations;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;

public final class DurationSerializer extends ScalarSerializer<Duration> {

    public DurationSerializer() {
        super(Duration.class);
    }

    @Override
    public Duration deserialize(final Type type, final Object value) throws SerializationException {
        return Durations.parse(value.toString()).orElseThrow(() -> new SerializationException(
                "\"" + value + "\" is not a duration (like 30s, 10m, 1h30m, 7d)"));
    }

    @Override
    protected Object serialize(final Duration item, final Predicate<Class<?>> typeSupported) {
        return Durations.formatCompact(item).replace(" ", "");
    }
}
