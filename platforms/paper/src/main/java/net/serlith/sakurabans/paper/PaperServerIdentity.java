package net.serlith.sakurabans.paper;

import java.util.Optional;
import java.util.Set;
import net.serlith.sakurabans.core.platform.ServerIdentity;

final class PaperServerIdentity implements ServerIdentity {

    @Override
    public Optional<String> name() {
        return Optional.empty();
    }

    @Override
    public Set<String> backends() {
        return Set.of();
    }
}
