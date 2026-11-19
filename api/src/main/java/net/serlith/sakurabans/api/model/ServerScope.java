package net.serlith.sakurabans.api.model;

/**
 * The scope of one server.
 */
public non-sealed interface ServerScope extends Scope {

    /**
     * The server.
     *
     * @return the server name, lower case
     */
    String server();

    /**
     * Creates the scope.
     *
     * @param server the server name: 1-64 characters of {@code a-z}, {@code 0-9}, {@code _},
     *         {@code -}, in any case
     * @return the scope
     */
    static ServerScope of(final String server) {
        record Impl(String server) implements ServerScope {
            Impl {
                server = Checks.identifier(server, "server");
            }
        }

        return new Impl(server);
    }
}
