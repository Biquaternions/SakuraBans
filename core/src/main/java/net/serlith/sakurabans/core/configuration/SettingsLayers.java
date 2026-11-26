package net.serlith.sakurabans.core.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.validation.Validator;
import org.jspecify.annotations.Nullable;

/** The resolution rule of protocol 5.1, shared by settings and messages. */
public final class SettingsLayers {

    private SettingsLayers() {
    }

    /**
     * A server's categories, in order: those of {@code categories} (the effective
     * {@code scopes.categories}) that list the server, as the map declares them, then its own
     * that aren't among them.
     */
    public static List<String> categories(final @Nullable String server,
            final List<String> own, final Map<String, ? extends Collection<String>> categories) {
        final Set<String> ordered = new LinkedHashSet<>();
        if (server != null && !server.isEmpty()) {
            categories.forEach((category, servers) -> {
                if (servers.contains(server)) {
                    ordered.add(category);
                }
            });
        }
        ordered.addAll(own);
        // A name the file got wrong is reported by the file's checks, not here.
        ordered.removeIf(category -> Validator.normalizeIdentifier(category).isEmpty());
        return List.copyOf(ordered);
    }

    /** The scopes that apply to a server, the one that wins first; {@code global} last. */
    public static List<Scope> scopes(final @Nullable String server, final List<String> own,
            final Map<String, ? extends Collection<String>> categories) {
        final List<Scope> scopes = new ArrayList<>();
        if (server != null && Validator.normalizeIdentifier(server).isPresent()) {
            scopes.add(ServerScope.of(server));
        }
        categories(server, own, categories).forEach(category ->
                scopes.add(CategoryScope.of(category)));
        scopes.add(GlobalScope.INSTANCE);
        return List.copyOf(scopes);
    }
}
