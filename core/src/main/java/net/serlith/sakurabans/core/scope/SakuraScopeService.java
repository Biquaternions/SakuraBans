package net.serlith.sakurabans.core.scope;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.model.CategoryScope;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.scope.ScopeService;
import net.serlith.sakurabans.api.validation.Validator;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.Settings;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.util.Scopes;
import org.jspecify.annotations.Nullable;

public final class SakuraScopeService implements ScopeService {

    public static final String PROXY_ORIGIN = "proxy";

    private final ConfigService config;
    private final Platform platform;
    private final boolean proxy;
    private final ComponentLogger logger;

    public SakuraScopeService(final ConfigService config, final Platform platform,
            final boolean proxy, final ComponentLogger logger) {
        this.config = config;
        this.platform = platform;
        this.proxy = proxy;
        this.logger = logger;
    }

    @Override
    public Optional<Scope> parse(final String text) {
        if (text.strip().equalsIgnoreCase("local")) {
            return this.thisServer().map(Scope.class::cast);
        }
        return Scopes.parse(text);
    }

    @Override
    public String format(final Scope scope) {
        return Scopes.format(scope);
    }

    @Override
    public Scope defaultScope() {
        return this.defaultScope(null);
    }

    public Scope defaultScope(final @Nullable UUID issuer) {
        return switch (this.config.settings().scopes().defaultScope()) {
            case Settings.DefaultScope.Fixed fixed -> fixed.scope();
            case Settings.DefaultScope.Local local -> this.local(issuer);
        };
    }

    private Scope local(final @Nullable UUID issuer) {
        if (this.proxy()) {
            return Optional.ofNullable(issuer)
                    .flatMap(this.platform::player)
                    .flatMap(OnlinePlayer::currentServer)
                    .flatMap(Validator::normalizeIdentifier)
                    .<Scope>map(ServerScope::of)
                    .orElse(GlobalScope.INSTANCE);
        }
        return this.thisServer().map(Scope.class::cast).orElse(GlobalScope.INSTANCE);
    }

    public void checkServerName() {
        if (this.proxy() || this.config.settings().server().name() != null) {
            return;
        }
        final Settings.Scopes scopes = this.config.settings().scopes();
        final boolean local = scopes.defaultScope() instanceof Settings.DefaultScope.Local;
        if (!local && scopes.categories().isEmpty()) {
            return;
        }
        this.logger.warn("server.name isn't set, so this server has no name for scopes:"
                + " punishments scoped to it, or to the categories scopes.categories gives it,"
                + " don't apply here{}. Set server.name in config.yml to the name the network"
                + " knows this server by", local ? ", and scopes.default: local punishes globally"
                : "");
    }

    @Override
    public Optional<ServerScope> thisServer() {
        if (this.proxy()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.config.settings().server().name()).map(ServerScope::of);
    }

    public Optional<String> originServer() {
        if (this.proxy()) {
            return Optional.of(Optional.ofNullable(this.config.settings().server().name())
                    .orElse(PROXY_ORIGIN));
        }
        return this.thisServer().map(ServerScope::server);
    }

    public boolean proxy() {
        return this.proxy;
    }

    @Override
    public Set<CategoryScope> categories(final ServerScope server) {
        final Set<CategoryScope> categories = new LinkedHashSet<>();
        if (this.isThisServer(server)) {
            categories.addAll(this.ownCategories());
        }
        this.config.settings().scopes().categories().forEach((category, servers) -> {
            if (servers.contains(server.server())) {
                categories.add(CategoryScope.of(category));
            }
        });
        return Set.copyOf(categories);
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
        final Optional<ServerScope> own = this.thisServer();
        if (own.isPresent()) {
            return this.applicableScopes(own.get());
        }
        final Set<Scope> scopes = new LinkedHashSet<>();
        scopes.add(GlobalScope.INSTANCE);
        if (!this.proxy()) {
            scopes.addAll(this.ownCategories());
        }
        return Set.copyOf(scopes);
    }

    public Set<Scope> scopesOn(final @Nullable String server) {
        if (server == null || !this.proxy()) {
            return this.localScopes();
        }
        return Validator.normalizeIdentifier(server)
                .map(name -> this.applicableScopes(ServerScope.of(name)))
                .orElseGet(this::localScopes);
    }

    public Set<Scope> scopesOf(final UUID player) {
        if (!this.proxy()) {
            return this.localScopes();
        }
        return this.scopesOn(this.platform.player(player)
                .flatMap(OnlinePlayer::currentServer)
                .orElse(null));
    }

    public List<String> fallbackServers() {
        return this.platform.serverIdentity().fallbackServers();
    }

    public boolean appliesTo(final Scope scope, final OnlinePlayer player) {
        return this.scopesOn(player.currentServer().orElse(null)).contains(scope);
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

    public Set<String> knownServers() {
        final Set<String> servers = new TreeSet<>();
        this.thisServer().ifPresent(own -> servers.add(own.server()));
        this.platform.serverIdentity().backends().forEach(backend ->
                Validator.normalizeIdentifier(backend).ifPresent(servers::add));
        this.config.settings().scopes().categories().values().forEach(servers::addAll);
        return Collections.unmodifiableSet(servers);
    }

    public Set<String> knownCategories() {
        final Set<String> categories = new TreeSet<>(this.config.settings().scopes()
                .categories().keySet());
        this.ownCategories().forEach(category -> categories.add(category.category()));
        return Collections.unmodifiableSet(categories);
    }

    private boolean isThisServer(final ServerScope server) {
        return this.thisServer().map(own -> own.server().equals(server.server())).orElse(false);
    }

    private Set<CategoryScope> ownCategories() {
        return this.config.settings().server().categories().stream()
                .map(CategoryScope::of)
                .collect(Collectors.toUnmodifiableSet());
    }

}
