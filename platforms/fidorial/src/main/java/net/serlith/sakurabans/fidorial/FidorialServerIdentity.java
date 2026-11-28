package net.serlith.sakurabans.fidorial;

import net.serlith.sakurabans.core.platform.ServerIdentity;

import java.util.List;
import java.util.Set;

final class FidorialServerIdentity implements ServerIdentity {

    @Override
    public Set<String> backends() {
        return Set.of();
    }

    @Override
    public List<String> fallbackServers() {
        return List.of();
    }
}
