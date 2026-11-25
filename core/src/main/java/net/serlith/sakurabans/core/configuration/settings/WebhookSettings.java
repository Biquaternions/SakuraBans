package net.serlith.sakurabans.core.configuration.settings;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record WebhookSettings(boolean enabled, @Nullable URI url, String username,
        @Nullable URI avatarUrl, boolean includeSilent, Map<String, Route> routes) {

    public static final Set<String> ROUTES = Set.of("ban", "mute", "warn", "kick", "unban",
            "unmute", "unwarn", "void", "delete", "edit", "rollback");

    public WebhookSettings {
        routes = Map.copyOf(routes);
    }

    public Optional<Route> route(final String event) {
        final Route route = this.routes.get(event);
        if (!this.enabled || route == null || !route.enabled()) {
            return Optional.empty();
        }
        return Optional.of(route);
    }

    public URI urlOf(final Route route) {
        final URI target = route.url() != null ? route.url() : this.url;
        if (target == null) {
            throw new IllegalStateException("an enabled route without a URL");
        }
        return target;
    }

    public record Route(boolean enabled, @Nullable URI url, String title, int color,
            String description, List<Field> fields) {

        public Route {
            fields = List.copyOf(fields);
        }
    }

    public record Field(String name, String value) {
    }

}
