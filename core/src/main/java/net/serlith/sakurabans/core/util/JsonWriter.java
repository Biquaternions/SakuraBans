package net.serlith.sakurabans.core.util;

import java.util.ArrayDeque;
import java.util.Deque;
import org.jspecify.annotations.Nullable;

/** A minimal JSON writer (player data exports, S37e); values are escaped, keys too. */
public final class JsonWriter {

    private final StringBuilder out = new StringBuilder();
    /** Per open object or array: whether it has an element yet. */
    private final Deque<Boolean> open = new ArrayDeque<>();
    private boolean afterKey;

    public JsonWriter beginObject() {
        this.element();
        this.out.append('{');
        this.open.push(false);
        return this;
    }

    public JsonWriter endObject() {
        this.open.pop();
        this.out.append('}');
        return this;
    }

    public JsonWriter beginArray() {
        this.element();
        this.out.append('[');
        this.open.push(false);
        return this;
    }

    public JsonWriter endArray() {
        this.open.pop();
        this.out.append(']');
        return this;
    }

    public JsonWriter name(final String name) {
        this.element();
        quote(this.out, name);
        this.out.append(':');
        this.afterKey = true;
        return this;
    }

    public JsonWriter value(final @Nullable String value) {
        this.element();
        if (value == null) {
            this.out.append("null");
        } else {
            quote(this.out, value);
        }
        return this;
    }

    public JsonWriter value(final long value) {
        this.element();
        this.out.append(value);
        return this;
    }

    public JsonWriter value(final boolean value) {
        this.element();
        this.out.append(value);
        return this;
    }

    public JsonWriter field(final String name, final @Nullable Object value) {
        this.name(name);
        return switch (value) {
            case null -> this.value((String) null);
            case Long number -> this.value(number.longValue());
            case Integer number -> this.value(number.longValue());
            case Boolean bool -> this.value(bool.booleanValue());
            default -> this.value(value.toString());
        };
    }

    @Override
    public String toString() {
        return this.out.toString();
    }

    private void element() {
        if (this.afterKey) {
            this.afterKey = false;
            return;
        }
        if (!this.open.isEmpty()) {
            if (this.open.peek()) {
                this.out.append(',');
            } else {
                this.open.pop();
                this.open.push(true);
            }
        }
    }

    private static void quote(final StringBuilder out, final String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
