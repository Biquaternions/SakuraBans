package net.serlith.sakurabans.core.storage.converter;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class EnumCode<E extends Enum<E>> {

    private final Class<E> type;
    private final Map<E, Short> codes;
    private final Map<Short, E> values;

    private EnumCode(final Class<E> type, final Map<E, Short> codes,
            final Map<Short, E> values) {
        this.type = type;
        this.codes = codes;
        this.values = values;
    }

    public static <E extends Enum<E>> EnumCode<E> of(final Class<E> type,
            final Map<E, Integer> codes) {
        final Map<E, Short> byValue = new EnumMap<>(type);
        final Map<Short, E> byCode = new HashMap<>();
        for (final E constant : type.getEnumConstants()) {
            final Integer code = codes.get(constant);
            if (code == null) {
                throw new IllegalArgumentException(
                        type.getSimpleName() + "." + constant + " has no code");
            }
            if (code < 0 || code > Short.MAX_VALUE) {
                throw new IllegalArgumentException(
                        type.getSimpleName() + "." + constant + ": code out of range: " + code);
            }
            final E previous = byCode.put(code.shortValue(), constant);
            if (previous != null) {
                throw new IllegalArgumentException(type.getSimpleName() + "." + previous
                        + " and " + constant + " share the code " + code);
            }
            byValue.put(constant, code.shortValue());
        }
        return new EnumCode<>(type, byValue, Map.copyOf(byCode));
    }

    public Class<E> type() {
        return this.type;
    }

    public short code(final E value) {
        return this.codes.get(value);
    }

    public E value(final short code) {
        final E value = this.values.get(code);
        if (value == null) {
            throw new IllegalStateException(
                    "unknown " + this.type.getSimpleName() + " code in the database: " + code);
        }
        return value;
    }

}
