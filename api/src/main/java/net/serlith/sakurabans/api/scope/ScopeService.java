package net.serlith.sakurabans.api.scope;

import java.util.Optional;
import java.util.Set;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;

/**
 * Parses scopes and resolves them against the configured servers and categories. Implemented by
 * SakuraBans; don't implement it.
 *
 * <p>Every method answers from the loaded configuration, without I/O, so it may be called on any
 * thread.
 */
public interface ScopeService {

    /**
     * Parses the text form of a scope: {@code global}, {@code server:<name>},
     * {@code category:<name>}, or {@code local} for this server.
     *
     * @param text the text, in any case
     * @return the scope, or empty if the text isn't a valid scope (or {@code local} on an
     *         instance without its own server scope)
     */
    Optional<Scope> parse(String text);

    /**
     * The text form of a scope, which {@link #parse(String)} reads back.
     *
     * @param scope the scope
     * @return {@code global}, {@code server:<name>} or {@code category:<name>}
     */
    String format(Scope scope);

    /**
     * The scope used when a punishment doesn't name one ({@code scopes.default}).
     *
     * @return the default scope
     */
    Scope defaultScope();

    /**
     * The scope of the server this instance runs. A proxy has none of its own.
     *
     * @return this server's scope, or empty on a proxy
     */
    Optional<ServerScope> thisServer();

    /**
     * The categories a server belongs to.
     *
     * @param server the server
     * @return its configured categories, possibly empty
     */
    Set<CategoryScope> categories(ServerScope server);

    /**
     * Every scope whose punishments apply on a server: global, the server itself and its
     * categories.
     *
     * @param server the server
     * @return the applicable scopes
     */
    Set<Scope> applicableScopes(ServerScope server);
}
