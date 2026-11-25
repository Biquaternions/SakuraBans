package net.serlith.sakurabans.core.configuration.file;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class WebhooksConfig {

    private boolean enabled;
    private String url = "";
    private String username = "SakuraBans";
    private String avatarUrl = "";
    private boolean includeSilent;
    private Map<String, RouteSection> routes = defaultRoutes();

    @Getter
    @ConfigSerializable
    public static final class RouteSection {

        private boolean enabled = true;
        private String url = "";
        private String title = "";
        private String color = "#95a5a6";
        private String description = "";
        private Map<String, String> fields = new LinkedHashMap<>();
    }

    private static Map<String, RouteSection> defaultRoutes() {
        final Map<String, RouteSection> routes = new LinkedHashMap<>();
        routes.put("ban", route("Ban", "#e74c3c",
                "{victim} was banned by {operator}",
                "Reason", "{reason}",
                "Duration", "{duration}",
                "Scope", "{scope}",
                "Id", "#{display_id}"));
        routes.put("mute", route("Mute", "#e67e22",
                "{victim} was muted by {operator}",
                "Reason", "{reason}",
                "Duration", "{duration}",
                "Scope", "{scope}",
                "Id", "#{display_id}"));
        routes.put("warn", route("Warning", "#f1c40f",
                "{victim} was warned by {operator}",
                "Reason", "{reason}",
                "Id", "#{display_id}"));
        routes.put("kick", route("Kick", "#f39c12",
                "{victim} was kicked by {operator}",
                "Reason", "{reason}"));
        routes.put("unban", route("Unban", "#2ecc71",
                "{victim} was unbanned by {revoker}",
                "Reason", "{revoke_reason}",
                "Id", "#{display_id}"));
        routes.put("unmute", route("Unmute", "#2ecc71",
                "{victim} was unmuted by {revoker}",
                "Reason", "{revoke_reason}",
                "Id", "#{display_id}"));
        routes.put("unwarn", route("Warning removed", "#2ecc71",
                "A warning of {victim} was removed by {revoker}",
                "Id", "#{display_id}"));
        routes.put("void", route("Voided", "#95a5a6",
                "The {type} of {victim} was voided by {revoker}",
                "Reason", "{revoke_reason}",
                "Id", "#{display_id}"));
        routes.put("delete", route("Deleted", "#7f8c8d",
                "The {type} of {victim} was deleted",
                "Id", "#{display_id}"));
        routes.put("edit", route("Changed", "#3498db",
                "The {type} of {victim} was changed: {edit}",
                "Id", "#{display_id}"));
        routes.put("rollback", route("Staff rollback", "#9b59b6",
                "{count} punishments by {victim} were lifted by {revoker}",
                "Reason", "{revoke_reason}"));
        return routes;
    }

    private static RouteSection route(final String title, final String color,
            final String description, final String... fields) {
        final RouteSection route = new RouteSection();
        route.title = title;
        route.color = color;
        route.description = description;
        for (int i = 0; i < fields.length; i += 2) {
            route.fields.put(fields[i], fields[i + 1]);
        }
        return route;
    }
}
