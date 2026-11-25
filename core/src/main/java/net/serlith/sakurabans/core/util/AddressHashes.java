package net.serlith.sakurabans.core.util;

import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class AddressHashes {

    public static final int LENGTH = 32;

    private AddressHashes() {
    }

    public static byte[] of(final InetAddress address) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(InetAddresses.toBytes(InetAddresses.normalize(address)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing", e);
        }
    }
}
