package net.serlith.sakurabans.core.scope;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.jspecify.annotations.Nullable;

public final class SakuraScopeService implements ScopeService {

    public static final String PROXY_ORIGIN = "proxy";

    private final ConfigService config;
    private final Platform platform;
    private final boolean proxy;
    private final ServerNameDetector detector;
    private final ComponentLogger logger;
    private final AtomicBoolean warnedLocal = new AtomicBoolean();

    public SakuraScopeService(final ConfigService config, final Platform platform,
            final boolean proxy, final ServerNameDetector detector,
            final ComponentLogger logger) {
        this.config = config;
        this.platform = platform;
        this.proxy = proxy;
        this.detector = detector;
        this.logger = logger;
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
        final Optional<ServerScope> own = this.thisServer();
        if (own.isPresent()) {
            return own.get();
        }
        if (!this.warnedLocal.getAndSet(true)) {
            this.logger.warn("scopes.default is local, but this server's name isn't known yet"
                    + " (set server.name, or let a player join behind the proxy); punishing"
                    + " globally until then");
        }
        return GlobalScope.INSTANCE;
    }

    @Override
    public Optional<ServerScope> thisServer() {
        if (this.proxy()) {
            return Optional.empty();
        }
        final String configured = this.config.settings().server().name();
        return Optional.ofNullable(configured).or(this.detector::name).map(ServerScope::of);
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
