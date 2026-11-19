package net.serlith.sakurabans.api.punish;

import java.util.List;

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
     * This page's number.
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
