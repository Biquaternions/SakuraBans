package net.serlith.sakurabans.paper;

import java.util.List;
import java.util.Set;
import net.serlith.sakurabans.core.platform.ServerIdentity;
import org.bukkit.Server;

final class PaperServerIdentity implements ServerIdentity {

    private final Server server;

    PaperServerIdentity(final Server server) {
        this.server = server;
    }

    @Override
    public Set<String> backends() {
        return Set.of();
    }

    @Override
    public List<String> fallbackServers() {
        return List.of();
    }

    @Override
    public boolean behindProxy() {
        return this.server.getServerConfig().isProxyEnabled();
    }
}
