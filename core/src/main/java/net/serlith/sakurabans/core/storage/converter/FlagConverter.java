package net.serlith.sakurabans.core.storage.converter;

import org.jooq.Converter;
import org.jspecify.annotations.Nullable;

public final class FlagConverter implements Converter<Short, Boolean> {

    private static final long serialVersionUID = 1L;

    @Override
    public @Nullable Boolean from(final @Nullable Short value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case 0 -> Boolean.FALSE;
            case 1 -> Boolean.TRUE;
            default -> throw new IllegalStateException("not a flag: " + value);
        };
    }

    @Override
    public @Nullable Short to(final @Nullable Boolean value) {
        return value == null ? null : (short) (value ? 1 : 0);
    }

    @Override
    public Class<Short> fromType() {
        return Short.class;
    }

    @Override
    public Class<Boolean> toType() {
        return Boolean.class;
    }
}
