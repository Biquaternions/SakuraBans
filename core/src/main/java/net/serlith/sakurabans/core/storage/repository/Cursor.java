package net.serlith.sakurabans.core.storage.repository;

import com.google.common.base.Preconditions;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.jspecify.annotations.Nullable;

record Cursor(String kind, @Nullable Instant key, long id) {

    String encode() {
        final String text = this.kind + ":" + (this.key == null ? "-" : this.key.toEpochMilli())
                + ":" + this.id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a cursor from a string.
     *
     * @param cursor cursor
     * @param expectedKind kind
     *
     * @return cursor
     * @throws IllegalArgumentException for a malformed cursor or one of another kind
     */
    static Cursor decode(final String cursor, final String expectedKind)
            throws IllegalArgumentException {
        final String text;
        try {
            text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("not a cursor: " + cursor, e);
        }
        final String[] parts = text.split(":", -1);
        Preconditions.checkArgument(parts.length == 3 && parts[0].equals(expectedKind),
                "not a cursor for this query: %s", cursor);
        try {
            final Instant key = parts[1].equals("-") ? null
                    : Instant.ofEpochMilli(Long.parseLong(parts[1]));
            return new Cursor(parts[0], key, Long.parseLong(parts[2]));
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("not a cursor: " + cursor, e);
        }
    }
}
