/**
 * SakuraBans' event bus and events.
 *
 * <p>Subscribe through {@link net.serlith.sakurabans.api.event.EventBus}. Pre-events (present
 * tense, for example {@link net.serlith.sakurabans.api.event.PunishmentCreateEvent}) run before
 * an action and can cancel it. Post-events (past tense) report what happened. Events are local
 * to the server where the action happens; other servers of a network don't receive them.
 *
 * <p>Each event documents its thread. Handlers must not block: run slow work on your own
 * executor. A handler that throws is logged and skipped; the others still run.
 */
@NullMarked
package net.serlith.sakurabans.api.event;

import org.jspecify.annotations.NullMarked;
