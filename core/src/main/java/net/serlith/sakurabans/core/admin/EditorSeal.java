package net.serlith.sakurabans.core.admin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@code 0x01 | nonce (12) | AES-256-GCM(key, nonce, aad, gzip(utf8(json)))} (protocol
 * section 4).
 */
public final class EditorSeal {

    public static final int KEY_BYTES = 32;
    private static final byte VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    /** Unpacked size cap, so a small blob can't expand without bound. */
    private static final int MAX_PLAINTEXT = 16 * 1024 * 1024;

    public enum Role {
        PAYLOAD("sakurabans-editor:1:payload"),
        CHANGES("sakurabans-editor:1:changes"),
        RESULT("sakurabans-editor:1:result");

        private final byte[] aad;

        Role(final String aad) {
            this.aad = aad.getBytes(StandardCharsets.US_ASCII);
        }
    }

    /** A blob that doesn't open; deliberately without detail. */
    public static final class SealException extends Exception {

        private static final long serialVersionUID = 1L;

        SealException() {
            super("the blob can't be opened");
        }
    }

    private final SecureRandom random;

    public EditorSeal(final SecureRandom random) {
        this.random = random;
    }

    public byte[] newKey() {
        final byte[] key = new byte[KEY_BYTES];
        this.random.nextBytes(key);
        return key;
    }

    public byte[] seal(final byte[] key, final Role role, final String json) {
        final byte[] nonce = new byte[NONCE_BYTES];
        this.random.nextBytes(nonce);
        try {
            final Cipher cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce, role);
            final byte[] sealed = cipher.doFinal(gzip(json.getBytes(StandardCharsets.UTF_8)));
            final byte[] blob = new byte[1 + NONCE_BYTES + sealed.length];
            blob[0] = VERSION;
            System.arraycopy(nonce, 0, blob, 1, NONCE_BYTES);
            System.arraycopy(sealed, 0, blob, 1 + NONCE_BYTES, sealed.length);
            return blob;
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM is not available", e);
        }
    }

    public static String open(final byte[] key, final Role role, final byte[] blob)
            throws SealException {
        if (key.length != KEY_BYTES || blob.length < 1 + NONCE_BYTES + TAG_BITS / 8
                || blob[0] != VERSION) {
            throw new SealException();
        }
        final byte[] nonce = Arrays.copyOfRange(blob, 1, 1 + NONCE_BYTES);
        try {
            final byte[] packed = cipher(Cipher.DECRYPT_MODE, key, nonce, role)
                    .doFinal(blob, 1 + NONCE_BYTES, blob.length - 1 - NONCE_BYTES);
            return new String(gunzip(packed), StandardCharsets.UTF_8);
        } catch (final GeneralSecurityException | IOException e) {
            throw new SealException();
        }
    }

    private static Cipher cipher(final int mode, final byte[] key, final byte[] nonce,
            final Role role) throws GeneralSecurityException {
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(role.aad);
        return cipher;
    }

    private static byte[] gzip(final byte[] data) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(data);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static byte[] gunzip(final byte[] data) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            final byte[] read = in.readNBytes(MAX_PLAINTEXT + 1);
            if (read.length > MAX_PLAINTEXT) {
                throw new IOException("too large");
            }
            return read;
        }
    }
}
