/**
 * Creating, revoking, editing and finding punishments.
 *
 * <p>Every method that touches the database returns a
 * {@link java.util.concurrent.CompletableFuture} that completes on a SakuraBans worker thread,
 * never on a server thread. Don't block on it from
 * a server thread. Expected outcomes (already banned, exempt, not found, ...) are results, not
 * exceptional completions; a future only completes exceptionally on an unexpected error such as a
 * lost database connection.
 */
@NullMarked
package net.serlith.sakurabans.api.punish;

import org.jspecify.annotations.NullMarked;
