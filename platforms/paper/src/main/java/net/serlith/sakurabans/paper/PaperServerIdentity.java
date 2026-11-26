package net.serlith.sakurabans.paper;

import java.util.List;
import java.util.Set;
import net.serlith.sakurabans.core.platform.ServerIdentity;

final class PaperServerIdentity implements ServerIdentity {

    @Override
    public Set<String> backends() {
        return Set.of();
    }

    @Override
    public List<String> fallbackServers() {
        return List.of();
    }
}
