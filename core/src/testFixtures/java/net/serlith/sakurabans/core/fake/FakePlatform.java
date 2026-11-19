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
    private final @Nullable String serverName;
    private final Set<String> backends;

    public FakePlatform() {
        this(null, Set.of());
    }

    public FakePlatform(final @Nullable String serverName, final Set<String> backends) {
        this.serverName = serverName;
        this.backends = Set.copyOf(backends);
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
            public Optional<String> name() {
                return Optional.ofNullable(FakePlatform.this.serverName);
            }

            @Override
            public Set<String> backends() {
                return FakePlatform.this.backends;
            }
        };
    }

    @Override
    public boolean onlineMode() {
        return true;
    }

    @Override
    public String platformVersion() {
        return "Fake";
    }
}
