package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.serlith.sakurabans.core.platform.ServerIdentity;

final class VelocityServerIdentity implements ServerIdentity {

    private final ProxyServer proxy;

    VelocityServerIdentity(final ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public Set<String> backends() {
        return this.proxy.getAllServers().stream()
                .map(RegisteredServer::getServerInfo)
                .map(ServerInfo::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<String> fallbackServers() {
        return List.copyOf(this.proxy.getConfiguration().getAttemptConnectionOrder());
    }

    @Override
    public boolean behindProxy() {
        return false;
    }

}
