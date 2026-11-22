package net.serlith.sakurabans.core.command;

import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.permission.Permissions;

public final class ScopeAccess {

    private final ConfigService config;

    public ScopeAccess(final ConfigService config) {
        this.config = config;
    }

    public boolean mayUse(final CommandActor actor, final Scope scope) {
        if (!this.config.settings().scopes().requirePermissions()) {
            return true;
        }
        return actor.hasPermission(switch (scope) {
            case GlobalScope global -> Permissions.SCOPE_GLOBAL;
            case ServerScope server -> Permissions.scopeServer(server.server());
            case CategoryScope category -> Permissions.scopeCategory(category.category());
        });
    }

    public boolean mayUseDefault(final CommandActor actor) {
        return !this.config.settings().scopes().requirePermissions()
                || actor.hasPermission(Permissions.SCOPE_DEFAULT);
    }
}
