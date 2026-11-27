package net.serlith.sakurabans.core.storage;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.serlith.sakurabans.core.util.InetAddresses;
import org.jspecify.annotations.Nullable;

public final class AddressHasher {

    public static final int LENGTH = 32;
    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] CHECK_LABEL =
            "SakuraBans address-hash-key check".getBytes(StandardCharsets.UTF_8);

    private final AtomicReference<@Nullable SecretKeySpec> key = new AtomicReference<>();

    public static AddressHasher keyed(final String key) {
        final AddressHasher hasher = new AddressHasher();
        hasher.key(key);
        return hasher;
    }

    public void key(final String key) {
        Preconditions.checkArgument(!key.isEmpty(), "an empty address hash key");
        this.key.set(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
    }

    public boolean ready() {
        return this.key.get() != null;
    }

    public byte[] hash(final InetAddress address) {
        return this.mac(InetAddresses.toBytes(InetAddresses.normalize(address)));
    }

    public String hex(final InetAddress address) {
        return HexFormat.of().formatHex(this.hash(address));
    }

    public String fingerprint() {
        return HexFormat.of().formatHex(this.mac(CHECK_LABEL));
    }

    private byte[] mac(final byte[] input) {
        final SecretKeySpec current = this.key.get();
        Preconditions.checkState(current != null,
                "the address hash key isn't set yet: the storage hasn't started");
        try {
            final Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(current);
            return mac.doFinal(input);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA-256 is missing", e);
        }
    }

    @Override
    public String toString() {
        return "AddressHasher[key=***]";
    }
}
