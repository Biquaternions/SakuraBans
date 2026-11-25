package net.serlith.sakurabans.core.storage;

import com.google.common.base.Preconditions;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.IdStyle;
import net.serlith.sakurabans.core.storage.repository.MetaRepository;
import org.jspecify.annotations.Nullable;

public final class DisplayIdCodec {

    static final String SECRET_KEY = "display_id_secret";
    static final long LIMIT = 1L << 38;
    private static final int HALF_BITS = 19;
    private static final long HALF_MASK = (1L << HALF_BITS) - 1;
    private static final int ROUNDS = 4;
    private static final String FIRST = "ABCDEFGH";
    private static final String REST = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final MetaRepository meta;
    private final ConfigService config;
    private volatile byte @Nullable [] secret;

    public DisplayIdCodec(final MetaRepository meta, final ConfigService config) {
        this.meta = meta;
        this.config = config;
    }

    public CompletableFuture<Void> load() {
        final byte[] fresh = new byte[32];
        new SecureRandom().nextBytes(fresh);
        return this.meta.putIfAbsent(SECRET_KEY, HexFormat.of().formatHex(fresh))
                .thenCompose(ignored -> this.meta.get(SECRET_KEY))
                .thenAccept(stored -> this.secret = HexFormat.of().parseHex(stored.orElseThrow(
                        () -> new StorageException("the display id secret is missing"))));
    }

    public void secret(final byte[] secret) {
        Preconditions.checkArgument(secret.length > 0, "empty secret");
        this.secret = secret.clone();
    }

    public String display(final long id) {
        final IdStyle style = this.config.settings().display().idStyle();
        if (style == IdStyle.NUMERIC || id >= LIMIT || this.secret == null) {
            return Long.toString(id);
        }
        final String code = this.encode(id);
        return style == IdStyle.BOTH ? id + "/" + code : code;
    }

    public OptionalLong decode(final String text) {
        String value = text.strip();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        final int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        if (!value.isEmpty() && value.chars().allMatch(Character::isDigit)) {
            try {
                final long id = Long.parseLong(value);
                return id >= 1 ? OptionalLong.of(id) : OptionalLong.empty();
            } catch (final NumberFormatException e) {
                return OptionalLong.empty();
            }
        }
        return this.decodeCode(value.toUpperCase(Locale.ROOT));
    }

    public static boolean looksLikeCode(final String text) {
        if (text.length() != CODE_LENGTH) {
            return false;
        }
        final String upper = text.toUpperCase(Locale.ROOT);
        if (FIRST.indexOf(upper.charAt(0)) < 0) {
            return false;
        }
        return upper.substring(1).chars().allMatch(c -> REST.indexOf(c) >= 0);
    }

    String encode(final long id) {
        Preconditions.checkArgument(id >= 1 && id < LIMIT, "no code for id %s", id);
        long value = this.permute(id, false);
        final char[] chars = new char[CODE_LENGTH];
        for (int i = CODE_LENGTH - 1; i >= 1; i--) {
            chars[i] = REST.charAt((int) (value & 31));
            value >>>= 5;
        }
        chars[0] = FIRST.charAt((int) value);
        return new String(chars);
    }

    private OptionalLong decodeCode(final String code) {
        if (!looksLikeCode(code) || this.secret == null) {
            return OptionalLong.empty();
        }
        long value = FIRST.indexOf(code.charAt(0));
        for (int i = 1; i < CODE_LENGTH; i++) {
            value = value << 5 | REST.indexOf(code.charAt(i));
        }
        final long id = this.permute(value, true);
        return id >= 1 ? OptionalLong.of(id) : OptionalLong.empty();
    }

    private long permute(final long value, final boolean inverse) {
        long left = value >>> HALF_BITS & HALF_MASK;
        long right = value & HALF_MASK;
        for (int i = 0; i < ROUNDS; i++) {
            final int round = inverse ? ROUNDS - 1 - i : i;
            if (inverse) {
                final long previousRight = left;
                left = right ^ this.round(round, previousRight);
                right = previousRight;
            } else {
                final long next = left ^ this.round(round, right);
                left = right;
                right = next;
            }
        }
        return left << HALF_BITS | right;
    }

    private long round(final int round, final long half) {
        final byte[] key = this.secret;
        Preconditions.checkState(key != null, "the display id secret isn't loaded");
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update((byte) round);
            mac.update(ByteBuffer.allocate(Long.BYTES).putLong(half).array());
            return ByteBuffer.wrap(mac.doFinal()).getLong() & HALF_MASK;
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is missing", e);
        }
    }
}
