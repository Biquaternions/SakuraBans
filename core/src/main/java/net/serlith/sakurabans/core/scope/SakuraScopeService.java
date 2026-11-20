package net.serlith.sakurabans.core.scope;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.scope.ScopeService;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.platform.PlatformType;

public final class SakuraScopeService implements ScopeService {

    private final ConfigService config;
    private final Platform platform;
    private final PlatformType platformType;

    public SakuraScopeService(final ConfigService config, final Platform platform,
            final PlatformType platformType) {
        this.config = config;
        this.platform = platform;
        this.platformType = platformType;
    }

    @Override
    public Optional<Scope> parse(final String text) {
        final String value = text.strip().toLowerCase(Locale.ROOT);
        if (value.equals("global") || value.equals("*")) {
            return Optional.of(GlobalScope.INSTANCE);
        }
        if (value.equals("local")) {
            return this.thisServer().map(Scope.class::cast);
        }
        final int colon = value.indexOf(':');
        if (colon < 0) {
            return Optional.empty();
        }
        final String kind = value.substring(0, colon);
        final Optional<String> name = Validator.normalizeIdentifier(value.substring(colon + 1));
        return switch (kind) {
            case "server" -> name.map(ServerScope::of);
            case "category" -> name.map(CategoryScope::of);
            default -> Optional.empty();
        };
    }

    @Override
    public String format(final Scope scope) {
        return switch (scope) {
            case GlobalScope global -> "global";
            case ServerScope server -> "server:" + server.server();
            case CategoryScope category -> "category:" + category.category();
        };
    }

    @Override
    public Scope defaultScope() {
        return switch (this.config.settings().scopes().defaultScope()) {
            case Settings.DefaultScope.Fixed fixed -> fixed.scope();
            case Settings.DefaultScope.Local local ->
                    this.thisServer().<Scope>map(server -> server).orElse(GlobalScope.INSTANCE);
        };
    }

    @Override
    public Optional<ServerScope> thisServer() {
        final Settings.Server server = this.config.settings().server();
        final boolean proxy = switch (server.role()) {
            case PROXY -> true;
            case BACKEND -> false;
            case AUTO -> this.platformType.proxy();
        };
        if (proxy) {
            return Optional.empty();
        }
        if (server.name() != null) {
            return Optional.of(ServerScope.of(server.name()));
        }
        return this.platform.serverIdentity().name()
                .flatMap(Validator::normalizeIdentifier)
                .map(ServerScope::of);
    }

    @Override
    public Set<CategoryScope> categories(final ServerScope server) {
        if (!this.thisServer().map(own -> own.server().equals(server.server())).orElse(false)) {
            return Set.of();
        }
        return this.config.settings().server().categories().stream()
                .map(CategoryScope::of)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<Scope> applicableScopes(final ServerScope server) {
        final Set<Scope> scopes = new LinkedHashSet<>();
        scopes.add(GlobalScope.INSTANCE);
        scopes.add(ServerScope.of(server.server()));
        scopes.addAll(this.categories(server));
        return Set.copyOf(scopes);
    }

    public Set<Scope> localScopes() {
        return this.thisServer().map(this::applicableScopes)
                .orElse(Set.of(GlobalScope.INSTANCE));
    }

    public Set<Scope> covering(final Scope scope) {
        return switch (scope) {
            case GlobalScope global -> Set.of(GlobalScope.INSTANCE);
            case ServerScope server -> this.applicableScopes(server);
            case CategoryScope category -> Set.of(GlobalScope.INSTANCE, category);
        };
    }

    public boolean appliesHere(final Scope scope) {
        return this.localScopes().contains(scope);
    }

}
