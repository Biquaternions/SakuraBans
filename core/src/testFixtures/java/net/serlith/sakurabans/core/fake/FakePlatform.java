package net.serlith.sakurabans.core.fake;

import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.platform.ProfileLookup;
import net.serlith.sakurabans.core.platform.ServerIdentity;
import net.serlith.sakurabans.core.platform.TaskScheduler;
import org.jspecify.annotations.Nullable;

/** An in-memory server for core tests; hops run on the calling thread. */
public final class FakePlatform implements Platform {

    private final Map<UUID, FakeOnlinePlayer> players = new ConcurrentHashMap<>();
    private final List<String> consoleCommands = new CopyOnWriteArrayList<>();
    private final List<Component> consoleMessages = new CopyOnWriteArrayList<>();
    private final TaskScheduler scheduler = new ImmediateTaskScheduler(this.players::containsKey);
    private final Audience console = new Audience() {
        @Override
        public void sendMessage(final Component message) {
            FakePlatform.this.consoleMessages.add(message);
        }
    };
    private final Set<String> backends;
    private final List<String> fallbacks;
    private volatile boolean onlineMode = true;
    private volatile ProfileLookup profiles = ProfileLookup.NONE;

    public FakePlatform() {
        this(Set.of(), List.of());
    }

    /** A proxy with these servers and try list; players can connect to any of the servers. */
    public FakePlatform(final Set<String> backends, final List<String> fallbacks) {
        this.backends = Set.copyOf(backends);
        this.fallbacks = List.copyOf(fallbacks);
    }

    boolean accepts(final String server) {
        return this.backends.contains(server);
    }

    public FakeOnlinePlayer join(final UUID uuid, final String name, final InetAddress address) {
        return this.join(uuid, name, address, null, null);
    }

    public FakeOnlinePlayer join(final UUID uuid, final String name, final InetAddress address,
            final @Nullable Locale locale, final @Nullable String currentServer) {
        final FakeOnlinePlayer player =
                new FakeOnlinePlayer(this, uuid, name, address, locale, currentServer);
        this.players.put(uuid, player);
        return player;
    }

    public void leave(final UUID uuid) {
        this.players.remove(uuid);
    }

    public List<String> consoleCommands() {
        return List.copyOf(this.consoleCommands);
    }

    public List<Component> consoleMessages() {
        return List.copyOf(this.consoleMessages);
    }

    @Override
    public TaskScheduler scheduler() {
        return this.scheduler;
    }

    @Override
    public Optional<OnlinePlayer> player(final UUID uuid) {
        return Optional.ofNullable(this.players.get(uuid));
    }

    @Override
    public Collection<? extends OnlinePlayer> onlinePlayers() {
        return List.copyOf(this.players.values());
    }

    @Override
    public Audience console() {
        return this.console;
    }

    @Override
    public void dispatchConsoleCommand(final String command) {
        this.scheduler.runGlobal(() -> this.consoleCommands.add(command));
    }

    @Override
    public ServerIdentity serverIdentity() {
        return new ServerIdentity() {
            @Override
            public Set<String> backends() {
                return FakePlatform.this.backends;
            }

            @Override
            public List<String> fallbackServers() {
                return FakePlatform.this.fallbacks;
            }
        };
    }

    @Override
    public ProfileLookup profiles() {
        return this.profiles;
    }

    public FakePlatform profiles(final ProfileLookup profiles) {
        this.profiles = profiles;
        return this;
    }

    @Override
    public boolean onlineMode() {
        return this.onlineMode;
    }

    public FakePlatform onlineMode(final boolean onlineMode) {
        this.onlineMode = onlineMode;
        return this;
    }

    @Override
    public String platformVersion() {
        return "Fake";
    }
}
