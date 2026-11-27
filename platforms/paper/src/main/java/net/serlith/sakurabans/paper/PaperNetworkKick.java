package net.serlith.sakurabans.paper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

final class PaperNetworkKick {

    static final String CHANNEL = "BungeeCord";
    static final long FALLBACK_TICKS = 40L;

    private PaperNetworkKick() {
    }

    static byte[] raw(final String player, final Component reason) {
        return message("KickPlayerRaw", player, GsonComponentSerializer.gson().serialize(reason));
    }

    static byte[] legacy(final String player, final Component reason) {
        return message("KickPlayer", player,
                LegacyComponentSerializer.legacySection().serialize(reason));
    }

    private static byte[] message(final String subChannel, final String player,
            final String text) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(subChannel);
            out.writeUTF(player);
            out.writeUTF(text);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
