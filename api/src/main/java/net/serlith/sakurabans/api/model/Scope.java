package net.serlith.sakurabans.api.model;

/**
 * Where a punishment applies: {@link GlobalScope#INSTANCE everywhere}, on one
 * {@link ServerScope server}, or on a {@link CategoryScope category} of servers.
 *
 * <p>Server and category names are lower case and use only {@code a-z}, {@code 0-9}, {@code _}
 * and {@code -}, because they appear in permission nodes ({@code sakurabans.scope.server.<name>})
 * and in the {@code server:<name>} syntax. The factories lower-case their argument.
 */
public sealed interface Scope permits GlobalScope, ServerScope, CategoryScope {
}
