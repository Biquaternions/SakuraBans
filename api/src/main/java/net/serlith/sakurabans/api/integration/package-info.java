/**
 * Extension points other plugins implement and register with SakuraBans through
 * {@link net.serlith.sakurabans.api.integration.IntegrationRegistry}.
 *
 * <p>These are the only API interfaces meant to be implemented outside SakuraBans. SakuraBans
 * calls them on its worker threads and expects an answer within a few seconds; a call that fails
 * or times out is logged and treated as described on each interface.
 */
@NullMarked
package net.serlith.sakurabans.api.integration;

import org.jspecify.annotations.NullMarked;
