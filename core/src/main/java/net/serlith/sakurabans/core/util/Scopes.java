package net.serlith.sakurabans.core.util;

import java.util.Locale;
import java.util.Optional;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.validation.Validator;

public final class Scopes {

    private Scopes() {
    }

    public static String format(final Scope scope) {
        return switch (scope) {
            case GlobalScope global -> "global";
            case ServerScope server -> "server:" + server.server();
            case CategoryScope category -> "category:" + category.category();
        };
    }

    public static Optional<Scope> parse(final String text) {
        final String value = text.strip().toLowerCase(Locale.ROOT);
        if (value.equals("global") || value.equals("*")) {
            return Optional.of(GlobalScope.INSTANCE);
        }
        final int colon = value.indexOf(':');
        if (colon < 0) {
            return Optional.empty();
        }
        final Optional<String> name = Validator.normalizeIdentifier(value.substring(colon + 1));
        return switch (value.substring(0, colon)) {
            case "server" -> name.map(ServerScope::of);
            case "category" -> name.map(CategoryScope::of);
            default -> Optional.empty();
        };
    }

}
