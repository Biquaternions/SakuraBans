/**
 * SakuraBans' event bus and events.
 *
 * <p>Subscribe through {@link net.serlith.sakurabans.api.event.EventBus}. Pre-events (present
 * tense, for example {@link net.serlith.sakurabans.api.event.PunishmentCreateEvent}) run before
 * an action and can usually cancel or change it. Post-events (past tense) report what happened;
 * the ones that are also {@link net.serlith.sakurabans.api.event.SyncedEvent}s fire on every
 * instance of a network, with {@code remote()} telling where the action happened.
 *
 * <p>Each event documents its thread. Handlers must not block: run slow work on your own
 * executor. A handler that throws is logged and skipped; the others still run.
 */
@NullMarked
package net.serlith.sakurabans.api.event;

import org.jspecify.annotations.NullMarked;
