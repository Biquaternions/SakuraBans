package net.serlith.sakurabans.core.platform;

import java.util.List;
import java.util.Set;

public interface ServerIdentity {

    Set<String> backends();

    List<String> fallbackServers();

    boolean behindProxy();

}
