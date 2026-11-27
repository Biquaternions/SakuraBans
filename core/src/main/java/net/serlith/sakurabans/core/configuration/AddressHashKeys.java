package net.serlith.sakurabans.core.configuration;

import java.security.SecureRandom;
import java.util.Base64;

final class AddressHashKeys {

    static final int MIN_LENGTH = 32;
    static final String EMPTY_LINE = "address-hash-key: ''";

    private AddressHashKeys() {
    }

    static String generate(final SecureRandom random) {
        final byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String withGeneratedKey(final String shipped, final SecureRandom random) {
        final int at = shipped.indexOf("\n" + EMPTY_LINE + "\n");
        if (at < 0) {
            throw new IllegalStateException("the shipped storage.yml has no " + EMPTY_LINE);
        }
        return shipped.substring(0, at + 1) + "address-hash-key: '" + generate(random) + "'"
                + shipped.substring(at + 1 + EMPTY_LINE.length());
    }
}
