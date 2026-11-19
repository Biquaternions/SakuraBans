package net.serlith.sakurabans.core.platform;

import java.util.Optional;
import java.util.Set;

public interface ServerIdentity {

    Optional<String> name();

    Set<String> backends();

}
