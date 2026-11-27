package net.serlith.sakurabans.core.geoip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

final class TarReader {

    private static final int BLOCK = 512;
    private static final long MAX_LONG_NAME = 4096;

    private TarReader() {
    }

    static boolean extract(final InputStream tar, final Predicate<String> wanted,
            final OutputStream out, final long maxSize) throws IOException {
        final byte[] header = new byte[BLOCK];
        String longName = null;
        while (true) {
            if (!readBlock(tar, header)) {
                return false;
            }
            if (zero(header)) {
                return false;
            }
            final long size = octal(header, 124, 12);
            final byte type = header[156];
            final String name = longName != null ? longName : name(header);
            longName = null;
            if (type == 'L') {
                if (size > MAX_LONG_NAME) {
                    throw new IOException("a tar entry name is too long");
                }
                final byte[] text = new byte[(int) size];
                readFully(tar, text);
                skip(tar, padding(size));
                longName = text(text, 0, text.length);
                continue;
            }
            final boolean regular = type == '0' || type == 0;
            if (regular && wanted.test(name)) {
                if (size > maxSize) {
                    throw new IOException("the database in the archive is larger than "
                            + maxSize + " bytes");
                }
                copy(tar, out, size);
                return true;
            }
            skip(tar, size + padding(size));
        }
    }

    private static String name(final byte[] header) {
        final String name = text(header, 0, 100);
        final boolean ustar = text(header, 257, 6).startsWith("ustar");
        final String prefix = ustar ? text(header, 345, 155) : "";
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    private static String text(final byte[] bytes, final int offset, final int length) {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long octal(final byte[] header, final int offset, final int length)
            throws IOException {
        long value = 0;
        boolean digits = false;
        for (int i = offset; i < offset + length; i++) {
            final byte b = header[i];
            if (b == 0 || b == ' ') {
                if (digits) {
                    break;
                }
                continue;
            }
            if (b < '0' || b > '7') {
                throw new IOException("a tar header has a size that isn't octal");
            }
            digits = true;
            value = value * 8 + (b - '0');
            if (value < 0) {
                throw new IOException("a tar entry is too large");
            }
        }
        return value;
    }

    private static long padding(final long size) {
        final long rest = size % BLOCK;
        return rest == 0 ? 0 : BLOCK - rest;
    }

    private static boolean zero(final byte[] block) {
        for (final byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean readBlock(final InputStream in, final byte[] block)
            throws IOException {
        int read = 0;
        while (read < block.length) {
            final int n = in.read(block, read, block.length - read);
            if (n < 0) {
                if (read == 0) {
                    return false;
                }
                throw new IOException("the tar archive ends inside a header");
            }
            read += n;
        }
        return true;
    }

    private static void readFully(final InputStream in, final byte[] bytes) throws IOException {
        int read = 0;
        while (read < bytes.length) {
            final int n = in.read(bytes, read, bytes.length - read);
            if (n < 0) {
                throw new IOException("the tar archive ends inside an entry");
            }
            read += n;
        }
    }

    private static void copy(final InputStream in, final OutputStream out, final long size)
            throws IOException {
        final byte[] buffer = new byte[8192];
        long left = size;
        while (left > 0) {
            final int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (n < 0) {
                throw new IOException("the tar archive ends inside an entry");
            }
            out.write(buffer, 0, n);
            left -= n;
        }
    }

    private static void skip(final InputStream in, final long count) throws IOException {
        long left = count;
        final byte[] buffer = new byte[8192];
        while (left > 0) {
            final int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (n < 0) {
                throw new IOException("the tar archive ends inside an entry");
            }
            left -= n;
        }
    }
}
