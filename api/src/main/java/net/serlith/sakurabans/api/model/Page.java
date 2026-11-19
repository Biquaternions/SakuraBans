package net.serlith.sakurabans.api.model;

import java.util.List;
import java.util.Optional;

/**
 * One page of results. Implemented by SakuraBans; don't implement it.
 *
 * @param <T> the item type
 */
public interface Page<T> {

    /**
     * The items on this page, newest first.
     *
     * @return the items, at most {@link #pageSize()}
     */
    List<T> items();

    /**
     * The number of items on all pages.
     *
     * @return the total
     */
    long total();

    /**
     * This page's number. For a query that pages by cursor, it is the number this page would
     * have if counted from the first one.
     *
     * @return the page, counted from 1
     */
    int page();

    /**
     * The maximum number of items per page.
     *
     * @return the page size
     */
    int pageSize();

    /**
     * A cursor for the next page, for queries that page by cursor
     * (for example {@code PunishmentQuery.after()}). Cursors are opaque; pass them back unchanged.
     *
     * @return the cursor, or empty on the last page
     */
    Optional<String> nextCursor();

    /**
     * The number of pages.
     *
     * @return the page count, at least 1
     */
    default int pageCount() {
        return (int) Math.max(1, (this.total() + this.pageSize() - 1) / this.pageSize());
    }

    /**
     * Whether a later page exists.
     *
     * @return {@code true} if {@link #page()} is before {@link #pageCount()}
     */
    default boolean hasNext() {
        return this.page() < this.pageCount();
    }
}
