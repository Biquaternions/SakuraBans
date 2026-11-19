package net.serlith.sakurabans.core.storage.converter;

import org.jooq.Converter;
import org.jspecify.annotations.Nullable;

public final class EnumCodeConverter<E extends Enum<E>> implements Converter<Short, E> {

    private static final long serialVersionUID = 1L;

    private final EnumCode<E> codes;

    public EnumCodeConverter(final EnumCode<E> codes) {
        this.codes = codes;
    }

    @Override
    public @Nullable E from(final @Nullable Short code) {
        return code == null ? null : this.codes.value(code);
    }

    @Override
    public @Nullable Short to(final @Nullable E value) {
        return value == null ? null : this.codes.code(value);
    }

    @Override
    public Class<Short> fromType() {
        return Short.class;
    }

    @Override
    public Class<E> toType() {
        return this.codes.type();
    }
}
