package net.serlith.sakurabans.core.model;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record LoginAttempt(UUID uuid, String name, InetAddress address, @Nullable String server) {

    public LoginAttempt {
        Preconditions.checkNotNull(uuid, "uuid");
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(address, "address");
    }

}
