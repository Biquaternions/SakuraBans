package net.serlith.sakurabans.core.util;

public final class Pagination {

    private Pagination() {
    }

    public static int pageCount(final long total, final int pageSize) {
        requirePositive(pageSize);
        final long pages = (Math.max(0, total) + pageSize - 1) / pageSize;
        return Math.clamp(pages, 1, Integer.MAX_VALUE);
    }

    public static int clamp(final int page, final int pageCount) {
        return Math.clamp(page, 1, Math.max(1, pageCount));
    }

    public static long offset(final int page, final int pageSize) {
        requirePositive(pageSize);
        return (long) (Math.max(1, page) - 1) * pageSize;
    }

    private static void requirePositive(final int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be positive: " + pageSize);
        }
    }

}
