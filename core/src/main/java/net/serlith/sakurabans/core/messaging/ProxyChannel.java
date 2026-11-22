package net.serlith.sakurabans.core.messaging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import org.jspecify.annotations.Nullable;

public final class ProxyChannel {

    public static final String NAME = "sakurabans:main";

    static final String TOKEN_KEY = "channel_token";
    private static final int VERSION = 1;
    private static final int SERVER_NAME = 0;
    private static final int ENFORCE = 1;

    private final MetaRepository meta;
    private final ComponentLogger logger;
    private volatile byte @Nullable [] token;

    public ProxyChannel(final MetaRepository meta, final ComponentLogger logger) {
        this.meta = meta;
        this.logger = logger;
    }

    public sealed interface Message {

        record ServerName(String name) implements Message {
        }

        record Enforce(long punishmentId) implements Message {
        }
    }

    public void load() {
        final byte[] fresh = new byte[24];
        new SecureRandom().nextBytes(fresh);
        final String created = Base64.getEncoder().encodeToString(fresh);
        // Another instance may create it at the same moment; whichever row exists wins.
        final String stored = this.meta.putIfAbsent(TOKEN_KEY, created)
                .exceptionally(failure -> false)
                .thenCompose(ignored -> this.meta.get(TOKEN_KEY))
                .join()
                .orElse(created);
        this.token = stored.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] serverName(final String name) {
        return write(SERVER_NAME, out -> {
            out.writeUTF(new String(this.token(), StandardCharsets.UTF_8));
            out.writeUTF(name);
        });
    }

    public byte[] enforce(final long punishmentId) {
        return write(ENFORCE, out -> out.writeLong(punishmentId));
    }

    public Optional<Message> decode(final byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readUnsignedByte() != VERSION) {
                this.logger.warn("Ignoring a {} message of another SakuraBans version", NAME);
                return Optional.empty();
            }
            return switch (in.readUnsignedByte()) {
                case SERVER_NAME -> {
                    final byte[] given = in.readUTF().getBytes(StandardCharsets.UTF_8);
                    final String name = in.readUTF();
                    if (!MessageDigest.isEqual(given, this.token())) {
                        this.logger.warn("Ignoring the server name \"{}\" sent on {}: its token"
                                + " doesn't match (is the proxy on the same database, and does"
                                + " it run SakuraBans?)", name, NAME);
                        yield Optional.empty();
                    }
                    yield Optional.of(new Message.ServerName(name));
                }
                case ENFORCE -> Optional.of(new Message.Enforce(in.readLong()));
                default -> Optional.empty();
            };
        } catch (final IOException e) {
            return Optional.empty();
        }
    }

    private byte[] token() {
        final byte[] value = this.token;
        if (value == null) {
            throw new IllegalStateException("the channel token isn't loaded yet");
        }
        return value;
    }

    private interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] write(final int type, final Body body) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeByte(type);
            body.write(out);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
